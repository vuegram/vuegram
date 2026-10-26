package com.vuegram.data.library

import android.content.Context
import android.content.SharedPreferences

/** Groups and channels the viewer marked as not relevant; the library leaves them out. Stored on the TV only. */
object HiddenChats {
    private const val PREFS = "hidden_chats"
    private const val KEY = "ids"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun ids(): Set<Long> = prefs.getStringSet(KEY, emptySet()).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

    fun isHidden(chatId: Long) = chatId in ids()

    fun setHidden(chatId: Long, hidden: Boolean) {
        val current = prefs.getStringSet(KEY, emptySet()).orEmpty().toMutableSet()
        if (hidden) current += chatId.toString() else current -= chatId.toString()
        prefs.edit().putStringSet(KEY, current).apply()
    }
}
