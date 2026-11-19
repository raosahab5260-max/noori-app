package com.noori.companion

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class PendingPhoneAction(val title: String, val description: String) {
    class OpenApp(val appName: String) :
        PendingPhoneAction("ऐप खोलें", "क्या मैं \"$appName\" ऐप खोल दूँ?")

    class SetAlarm(val hour: Int, val minute: Int, val label: String = "NOORI Alarm") :
        PendingPhoneAction("अलार्म सेट करें", "क्या मैं ${String.format("%02d:%02d", hour, minute)} का अलार्म सेट कर दूँ?")

    class SetTimer(val seconds: Int, val label: String = "NOORI Timer") :
        PendingPhoneAction("टाइमर सेट करें", "क्या मैं ${seconds / 60} मिनट का टाइमर शुरू कर दूँ?")

    class OpenSettings(val settingType: String) :
        PendingPhoneAction("Settings खोलें", "क्या मैं फ़ोन की $settingType सेटिंग्स खोल दूँ?")
}

object PhoneAssistant {
    fun getCurrentTimeText(): String {
        val timeFmt = SimpleDateFormat("hh:mm a", Locale("hi", "IN"))
        val dateFmt = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("hi", "IN"))
        val now = Date()
        return "अभी ${timeFmt.format(now)} हो रहे हैं, और आज ${dateFmt.format(now)} है।"
    }

    fun parseActionTag(tag: String): PendingPhoneAction? {
        val parts = tag.split(":").map { it.trim() }
        if (parts.isEmpty()) return null
        return when (parts[0].uppercase()) {
            "OPEN_APP" -> {
                val name = parts.getOrNull(1) ?: return null
                PendingPhoneAction.OpenApp(name)
            }
            "SET_ALARM" -> {
                val timeParts = (parts.getOrNull(1) ?: "07:00").split("-")
                val h = timeParts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
                val m = timeParts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                PendingPhoneAction.SetAlarm(h, m)
            }
            "SET_TIMER" -> {
                val mins = parts.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 5
                PendingPhoneAction.SetTimer(mins * 60)
            }
            "OPEN_SETTINGS" -> {
                val type = parts.getOrNull(1) ?: "main"
                PendingPhoneAction.OpenSettings(type)
            }
            else -> null
        }
    }

    fun executeAction(context: Context, action: PendingPhoneAction): String {
        return try {
            when (action) {
                is PendingPhoneAction.OpenApp -> openInstalledApp(context, action.appName)
                is PendingPhoneAction.SetAlarm -> {
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, action.hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "अलार्म स्क्रीन खोल दी है!"
                }
                is PendingPhoneAction.SetTimer -> {
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, action.label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    "टाइमर स्क्रीन खोल दी है!"
                }
                is PendingPhoneAction.OpenSettings -> {
                    val actionStr = when (action.settingType.lowercase()) {
                        "wifi", "wi-fi" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "display" -> Settings.ACTION_DISPLAY_SETTINGS
                        "sound" -> Settings.ACTION_SOUND_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    val intent = Intent(actionStr).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    "${action.settingType} सेटिंग्स खोल दी हैं!"
                }
            }
        } catch (e: Exception) {
            "यह एक्शन इस फ़ोन पर नहीं खुल सका: ${e.localizedMessage ?: "Unsupported"}"
        }
    }

    private fun openInstalledApp(context: Context, queryName: String): String {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val match = apps.firstOrNull { info ->
            val label = info.loadLabel(pm).toString()
            label.contains(queryName, ignoreCase = true) ||
                info.activityInfo.packageName.contains(queryName, ignoreCase = true)
        }
        return if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                "${match.loadLabel(pm)} खोल दिया है!"
            } else {
                "$queryName को खोला नहीं जा सका।"
            }
        } else {
            "फ़ोन में \"$queryName\" ऐप नहीं मिला।"
        }
    }
}
