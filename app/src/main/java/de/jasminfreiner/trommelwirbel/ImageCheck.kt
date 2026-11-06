package de.jasminfreiner.trommelwirbel

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Alles läuft offline auf dem Gerät. Kein Bild verlässt das Handy, nichts wird gespeichert. */
object ImageCheck {

    private val applianceWords = listOf(
        "washing machine", "washer", "appliance", "home appliance", "major appliance",
        "clothes dryer", "dryer", "laundry", "machine", "porthole", "circle", "display device",
        "gadget", "electronic device", "refrigerator", "kitchen appliance"
    )

    fun readText(image: InputImage, onResult: (String) -> Unit) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { onResult(it.text) }
            .addOnFailureListener { onResult("") }
            .addOnCompleteListener { recognizer.close() }
    }

    /** Liefert true, wenn die Bilderkennung irgendwas Haushaltsgeräte-artiges sieht. */
    fun looksLikeAppliance(image: InputImage, onResult: (Boolean, String) -> Unit) {
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(0.35f).build()
        )
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val names = labels.map { it.text.lowercase() }
                val hit = names.any { n -> applianceWords.any { w -> n.contains(w) } }
                onResult(hit, names.joinToString())
            }
            .addOnFailureListener { onResult(false, "") }
            .addOnCompleteListener { labeler.close() }
    }

    /** Winziger 64-Bit-Fingerabdruck (dHash) der Bildmitte – daraus lässt sich kein Foto zurückrechnen. */
    fun dHash(src: Bitmap): Long {
        // Mittlere 70 % nehmen (da, wo die Schablone ist)
        val w = src.width
        val h = src.height
        val cw = (w * 0.7f).toInt().coerceAtLeast(9)
        val ch = (h * 0.7f).toInt().coerceAtLeast(8)
        val crop = Bitmap.createBitmap(src, (w - cw) / 2, (h - ch) / 2, cw, ch)
        val small = Bitmap.createScaledBitmap(crop, 9, 8, true)
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val l = luma(small.getPixel(x, y))
                val r = luma(small.getPixel(x + 1, y))
                if (l > r) hash = hash or (1L shl bit)
                bit++
            }
        }
        if (crop != src) crop.recycle()
        small.recycle()
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    private fun luma(c: Int): Int {
        val r = (c shr 16) and 0xff
        val g = (c shr 8) and 0xff
        val b = c and 0xff
        return (r * 299 + g * 587 + b * 114) / 1000
    }
}
