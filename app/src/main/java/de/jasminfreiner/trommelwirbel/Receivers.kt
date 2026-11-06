package de.jasminfreiner.trommelwirbel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (prefs.alarmAt == 0L) return
        prefs.ringing = true
        ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (prefs.ringing) {
            // Handy wurde während des Alarms neu gestartet -> weiter nerven
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))
        } else {
            AlarmScheduler.reschedule(context)
        }
    }
}
