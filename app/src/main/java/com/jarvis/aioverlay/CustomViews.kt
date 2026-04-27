package com.jarvis.aioverlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText

// ════════════════════════════════════════════════════════════════════════════
//  HexPanel — kesik köşeli panel (hexagonal clip)
// ════════════════════════════════════════════════════════════════════════════
class HexPanel @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#050f1c")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0d2a42")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val cornerSize = 18f

    init {
        setWillNotDraw(false)
        setPadding(0, 0, 0, 0)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val path = buildHexPath(w, h)
        canvas.drawPath(path, bgPaint)
        canvas.drawPath(path, borderPaint)
    }

    private fun buildHexPath(w: Float, h: Float): Path {
        val c = cornerSize
        return Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(0f, h - c)
            lineTo(0f, c)
            close()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.clipPath(buildHexPath(w, h))
        super.dispatchDraw(canvas)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  ScanLineView — yatay tarama çizgisi animasyonu
// ════════════════════════════════════════════════════════════════════════════
class ScanLineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var scanPos = 0f
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2500
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            scanPos = it.animatedFraction
            invalidate()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); animator.start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); animator.cancel() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Base line
        linePaint.color = Color.parseColor("#0d2a42")
        linePaint.alpha = 255
        linePaint.maskFilter = null
        canvas.drawLine(0f, h / 2f, w, h / 2f, linePaint)

        // Animated glow scan
        val scanX = scanPos * w
        val glowShader = LinearGradient(
            scanX - 80f, 0f, scanX + 80f, 0f,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#3399ff"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        linePaint.shader = glowShader
        linePaint.maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL)
        linePaint.alpha = 255
        canvas.drawLine(0f, h / 2f, w, h / 2f, linePaint)
        linePaint.shader = null
        linePaint.maskFilter = null
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  JarvisButton — JARVIS tarzı basılabilir buton
// ════════════════════════════════════════════════════════════════════════════
class JarvisButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : androidx.appcompat.widget.AppCompatButton(context, attrs) {

    private val cornerSize = 12f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var pressProgress = 0f
    private val pressAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 150
        addUpdateListener { pressProgress = it.animatedValue as Float; invalidate() }
    }

    init {
        setWillNotDraw(false)
        background = null
        isClickable = true
        isFocusable = true
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        pressAnimator.cancel()
        if (pressed) {
            pressAnimator.setFloatValues(pressProgress, 1f)
        } else {
            pressAnimator.setFloatValues(pressProgress, 0f)
        }
        pressAnimator.start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val c = cornerSize

        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(0f, h - c)
            lineTo(0f, c)
            close()
        }

        // Determine colors from background resource tag
        val isStart = tag == "start"
        val bgAlpha = (0.15f + pressProgress * 0.15f)

        bgPaint.style = Paint.Style.FILL
        bgPaint.color = Color.parseColor("#3399ff")
        bgPaint.alpha = (bgAlpha * 255).toInt()
        canvas.drawPath(path, bgPaint)

        // Border
        borderPaint.color = Color.parseColor("#3399ff")
        borderPaint.alpha = (120 + (pressProgress * 80).toInt())
        canvas.drawPath(path, borderPaint)

        // Corner accent dots
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#3399ff")
            alpha = 200
        }
        canvas.drawCircle(0f, 0f, 3f, dotPaint)
        canvas.drawCircle(w, 0f, 3f, dotPaint)
        canvas.drawCircle(0f, h, 3f, dotPaint)
        canvas.drawCircle(w, h, 3f, dotPaint)

        // Glow on press
        if (pressProgress > 0) {
            glowPaint.color = Color.parseColor("#3399ff")
            glowPaint.alpha = (pressProgress * 100).toInt()
            glowPaint.maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawPath(path, glowPaint)
            glowPaint.maskFilter = null
        }

        super.onDraw(canvas)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  JarvisEditText — JARVIS tarzı text input
// ════════════════════════════════════════════════════════════════════════════
class JarvisEditText @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs) {

    private val cornerSize = 8f
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#050f1c")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#0d2a42")
    }
    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#3399ff")
        maskFilter = BlurMaskFilter(6f, BlurMaskFilter.Blur.NORMAL)
    }

    init {
        setWillNotDraw(false)
        background = null
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val c = cornerSize
        val path = Path().apply {
            moveTo(c, 0f)
            lineTo(w - c, 0f)
            lineTo(w, c)
            lineTo(w, h - c)
            lineTo(w - c, h)
            lineTo(c, h)
            lineTo(0f, h - c)
            lineTo(0f, c)
            close()
        }

        canvas.drawPath(path, bgPaint)

        if (isFocused) {
            canvas.drawPath(path, focusPaint)
        } else {
            canvas.drawPath(path, borderPaint)
        }

        // Left accent bar
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isFocused) Color.parseColor("#3399ff") else Color.parseColor("#0d2a42")
        }
        canvas.drawRect(0f, c, 3f, h - c, accentPaint)

        super.onDraw(canvas)
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  CommandCard — Komut rehberi kartı
// ════════════════════════════════════════════════════════════════════════════
class CommandCard @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#040d18")
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0a1e30")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    init {
        setWillNotDraw(false)
        orientation = HORIZONTAL
        setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))

        // Attrs
        val a = context.obtainStyledAttributes(attrs, R.styleable.CommandCard)
        val icon    = a.getString(R.styleable.CommandCard_cmd_icon) ?: "◆"
        val trigger = a.getString(R.styleable.CommandCard_cmd_trigger) ?: ""
        val desc    = a.getString(R.styleable.CommandCard_cmd_desc) ?: ""
        a.recycle()

        // Icon
        val iconView = TextView(context).apply {
            text = icon
            textSize = 18f
            layoutParams = LayoutParams(dpToPx(36), LayoutParams.WRAP_CONTENT)
            gravity = android.view.Gravity.CENTER
        }

        // Text column
        val textCol = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dpToPx(8), 0, 0, 0)
        }

        val triggerView = TextView(context).apply {
            text = trigger
            textSize = 12f
            setTextColor(Color.parseColor("#7ecfff"))
            typeface = android.graphics.Typeface.MONOSPACE
        }

        val descView = TextView(context).apply {
            text = desc
            textSize = 10f
            setTextColor(Color.parseColor("#335566"))
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(2) }
        }

        textCol.addView(triggerView)
        textCol.addView(descView)
        addView(iconView)
        addView(textCol)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val path = Path().apply {
            moveTo(8f, 0f)
            lineTo(w, 0f)
            lineTo(w, h - 8f)
            lineTo(w - 8f, h)
            lineTo(0f, h)
            lineTo(0f, 8f)
            close()
        }
        canvas.drawPath(path, bgPaint)
        canvas.drawPath(path, borderPaint)

        // Left accent
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0d2a42")
        }
        canvas.drawRect(0f, 8f, 2f, h, accentPaint)
    }

    private fun dpToPx(dp: Int) = (dp * resources.displayMetrics.density).toInt()
}
