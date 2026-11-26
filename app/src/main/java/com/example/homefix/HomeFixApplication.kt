package com.example.homefix

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

class HomeFixApplication : Application() {

    companion object {
        const val CHANNEL_ID = "home_fix"
        const val NOTIFICATION_ID = 1

        fun isAccessibilityEnabled(context: Context): Boolean {
            val cn = ComponentName(context, HomeFixService::class.java)
            val raw = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return raw.split(':').any { ComponentName.unflattenFromString(it) == cn }
        }
    }

    override fun onCreate() {
        super.onCreate()
        HomeFixService.enabled = Prefs.isEnabled(this)
        Notifications.ensureChannel(this)
    }
}
