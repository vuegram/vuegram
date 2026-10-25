package com.vuegram.presentation.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * A translucent glass slab: soft sheen, bright top specular, crisp rim and a faint outer glow. One instance
 * floats over a grid and springs between cards.
 *
 * Ported from GlassView in the cleanyourtv launcher (github.com/Gathin23/cleanyourtv, MIT).
 */
class GlassView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specular = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xCCFFFFFF.toInt()
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private val density = resources.displayMetrics.density
    private var glowRgb = 0xFFFFFF
    private var glowAlpha = 0x30

    /** Room around the card for the outer glow, in px. */
    val inset = 6f * density
    var radius = 18f * density
        set(value) {
            field = value
            invalidate()
        }

    init {
        rim.strokeWidth = 1.5f * density
        // The animation only changes translation, so the slab is drawn once into a texture and moved by the GPU.
        setLayerType(LAYER_TYPE_HARDWARE, null)
        isFocusable = false
        isClickable = false
    }

    /** Tints the rim and the glow, e.g. with the focused chat's colour; 0 is plain white glass. */
    fun setAccent(argb: Int) {
        if (argb == 0) {
            glowRgb = 0xFFFFFF
            glowAlpha = 0x30
            rim.color = 0xCCFFFFFF.toInt()
        } else {
            glowRgb = argb and 0xFFFFFF
            glowAlpha = 0x70
            val r = (((glowRgb shr 16) and 0xFF) + 255 * 2) / 3
            val g = (((glowRgb shr 8) and 0xFF) + 255 * 2) / 3
            val b = ((glowRgb and 0xFF) + 255 * 2) / 3
            rim.color = (0xE6 shl 24) or (r shl 16) or (g shl 8) or b
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fill.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0x2EFFFFFF, 0x0AFFFFFF, Shader.TileMode.CLAMP)
        specular.shader = LinearGradient(0f, 0f, 0f, h * 0.45f, 0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(inset, inset, width - inset, height - inset)
        // The glow as thin rings across the inset, each fainter than the last, so it fades out smoothly.
        val rings = 12
        val step = inset / rings
        glow.strokeWidth = step + 0.5f
        for (i in 0 until rings) {
            val o = step * (i + 0.5f)
            val t = 1f - i.toFloat() / rings
            glow.color = (Math.round(glowAlpha * t) shl 24) or glowRgb
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, glow)
        }
        canvas.drawRoundRect(rect, radius, radius, fill)
        canvas.save()
        canvas.clipRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f)
        canvas.drawRoundRect(rect, radius, radius, specular)
        canvas.restore()
        canvas.drawRoundRect(rect, radius, radius, rim)
    }
}
