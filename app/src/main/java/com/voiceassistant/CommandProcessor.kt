package com.voiceassistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.util.Calendar
import java.util.Locale

object CommandProcessor {

    data class CommandResult(
        val reply: String,
        val action: (() -> Unit)? = null,
        val needsFollowUp: Boolean = false,
        val followUpPrompt: String = ""
    )

    fun process(context: Context, input: String): CommandResult {
        val lower = input.lowercase(Locale.ROOT).trim()

        // ── WHATSAPP FOLLOW-UP (user answered which WA) ────────────
        if (lower.startsWith("__wa_select__:")) {
            val payload = input.removePrefix("__wa_select__:")
            val sep = payload.indexOf("|||")
            val appsRaw = if (sep >= 0) payload.substring(0, sep) else payload
            val userChoice = if (sep >= 0) payload.substring(sep + 3).lowercase() else ""
            val allApps = appsRaw.split(",").mapNotNull {
                val s = it.split("|")
                if (s.size == 2) Pair(s[0], s[1]) else null
            }
            val selected = allApps.firstOrNull { userChoice.contains(it.first.lowercase()) }
                ?: allApps.firstOrNull {
                    (userChoice.contains("second") || userChoice.contains("two")) && allApps.indexOf(it) == 1
                }
                ?: allApps.firstOrNull {
                    (userChoice.contains("first") || userChoice.contains("one")) && allApps.indexOf(it) == 0
                }
                ?: allApps.first()

            return CommandResult(
                reply = "Opening ${selected.first}",
                action = {
                    val intent = context.packageManager.getLaunchIntentForPackage(selected.second)
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            )
        }

        // ── WHATSAPP SEND MESSAGE ──────────────────────────────────
        // e.g. "send message to Raj saying hello" / "whatsapp Raj hello" / "message Raj saying I'm late"
        if (lower.containsAny("whatsapp", "whats app") &&
            lower.containsAny("send", "message", "text", "msg", "saying", "tell")) {

            // Capture name between "to" and "saying/that/text" keywords
            val toRegex = Regex("""\bto\s+([a-zA-Z]+(?:\s+[a-zA-Z]+)?)\s+(?:saying|that|text|message)\b""")
            val msgRegex = Regex("""(?:saying|that)\s+(.+)$""")
            val contactName = toRegex.find(lower)?.groupValues?.getOrNull(1)?.trim() ?: ""
            val message = msgRegex.find(lower)?.groupValues?.getOrNull(1)?.trim() ?: ""

            val waApps = getInstalledWhatsApps(context)
            return when {
                waApps.isEmpty() ->
                    CommandResult(reply = "WhatsApp is not installed")
                waApps.size == 1 -> {
                    val reply = if (contactName.isNotBlank())
                        "Sending message to ${contactName.replaceFirstChar { it.uppercase() }} on WhatsApp"
                    else "Opening WhatsApp"
                    CommandResult(
                        reply = reply,
                        action = { openWhatsAppWithMessage(context, waApps.first().second, message) }
                    )
                }
                else -> {
                    val names = waApps.joinToString(" or ") { it.first }
                    val followUp = waApps.joinToString(",") { "${it.first}|${it.second}" }
                    CommandResult(
                        reply = "Which WhatsApp? $names",
                        needsFollowUp = true,
                        followUpPrompt = followUp
                    )
                }
            }
        }

        // ── WHATSAPP OPEN ONLY ─────────────────────────────────────
        if (lower.containsAny("whatsapp", "whats app")) {
            val waApps = getInstalledWhatsApps(context)
            return when {
                waApps.isEmpty() ->
                    CommandResult(reply = "WhatsApp is not installed")
                waApps.size == 1 ->
                    CommandResult(
                        reply = "Opening WhatsApp",
                        action = {
                            val intent = context.packageManager.getLaunchIntentForPackage(waApps.first().second)
                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )
                else -> {
                    val names = waApps.joinToString(" or ") { it.first }
                    val followUp = waApps.joinToString(",") { "${it.first}|${it.second}" }
                    CommandResult(
                        reply = "Which WhatsApp? $names",
                        needsFollowUp = true,
                        followUpPrompt = followUp
                    )
                }
            }
        }

        // ── ALARM ──────────────────────────────────────────────────
        if (lower.containsAny("alarm", "wake me", "wake up", "reminder", "remind me")) {
            val time = parseTime(lower)
            return if (time != null) {
                CommandResult(
                    reply = "Opening alarm for ${time.display}",
                    action = {
                        // Vivo / FunTouch OS sometimes ignores EXTRA_SKIP_UI on the stock
                        // clock and may show its own UI to confirm — that's an OS-level
                        // restriction, not a bug. We still pass it for OEMs that respect it.
                        var launched = false
                        try {
                            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                putExtra(AlarmClock.EXTRA_HOUR, time.hour)
                                putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
                                putExtra(AlarmClock.EXTRA_MESSAGE, "Assistant Alarm")
                                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            launched = true
                        } catch (e: Exception) { launched = false }

                        if (!launched) {
                            // Try Vivo's own clock app package directly
                            val vivoPackages = listOf(
                                "com.android.deskclock",
                                "com.vivo.deskclock",
                                "com.bbk.deskclock"
                            )
                            for (pkg in vivoPackages) {
                                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                    break
                                }
                            }
                        }
                    }
                )
            } else {
                CommandResult(reply = "What time? Say for example set alarm for 7 AM")
            }
        }

        // ── PLAY MUSIC ─────────────────────────────────────────────
        if (lower.containsAny("play", "music", "song", "songs", "listen to")) {
            val query = lower
                .replace(Regex("\\b(play|music|song|songs|listen to|put on|the|please|me|can you)\\b"), " ")
                .trim().replace(Regex("\\s+"), " ").trim()
            val songName = query.replaceFirstChar { it.uppercase() }
            return CommandResult(
                reply = if (query.isNotBlank()) "Playing $songName from YouTube" else "Opening YouTube Music",
                action = {
                    val searchUrl = if (query.isNotBlank())
                        "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                    else "https://music.youtube.com"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try {
                        intent.setPackage("com.google.android.youtube")
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        intent.setPackage(null)
                        context.startActivity(intent)
                    }
                }
            )
        }

        // ── SEARCH ─────────────────────────────────────────────────
        if (lower.containsAny("search", "google", "look up", "find", "what is", "who is", "where is")) {
            val isYoutube = lower.containsAny("youtube", "video", "watch")
            val query = lower
                .replace(Regex("\\b(search|google|look up|find|what is|who is|where is|on youtube|on google|for|the|please|in|show me)\\b"), " ")
                .trim().replace(Regex("\\s+"), " ").trim()
            return if (isYoutube) {
                CommandResult(
                    reply = "Searching YouTube for $query",
                    action = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try { intent.setPackage("com.google.android.youtube"); context.startActivity(intent) }
                        catch (e: Exception) { intent.setPackage(null); context.startActivity(intent) }
                    }
                )
            } else {
                CommandResult(
                    reply = "Searching Google for $query",
                    action = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply {
                                putExtra("query", query)
                                setPackage("com.google.android.googlequicksearchbox")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra("query", query)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            } catch (e2: Exception) {
                                context.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            }
                        }
                    }
                )
            }
        }

        // ── SETTINGS (explicit, before generic open-app) ────────────
        // Fix #4: "open settings" must open the real system Settings app, never Play Store
        if (lower.containsAny("open setting", "open settings", "go to settings", "system settings")) {
            return CommandResult(
                reply = "Opening Settings",
                action = {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            )
        }

        // ── OPEN APP ───────────────────────────────────────────────
        if (lower.containsAny("open", "launch", "start", "go to", "take me to")) {
            val appName = lower
                .replace(Regex("\\b(open|launch|start|go to|take me to|please|the|app|me)\\b"), " ")
                .trim().replace(Regex("\\s+"), " ").trim()
            return CommandResult(
                reply = "Opening ${appName.replaceFirstChar { it.uppercase() }}",
                action = { AppLauncher.launch(context, appName) }
            )
        }

        // ── CALL ───────────────────────────────────────────────────
        if (lower.containsAny("call", "dial", "ring")) {
            val name = lower
                .replace(Regex("\\b(call|dial|ring|please)\\b"), " ")
                .trim().replace(Regex("\\s+"), " ").trim()
            return CommandResult(
                reply = "Calling ${name.replaceFirstChar { it.uppercase() }}",
                action = {
                    context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            )
        }

        // ── TIME & DATE ────────────────────────────────────────────
        if (lower.containsAny("time", "what time", "date", "today", "what day")) {
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
            val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.ENGLISH) ?: ""
            val weekday = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.ENGLISH) ?: ""
            return CommandResult(reply = "It is $hour:$min $amPm, $weekday $day $month")
        }

        // ── JOKE ───────────────────────────────────────────────────
        if (lower.containsAny("joke", "funny", "make me laugh", "tell me a joke")) {
            val jokes = listOf(
                "Why don't scientists trust atoms? Because they make up everything.",
                "I told my wife she was drawing her eyebrows too high. She looked surprised.",
                "Why did the phone go to therapy? It had too many hang-ups.",
                "What do you call a fake noodle? An impasta.",
                "Why can't you give Elsa a balloon? Because she will let it go."
            )
            return CommandResult(reply = jokes.random())
        }

        // ── KNOWN APPS FALLBACK ────────────────────────────────────
        val knownApps = listOf("instagram","facebook","twitter","telegram","spotify",
            "camera","gallery","maps","gmail","chrome","calculator",
            "clock","calendar","netflix","snapchat","gpay","paytm","phonepe",
            "zomato","swiggy","flipkart","amazon","youtube")
        val foundApp = knownApps.firstOrNull { lower.contains(it) }
        if (foundApp != null) {
            return CommandResult(
                reply = "Opening ${foundApp.replaceFirstChar { it.uppercase() }}",
                action = { AppLauncher.launch(context, foundApp) }
            )
        }

        // ── FALLBACK ───────────────────────────────────────────────
        return CommandResult(
            reply = "Please say again. Try set alarm for 7 AM, play a song, open WhatsApp, or search on Google"
        )
    }

    private fun openWhatsAppWithMessage(context: Context, packageName: String, message: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/?text=${Uri.encode(message)}")
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallback = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send"))
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    fun getInstalledWhatsApps(context: Context): List<Pair<String, String>> {
        val pm = context.packageManager
        val candidates = listOf(
            "com.whatsapp"          to "WhatsApp",
            "com.whatsapp.w4b"      to "WhatsApp Business",
            "com.gbwhatsapp"        to "GBWhatsApp",
            "com.poor.deltawhatsapp" to "Delta WhatsApp"
        )
        return candidates.filter { (pkg, _) ->
            try { pm.getPackageInfo(pkg, 0); true }
            catch (e: PackageManager.NameNotFoundException) { false }
        }.map { (pkg, fallback) ->
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) { fallback }
            Pair(label, pkg)
        }
    }

    private fun String.containsAny(vararg kw: String) = kw.any { this.contains(it) }

    data class TimeResult(val hour: Int, val minute: Int, val display: String)

    fun parseTime(text: String): TimeResult? {
        val patterns = listOf(
            Regex("""(\d{1,2}):(\d{2})\s*(am|pm)?"""),
            Regex("""(\d{1,2})\s*(am|pm)"""),
            Regex("""(?:alarm|wake|reminder).*?(\d{1,2})\b"""),
            Regex("""\b(\d{1,2})\b.*(?:alarm|wake|reminder)""")
        )
        for (pattern in patterns) {
            val m = pattern.find(text) ?: continue
            val rawHour = m.groupValues[1].toIntOrNull() ?: continue
            val minute = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
            val suffix = (m.groupValues.getOrNull(3) ?: m.groupValues.getOrNull(2) ?: "").lowercase()
            val hour = when {
                suffix == "pm" && rawHour < 12 -> rawHour + 12
                suffix == "am" && rawHour == 12 -> 0
                suffix.isEmpty() && rawHour in 1..6 -> rawHour + 12
                else -> rawHour
            }
            val displayHour = when { hour == 0 -> 12; hour > 12 -> hour - 12; else -> hour }
            val amPm = if (hour >= 12) "PM" else "AM"
            val display = "$displayHour${if (minute > 0) ":${minute.toString().padStart(2,'0')}" else ""} $amPm"
            return TimeResult(hour, minute, display)
        }
        return null
    }
}
