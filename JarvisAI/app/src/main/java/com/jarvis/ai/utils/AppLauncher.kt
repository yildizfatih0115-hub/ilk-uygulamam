package com.jarvis.ai.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

class AppLauncher(private val context: Context) {

    fun launch(packageOrAction: String, appName: String): String {
        return try {
            val pm = context.packageManager

            // Normal uygulama paketi mi?
            if (packageOrAction.contains(".")) {
                val intent = pm.getLaunchIntentForPackage(packageOrAction)
                if (intent != null) {
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(intent)
                    "$appName açılıyor efendim."
                } else {
                    // Yüklü değil, Play Store'a git
                    val storeIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$packageOrAction"))
                    storeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(storeIntent)
                    "$appName yüklü değil efendim. Play Store açılıyor."
                }
            } else {
                // Action intent
                val intent = Intent(packageOrAction)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                "$appName açılıyor efendim."
            }
        } catch (e: Exception) {
            "Uygulama açılamadı efendim: ${e.message?.take(50)}"
        }
    }

    fun isInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
