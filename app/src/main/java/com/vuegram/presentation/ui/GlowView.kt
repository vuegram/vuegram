package com.vuegram.presentation.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/** A soft light in the top-left corner, in the colour of whatever is focused; it fades between colours. */
class GlowView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var color = 0xFF2A3A5A.toInt()
    private var animator: ValueAnimator? = null

    fun setGlowColor(target: Int) {
        if (target == 0 || target == color) return
        val from = color
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            addUpdateListener {
                color = ColorUtils.blendARGB(from, target, it.animatedFraction)
                updateShader()
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShader()
    }

    private fun updateShader() {
        if (width == 0 || height == 0) return
        val center = (color and 0xFFFFFF) or (0x66 shl 24)
        paint.shader = RadialGradient(
            width * 0.18f, height * 0.05f, width * 0.65f,
            intArrayOf(center, (color and 0xFFFFFF) or (0x1A shl 24), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
