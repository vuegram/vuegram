package com.vuegram.presentation.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.EditText

/**
 * A search box that rests as a round button (just the magnifier) and widens to [expandedWidthPx] when focused or when
 * it holds a search, so a header's title keeps the room the rest of the time. The hint shows only while it is wide.
 */
class ExpandingSearch(private val box: EditText, private val expandedWidthPx: Int) {

    private val hint: CharSequence? = box.hint
    private val density = box.resources.displayMetrics.density
    private val collapsedWidthPx = box.layoutParams.width
    private var animator: ValueAnimator? = null

    /** The hint to show while wide; screens that change it (search all chats, search messages) call [setHint]. */
    private var currentHint: CharSequence? = hint

    init {
        box.setOnFocusChangeListener { _, _ -> update(animate = true) }
        update(animate = false)
    }

    /** Call after changing the text from code (a search set or cleared), so the width follows. */
    fun update(animate: Boolean = true) {
        val wide = box.hasFocus() || box.text.isNotEmpty()
        box.hint = if (wide) currentHint else null
        // Collapsed, the magnifier sits in the middle of the circle; wide, it leads the text.
        val start = if (wide) (20 * density).toInt() else (box.layoutParams.height - ICON_DP * density).toInt() / 2
        box.setPaddingRelative(start, 0, if (wide) (20 * density).toInt() else 0, 0)
        val target = if (wide) expandedWidthPx else collapsedWidthPx
        animator?.cancel()
        if (!animate || box.layoutParams.width == target) {
            setWidth(target)
            return
        }
        animator = ValueAnimator.ofInt(box.layoutParams.width, target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener { setWidth(it.animatedValue as Int) }
            start()
        }
    }

    fun setHint(text: CharSequence) {
        currentHint = text
        if (box.hasFocus() || box.text.isNotEmpty()) box.hint = text
    }

    private fun setWidth(width: Int) {
        val params = box.layoutParams
        params.width = width
        box.layoutParams = params
        box.visibility = View.VISIBLE
    }

    private companion object {
        /** The magnifier's size (ic_search). */
        const val ICON_DP = 22
    }
}
