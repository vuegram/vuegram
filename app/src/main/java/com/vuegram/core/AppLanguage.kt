package com.vuegram.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The language VueGram shows, chosen in Settings: the TV's own language by default, or one of [SUPPORTED]. Applied by
 * wrapping the base context of the application and of each activity, so text, dates and the reading direction
 * (right-to-left for Arabic, Hebrew and Persian) all follow it. Changing it restarts VueGram.
 */
object AppLanguage {
    private const val PREFS = "settings"
    private const val KEY = "language"

    /** The languages VueGram is translated into, as BCP 47 tags, in the order Settings lists them. */
    val SUPPORTED = listOf("en", "zh-CN", "zh-TW", "es", "pt-BR", "ru", "ar", "he", "hi", "id", "fa", "tr")

    /** The chosen tag, or "" for the TV's own language. */
    fun get(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
    }

    /** [tag]'s name in its own language ("Español", "עברית", "中文（简体）"), for the language list. */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }

    /** [base] with the chosen language applied, or [base] itself when VueGram follows the TV. */
    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}
