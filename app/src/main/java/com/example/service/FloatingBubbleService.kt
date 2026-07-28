package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.TwoFactorKey
import com.example.data.local.UserPreferences
import com.example.security.KeystoreEncryption
import com.example.totp.TotpGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class FloatingBubbleService : Service() {

    companion object {
        const val CHANNEL_ID = "2fa_floating_bubble_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_SERVICE = "action_start_service"
        const val ACTION_STOP_SERVICE = "action_stop_service"
        const val ACTION_SHOW_BUBBLE = "action_show_bubble"
        const val ACTION_HIDE_BUBBLE = "action_hide_bubble"
        const val ACTION_TOGGLE_BUBBLE = "action_toggle_bubble"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: UserPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var overlayView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private var isHidden = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var keysContainerLayout: LinearLayout? = null
    private var timerTv: TextView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = UserPreferences(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        if (action == ACTION_STOP_SERVICE) {
            prefs.isOverlayEnabled = false
            removeBubbleWindow()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            prefs.isOverlayEnabled = false
            removeBubbleWindow()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        when (action) {
            ACTION_HIDE_BUBBLE -> {
                prefs.isBubbleHidden = true
                hideBubbleWindow()
                startForeground(NOTIFICATION_ID, buildNotification(hidden = true))
            }
            ACTION_SHOW_BUBBLE -> {
                prefs.isOverlayEnabled = true
                prefs.isBubbleHidden = false
                showOrUpdateBubbleWindow()
                startForeground(NOTIFICATION_ID, buildNotification(hidden = false))
            }
            ACTION_TOGGLE_BUBBLE -> {
                if (isHidden) {
                    prefs.isBubbleHidden = false
                    showOrUpdateBubbleWindow()
                    startForeground(NOTIFICATION_ID, buildNotification(hidden = false))
                } else {
                    prefs.isBubbleHidden = true
                    hideBubbleWindow()
                    startForeground(NOTIFICATION_ID, buildNotification(hidden = true))
                }
            }
            else -> {
                prefs.isOverlayEnabled = true
                startForeground(NOTIFICATION_ID, buildNotification(hidden = prefs.isBubbleHidden))
                if (!prefs.isBubbleHidden) {
                    showOrUpdateBubbleWindow()
                }
            }
        }

        return START_STICKY
    }

    private fun showOrUpdateBubbleWindow() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please enable 'Display over other apps' permission", Toast.LENGTH_LONG).show()
            return
        }

        isHidden = false

        if (overlayView == null) {
            createBubbleView()
        } else {
            overlayView?.visibility = View.VISIBLE
        }
    }

    private fun hideBubbleWindow() {
        isHidden = true
        overlayView?.visibility = View.GONE
        Toast.makeText(this, "2FA Floating Bubble hidden. Re-enable from Settings or App.", Toast.LENGTH_SHORT).show()
    }

    private fun removeBubbleWindow() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                // Ignore if already removed
            }
        }
        overlayView = null
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun createBubbleView() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }

        // 1. Expanded Menu Card
        val menuBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16f), dpToPx(16f), dpToPx(16f), dpToPx(16f))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dpToPx(320f), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(8f))
            }

            // Dark sleek dialog background
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(20f).toFloat()
                setColor(0xF00F172A.toInt()) // Deep dark slate navy
                setStroke(dpToPx(2f), 0xFF00E5FF.toInt()) // Bright cyan border
            }
            elevation = dpToPx(16f).toFloat()
        }

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "⚡ 2FA Authenticator"
            textSize = 16f
            setTextColor(0xFF00E5FF.toInt())
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(titleTv)

        val remainingTimerTv = TextView(this).apply {
            text = "30s"
            textSize = 13f
            setTextColor(0xFFFFD54F.toInt())
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        timerTv = remainingTimerTv
        headerRow.addView(remainingTimerTv)

        menuBox.addView(headerRow)

        // Divider
        val divider = View(this).apply {
            setBackgroundColor(0x3300E5FF.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1f)).apply {
                setMargins(0, dpToPx(10f), 0, dpToPx(10f))
            }
        }
        menuBox.addView(divider)

        // Scrollable list for saved 2FA keys
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(240f))
        }

        val keysLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        keysContainerLayout = keysLayout
        scrollView.addView(keysLayout)
        menuBox.addView(scrollView)

        // Action Buttons Row
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(10f), 0, 0)
        }

        // Button: Add 2FA Key
        val addKeyBtn = TextView(this).apply {
            text = "➕ Add 2FA Key"
            textSize = 14f
            setTextColor(0xFF00E5FF.toInt())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(12f), dpToPx(10f), dpToPx(12f), dpToPx(10f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12f).toFloat()
                setColor(0x2200E5FF.toInt())
                setStroke(dpToPx(1f), 0xFF00E5FF.toInt())
            }
            setOnClickListener {
                isExpanded = false
                menuBox.visibility = View.GONE
                openAppWithAction("ADD_KEY")
            }
        }
        actionsLayout.addView(addKeyBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dpToPx(8f))
        })

        // Row with Hide and Open App
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val hideBtn = TextView(this).apply {
            text = "👁 Hide"
            textSize = 13f
            setTextColor(0xFFFFD54F.toInt())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(10f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(0, 0, dpToPx(4f), 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12f).toFloat()
                setColor(0x22FFD54F.toInt())
            }
            setOnClickListener {
                isExpanded = false
                menuBox.visibility = View.GONE
                hideBubbleWindow()
                startForeground(NOTIFICATION_ID, buildNotification(hidden = true))
            }
        }
        bottomRow.addView(hideBtn)

        val openAppBtn = TextView(this).apply {
            text = "🚀 App"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dpToPx(10f), dpToPx(8f), dpToPx(10f), dpToPx(8f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dpToPx(4f), 0, 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(12f).toFloat()
                setColor(0x33FFFFFF.toInt())
            }
            setOnClickListener {
                isExpanded = false
                menuBox.visibility = View.GONE
                openAppWithAction("OPEN_HOME")
            }
        }
        bottomRow.addView(openAppBtn)

        actionsLayout.addView(bottomRow)
        menuBox.addView(actionsLayout)

        // 2. Beautiful Circular Main Icon Bubble
        val bubbleIcon = TextView(this).apply {
            text = "🔑 2FA"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            contentDescription = "2FA Floating Bubble"

            // Circular Shape with Cyan Gradient
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF00E5FF.toInt(), 0xFF0052D4.toInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dpToPx(3f), Color.WHITE)
            }
            elevation = dpToPx(12f).toFloat()
        }

        container.addView(menuBox)
        val bubbleSize = dpToPx(64f)
        val bubbleLp = LinearLayout.LayoutParams(bubbleSize, bubbleSize).apply {
            setMargins(0, dpToPx(6f), 0, 0)
        }
        container.addView(bubbleIcon, bubbleLp)

        // WindowManager Layout Parameters
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val savedX = prefs.bubbleX
        val savedY = prefs.bubbleY

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        // Draggable Touch Listener on Circular Icon
        bubbleIcon.setOnTouchListener { _, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x
                    initialY = p.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = initialX + (event.rawX - initialTouchX).toInt()
                    p.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(container, p)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = Math.abs(event.rawX - initialTouchX)
                    val diffY = Math.abs(event.rawY - initialTouchY)
                    if (diffX < 14 && diffY < 14) {
                        // Clicked! Toggle menu expansion
                        isExpanded = !isExpanded
                        menuBox.visibility = if (isExpanded) View.VISIBLE else View.GONE
                        if (isExpanded) {
                            refreshKeysList()
                        }
                    } else {
                        // Save new position
                        prefs.bubbleX = p.x
                        prefs.bubbleY = p.y
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = container
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                windowManager.addView(overlayView, params)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Periodic TOTP Refresh & Countdown
        serviceScope.launch {
            while (true) {
                val remainingSeconds = 30 - ((System.currentTimeMillis() / 1000) % 30).toInt()
                timerTv?.text = "${remainingSeconds}s"

                if (isExpanded && overlayView != null && !isHidden) {
                    refreshKeysList()
                }
                delay(1000L)
            }
        }
    }

    private fun openAppWithAction(action: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("LAUNCH_ACTION", action)
        }
        startActivity(intent)
    }

    private fun refreshKeysList() {
        val layout = keysContainerLayout ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val keys = db.twoFactorKeyDao().getAllKeys().firstOrNull() ?: emptyList()

                serviceScope.launch(Dispatchers.Main) {
                    layout.removeAllViews()
                    if (keys.isEmpty()) {
                        val emptyTv = TextView(this@FloatingBubbleService).apply {
                            text = "No 2FA keys saved yet.\nTap '+ Add 2FA Key' below."
                            textSize = 13f
                            setTextColor(0xAAFFFFFF.toInt())
                            gravity = Gravity.CENTER
                            setPadding(16, 32, 16, 32)
                        }
                        layout.addView(emptyTv)
                    } else {
                        keys.forEach { keyEntity ->
                            val cardView = createKeyCardView(keyEntity)
                            layout.addView(cardView)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createKeyCardView(keyEntity: TwoFactorKey): View {
        val plainSecret = KeystoreEncryption.decrypt(keyEntity.encryptedSecret)
        val rawCode = TotpGenerator.generateTotp(plainSecret)
        val formattedCode = if (rawCode.length == 6) "${rawCode.substring(0, 3)} ${rawCode.substring(3)}" else rawCode

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(14f), dpToPx(12f), dpToPx(14f), dpToPx(12f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(14f).toFloat()
                setColor(0x22FFFFFF.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dpToPx(10f))
            }
        }

        // Issuer & Account
        val displayName = if (keyEntity.issuer.isNotEmpty() && keyEntity.label.isNotEmpty()) {
            "${keyEntity.issuer} (${keyEntity.label})"
        } else keyEntity.issuer.ifEmpty { keyEntity.label }

        val nameTv = TextView(this).apply {
            text = displayName
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }
        cardLayout.addView(nameTv)

        // Code & Copy Row
        val codeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpToPx(6f), 0, 0)
        }

        val codeTv = TextView(this).apply {
            text = formattedCode
            textSize = 20f
            setTextColor(0xFF00E5FF.toInt())
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        codeRow.addView(codeTv)

        val newCodeBtn = TextView(this).apply {
            text = "🔄 New Code"
            textSize = 12f
            setTextColor(0xFF00E5FF.toInt())
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f).toFloat()
                setColor(0x3300E5FF.toInt())
                setStroke(dpToPx(1f), 0xFF00E5FF.toInt())
            }
            setOnClickListener {
                val freshCode = TotpGenerator.generateTotp(plainSecret)
                val newFormatted = if (freshCode.length == 6) "${freshCode.substring(0, 3)} ${freshCode.substring(3)}" else freshCode
                codeTv.text = newFormatted

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", freshCode))
                Toast.makeText(applicationContext, "🔄 New Code ($freshCode) Copied!", Toast.LENGTH_SHORT).show()
            }
        }
        codeRow.addView(newCodeBtn)

        val copyBtn = TextView(this).apply {
            text = "📋 Copy"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dpToPx(6f), 0, 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f).toFloat()
                setColor(0x33FFFFFF.toInt())
            }
            setOnClickListener {
                val currentSecret = KeystoreEncryption.decrypt(keyEntity.encryptedSecret)
                val currentCode = TotpGenerator.generateTotp(currentSecret)
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("2FA Code", currentCode))
                Toast.makeText(applicationContext, "Code $currentCode Copied!", Toast.LENGTH_SHORT).show()
            }
        }
        codeRow.addView(copyBtn)

        val deleteBtn = TextView(this).apply {
            text = "🗑"
            textSize = 12f
            setTextColor(0xFFFF5252.toInt())
            setPadding(dpToPx(10f), dpToPx(6f), dpToPx(10f), dpToPx(6f))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dpToPx(6f), 0, 0, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10f).toFloat()
                setColor(0x33FF5252.toInt())
            }
            setOnClickListener {
                serviceScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.twoFactorKeyDao().deleteKeyById(keyEntity.id)
                    refreshKeysList()
                    serviceScope.launch(Dispatchers.Main) {
                        Toast.makeText(applicationContext, "Key deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        codeRow.addView(deleteBtn)

        cardLayout.addView(codeRow)
        return cardLayout
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "2FA Floating Bubble Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for 2FA overlay bubble service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(hidden: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(this, FloatingBubbleService::class.java).apply {
            action = ACTION_TOGGLE_BUBBLE
        }
        val togglePendingIntent = PendingIntent.getService(
            this, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (hidden) "Bubble Hidden (Tap 'Show Bubble' to reveal)" else "Floating 2FA Bubble Active"
        val toggleActionText = if (hidden) "Show Bubble" else "Hide Bubble"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("2FA Generate Service")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleActionText, togglePendingIntent)
            .build()
    }

    override fun onDestroy() {
        removeBubbleWindow()
        super.onDestroy()
    }
}
