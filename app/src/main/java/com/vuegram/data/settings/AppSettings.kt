package com.vuegram.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * The viewer's choices from the Settings screen. Show people and Show messages are off by default, so a fresh install
 * shows only groups and channels, and only their videos; Play next is on. Stored on the TV only.
 */
object AppSettings {
    private const val PREFS = "settings"
    private const val SHOW_PEOPLE = "show_people"
    private const val SHOW_MESSAGES = "show_messages"
    private const val PLAY_NEXT = "play_next"

    private lateinit var prefs: SharedPreferences

    /** Goes up on every change, so a screen can tell whether it must reload after the viewer leaves Settings. */
    @Volatile var version = 0
        private set

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** The library also lists private chats with people (and Saved Messages), not only groups and channels. */
    var showPeople: Boolean
        get() = prefs.getBoolean(SHOW_PEOPLE, false)
        set(value) = put(SHOW_PEOPLE, value)

    /** A chat shows all its messages (text, photos, videos, files), not only its videos. */
    var showMessages: Boolean
        get() = prefs.getBoolean(SHOW_MESSAGES, false)
        set(value) = put(SHOW_MESSAGES, value)

    /** When a video ends, the chat's next one starts after a 10-second "Up next" countdown (on by default). */
    var playNext: Boolean
        get() = prefs.getBoolean(PLAY_NEXT, true)
        set(value) = put(PLAY_NEXT, value)

    private fun put(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
        version++
    }
}
