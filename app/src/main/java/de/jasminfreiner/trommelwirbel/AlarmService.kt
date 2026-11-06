package de.jasminfreiner.trommelwirbel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * Spielt den Alarm im Dauerloop über den Wecker-Kanal – auch wenn das Handy auf lautlos steht.
 * Läuft so lange, bis die App ihn nach erfolgreichem Foto (oder im Nur-Wecker-Modus per Knopf) beendet.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var originalVolume = -1
    private val handler = Handler(Looper.getMainLooper())

    private val keepLoud = object : Runnable {
        override fun run() {
            forceMaxVolume()
            if (player?.isPlaying != true) startSound()
            handler.postDelayed(this, 5_000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !Prefs(this).ringing) {
            stopEverything()
            return START_NOT_STICKY
        }
        startInForeground()
        if (player == null) {
            acquireWakeLock()
            val am = getSystemService(AudioManager::class.java)
            if (originalVolume < 0) originalVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            startSound()
            startVibration()
            handler.removeCallbacks(keepLoud)
            handler.post(keepLoud)
        }
        launchAlarmScreen()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            nm.createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            this, 2001,
            Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.alarm_title))
            .setContentText(getString(R.string.alarm_notification_text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun launchAlarmScreen() {
        try {
            startActivity(Intent(this, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // Android blockiert das evtl. im Hintergrund – dann übernimmt die Vollbild-Benachrichtigung.
        }
    }

    private fun forceMaxVolume() {
        try {
            val am = getSystemService(AudioManager::class.java)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
            }
        } catch (_: Exception) { }
    }

    private fun startSound() {
        player?.release()
        player = null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            Prefs(this).ringtoneUri,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )
        for (uri in candidates) {
            try {
                val mp = MediaPlayer()
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.setVolume(1f, 1f)
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (_: Exception) {
                // nächsten Ton probieren
            }
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator = v
        val pattern = longArrayOf(0, 800, 300, 800, 300, 1500, 500)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
        @Suppress("DEPRECATION")
        v.vibrate(VibrationEffect.createWaveform(pattern, 0), attrs)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Trommelwirbel:alarm").apply {
            acquire(60 * 60 * 1000L)
        }
    }

    private fun stopEverything() {
        handler.removeCallbacks(keepLoud)
        try { player?.stop() } catch (_: Exception) { }
        player?.release()
        player = null
        vibrator?.cancel()
        if (originalVolume >= 0) {
            try {
                getSystemService(AudioManager::class.java)
                    .setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0)
            } catch (_: Exception) { }
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(keepLoud)
        player?.release()
        vibrator?.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "alarm"
        const val NOTIFICATION_ID = 42
        const val ACTION_STOP = "de.jasminfreiner.trommelwirbel.STOP"

        /** Beendet den Alarm endgültig. Nur aufrufen, wenn das Ausschalten erlaubt ist. */
        fun stop(context: Context) {
            val prefs = Prefs(context)
            prefs.ringing = false
            prefs.alarmAt = 0L
            prefs.hasReferenceHash = false
            prefs.failedDismissAttempts = 0
            context.startService(Intent(context, AlarmService::class.java).setAction(ACTION_STOP))
        }
    }
}
