package com.example.homefix

import android.content.Context

object Prefs {
    private const val NAME = "home_fix_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TIMEOUT_MS = "timeout_ms"
    private const val KEY_REACTION_MS = "reaction_ms"
    private const val KEY_SECOND_DELAY_MS = "second_delay_ms"
    private const val KEY_DEBUG = "debug"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun getTimeoutMs(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(KEY_TIMEOUT_MS, 3000L)

    fun setTimeoutMs(context: Context, value: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TIMEOUT_MS, value).apply()
    }

    fun getReactionMs(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(KEY_REACTION_MS, 0L)

    fun setReactionMs(context: Context, value: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_REACTION_MS, value).apply()
    }

    fun getSecondDelayMs(context: Context): Long =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getLong(KEY_SECOND_DELAY_MS, 600L)

    fun setSecondDelayMs(context: Context, value: Long) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SECOND_DELAY_MS, value).apply()
    }

    fun isDebug(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_DEBUG, false)

    fun setDebug(context: Context, value: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DEBUG, value).apply()
    }
}
