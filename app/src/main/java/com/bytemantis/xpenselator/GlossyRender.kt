package com.bytemantis.xpenselator

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader

object GlossyRender {

    // Shared "Glass Tube" drawing logic
    fun drawGlossyBar(
        canvas: Canvas,
        rect: RectF,
        baseColor: Int,
        paint: Paint,
        isPdf: Boolean = false // NEW: Added PDF-awareness flag
    ) {
        val radius = rect.height() / 4f

        if (isPdf) {
            // PDF-Safe drawing: Strip shaders and use solid base color
            paint.shader = null
            paint.color = baseColor
            canvas.drawRoundRect(rect, radius, radius, paint)
        } else {
            // 1. Draw Base Cylinder (Darker at bottom for roundness)
            paint.shader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(baseColor, darken(baseColor, 0.7f)),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(rect, radius, radius, paint)

            // 2. Draw "Glass Highlight" (The white shine on top)
            // We shrink the rect slightly so the shine sits inside
            val shineRect = RectF(rect.left, rect.top, rect.right, rect.centerY())

            paint.shader = LinearGradient(
                shineRect.left, shineRect.top, shineRect.left, shineRect.bottom,
                intArrayOf(Color.argb(100, 255, 255, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(shineRect, radius, radius, paint)

            // Reset Paint
            paint.shader = null
        }
    }

    private fun darken(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.argb(a, r, g, b)
    }
}