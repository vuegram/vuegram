package com.vuegram.presentation.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.vuegram.R

/**
 * Grey cards in the shape of what is loading, with a soft light sweeping across them, instead of "Loading…": the
 * screen looks like itself from the first moment. [columns] cards a row, each [aspect] as tall as it is wide, with a
 * name line under each; two rows. It animates only while shown, and fades out when the real cards arrive.
 */
class SkeletonView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var columns = 6
    var aspect = 1f

    private val density = resources.displayMetrics.density
    private val base = ContextCompat.getColor(context, R.color.tv_card)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweep = Matrix()
    private val rect = RectF()
    private var shift = 0f
    private var animator: ValueAnimator? = null

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** Shows the placeholders (already shown: nothing changes). */
    fun show() {
        animate().cancel()
        alpha = 1f
        visibility = VISIBLE
    }

    /** Fades them out; the real cards are underneath. */
    fun hide() {
        if (visibility != VISIBLE) return
        animate().alpha(0f).setDuration(Motion.NORMAL).withEndAction { visibility = GONE }.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val light = ColorUtils.blendARGB(base, 0xFFFFFFFF.toInt(), 0.07f)
        paint.shader = LinearGradient(0f, 0f, w * 0.35f, 0f, intArrayOf(base, light, base), null, Shader.TileMode.CLAMP)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        update()
    }

    /** The player or another app covering the screen: no shimmer nobody sees (it would wake the main thread). */
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        update()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        update()
    }

    private fun update() {
        if (isShown && windowVisibility == VISIBLE) start() else stop()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun start() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(-0.4f, 1.1f).apply {
            duration = SWEEP_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                shift = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stop() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        sweep.setTranslate(width * shift, 0f)
        paint.shader?.setLocalMatrix(sweep)
        val gap = 24 * density
        val usable = width - paddingLeft - paddingRight
        val tile = (usable - gap * (columns - 1)) / columns
        val tileHeight = tile * aspect
        val radius = 18 * density
        var top = paddingTop.toFloat()
        repeat(ROWS) {
            for (i in 0 until columns) {
                val left = paddingLeft + i * (tile + gap)
                rect.set(left, top, left + tile, top + tileHeight)
                canvas.drawRoundRect(rect, radius, radius, paint)
                // The name under the card.
                val line = 14 * density
                rect.set(left, top + tileHeight + 12 * density, left + tile * 0.7f, top + tileHeight + 12 * density + line)
                canvas.drawRoundRect(rect, line / 2, line / 2, paint)
            }
            top += tileHeight + 60 * density
        }
    }

    private companion object {
        const val ROWS = 2
        const val SWEEP_MS = 1_400L
    }
}
