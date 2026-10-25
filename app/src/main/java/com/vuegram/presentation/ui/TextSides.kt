package com.vuegram.presentation.ui

import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.core.text.BidiFormatter

/**
 * Puts a title and the lines that go with it on the side the title reads from: a Hebrew or Arabic title and its lines
 * on the right, any other on the left, whatever VueGram's language. Each line on its own would follow its own text,
 * so a Hebrew title over "Channel" would split across the screen.
 */
fun alignWithTitle(title: TextView, vararg lines: TextView) {
    val side = if (BidiFormatter.getInstance().isRtl(title.text.toString())) Gravity.RIGHT else Gravity.LEFT
    for (view in listOf(title, *lines)) {
        view.textAlignment = View.TEXT_ALIGNMENT_GRAVITY
        view.gravity = side or (view.gravity and Gravity.VERTICAL_GRAVITY_MASK)
    }
}
