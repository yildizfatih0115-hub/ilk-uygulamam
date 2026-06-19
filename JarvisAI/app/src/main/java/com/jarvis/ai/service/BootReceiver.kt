package com.jarvis.ai.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jarvis.ai.utils.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = Prefs(context)
            if (prefs.autoStart) {
                val serviceIntent = Intent(context, JarvisService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
