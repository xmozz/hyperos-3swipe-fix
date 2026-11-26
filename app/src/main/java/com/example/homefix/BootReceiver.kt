package com.example.homefix

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.isEnabled(context)) return

        Notifications.ensureChannel(context)
        val accessible = HomeFixApplication.isAccessibilityEnabled(context)
        if (accessible) {
            // Re-post the persistent notification immediately; the system will
            // also rebind the accessibility service, whose onServiceConnected()
            // re-attaches this same notification to the foreground service.
            context.getSystemService(NotificationManager::class.java)
                .notify(HomeFixApplication.NOTIFICATION_ID, Notifications.build(context))
        }
        Log.d(HomeFixService.TAG, "boot completed, accessibility=$accessible")
    }
}
