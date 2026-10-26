package com.vuegram.presentation.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText

/**
 * Left and Right stay in a screen's top bar. At its ends Android would otherwise look below, so Left on the first
 * button or Right on the last dropped into the grid. A search box with text in it keeps the arrows for its cursor.
 */
fun keepSidewaysInBar(bar: ViewGroup, vararg ids: Int) {
    val stay = View.OnKeyListener { item, keyCode, event ->
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            else -> return@OnKeyListener false
        }
        if (event.action != KeyEvent.ACTION_DOWN || (item is EditText && item.text.isNotEmpty())) return@OnKeyListener false
        val next = item.focusSearch(direction)
        var parent = next?.parent
        while (parent != null && parent !== bar) parent = parent.parent
        parent == null
    }
    ids.forEach { bar.findViewById<View>(it)?.setOnKeyListener(stay) }
}
