package com.vuegram.data.library

import android.content.Context
import android.content.SharedPreferences

/**
 * The chats whose videos the viewer chose to see oldest first (a series, a course), so they open that way next time.
 * Every other chat shows its newest videos first. Stored on the TV only, and cleared on logging out.
 */
object ChatOrder {
    const val PREFS = "chat_order"
    private const val KEY = "oldest_first"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun ids(): Set<Long> = prefs.getString(KEY, "").orEmpty().split(',').mapNotNullTo(HashSet()) { it.toLongOrNull() }

    fun isOldestFirst(chatId: Long) = chatId in ids()

    fun setOldestFirst(chatId: Long, oldestFirst: Boolean) {
        val current = ids().filter { it != chatId }.toMutableList()
        if (oldestFirst) current += chatId
        prefs.edit().putString(KEY, current.joinToString(",")).apply()
    }
}
