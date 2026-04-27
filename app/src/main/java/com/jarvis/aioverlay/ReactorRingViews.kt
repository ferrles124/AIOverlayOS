package com.jarvis.aioverlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.*

/**
 * Header'daki dönen reaktör halkası.
 * Üç katmanlı: dış glow halkası → orta segment halkası → iç core.
 */
class ReactorRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var outerRotation = 0f
    private var innerRotation = 0f
    private var breathPhase   = 0f

    private val outerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 8000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { outerRotation = it.animatedValue as Float; invalidate() }
    }

    private val innerAnimator = ValueAnimator.ofFloat(360f, 0f).apply {
        duration = 5000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { innerRotation = it.animatedValue as Float }
    }

    private val breathAnimator = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { breathPhase = it.animatedValue as Float }
    }

    // Paintler
    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3399ff")
        maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
    }

    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val innerCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#1a5580")
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        outerAnimator.start()
        innerAnimator.start()
        breathAnimator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        outerAnimator.cancel()
        innerAnimator.cancel()
        breathAnimator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r  = minOf(cx, cy) * 0.88f
        val breathe = (sin(breathPhase) * 0.08f + 1f)

        // ── Tick marks (dış çevre) ───────────────────────────────────────────
        for (i in 0 until 36) {
            val angle = Math.toRadians((i * 10).toDouble())
            val isMajor = i % 9 == 0
            val innerR = if (isMajor) r * 0.90f else r * 0.94f
            val x1 = cx + cos(angle).toFloat() * innerR
            val y1 = cy + sin(angle).toFloat() * innerR
            val x2 = cx + cos(angle).toFloat() * r
            val y2 = cy + sin(angle).toFloat() * r
            tickPaint.alpha = if (isMajor) 180 else 80
            tickPaint.strokeWidth = if (isMajor) 2f else 1f
            canvas.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // ── Dış glow halkası ────────────────────────────────────────────────
        canvas.save()
        canvas.rotate(outerRotation, cx, cy)
        val outerRect = RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.82f)
        // 6 segment
        for (i in 0 until 6) {
            val startAngle = i * 60f
            val sweepAngle = 40f
            val alpha = if (i % 2 == 0) 220 else 80
            outerRingPaint.alpha = alpha
            canvas.drawArc(outerRect, startAngle, sweepAngle, false, outerRingPaint)
        }
        canvas.restore()

        // ── Orta halka — ters döner ──────────────────────────────────────────
        canvas.save()
        canvas.rotate(innerRotation, cx, cy)
        val midRect = RectF(cx - r * 0.62f, cy - r * 0.62f, cx + r * 0.62f, cy + r * 0.62f)
        for (i in 0 until 3) {
            val startAngle = i * 120f
            val sweepAngle = 80f
            val colors = intArrayOf(
                Color.parseColor("#5ec8ff"),
                Color.parseColor("#3399ff"),
                Color.parseColor("#1a70bb")
            )
            segmentPaint.color = colors[i]
            segmentPaint.alpha = 200
            segmentPaint.maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(midRect, startAngle, sweepAngle, false, segmentPaint)
        }
        canvas.restore()

        // ── İç core (nefes alıyor) ───────────────────────────────────────────
        val coreR = r * 0.28f * breathe
        val coreShader = RadialGradient(
            cx, cy, coreR,
            intArrayOf(
                Color.parseColor("#ffffff"),
                Color.parseColor("#a0e4ff"),
                Color.parseColor("#3399ff"),
                Color.parseColor("#0d2a42"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        innerCorePaint.shader = coreShader
        innerCorePaint.maskFilter = BlurMaskFilter(coreR * 0.4f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, coreR, innerCorePaint)

        // Parlak merkez
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
            maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(cx, cy, 4f, centerPaint)
    }
}
