package com.example.xpenselator

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

// FIX: Added @JvmOverloads and 'attrs' so XML layout inflation works without crashing
class GlossyBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress: Float = 0f // 0.0 to 1.0
    private var targetProgress: Float = 0f
    private var barColor: Int = Color.GRAY
    private var labelText: String = ""
    private var valueText: String = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        textPaint.color = Color.WHITE
        textPaint.textSize = 30f // Adjust size as needed
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.setShadowLayer(5f, 2f, 2f, Color.BLACK) // Text Shadow for readability
    }

    fun setup(name: String, value: String, percentage: Float, color: Int) {
        this.labelText = name
        this.valueText = value
        this.targetProgress = percentage
        this.barColor = color
        invalidate()
    }

    // Call this to trigger the "Smooth and Fast" animation
    fun animateBar() {
        val animator = ValueAnimator.ofFloat(0f, targetProgress)
        animator.duration = 600 // Fast but smooth (600ms)
        animator.interpolator = DecelerateInterpolator() // Starts fast, slows at end
        animator.addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    // For non-animated lists (Summary tab)
    fun setStatic() {
        progress = targetProgress
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fixed height for the bar rows
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 100)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Calculate Bar Width
        val barWidth = w * progress

        // Safety: Even if progress is 0, draw a tiny line so it looks valid
        val drawWidth = if (barWidth < 20f) 20f else barWidth

        rect.set(10f, 15f, drawWidth, h - 15f)

        // Use the Shared Renderer
        GlossyRender.drawGlossyBar(canvas, rect, barColor, paint)

        // Draw Text ON TOP of the bar
        val text = "$labelText: $valueText"
        // Draw text slightly indented
        canvas.drawText(text, 30f, (h / 2f) + 10f, textPaint)
    }
}