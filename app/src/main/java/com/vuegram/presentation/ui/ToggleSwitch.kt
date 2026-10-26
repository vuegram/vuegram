package com.vuegram.presentation.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * The on/off switch at the end of a settings row: a violet track with the knob at the end when on, a grey one with
 * the knob at the start when off. The row handles OK; this only shows the state (and says it to TalkBack).
 */
class ToggleSwitch @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bounds = RectF()
    /** 0 = off, 1 = on; animated in between. */
    private var position = 0f
    private var animator: ValueAnimator? = null

    var isOn = false
        private set

    init {
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setOn(on: Boolean, animate: Boolean) {
        isOn = on
        animator?.cancel()
        val target = if (on) 1f else 0f
        if (!animate || !isLaidOut) {
            position = target
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = 160
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension((WIDTH_DP * density).toInt(), (HEIGHT_DP * density).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        bounds.set(0f, 0f, width.toFloat(), h)
        track.color = ColorUtils.blendARGB(OFF, ON, position)
        canvas.drawRoundRect(bounds, h / 2, h / 2, track)
        val inset = 3 * density
        val radius = h / 2 - inset
        val travel = width - 2 * (inset + radius)
        val fromStart = inset + radius + travel * position
        // In right-to-left languages "on" is at the left.
        val cx = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - fromStart else fromStart
        knob.alpha = (200 + 55 * position).toInt()
        canvas.drawCircle(cx, h / 2, radius, knob)
    }

    private companion object {
        const val WIDTH_DP = 46
        const val HEIGHT_DP = 26
        const val ON = 0xFF8B5CF6.toInt()
        const val OFF = 0x59FFFFFF
    }
}
