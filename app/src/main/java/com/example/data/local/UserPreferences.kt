package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("2fa_generate_prefs", Context.MODE_PRIVATE)

    var isDarkMode: Boolean
        get() = prefs.getBoolean("is_dark_mode", true)
        set(value) = prefs.edit().putBoolean("is_dark_mode", value).apply()

    var autoCopy: Boolean
        get() = prefs.getBoolean("auto_copy", true)
        set(value) = prefs.edit().putBoolean("auto_copy", value).apply()

    var autoPaste: Boolean
        get() = prefs.getBoolean("auto_paste", true)
        set(value) = prefs.edit().putBoolean("auto_paste", value).apply()

    var hapticFeedback: Boolean
        get() = prefs.getBoolean("haptic_feedback", true)
        set(value) = prefs.edit().putBoolean("haptic_feedback", value).apply()

    var language: String
        get() = prefs.getString("language", "English") ?: "English"
        set(value) = prefs.edit().putString("language", value).apply()

    var isFirstLaunch: Boolean
        get() = prefs.getBoolean("is_first_launch", true)
        set(value) = prefs.edit().putBoolean("is_first_launch", value).apply()

    var isOverlayEnabled: Boolean
        get() = prefs.getBoolean("is_overlay_enabled", false)
        set(value) = prefs.edit().putBoolean("is_overlay_enabled", value).apply()

    var isBubbleHidden: Boolean
        get() = prefs.getBoolean("is_bubble_hidden", false)
        set(value) = prefs.edit().putBoolean("is_bubble_hidden", value).apply()

    var bubbleX: Int
        get() = prefs.getInt("bubble_x", 100)
        set(value) = prefs.edit().putInt("bubble_x", value).apply()

    var bubbleY: Int
        get() = prefs.getInt("bubble_y", 300)
        set(value) = prefs.edit().putInt("bubble_y", value).apply()
}
