package com.example.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GlobalStats(
    val totalUsers: Int = 1250,
    val totalCodesGenerated: Int = 8430,
    val todayCodesGenerated: Int = 342,
    val myCodesGenerated: Int = 0
)

class FirebaseSyncManager {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val dbUrl = "https://telegram-mini-app-1b599-default-rtdb.asia-southeast1.firebasedatabase.app/"

    private val _stats = MutableStateFlow(GlobalStats())
    val stats: StateFlow<GlobalStats> = _stats

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val rtdb: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(dbUrl).apply {
            setPersistenceEnabled(true)
        }
    }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    init {
        initRealtimeListeners()
    }

    private fun initRealtimeListeners() {
        try {
            val statsRef = rtdb.getReference("global_stats")
            statsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val totalUsers = snapshot.child("total_users").getValue(Int::class.java) ?: 1250
                    val totalCodes = snapshot.child("total_codes_generated").getValue(Int::class.java) ?: 8430
                    val todayCodes = snapshot.child("today_codes_generated").getValue(Int::class.java) ?: 342
                    
                    val userId = auth.currentUser?.uid ?: "guest"
                    val myCodes = snapshot.child("user_codes").child(userId).getValue(Int::class.java) ?: 0

                    _stats.value = GlobalStats(
                        totalUsers = totalUsers,
                        totalCodesGenerated = totalCodes,
                        todayCodesGenerated = todayCodes,
                        myCodesGenerated = myCodes
                    )
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseSync", "RTDB Listener cancelled: ${error.message}")
                    fetchStatsViaRest()
                }
            })
        } catch (e: Exception) {
            Log.e("FirebaseSync", "RTDB init exception", e)
            fetchStatsViaRest()
        }
    }

    private fun fetchStatsViaRest() {
        scope.launch {
            try {
                val request = Request.Builder()
                    .url("${dbUrl}global_stats.json")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotEmpty() && body != "null") {
                            val json = JSONObject(body)
                            val totalUsers = json.optInt("total_users", 1250)
                            val totalCodes = json.optInt("total_codes_generated", 8430)
                            val todayCodes = json.optInt("today_codes_generated", 342)
                            val userId = auth.currentUser?.uid ?: "guest"

                            val myCodes = if (json.has("user_codes")) {
                                val userCodesObj = json.optJSONObject("user_codes")
                                userCodesObj?.optInt(userId, 0) ?: 0
                            } else 0

                            _stats.value = GlobalStats(
                                totalUsers = totalUsers,
                                totalCodesGenerated = totalCodes,
                                todayCodesGenerated = todayCodes,
                                myCodesGenerated = myCodes
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Rest fetch error", e)
            }
        }
    }

    fun recordCodeGenerated() {
        scope.launch {
            val current = _stats.value
            val newTotal = current.totalCodesGenerated + 1
            val newToday = current.todayCodesGenerated + 1
            val newMy = current.myCodesGenerated + 1

            _stats.value = current.copy(
                totalCodesGenerated = newTotal,
                todayCodesGenerated = newToday,
                myCodesGenerated = newMy
            )

            val userId = auth.currentUser?.uid ?: "guest"

            // 1. Update Firebase RTDB via SDK
            try {
                val ref = rtdb.getReference("global_stats")
                ref.child("total_codes_generated").setValue(newTotal)
                ref.child("today_codes_generated").setValue(newToday)
                ref.child("user_codes").child(userId).setValue(newMy)
            } catch (e: Exception) {
                Log.e("FirebaseSync", "SDK write failed, trying REST", e)
                // 2. REST Fallback
                try {
                    val patchJson = JSONObject().apply {
                        put("total_codes_generated", newTotal)
                        put("today_codes_generated", newToday)
                    }.toString()

                    val req = Request.Builder()
                        .url("${dbUrl}global_stats.json")
                        .patch(patchJson.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(req).execute().close()

                    val userPatch = JSONObject().apply {
                        put(userId, newMy)
                    }.toString()

                    val userReq = Request.Builder()
                        .url("${dbUrl}global_stats/user_codes.json")
                        .patch(userPatch.toRequestBody(jsonMediaType))
                        .build()

                    client.newCall(userReq).execute().close()
                } catch (ex: Exception) {
                    Log.e("FirebaseSync", "REST patch error", ex)
                }
            }

            // Sync User activity to Firestore
            if (userId != "guest") {
                try {
                    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                    val userDoc = firestore.collection("users").document(userId)
                    userDoc.set(
                        mapOf(
                            "last_generated_time" to System.currentTimeMillis(),
                            "total_generated" to newMy,
                            "last_active_date" to todayDate
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                } catch (e: Exception) {
                    Log.e("FirebaseSync", "Firestore sync error", e)
                }
            }
        }
    }

    fun syncUserProfile(email: String, name: String) {
        val user = auth.currentUser ?: return
        scope.launch {
            try {
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                val profileData = mapOf(
                    "email" to email,
                    "name" to name.ifEmpty { email.substringBefore("@") },
                    "registration_date" to todayDate,
                    "uid" to user.uid
                )
                firestore.collection("users").document(user.uid).set(
                    profileData,
                    com.google.firebase.firestore.SetOptions.merge()
                )
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Sync profile error", e)
            }
        }
    }
}
