package de.jasminfreiner.trommelwirbel

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Schablone über dem Kamerabild: oben ein Rechteck fürs Display, darunter der Kreis fürs Bullauge. */
class TemplateOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val dim = Paint().apply { color = Color.argb(150, 0, 0, 0) }
    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#FFD54F")
    }
    private val dashed = Paint(stroke).apply {
        pathEffect = DashPathEffect(floatArrayOf(30f, 18f), 0f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 44f
        isFakeBoldText = true
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f

        val displayRect = RectF(w * 0.18f, h * 0.17f, w * 0.82f, h * 0.30f)
        val radius = w * 0.36f
        val cy = h * 0.30f + 60f + radius

        canvas.drawRect(0f, 0f, w, h, dim)
        canvas.drawRoundRect(displayRect, 28f, 28f, clear)
        canvas.drawCircle(cx, cy, radius, clear)

        canvas.drawRoundRect(displayRect, 28f, 28f, dashed)
        canvas.drawCircle(cx, cy, radius, stroke)

        canvas.drawText(context.getString(R.string.overlay_display), cx, displayRect.top - 24f, text)
        canvas.drawText(context.getString(R.string.overlay_door), cx, cy + radius + 64f, text)
    }
}
