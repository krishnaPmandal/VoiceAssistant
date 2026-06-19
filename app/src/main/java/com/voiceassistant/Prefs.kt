package com.voiceassistant

import android.content.Context
import androidx.core.content.edit

object Prefs {
    private const val NAME = "va_prefs"
    private const val KEY_NAME = "assistant_name"
    private const val KEY_THEME = "app_theme"
    private const val KEY_HISTORY = "command_history"

    fun getAssistantName(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "Assistant") ?: "Assistant"

    fun setAssistantName(ctx: Context, name: String) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit { putString(KEY_NAME, name) }

    fun getTheme(ctx: Context): String =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME, "glass") ?: "glass"

    fun setTheme(ctx: Context, theme: String) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit { putString(KEY_THEME, theme) }

    fun saveHistoryItem(ctx: Context, command: String, reply: String) {
        val prefs = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_HISTORY, "") ?: ""
        val timestamp = System.currentTimeMillis()
        val entry = "$timestamp|||$command|||$reply\n"
        val updated = (entry + existing).lines().take(50).joinToString("\n")
        prefs.edit { putString(KEY_HISTORY, updated) }
    }

    fun getHistory(ctx: Context): List<Triple<Long, String, String>> {
        val raw = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "") ?: ""
        return raw.lines()
            .filter { it.isNotBlank() }
            .mapNotNull {
                val parts = it.split("|||")
                if (parts.size == 3) Triple(parts[0].toLongOrNull() ?: 0L, parts[1], parts[2])
                else null
            }
    }

    fun clearHistory(ctx: Context) =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit { remove(KEY_HISTORY) }
}
