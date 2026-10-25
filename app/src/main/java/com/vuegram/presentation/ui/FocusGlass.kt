package com.vuegram.presentation.ui

import android.graphics.Rect
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * Moves one [GlassView] over whichever card of [list] has focus. The glass springs (overshoots slightly)
 * between cards and follows the grid without animation while it scrolls.
 *
 * [container] must be a FrameLayout that holds [list]; the glass is added to it, above the list.
 */
class FocusGlass(private val container: FrameLayout, private val list: RecyclerView, cornerRadiusPx: Float) {

    val glass = GlassView(container.context).apply {
        radius = cornerRadiusPx
        alpha = 0f
    }
    private val rect = Rect()
    private val spring = OvershootInterpolator(0.9f)
    private var focused: View? = null

    init {
        container.addView(glass, FrameLayout.LayoutParams(0, 0))
        follow(list)
    }

    /** Keeps the glass on its card while [scrolling] scrolls: the grid, or a sideways shelf inside it. */
    fun follow(scrolling: RecyclerView) {
        scrolling.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                focused?.let { place(it, animate = false) }
            }
        })
        scrolling.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) = Unit
            override fun onChildViewDetachedFromWindow(view: View) {
                if (view === focused) {
                    focused = null
                    glass.animate().alpha(0f).setDuration(120).start()
                }
            }
        })
    }

    /** Call from a card's focus listener. [scale] is how much the focused card grows, so the glass covers it. */
    fun onFocus(view: View, hasFocus: Boolean, scale: Float) {
        if (hasFocus) {
            val first = focused == null || glass.alpha == 0f
            focused = view
            place(view, animate = !first, scale = scale)
            glass.animate().alpha(1f).setDuration(if (first) 160 else 0).start()
        } else if (view === focused) {
            // Losing focus, the card's grid still counts as focused: look again once focus has settled.
            list.post {
                if (focused === view && !list.hasFocus()) {
                    focused = null
                    glass.animate().alpha(0f).setDuration(120).start()
                }
            }
        }
    }

    fun setAccent(argb: Int) = glass.setAccent(argb)

    private fun isInContainer(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === container) return true
            parent = parent.parent
        }
        return false
    }

    private var lastScale = 1f

    private fun place(view: View, animate: Boolean, scale: Float = lastScale) {
        lastScale = scale
        // A shelf's card stays attached while the grid briefly takes the whole shelf out during a layout: skip it then.
        if (!view.isAttachedToWindow || !isInContainer(view)) return
        view.getDrawingRect(rect)
        container.offsetDescendantRectToMyCoords(view, rect)
        val growX = (rect.width() * (scale - 1f) / 2f).toInt()
        val growY = (rect.height() * (scale - 1f) / 2f).toInt()
        val pad = glass.inset.toInt()
        val w = rect.width() + 2 * (growX + pad)
        val h = rect.height() + 2 * (growY + pad)
        val lp = glass.layoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            glass.layoutParams = lp
        }
        val x = (rect.left - growX - pad).toFloat()
        val y = (rect.top - growY - pad).toFloat()
        if (animate) {
            glass.animate().translationX(x).translationY(y).setInterpolator(spring).setDuration(280).start()
        } else {
            glass.animate().cancel()
            glass.translationX = x
            glass.translationY = y
            if (focused != null) glass.alpha = 1f
        }
    }
}
