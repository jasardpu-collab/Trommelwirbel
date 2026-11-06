package de.jasminfreiner.trommelwirbel

import android.app.Activity
import android.view.LayoutInflater
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Restzeit bestätigen oder selbst eintippen (Stunden + Minuten). */
object TimeDialog {
    fun show(activity: Activity, startMinutes: Int, message: String?, onOk: (Int) -> Unit) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_time, null)
        val hours = view.findViewById<NumberPicker>(R.id.hours)
        val mins = view.findViewById<NumberPicker>(R.id.minutes)
        val msg = view.findViewById<TextView>(R.id.message)

        hours.minValue = 0
        hours.maxValue = 9
        mins.minValue = 0
        mins.maxValue = 59
        mins.setFormatter { String.format("%02d", it) }
        hours.value = (startMinutes / 60).coerceIn(0, 9)
        mins.value = startMinutes % 60
        if (message != null) msg.text = message else msg.setText(R.string.time_manual)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.time_title)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.start_alarm) { _, _ ->
                val total = hours.value * 60 + mins.value
                if (total < 1) {
                    Toast.makeText(activity, R.string.time_too_short, Toast.LENGTH_SHORT).show()
                    show(activity, 1, message, onOk)
                } else onOk(total)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
