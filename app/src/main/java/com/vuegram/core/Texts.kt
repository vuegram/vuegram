package com.vuegram.core

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import com.vuegram.R
import java.util.Locale

/**
 * Words the data layer needs (a video with no caption is called "Video", a file message reads "📄 name") in the
 * viewer's language. They come from the app's string resources; before [init] (in unit tests) the English text is
 * used. The application context follows the language chosen in Settings (see [AppLanguage]).
 */
// Holds only the application context, which lives as long as the process: no activity can leak through it.
@SuppressLint("StaticFieldLeak")
object Texts {
    @Volatile private var app: Context? = null

    fun init(context: Context) {
        app = context.applicationContext
    }

    private fun text(@StringRes id: Int, english: String, vararg args: Any): String =
        app?.getString(id, *args) ?: String.format(Locale.US, english, *args)

    fun video() = text(R.string.feed_video, "Video")

    /**
     * A video with no caption and no file name: "Video · Oct 22, 2026", so a grid of them still tells them apart (and
     * a series reads in order). [unixSeconds] is the message's date; 0 leaves the date out.
     */
    fun untitledVideo(unixSeconds: Int): String {
        if (unixSeconds <= 0) return video()
        val date = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(unixSeconds * 1000L))
        return "${video()} · $date"
    }
    fun photo() = text(R.string.feed_photo, "Photo")
    fun videoMessage() = text(R.string.label_video_message, "Video message")
    fun gif() = text(R.string.label_gif, "GIF")
    fun file(name: String) = text(R.string.label_file, "📄 %1\$s", name)
    fun fileUnnamed() = text(R.string.label_file_unnamed, "📄 File")
    fun audio(name: String) = text(R.string.label_audio, "🎵 %1\$s", name)
    fun audioUnnamed() = text(R.string.label_audio_unnamed, "🎵 Audio")
    fun voice() = text(R.string.label_voice, "🎤 Voice message")
    fun sticker(emoji: String) = text(R.string.label_sticker, "%1\$s Sticker", emoji).trim()
    fun poll(question: String) = text(R.string.label_poll, "📊 %1\$s", question)
    fun location() = text(R.string.label_location, "📍 Location")
    fun contact(name: String) = text(R.string.label_contact, "👤 %1\$s", name)
    fun savedMessages() = text(R.string.kind_saved, "Saved Messages")
    fun unknownError() = text(R.string.error_unknown, "Unknown error")
    fun unexpectedAnswer() = text(R.string.error_unexpected, "Unexpected answer from Telegram")
    fun couldNotLoadChats() = text(R.string.error_load_chats, "Could not load your chats")
}
