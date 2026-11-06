package de.jasminfreiner.trommelwirbel

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.AlarmClock
import java.util.Calendar

object AlarmScheduler {

    private const val REQ_ALARM = 1001
    private const val REQ_SHOW = 1002

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    /** Plant den Trommelwirbel-Alarm. Funktioniert auch, wenn die App zu und das Handy gesperrt ist. */
    fun schedule(context: Context, minutes: Int, photoMode: Boolean) {
        val prefs = Prefs(context)
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        prefs.alarmAt = triggerAt
        prefs.photoMode = photoMode
        prefs.lastMinutes = minutes
        prefs.ringing = false
        prefs.failedDismissAttempts = 0
        setSystemAlarm(context, triggerAt)

        if (prefs.backupAlarm) setBackupClockAlarm(context, triggerAt + prefs.backupOffsetMin * 60_000L)
        else prefs.backupLabelTime = ""
    }

    /** Nach Neustart erneut planen. */
    fun reschedule(context: Context) {
        val prefs = Prefs(context)
        val at = prefs.alarmAt
        if (at == 0L) return
        if (at <= System.currentTimeMillis()) {
            // Zeit ist während des Ausschaltens abgelaufen -> sofort klingeln
            setSystemAlarm(context, System.currentTimeMillis() + 5_000L)
        } else {
            setSystemAlarm(context, at)
        }
    }

    private fun setSystemAlarm(context: Context, triggerAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = alarmPendingIntent(context)
        val show = PendingIntent.getActivity(
            context, REQ_SHOW,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            // setAlarmClock = höchste Priorität, wird auch im Energiesparmodus pünktlich ausgelöst
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), fire)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, fire)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(alarmPendingIntent(context))
        val prefs = Prefs(context)
        prefs.alarmAt = 0L
        prefs.ringing = false
        prefs.hasReferenceHash = false
    }

    private fun alarmPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ_ALARM,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Stellt zusätzlich einen ganz normalen Wecker in der Uhr-App des Handys. */
    private fun setBackupClockAlarm(context: Context, at: Long) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = at
            // auf die nächste volle Minute aufrunden
            if (get(Calendar.SECOND) > 0 || get(Calendar.MILLISECOND) > 0) add(Calendar.MINUTE, 1)
        }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val label = context.getString(R.string.backup_label)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h)
            putExtra(AlarmClock.EXTRA_MINUTES, m)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_VIBRATE, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Prefs(context).backupLabelTime = String.format("%02d:%02d", h, m)
        } catch (e: Exception) {
            Prefs(context).backupLabelTime = ""
        }
    }

    /** Öffnet die Uhr-App, um den Sicherheits-Wecker auszuschalten. */
    fun dismissBackupClockAlarm(context: Context): Boolean {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE, AlarmClock.ALARM_SEARCH_MODE_LABEL)
            putExtra(AlarmClock.EXTRA_MESSAGE, context.getString(R.string.backup_label))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent); true
        } catch (e: Exception) {
            false
        }
    }
}
