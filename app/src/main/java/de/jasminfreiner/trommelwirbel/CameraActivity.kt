package de.jasminfreiner.trommelwirbel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage

/**
 * Kamera mit Schablone.
 * MODE_START: Foto -> Restzeit lesen -> bestätigen -> Alarm planen.
 * MODE_DISMISS: Foto -> prüfen, ob es die Waschmaschine ist -> Alarm aus.
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var shutter: Button
    private lateinit var progress: ProgressBar
    private lateinit var hint: TextView
    private var imageCapture: ImageCapture? = null
    private var mode = MODE_START

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startCamera() else {
            Toast.makeText(this, R.string.camera_needed, Toast.LENGTH_LONG).show()
            if (mode == MODE_START) {
                TimeDialog.show(this, Prefs(this).lastMinutes, null) { schedule(it, null) }
            } else finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_START
        previewView = findViewById(R.id.preview)
        shutter = findViewById(R.id.shutter)
        progress = findViewById(R.id.progress)
        hint = findViewById(R.id.hint)
        val manual = findViewById<Button>(R.id.manual)

        hint.setText(if (mode == MODE_START) R.string.hint_start else R.string.hint_dismiss)
        if (mode == MODE_DISMISS) manual.visibility = View.GONE
        manual.setOnClickListener {
            TimeDialog.show(this, Prefs(this).lastMinutes, null) { schedule(it, null) }
        }
        shutter.setOnClickListener { takePhoto() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
            } catch (e: Exception) {
                Toast.makeText(this, R.string.camera_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun busy(b: Boolean) {
        progress.visibility = if (b) View.VISIBLE else View.GONE
        shutter.isEnabled = !b
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        busy(true)
        capture.takePicture(ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bitmap = try {
                    rotate(image.toBitmap(), image.imageInfo.rotationDegrees)
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                }
                if (bitmap == null) {
                    busy(false)
                    Toast.makeText(this@CameraActivity, R.string.camera_error, Toast.LENGTH_SHORT).show()
                    return
                }
                if (mode == MODE_START) handleStart(bitmap) else handleDismiss(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                busy(false)
                Toast.makeText(this@CameraActivity, R.string.camera_error, Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun handleStart(bitmap: Bitmap) {
        val hash = ImageCheck.dHash(bitmap)
        ImageCheck.readText(InputImage.fromBitmap(bitmap, 0)) { text ->
            bitmap.recycle() // Foto sofort wegwerfen
            busy(false)
            val found = TimeParser.parse(text)
            val start = found ?: Prefs(this).lastMinutes
            val msg = if (found != null) getString(R.string.time_found) else getString(R.string.time_not_found)
            TimeDialog.show(this, start, msg) { minutes -> schedule(minutes, hash) }
        }
    }

    private fun schedule(minutes: Int, hash: Long?) {
        val prefs = Prefs(this)
        if (hash != null) {
            prefs.referenceHash = hash
            prefs.hasReferenceHash = true
        } else {
            prefs.hasReferenceHash = false
        }
        AlarmScheduler.schedule(this, minutes, photoMode = true)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun handleDismiss(bitmap: Bitmap) {
        val prefs = Prefs(this)
        val hash = ImageCheck.dHash(bitmap)
        val similar = prefs.hasReferenceHash && ImageCheck.distance(hash, prefs.referenceHash) <= MAX_HASH_DISTANCE
        ImageCheck.looksLikeAppliance(InputImage.fromBitmap(bitmap, 0)) { appliance, _ ->
            bitmap.recycle()
            busy(false)
            if (appliance || similar) {
                AlarmService.stop(this)
                setResult(RESULT_OK)
                finish()
            } else {
                prefs.failedDismissAttempts = prefs.failedDismissAttempts + 1
                Toast.makeText(this, R.string.dismiss_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_START = "start"
        const val MODE_DISMISS = "dismiss"
        private const val MAX_HASH_DISTANCE = 22

        fun start(context: Context) =
            context.startActivity(Intent(context, CameraActivity::class.java).putExtra(EXTRA_MODE, MODE_START))
    }
}
