package com.example.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FirebaseSyncManager
import com.example.data.firebase.GlobalStats
import com.example.data.local.AppDatabase
import com.example.data.local.TwoFactorKey
import com.example.data.local.UserPreferences
import com.example.security.KeystoreEncryption
import com.example.totp.Base32
import com.example.totp.TotpGenerator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Authenticated(val email: String, val name: String) : AuthState()
    data class Error(val message: String) : AuthState()
}

data class TotpItemState(
    val keyEntity: TwoFactorKey,
    val plainSecret: String,
    val currentCode: String,
    val remainingSeconds: Int,
    val progress: Float
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val keyDao = db.twoFactorKeyDao()
    val prefs = UserPreferences(application)
    val syncManager = FirebaseSyncManager()

    private val firebaseAuth by lazy { FirebaseAuth.getInstance() }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _externalAddRequested = MutableStateFlow(false)
    val externalAddRequested: StateFlow<Boolean> = _externalAddRequested.asStateFlow()

    fun openAddDialogRequested() {
        _externalAddRequested.value = true
    }

    fun consumeExternalAddRequest() {
        _externalAddRequested.value = false
    }

    private val _rawKeys = MutableStateFlow<List<TwoFactorKey>>(emptyList())

    private val _totpItems = MutableStateFlow<List<TotpItemState>>(emptyList())
    val totpItems: StateFlow<List<TotpItemState>> = _totpItems.asStateFlow()

    val stats: StateFlow<GlobalStats> = syncManager.stats

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isDarkMode = MutableStateFlow(prefs.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _isOverlayEnabled = MutableStateFlow(prefs.isOverlayEnabled)
    val isOverlayEnabled: StateFlow<Boolean> = _isOverlayEnabled.asStateFlow()

    init {
        checkAutoLogin()
        observeRoomKeys()
        startTotpTimer()
        val context = getApplication<Application>()
        if (prefs.isOverlayEnabled && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))) {
            startFloatingService(com.example.service.FloatingBubbleService.ACTION_START_SERVICE)
        }
    }

    private fun checkAutoLogin() {
        val user = firebaseAuth.currentUser
        if (user != null) {
            _authState.value = AuthState.Authenticated(
                email = user.email ?: "user@2fa.app",
                name = user.displayName ?: user.email?.substringBefore("@") ?: "User"
            )
            syncManager.syncUserProfile(user.email ?: "", user.displayName ?: "")
        } else {
            // Guest mode by default so app is fully usable right away
            _authState.value = AuthState.Authenticated("guest@2fagenerate.app", "Guest User")
        }
    }

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            emitToast("Please enter email and password")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    val mail = user?.email ?: email
                    val name = user?.displayName ?: mail.substringBefore("@")
                    _authState.value = AuthState.Authenticated(mail, name)
                    syncManager.syncUserProfile(mail, name)
                    emitToast("Welcome back, $name!")
                }
                .addOnFailureListener { e ->
                    _authState.value = AuthState.Error(e.message ?: "Authentication failed")
                    emitToast("Auth Error: ${e.message}")
                }
        }
    }

    fun register(email: String, pass: String) {
        if (email.isBlank() || pass.length < 6) {
            emitToast("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            firebaseAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { authResult ->
                    val user = authResult.user
                    val mail = user?.email ?: email
                    val name = mail.substringBefore("@")
                    _authState.value = AuthState.Authenticated(mail, name)
                    syncManager.syncUserProfile(mail, name)
                    emitToast("Account created successfully!")
                }
                .addOnFailureListener { e ->
                    _authState.value = AuthState.Error(e.message ?: "Registration failed")
                    emitToast("Registration Error: ${e.message}")
                }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank()) {
            emitToast("Please enter your email address")
            return
        }
        firebaseAuth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                emitToast("Password reset email sent!")
            }
            .addOnFailureListener { e ->
                emitToast("Error: ${e.message}")
            }
    }

    fun logout() {
        firebaseAuth.signOut()
        _authState.value = AuthState.Idle
        emitToast("Logged out")
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        recalculateTotpItems()
    }

    fun toggleDarkMode(enabled: Boolean) {
        prefs.isDarkMode = enabled
        _isDarkMode.value = enabled
    }

    fun toggleOverlay(enabled: Boolean) {
        val context = getApplication<Application>()
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                prefs.isOverlayEnabled = false
                _isOverlayEnabled.value = false
                emitToast("Please enable 'Display over other apps' permission")
                return
            }
            prefs.isOverlayEnabled = true
            _isOverlayEnabled.value = true
            prefs.isBubbleHidden = false
            startFloatingService(com.example.service.FloatingBubbleService.ACTION_SHOW_BUBBLE)
            emitToast("2FA Overlay Floating Bubble Enabled")
        } else {
            prefs.isOverlayEnabled = false
            _isOverlayEnabled.value = false
            startFloatingService(com.example.service.FloatingBubbleService.ACTION_STOP_SERVICE)
            emitToast("2FA Overlay Disabled")
        }
    }

    fun showBubble() {
        val context = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            emitToast("Please enable 'Display over other apps' permission")
            return
        }
        prefs.isOverlayEnabled = true
        prefs.isBubbleHidden = false
        _isOverlayEnabled.value = true
        startFloatingService(com.example.service.FloatingBubbleService.ACTION_SHOW_BUBBLE)
        emitToast("Floating Bubble Visible")
    }

    fun hideBubble() {
        prefs.isBubbleHidden = true
        startFloatingService(com.example.service.FloatingBubbleService.ACTION_HIDE_BUBBLE)
        emitToast("Floating Bubble Hidden")
    }

    private fun startFloatingService(action: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, com.example.service.FloatingBubbleService::class.java).apply {
            this.action = action
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun observeRoomKeys() {
        viewModelScope.launch {
            keyDao.getAllKeys().collect { keys ->
                _rawKeys.value = keys
                recalculateTotpItems()
            }
        }
    }

    private fun startTotpTimer() {
        viewModelScope.launch {
            while (true) {
                recalculateTotpItems()
                delay(1000L)
            }
        }
    }

    private fun recalculateTotpItems() {
        val nowSec = System.currentTimeMillis() / 1000L
        val rem = TotpGenerator.getRemainingSeconds(nowSec)
        val progress = TotpGenerator.getProgressFraction(nowSec)
        val query = _searchQuery.value.trim().lowercase()

        val items = _rawKeys.value
            .filter {
                query.isEmpty() || it.label.lowercase().contains(query) || it.issuer.lowercase().contains(query)
            }
            .map { keyEntity ->
                val plainSecret = KeystoreEncryption.decrypt(keyEntity.encryptedSecret)
                val code = try {
                    TotpGenerator.generateTotp(plainSecret, nowSec)
                } catch (e: Exception) {
                    "------"
                }
                TotpItemState(
                    keyEntity = keyEntity,
                    plainSecret = plainSecret,
                    currentCode = code,
                    remainingSeconds = rem,
                    progress = progress
                )
            }
        _totpItems.value = items
    }

    fun addSecretFromInput(label: String, issuer: String, secret: String, autoCopy: Boolean = true) {
        val cleanSecret = secret.uppercase().replace(" ", "").replace("-", "").trim()
        if (!Base32.isValidBase32(cleanSecret)) {
            emitToast("Invalid 2FA Secret")
            triggerHapticFeedback(success = false)
            return
        }

        val encrypted = KeystoreEncryption.encrypt(cleanSecret)
        val newKey = TwoFactorKey(
            label = if (label.isBlank()) "Account ${System.currentTimeMillis() % 1000}" else label,
            issuer = if (issuer.isBlank()) "2FA" else issuer,
            encryptedSecret = encrypted,
            lastGeneratedTime = System.currentTimeMillis()
        )

        viewModelScope.launch {
            keyDao.insertKey(newKey)
            syncManager.recordCodeGenerated()

            val currentCode = TotpGenerator.generateTotp(cleanSecret)
            if (autoCopy || prefs.autoCopy) {
                copyToClipboard(currentCode)
                emitToast("2FA Code Copied Successfully")
            } else {
                emitToast("2FA Secret Added Successfully")
            }
            triggerHapticFeedback(success = true)
        }
    }

    fun generateFreshCodeAndCopy(totpItem: TotpItemState) {
        viewModelScope.launch {
            try {
                val code = TotpGenerator.generateTotp(totpItem.plainSecret)
                copyToClipboard(code)
                keyDao.updateKey(totpItem.keyEntity.copy(lastGeneratedTime = System.currentTimeMillis()))
                syncManager.recordCodeGenerated()
                emitToast("2FA Code Copied Successfully")
                triggerHapticFeedback(success = true)
            } catch (e: Exception) {
                emitToast("Error generating code: ${e.message}")
            }
        }
    }

    fun renameKey(id: Int, newLabel: String, newIssuer: String) {
        viewModelScope.launch {
            val key = keyDao.getKeyById(id) ?: return@launch
            keyDao.updateKey(key.copy(label = newLabel, issuer = newIssuer))
            emitToast("Account updated")
            triggerHapticFeedback(success = true)
        }
    }

    fun deleteKey(id: Int) {
        viewModelScope.launch {
            keyDao.deleteKeyById(id)
            emitToast("Secret key deleted")
            triggerHapticFeedback(success = true)
        }
    }

    fun copyToClipboard(text: String) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("2FA Code", text)
        clipboard.setPrimaryClip(clip)
    }

    fun readClipboardContent(): String {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                return clip.getItemAt(0).text?.toString() ?: ""
            }
        }
        return ""
    }

    private fun triggerHapticFeedback(success: Boolean) {
        if (!prefs.hapticFeedback) return
        val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (success) {
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                } else {
                    VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    private fun emitToast(msg: String) {
        viewModelScope.launch {
            _toastEvent.emit(msg)
            Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
        }
    }
}
