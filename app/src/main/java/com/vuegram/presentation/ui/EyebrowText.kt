package com.vuegram.presentation.ui

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * The small spaced-out labels (TvText.Eyebrow). Wide letter spacing suits Latin, Greek and Cyrillic capitals only: it
 * pulls Hebrew, Chinese and Hindi apart and breaks the joins of Arabic and Persian. So any other letter in the text,
 * a translated heading or a chat's name, turns the spacing off.
 */
class EyebrowText(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {

    /** The style's spacing; read once the style is applied (TextView sets the text before this class is ready). */
    private val styleSpacing = letterSpacing
    private var ready = false

    init {
        ready = true
        fitSpacing()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(text, type)
        if (ready) fitSpacing()
    }

    private fun fitSpacing() {
        val spacing = if (spaceable(text)) styleSpacing else 0f
        if (letterSpacing != spacing) letterSpacing = spacing
    }

    companion object {
        /** True when every letter is Latin, Greek or Cyrillic (all below Armenian, U+0530). */
        fun spaceable(text: CharSequence?): Boolean = text.isNullOrEmpty() || text.none { Character.isLetter(it) && it.code >= 0x0530 }
    }
}
