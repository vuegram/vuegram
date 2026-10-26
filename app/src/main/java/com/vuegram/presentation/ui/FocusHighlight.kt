package com.vuegram.presentation.ui

import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.Gravity
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * Keeps one [FocusRing] over the card that has focus in [list], and in any sideways shelf inside it ([follow]): it
 * glides to each newly focused card, stays on its card while the lists scroll, and fades out when focus leaves.
 * [host] is the FrameLayout around [list]; the ring is added to it, above the list.
 */
class FocusHighlight(private val host: FrameLayout, private val list: RecyclerView, cornerRadiusPx: Float) {

    private val ring = FocusRing(host.context).apply {
        cornerRadius = cornerRadiusPx
        alpha = 0f
    }
    private var target: View? = null
    private var grow = 1f
    private val spot = Rect()
    private val easing = DecelerateInterpolator(2f)

    init {
        // Placed from the left edge in every language: the translation below is measured from there.
        host.addView(ring, FrameLayout.LayoutParams(0, 0, Gravity.TOP or Gravity.LEFT))
        follow(list)
    }

    /** Keeps the ring on its card while [scrolling] scrolls, and drops it when its card scrolls away. */
    fun follow(scrolling: RecyclerView) {
        scrolling.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                target?.let { place(it, glide = false) }
            }
        })
        scrolling.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) = Unit
            override fun onChildViewDetachedFromWindow(view: View) {
                if (view === target) fadeOut()
            }
        })
    }

    /** From a card's focus listener. [scale] is how much the focused card grows, so the ring fits around it. */
    fun onFocus(card: View, hasFocus: Boolean, scale: Float) {
        if (hasFocus) {
            val appearing = target == null || ring.alpha == 0f
            target = card
            grow = scale
            place(card, glide = !appearing)
            ring.animate().alpha(1f).setDuration(if (appearing) 150L else 0L).start()
        } else if (card === target) {
            // The card loses focus before the next one gets it, while the list still counts as focused: decide once
            // focus has settled whether it went to another card or left the list.
            list.post { if (target === card && !list.hasFocus()) fadeOut() }
        }
    }

    fun setAccent(argb: Int) = ring.setAccent(argb)

    private fun fadeOut() {
        target = null
        ring.animate().alpha(0f).setDuration(120L).start()
    }

    private fun place(card: View, glide: Boolean) {
        // A shelf's card can still be attached while the grid has taken the whole shelf out for a moment.
        if (!card.isAttachedToWindow || !inHost(card)) return
        spot.set(0, 0, card.width, card.height)
        host.offsetDescendantRectToMyCoords(card, spot)
        val padX = card.width * (grow - 1f) / 2f + ring.spread
        val padY = card.height * (grow - 1f) / 2f + ring.spread
        val width = (card.width + 2 * padX).toInt()
        val height = (card.height + 2 * padY).toInt()
        val params = ring.layoutParams
        if (params.width != width || params.height != height) {
            params.width = width
            params.height = height
            ring.layoutParams = params
        }
        val x = spot.left - padX
        val y = spot.top - padY
        if (glide) {
            ring.animate().translationX(x).translationY(y).setInterpolator(easing).setDuration(GLIDE_MS).start()
        } else {
            ring.animate().cancel()
            ring.translationX = x
            ring.translationY = y
            if (target != null) ring.alpha = 1f
        }
    }

    private fun inHost(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === host) return true
            parent = parent.parent
        }
        return false
    }

    private companion object {
        const val GLIDE_MS = 220L
    }
}
