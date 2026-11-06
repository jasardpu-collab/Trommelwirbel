package de.jasminfreiner.trommelwirbel

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Der nervige Vollbild-Alarm. */
class AlarmActivity : AppCompatActivity() {

    private var flasher: ValueAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var holdButton: Button
    private lateinit var holdInfo: TextView
    private var holdNeededMs = 3_000L
    private var holdStart = 0L

    private val dismissPhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) finishAfterStop() else updateEscape()
    }

    private val holdTick = object : Runnable {
        override fun run() {
            val held = System.currentTimeMillis() - holdStart
            val left = ((holdNeededMs - held) / 1000L) + 1
            if (held >= holdNeededMs) {
                AlarmService.stop(this@AlarmActivity)
                finishAfterStop()
            } else {
                holdInfo.text = getString(R.string.hold_countdown, left)
                handler.postDelayed(this, 100L)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContentView(R.layout.activity_alarm)

        val prefs = Prefs(this)
        if (!prefs.ringing) {
            finish(); return
        }

        val root = findViewById<View>(R.id.alarmRoot)
        flasher = ValueAnimator.ofObject(
            ArgbEvaluator(),
            Color.parseColor("#D32F2F"), Color.parseColor("#FFD600"), Color.parseColor("#7B1FA2")
        ).apply {
            duration = 450
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { root.setBackgroundColor(it.animatedValue as Int) }
            start()
        }

        val photo = findViewById<Button>(R.id.photoButton)
        holdButton = findViewById(R.id.holdButton)
        holdInfo = findViewById(R.id.holdInfo)
        val sub = findViewById<TextView>(R.id.alarmSub)

        if (prefs.photoMode) {
            sub.setText(R.string.alarm_sub_photo)
            photo.visibility = View.VISIBLE
            photo.setOnClickListener {
                dismissPhoto.launch(
                    Intent(this, CameraActivity::class.java)
                        .putExtra(CameraActivity.EXTRA_MODE, CameraActivity.MODE_DISMISS)
                )
            }
            holdNeededMs = 15_000L
            updateEscape()
        } else {
            sub.setText(R.string.alarm_sub_simple)
            photo.visibility = View.GONE
            holdNeededMs = 3_000L
            holdButton.visibility = View.VISIBLE
            holdButton.setText(R.string.hold_to_stop)
            holdInfo.visibility = View.VISIBLE
            holdInfo.setText(R.string.hold_info_simple)
        }

        holdButton.setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    holdStart = System.currentTimeMillis()
                    handler.post(holdTick)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(holdTick)
                    holdInfo.setText(if (prefs.photoMode) R.string.escape_info else R.string.hold_info_simple)
                }
            }
            true
        }

        // Zurück-Taste bringt nichts :)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* nö */ }
        })
    }

    /** Notausgang erst nach mehreren fehlgeschlagenen Foto-Versuchen. */
    private fun updateEscape() {
        val prefs = Prefs(this)
        if (!prefs.photoMode) return
        val show = prefs.failedDismissAttempts >= ESCAPE_AFTER_ATTEMPTS
        holdButton.visibility = if (show) View.VISIBLE else View.GONE
        holdInfo.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            holdButton.setText(R.string.escape_button)
            holdInfo.setText(R.string.escape_info)
        }
    }

    private fun finishAfterStop() {
        flasher?.cancel()
        handler.removeCallbacks(holdTick)
        val prefs = Prefs(this)
        val backup = prefs.backupLabelTime
        if (backup.isNotEmpty()) {
            prefs.backupLabelTime = ""
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.done_title)
                .setMessage(getString(R.string.backup_still_set, backup))
                .setPositiveButton(R.string.backup_dismiss) { _, _ ->
                    AlarmScheduler.dismissBackupClockAlarm(this)
                    finish()
                }
                .setNegativeButton(R.string.ok) { _, _ -> finish() }
                .setCancelable(false)
                .show()
        } else {
            finish()
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        flasher?.cancel()
        handler.removeCallbacks(holdTick)
        super.onDestroy()
    }

    companion object {
        const val ESCAPE_AFTER_ATTEMPTS = 5
    }
}
