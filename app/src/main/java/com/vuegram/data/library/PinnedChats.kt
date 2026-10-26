package com.vuegram.data.library

import android.content.Context
import android.content.SharedPreferences

/** Groups and channels pinned to the top of the library, in the order they were pinned. Stored on the TV only. */
object PinnedChats {
    private const val PREFS = "pinned_chats"
    private const val KEY = "ids"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Pinned chat ids, first pinned first. */
    fun ids(): List<Long> = prefs.getString(KEY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    fun isPinned(chatId: Long) = chatId in ids()

    fun setPinned(chatId: Long, pinned: Boolean) {
        val current = ids().filter { it != chatId }.toMutableList()
        if (pinned) current += chatId
        prefs.edit().putString(KEY, current.joinToString(",")).apply()
    }
}
