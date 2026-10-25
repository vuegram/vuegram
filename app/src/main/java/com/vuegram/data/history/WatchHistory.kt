package com.vuegram.data.history

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Where the viewer stopped in a video, and whether it was watched to the end. Stored on the TV only. */
data class WatchEntry(
    val chatId: Long,
    val messageId: Long,
    val title: String,
    val chatTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val watched: Boolean,
    val updatedAt: Long
) {
    /** 0..1 of the video seen, or 0 when the length is unknown. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)
}

object WatchHistory {
    private const val PREFS = "watch_history"
    private const val PENDING = "pending"
    /** Less than this in, and a video counts as not started; more than WATCHED_AT through, it counts as watched. */
    private const val MIN_RESUME_MS = 30_000L
    private const val WATCHED_AT = 0.92f
    private const val MAX_ENTRIES = 300

    private lateinit var prefs: SharedPreferences
    private val listeners = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        if (!::prefs.isInitialized) prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun key(chatId: Long, messageId: Long) = "v:$chatId:$messageId"

    fun get(chatId: Long, messageId: Long): WatchEntry? =
        prefs.getString(key(chatId, messageId), null)?.let(::parse)

    /** Where to start [chatId]/[messageId]: the saved position a few seconds early, or 0. */
    fun resumePositionMs(chatId: Long, messageId: Long): Long {
        val entry = get(chatId, messageId) ?: return 0L
        if (entry.watched || entry.positionMs < MIN_RESUME_MS) return 0L
        return (entry.positionMs - 5_000L).coerceAtLeast(0L)
    }

    /** Videos started but not finished, most recent first. */
    fun continueWatching(limit: Int): List<WatchEntry> =
        prefs.all.entries.asSequence()
            .filter { it.key.startsWith("v:") }
            .mapNotNull { (it.value as? String)?.let(::parse) }
            .filter { !it.watched && it.positionMs >= MIN_RESUME_MS }
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .toList()

    /** Remembers which video was handed to the player, in case VueGram's process is gone when it returns. */
    fun setPending(chatId: Long, messageId: Long, title: String, chatTitle: String, durationMs: Long) {
        prefs.edit().putString(PENDING, JSONObject()
            .put("chatId", chatId).put("messageId", messageId).put("title", title)
            .put("chatTitle", chatTitle).put("durationMs", durationMs).toString()).apply()
    }

    /**
     * The player has closed. [positionMs] and [durationMs] are what it reported (null when it reported nothing,
     * which mpv does when the video played to its end).
     */
    fun finishPending(positionMs: Long?, durationMs: Long?) = record(positionMs, durationMs, keepPending = false)

    /**
     * Saves where the built-in player is now, while it keeps playing, so a crash or a killed process loses at most
     * the time since the last checkpoint.
     */
    fun checkpoint(positionMs: Long, durationMs: Long) = record(positionMs, durationMs, keepPending = true)

    private fun record(positionMs: Long?, durationMs: Long?, keepPending: Boolean) {
        val pending = prefs.getString(PENDING, null)?.let { JSONObject(it) } ?: return
        if (!keepPending) prefs.edit().remove(PENDING).apply()
        val chatId = pending.getLong("chatId")
        val messageId = pending.getLong("messageId")
        val previous = get(chatId, messageId)
        val duration = durationMs?.takeIf { it > 0 } ?: pending.optLong("durationMs").takeIf { it > 0 }
            ?: previous?.durationMs ?: 0L
        val playedToEnd = positionMs == null
        val position = positionMs ?: duration
        val watched = playedToEnd || (duration > 0 && position >= duration * WATCHED_AT) || previous?.watched == true
        // Opened and closed within seconds, without having been started before: not worth remembering.
        if (!watched && position < MIN_RESUME_MS && previous == null) return
        save(
            WatchEntry(
                chatId, messageId,
                pending.optString("title"), pending.optString("chatTitle"),
                if (watched) duration else position, duration, watched, System.currentTimeMillis()
            )
        )
    }

    /** The player gave up before playing anything: nothing to record. */
    fun clearPending() {
        prefs.edit().remove(PENDING).apply()
    }

    /** Marks a video watched, e.g. from the Continue Watching card's menu. */
    fun markWatched(chatId: Long, messageId: Long) {
        val entry = get(chatId, messageId) ?: return
        save(entry.copy(positionMs = entry.durationMs, watched = true, updatedAt = System.currentTimeMillis()))
    }

    /** From a video's menu: watched (at its end) or not watched at all (no saved position). */
    fun setWatched(chatId: Long, messageId: Long, title: String, chatTitle: String, durationMs: Long, watched: Boolean) {
        if (!watched) {
            forget(chatId, messageId)
            return
        }
        val duration = durationMs.takeIf { it > 0 } ?: get(chatId, messageId)?.durationMs ?: 0L
        save(WatchEntry(chatId, messageId, title, chatTitle, duration, duration, true, System.currentTimeMillis()))
    }

    /** Takes a video off Continue Watching without marking it watched. */
    fun forget(chatId: Long, messageId: Long) {
        prefs.edit().remove(key(chatId, messageId)).apply()
        notifyChanged()
    }

    private fun save(entry: WatchEntry) {
        val saved = key(entry.chatId, entry.messageId)
        val editor = prefs.edit().putString(saved, JSONObject()
            .put("chatId", entry.chatId).put("messageId", entry.messageId)
            .put("title", entry.title).put("chatTitle", entry.chatTitle)
            .put("positionMs", entry.positionMs).put("durationMs", entry.durationMs)
            .put("watched", entry.watched).put("updatedAt", entry.updatedAt).toString())
        // Keep the store small: drop the oldest entries beyond the cap, never the one being saved (a remove in the
        // same edit would win over its put).
        val others = prefs.all.entries.filter { it.key.startsWith("v:") && it.key != saved }
        val excess = others.size + 1 - MAX_ENTRIES
        if (excess > 0) {
            others.mapNotNull { e -> (e.value as? String)?.let(::parse)?.let { e.key to it.updatedAt } }
                .sortedBy { it.second }
                .take(excess)
                .forEach { editor.remove(it.first) }
        }
        editor.apply()
        notifyChanged()
    }

    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)
    private fun notifyChanged() = listeners.toList().forEach { it() }

    private fun parse(json: String): WatchEntry? = try {
        val o = JSONObject(json)
        WatchEntry(
            o.getLong("chatId"), o.getLong("messageId"), o.optString("title"), o.optString("chatTitle"),
            o.optLong("positionMs"), o.optLong("durationMs"), o.optBoolean("watched"), o.optLong("updatedAt")
        )
    } catch (_: Exception) {
        null
    }
}
