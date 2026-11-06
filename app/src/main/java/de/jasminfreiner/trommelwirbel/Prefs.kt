package de.jasminfreiner.trommelwirbel

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/** Alles, was die App sich merkt. Es werden keine Fotos gespeichert, nur ein winziger Bild-Fingerabdruck. */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("trommelwirbel", Context.MODE_PRIVATE)

    /** Zeitpunkt (Millis), zu dem der Alarm klingelt. 0 = kein Alarm geplant. */
    var alarmAt: Long
        get() = p.getLong("alarmAt", 0L)
        set(v) = p.edit().putLong("alarmAt", v).apply()

    /** true = Alarm geht nur mit Foto aus. false = Nur-Wecker-Modus. */
    var photoMode: Boolean
        get() = p.getBoolean("photoMode", true)
        set(v) = p.edit().putBoolean("photoMode", v).apply()

    /** Fingerabdruck (dHash) vom Start-Foto, um das Ausschalt-Foto zu vergleichen. */
    var referenceHash: Long
        get() = p.getLong("refHash", 0L)
        set(v) = p.edit().putLong("refHash", v).apply()

    var hasReferenceHash: Boolean
        get() = p.getBoolean("hasRefHash", false)
        set(v) = p.edit().putBoolean("hasRefHash", v).apply()

    /** Läutet gerade? */
    var ringing: Boolean
        get() = p.getBoolean("ringing", false)
        set(v) = p.edit().putBoolean("ringing", v).apply()

    var ringtoneUri: Uri
        get() = p.getString("ringtone", null)?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        set(v) = p.edit().putString("ringtone", v.toString()).apply()

    /** Sicherheits-Wecker in der Uhr-App zusätzlich stellen. */
    var backupAlarm: Boolean
        get() = p.getBoolean("backupAlarm", true)
        set(v) = p.edit().putBoolean("backupAlarm", v).apply()

    /** Wie viele Minuten nach dem Trommelwirbel-Alarm der Sicherheits-Wecker klingelt. */
    var backupOffsetMin: Int
        get() = p.getInt("backupOffset", 5)
        set(v) = p.edit().putInt("backupOffset", v).apply()

    /** Uhrzeit des zuletzt gestellten Sicherheits-Weckers, z. B. "14:35". */
    var backupLabelTime: String
        get() = p.getString("backupTime", "") ?: ""
        set(v) = p.edit().putString("backupTime", v).apply()

    var lastMinutes: Int
        get() = p.getInt("lastMinutes", 90)
        set(v) = p.edit().putInt("lastMinutes", v).apply()

    var failedDismissAttempts: Int
        get() = p.getInt("failedDismiss", 0)
        set(v) = p.edit().putInt("failedDismiss", v).apply()
}
