package com.vuegram.presentation.ui

import android.view.animation.DecelerateInterpolator

/**
 * How things move in VueGram: three speeds and one easing (fast out, gentle stop), so every screen moves alike.
 * Fast: focus changes, a card growing. Normal: the focus ring gliding, a box widening. Slow: fades of whole areas.
 */
object Motion {
    const val FAST = 150L
    const val NORMAL = 220L
    const val SLOW = 320L

    /** A new instance each time: interpolators are cheap, and sharing one across animators is fine but not needed. */
    fun ease() = DecelerateInterpolator(2f)
}
