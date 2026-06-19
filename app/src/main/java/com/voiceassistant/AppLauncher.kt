package com.voiceassistant

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

object AppLauncher {

    // System actions that work across ALL OEMs (Vivo, Samsung, Xiaomi, etc.)
    // checked BEFORE package-name guessing, since OEMs rename system app packages
    // and a guessed package name failing was silently falling through to Play Store.
    private val SYSTEM_ACTIONS: Map<String, () -> Intent> = mapOf(
        "settings" to { Intent(Settings.ACTION_SETTINGS) },
        "setting" to { Intent(Settings.ACTION_SETTINGS) },
        "wifi" to { Intent(Settings.ACTION_WIFI_SETTINGS) },
        "wi-fi" to { Intent(Settings.ACTION_WIFI_SETTINGS) },
        "bluetooth" to { Intent(Settings.ACTION_BLUETOOTH_SETTINGS) },
        "clock" to { Intent(AlarmClock.ACTION_SHOW_ALARMS) },
        "alarm" to { Intent(AlarmClock.ACTION_SHOW_ALARMS) },
        "contacts" to { Intent(Intent.ACTION_VIEW, Uri.parse("content://contacts/people/")) },
        "phone" to { Intent(Intent.ACTION_DIAL) },
        "dialer" to { Intent(Intent.ACTION_DIAL) }
    )

    private val APP_MAP = mapOf(
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "youtube" to "com.google.android.youtube",
        "camera" to "com.android.camera2",
        "gallery" to "com.android.gallery3d",
        "chrome" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "facebook" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "telegram" to "org.telegram.messenger",
        "gmail" to "com.google.android.gm",
        "calculator" to "com.android.calculator2",
        "calendar" to "com.google.android.calendar",
        "messages" to "com.android.mms",
        "snapchat" to "com.snapchat.android",
        "netflix" to "com.netflix.mediaclient",
        "amazon" to "in.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "phonepe" to "com.phonepe.app",
        "zomato" to "com.application.zomato",
        "swiggy" to "in.swiggy.android",
        "ola" to "com.olacabs.customer",
        "uber" to "com.ubercab"
    )

    fun launch(context: Context, appName: String) {
        val key = appName.lowercase().trim()

        // Fix #4: check OEM-safe system actions FIRST, before guessing package names.
        // This is what was broken — "settings" guessed com.android.settings which
        // doesn't exist on Vivo FunTouch OS, so it silently fell through to Play Store.
        val systemAction = SYSTEM_ACTIONS.entries.firstOrNull { key.contains(it.key) }
        if (systemAction != null) {
            try {
                val intent = systemAction.value().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // fall through to package guessing below
            }
        }

        val packageName = APP_MAP.entries.firstOrNull { key.contains(it.key) }?.value

        if (packageName != null) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }

        // Fuzzy search installed apps
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(0)
        val match = installed.firstOrNull {
            pm.getApplicationLabel(it).toString().lowercase().contains(key)
        }
        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                return
            }
        }

        // Fallback: Play Store search
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/search?q=${Uri.encode(appName)}&c=apps")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
