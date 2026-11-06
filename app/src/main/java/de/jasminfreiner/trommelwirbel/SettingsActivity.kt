package de.jasminfreiner.trommelwirbel

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    private lateinit var toneName: TextView

    private val pickTone = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        if (r.resultCode == RESULT_OK && uri != null) {
            Prefs(this).ringtoneUri = uri
            showToneName()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val prefs = Prefs(this)

        toneName = findViewById(R.id.toneName)
        showToneName()
        findViewById<Button>(R.id.pickTone).setOnClickListener {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_tone))
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false) // stumm gibt's nicht!
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, prefs.ringtoneUri)
            }
            try { pickTone.launch(i) } catch (_: Exception) { }
        }

        val backup = findViewById<MaterialSwitch>(R.id.backupSwitch)
        val offsets = findViewById<RadioGroup>(R.id.backupOffset)
        backup.isChecked = prefs.backupAlarm
        offsets.isEnabled = prefs.backupAlarm
        backup.setOnCheckedChangeListener { _, checked -> prefs.backupAlarm = checked }
        offsets.check(
            when (prefs.backupOffsetMin) {
                0 -> R.id.offset0
                10 -> R.id.offset10
                else -> R.id.offset5
            }
        )
        offsets.setOnCheckedChangeListener { _, id ->
            prefs.backupOffsetMin = when (id) {
                R.id.offset0 -> 0
                R.id.offset10 -> 10
                else -> 5
            }
        }
    }

    private fun showToneName() {
        val uri = Prefs(this).ringtoneUri
        val name = try {
            RingtoneManager.getRingtone(this, uri)?.getTitle(this)
        } catch (_: Exception) { null }
        toneName.text = name ?: getString(R.string.tone_default)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish(); return true
    }
}
