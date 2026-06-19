package com.jarvis.ai.utils

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(v) = prefs.edit().putString("gemini_api_key", v).apply()

    var hotwordEnabled: Boolean
        get() = prefs.getBoolean("hotword_enabled", true)
        set(v) = prefs.edit().putBoolean("hotword_enabled", v).apply()

    var ttsEnabled: Boolean
        get() = prefs.getBoolean("tts_enabled", true)
        set(v) = prefs.edit().putBoolean("tts_enabled", v).apply()

    var gameMode: Boolean
        get() = prefs.getBoolean("game_mode", false)
        set(v) = prefs.edit().putBoolean("game_mode", v).apply()

    var jarvisName: String
        get() = prefs.getString("jarvis_name", "JARVIS") ?: "JARVIS"
        set(v) = prefs.edit().putString("jarvis_name", v).apply()

    var gibberLinkKey: String
        get() = prefs.getString("gibberlink_key", "") ?: ""
        set(v) = prefs.edit().putString("gibberlink_key", v).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", false)
        set(v) = prefs.edit().putBoolean("auto_start", v).apply()
}
