package com.vuegram.presentation.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * A soft light in the top-left corner, in the colour of whatever is focused; it fades between colours.
 *
 * The light is painted into a tiny picture ([W] x [H] pixels) and drawn stretched with smoothing: a full-screen
 * gradient computed by the GPU for every pixel, and rebuilt on every frame of each colour fade, cost a TV's GPU most
 * of a frame on screens that animate all the time (the library). A soft gradient looks the same either way.
 */
class GlowView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val small = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val smallCanvas = Canvas(small)
    private val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val target = Rect()
    private var color = 0xFF2A3A5A.toInt()
    private var animator: ValueAnimator? = null

    init {
        paintSmall()
    }

    fun setGlowColor(target: Int) {
        if (target == 0 || target == color) return
        val from = color
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            addUpdateListener {
                color = ColorUtils.blendARGB(from, target, it.animatedFraction)
                paintSmall()
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    /** The same light as before, in the small picture's own coordinates. */
    private fun paintSmall() {
        val center = (color and 0xFFFFFF) or (0x66 shl 24)
        shaderPaint.shader = RadialGradient(
            W * 0.18f, H * 0.05f, W * 0.65f,
            intArrayOf(center, (color and 0xFFFFFF) or (0x1A shl 24), 0x00000000),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
        small.eraseColor(0)
        smallCanvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), shaderPaint)
    }

    override fun onDraw(canvas: Canvas) {
        target.set(0, 0, width, height)
        canvas.drawBitmap(small, null, target, drawPaint)
    }

    private companion object {
        const val W = 96
        const val H = 54
    }
}
