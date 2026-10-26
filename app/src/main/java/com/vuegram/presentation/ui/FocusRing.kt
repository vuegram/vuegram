package com.vuegram.presentation.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * VueGram's focus highlight: a crisp ring around the focused card, a soft halo behind it and the faintest tint
 * inside, all in the colour of what is focused (VueGram's violet by default). [FocusHighlight] moves it.
 */
class FocusRing(context: Context) : View(context) {

    private val density = resources.displayMetrics.density

    /** How far the halo spreads around the card, in px; the view is that much larger than the card on each side. */
    val spread = 10f * density

    var cornerRadius = 18f * density
        set(value) {
            field = value
            invalidate()
        }

    private var accent = VIOLET
    private var ringEnd = Color.WHITE
    private val bounds = RectF()
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        maskFilter = BlurMaskFilter(7f * density, BlurMaskFilter.Blur.NORMAL)
    }
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    init {
        // The halo's blur is drawn in software only. The layer keeps the drawing, so gliding just moves it.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isFocusable = false
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        recolour()
    }

    /** The colour of what is focused, such as a chat photo's average colour; 0 means VueGram's violet. */
    fun setAccent(argb: Int) {
        val colour = if (argb == 0) VIOLET else argb or OPAQUE
        if (colour == accent) return
        accent = colour
        recolour()
        invalidate()
    }

    private fun recolour() {
        halo.color = ColorUtils.setAlphaComponent(accent, 0xB4)
        tint.color = ColorUtils.setAlphaComponent(accent, 0x1C)
        ringEnd = ColorUtils.blendARGB(accent, Color.WHITE, 0.6f)
        shadeRing()
    }

    /** The ring runs from white at the top left to a light tint of the accent at the bottom right. */
    private fun shadeRing() {
        if (width == 0 || height == 0) return
        ring.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.WHITE, ringEnd, Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shadeRing()
    }

    override fun onDraw(canvas: Canvas) {
        bounds.set(spread, spread, width - spread, height - spread)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, halo)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, tint)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, ring)
    }

    private companion object {
        const val VIOLET = 0xFF8B5CF6.toInt()
        const val OPAQUE = 0xFF000000.toInt()
    }
}
