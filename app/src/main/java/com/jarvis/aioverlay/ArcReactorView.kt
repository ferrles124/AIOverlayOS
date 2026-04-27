package com.jarvis.aioverlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * Animated arc reactor background — rotating rings, pulse waves, grid lines.
 * Çizim tamamen Canvas üzerinde, sıfır bitmap yok.
 */
class ArcReactorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ── Renkler ─────────────────────────────────────────────────────────────
    private val bgColor       = Color.parseColor("#020b14")
    private val gridColor     = Color.parseColor("#06182a")
    private val ring1Color    = Color.parseColor("#0d2a42")
    private val ring2Color    = Color.parseColor("#0a1e30")
    private val pulseColor    = Color.parseColor("#3399ff")
    private val glowColor     = Color.parseColor("#1a4a6a")

    // ── Paint nesneleri ─────────────────────────────────────────────────────
    private val bgPaint = Paint().apply { color = bgColor }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gridColor
        strokeWidth = 0.8f
        style = Paint.Style.STROKE
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = pulseColor
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = glowColor
        maskFilter = BlurMaskFilter(24f, BlurMaskFilter.Blur.NORMAL)
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#0d2a42")
    }

    // ── Animasyon state ─────────────────────────────────────────────────────
    private var rotationAngle = 0f
    private var pulseRadius   = 0f
    private var pulseAlpha    = 255

    private val rotateAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 18000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationAngle = it.animatedValue as Float
            invalidate()
        }
    }

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            val f = it.animatedFraction
            pulseRadius = f
            pulseAlpha = ((1f - f) * 180).toInt()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rotateAnimator.start()
        pulseAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rotateAnimator.cancel()
        pulseAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * 0.18f  // Reactor merkezini üste al

        // ── Arka plan ───────────────────────────────────────────────────────
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // ── Grid (perspektif ızgara) ─────────────────────────────────────────
        drawPerspectiveGrid(canvas, w, h)

        // ── Radyal çizgiler ─────────────────────────────────────────────────
        canvas.save()
        canvas.rotate(rotationAngle, cx, cy)
        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30).toDouble())
            val x2 = cx + cos(angle).toFloat() * w * 0.6f
            val y2 = cy + sin(angle).toFloat() * w * 0.6f
            ringPaint.color = ring2Color
            ringPaint.alpha = 80
            canvas.drawLine(cx, cy, x2, y2, ringPaint)
        }
        canvas.restore()

        // ── Konsentrik halkalar ─────────────────────────────────────────────
        val radii = listOf(60f, 120f, 200f, 300f, 420f, 560f)
        radii.forEachIndexed { i, r ->
            val alpha = (120 - i * 15).coerceAtLeast(20)
            ringPaint.color = if (i % 2 == 0) ring1Color else ring2Color
            ringPaint.alpha = alpha
            ringPaint.strokeWidth = if (i == 0) 2f else 1f
            canvas.drawCircle(cx, cy, r, ringPaint)
        }

        // ── Dönen arc segmentleri ────────────────────────────────────────────
        canvas.save()
        canvas.rotate(-rotationAngle * 1.5f, cx, cy)
        val arcRect60 = RectF(cx - 60f, cy - 60f, cx + 60f, cy + 60f)
        glowPaint.color = Color.parseColor("#3399ff")
        glowPaint.alpha = 180
        canvas.drawArc(arcRect60, 0f, 120f, false, glowPaint)
        canvas.drawArc(arcRect60, 180f, 120f, false, glowPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(rotationAngle * 0.7f, cx, cy)
        val arcRect120 = RectF(cx - 120f, cy - 120f, cx + 120f, cy + 120f)
        glowPaint.color = Color.parseColor("#1a5580")
        glowPaint.alpha = 120
        canvas.drawArc(arcRect120, 30f, 80f, false, glowPaint)
        canvas.drawArc(arcRect120, 150f, 80f, false, glowPaint)
        canvas.drawArc(arcRect120, 270f, 80f, false, glowPaint)
        canvas.restore()

        // ── Pulse dalgası ────────────────────────────────────────────────────
        val maxPulse = w * 0.9f
        pulsePaint.alpha = pulseAlpha.coerceIn(0, 255)
        canvas.drawCircle(cx, cy, pulseRadius * maxPulse, pulsePaint)

        // ── Merkez nokta ─────────────────────────────────────────────────────
        dotPaint.color = Color.parseColor("#5ec8ff")
        dotPaint.maskFilter = BlurMaskFilter(10f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, 6f, dotPaint)
        dotPaint.maskFilter = null
        dotPaint.color = Color.parseColor("#ffffff")
        canvas.drawCircle(cx, cy, 3f, dotPaint)
    }

    private fun drawPerspectiveGrid(canvas: Canvas, w: Float, h: Float) {
        val horizonY = h * 0.55f
        val vanishX = w / 2f

        // Yatay çizgiler
        for (i in 0..12) {
            val t = i / 12f
            val y = horizonY + (h - horizonY) * t * t
            gridPaint.alpha = (30 * (1f - t * 0.5f)).toInt()
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        // Dikey (vanishing point) çizgiler
        for (i in -8..8) {
            val startX = vanishX + i * (w / 8f)
            gridPaint.alpha = 25
            canvas.drawLine(vanishX, horizonY, startX, h, gridPaint)
        }
    }
}
