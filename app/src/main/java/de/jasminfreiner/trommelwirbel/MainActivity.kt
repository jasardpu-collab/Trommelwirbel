package de.jasminfreiner.trommelwirbel

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var idleBox: View
    private lateinit var runningBox: View
    private lateinit var countdown: TextView
    private lateinit var runningInfo: TextView
    private lateinit var setupBox: LinearLayout

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshSetup() }

    private val tick = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        idleBox = findViewById(R.id.idleBox)
        runningBox = findViewById(R.id.runningBox)
        countdown = findViewById(R.id.countdown)
        runningInfo = findViewById(R.id.runningInfo)
        setupBox = findViewById(R.id.setupBox)

        findViewById<Button>(R.id.photoStart).setOnClickListener {
            if (!readyOrWarn()) return@setOnClickListener
            CameraActivity.start(this)
        }
        findViewById<Button>(R.id.simpleStart).setOnClickListener {
            if (!readyOrWarn()) return@setOnClickListener
            TimeDialog.show(this, Prefs(this).lastMinutes, null) { minutes ->
                Prefs(this).hasReferenceHash = false
                AlarmScheduler.schedule(this, minutes, photoMode = false)
                refreshState()
            }
        }
        findViewById<Button>(R.id.cancel).setOnClickListener { confirmCancel() }
        findViewById<Button>(R.id.toAlarm).setOnClickListener {
            startActivity(Intent(this, AlarmActivity::class.java))
        }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSetup()
        handler.post(tick)
        if (Prefs(this).ringing) startActivity(Intent(this, AlarmActivity::class.java))
    }

    override fun onPause() {
        handler.removeCallbacks(tick)
        super.onPause()
    }

    private fun refreshState() {
        val prefs = Prefs(this)
        val at = prefs.alarmAt
        val toAlarm = findViewById<Button>(R.id.toAlarm)
        if (at == 0L) {
            idleBox.visibility = View.VISIBLE
            runningBox.visibility = View.GONE
            return
        }
        idleBox.visibility = View.GONE
        runningBox.visibility = View.VISIBLE
        val left = (at - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L
        countdown.text = String.format(Locale.GERMANY, "%d:%02d:%02d", left / 3600, (left % 3600) / 60, left % 60)
        val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(at))
        val mode = getString(if (prefs.photoMode) R.string.mode_photo else R.string.mode_simple)
        var info = getString(R.string.running_info, time, mode)
        if (prefs.backupLabelTime.isNotEmpty()) info += "\n" + getString(R.string.running_backup, prefs.backupLabelTime)
        runningInfo.text = info
        toAlarm.visibility = if (prefs.ringing) View.VISIBLE else View.GONE
    }

    private fun confirmCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cancel_title)
            .setMessage(R.string.cancel_message)
            .setPositiveButton(R.string.cancel_yes) { _, _ ->
                val wasRinging = Prefs(this).ringing
                AlarmScheduler.cancel(this)
                if (wasRinging) AlarmService.stop(this)
                refreshState()
            }
            .setNegativeButton(R.string.cancel_no, null)
            .show()
    }

    /** Alarm geht nur zuverlässig, wenn alle Erlaubnisse da sind. */
    private fun readyOrWarn(): Boolean {
        if (missingSetup().isEmpty()) return true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setup_title)
            .setMessage(R.string.setup_warn)
            .setPositiveButton(R.string.ok, null)
            .show()
        return false
    }

    private data class SetupItem(val label: Int, val action: () -> Unit)

    private fun missingSetup(): List<SetupItem> {
        val list = mutableListOf<SetupItem>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            list += SetupItem(R.string.setup_notifications) { openAppSettings() }
        }
        if (!AlarmScheduler.canScheduleExact(this) && Build.VERSION.SDK_INT >= 31) {
            list += SetupItem(R.string.setup_exact) {
                tryStart(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            list += SetupItem(R.string.setup_fullscreen) {
                tryStart(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }
        return list
    }

    private fun refreshSetup() {
        setupBox.removeAllViews()
        val items = missingSetup().toMutableList()
        val pm = getSystemService(PowerManager::class.java)
        val batteryMissing = !pm.isIgnoringBatteryOptimizations(packageName)
        if (items.isEmpty() && !batteryMissing) {
            setupBox.visibility = View.GONE
            return
        }
        setupBox.visibility = View.VISIBLE
        val title = TextView(this).apply {
            setText(R.string.setup_title)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        setupBox.addView(title)
        if (batteryMissing) {
            items += SetupItem(R.string.setup_battery) {
                @Suppress("BatteryLife")
                tryStart(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            }
        }
        for (item in items) {
            val b = Button(this).apply {
                setText(item.label)
                isAllCaps = false
                setOnClickListener { item.action() }
            }
            setupBox.addView(b)
        }
    }

    private fun openAppSettings() {
        tryStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun tryStart(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { openAppSettingsFallback() }
    }

    private fun openAppSettingsFallback() {
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (_: Exception) { }
    }
}
