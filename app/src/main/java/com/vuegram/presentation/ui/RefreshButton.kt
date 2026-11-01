package com.vuegram.presentation.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * A top bar's refresh button: OK asks Telegram for what is new ([onRefresh], which calls its `done` once the answer is
 * shown, with false when it failed). The arrows turn until then, at least one full turn, and then [doneMessage] says
 * the content is fresh ("Your channels are up to date": about the videos, so nobody takes it for an app update); a
 * failure says nothing here, as the screen already says what went wrong. A second press while it turns does nothing.
 */
class RefreshButton(
    private val button: View,
    @StringRes private val doneMessage: Int,
    private val onRefresh: (done: (ok: Boolean) -> Unit) -> Unit
) {
    private var spin: ObjectAnimator? = null
    private var startedAt = 0L

    init {
        button.setOnClickListener { start() }
        // The screen went (Back, another screen) before the answer came: stop turning, so nothing holds on to it.
        button.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                spin?.cancel()
                spin = null
                button.rotation = 0f
            }
        })
    }

    private fun start() {
        if (spin != null) return
        startedAt = SystemClock.uptimeMillis()
        spin = ObjectAnimator.ofFloat(button, View.ROTATION, 0f, 360f).apply {
            duration = TURN_MS
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        onRefresh { ok -> button.post { finish(ok) } }
    }

    private fun finish(ok: Boolean) {
        if (spin == null) return
        val left = TURN_MS - (SystemClock.uptimeMillis() - startedAt)
        if (left > 0) {
            button.postDelayed({ finish(ok) }, left)
            return
        }
        spin?.cancel()
        spin = null
        button.rotation = 0f
        if (ok && button.isAttachedToWindow) Toast.makeText(button.context, doneMessage, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TURN_MS = 800L
    }
}
