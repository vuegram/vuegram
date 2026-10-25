package com.vuegram.presentation.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import com.vuegram.R
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.stream.TdStreamServer
import com.vuegram.domain.model.MediaItem
import java.util.Locale

/**
 * Starts a video, streamed from Telegram by TdStreamServer, where the viewer stopped last time. It plays in the
 * built-in player ([PlayerActivity]) unless [play] is asked for mpv, which the built-in player also falls back to
 * for files it cannot decode. mpv is opened for a result and reports the position (ms) when it closes; see
 * mpv-android's intent API: http://mpv-android.github.io/mpv-android/intent.html
 */
object Player {
    /** When [play] last started a video; a second OK within [DOUBLE_PRESS_MS] is ignored. */
    private var lastPlayAt = 0L
    private const val DOUBLE_PRESS_MS = 1_500L
    private const val MPV = "is.xyz.mpv"

    /**
     * [fromStart] ignores the saved position; [useMpv] opens mpv instead of the built-in player.
     * [launcher] receives mpv's result (MainActivity.playerLauncher).
     */
    fun play(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        item: MediaItem,
        chatTitle: String,
        useMpv: Boolean = false,
        fromStart: Boolean = false,
        upNext: List<MediaItem> = emptyList()
    ) {
        // OK pressed twice before the player shows (slow TVs): one player, or the second loses the saved position.
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayAt < DOUBLE_PRESS_MS) return
        lastPlayAt = now
        val (url, resumeMs) = startStream(context, item, chatTitle, fromStart) ?: return
        try {
            if (!useMpv) {
                context.startActivity(PlayerActivity.intent(context, url, item.title, chatTitle, resumeMs, upNext))
                return
            }
            val mpv = mpvIntent(context, url, item.title, resumeMs)
            if (mpv != null) {
                launcher.launch(mpv)
            } else {
                Toast.makeText(context, R.string.no_mpv, Toast.LENGTH_LONG).show()
                context.startActivity(PlayerActivity.intent(context, url, item.title, chatTitle, resumeMs, upNext))
            }
        } catch (e: Exception) {
            Log.e("VueGram", "No player for the stream", e)
            WatchHistory.clearPending()
            Toast.makeText(context, R.string.no_player, Toast.LENGTH_LONG).show()
            TdStreamServer.release(context)
        }
    }

    /**
     * Starts streaming [item] (the previous video's file is deleted), remembers it as the one playing, and says where it
     * resumes. Returns the stream's URL and the position to start at, or null when the item has no file.
     */
    fun startStream(context: Context, item: MediaItem, chatTitle: String, fromStart: Boolean = false): Pair<String, Long>? {
        val fileId = item.fileId ?: return null
        val url = TdStreamServer.start(context, fileId)
        val resumeMs = if (fromStart) 0L else WatchHistory.resumePositionMs(item.chatId, item.messageId)
        WatchHistory.setPending(item.chatId, item.messageId, item.title, chatTitle, item.durationSeconds * 1000L)
        if (resumeMs > 0) {
            Toast.makeText(context, context.getString(R.string.resuming_at, formatClock(resumeMs)), Toast.LENGTH_SHORT).show()
        }
        return url to resumeMs
    }

    /**
     * What plays after [item] when it ends: the videos posted after it in the same chat, oldest first (the way
     * episodes are posted), at most [UP_NEXT_MAX] of them, passed light (without their tiny previews).
     */
    fun upNext(item: MediaItem, loaded: List<MediaItem>): List<MediaItem> =
        loaded.asSequence()
            .filter { it.chatId == item.chatId && it.messageId != item.messageId && it.fileId != null }
            .filter { it.date > item.date || (it.date == item.date && it.messageId > item.messageId) }
            .sortedWith(compareBy({ it.date }, { it.messageId }))
            .take(UP_NEXT_MAX)
            .map { it.copy(miniThumbnailBytes = null, thumbnailPath = null) }
            .toList()

    private const val UP_NEXT_MAX = 30

    /** mpv for [url] at [resumeMs], or null when mpv is not installed. */
    fun mpvIntent(context: Context, url: String, title: String, resumeMs: Long): Intent? {
        if (!hasMpv(context)) return null
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(url), "video/*")
            .setClassName(MPV, "$MPV.MPVActivity")
            .putExtra("title", title)
            .apply { if (resumeMs > 0) putExtra("position", resumeMs.toInt()) }
    }

    /**
     * mpv has closed. Back or Home report a position (and the duration); the end of the video reports a duration but
     * no position, so it was watched. With neither, mpv never played the stream (it failed to open it): nothing is
     * recorded, so a video is never marked watched by mistake.
     */
    fun onResult(result: ActivityResult) {
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK) {
            WatchHistory.clearPending()
            return
        }
        val position = data?.takeIf { it.hasExtra("position") }?.getIntExtra("position", 0)?.toLong()
        val duration = data?.takeIf { it.hasExtra("duration") }?.getIntExtra("duration", 0)?.toLong()?.takeIf { it > 0 }
        if (position == null && duration == null) {
            WatchHistory.clearPending()
            return
        }
        WatchHistory.finishPending(position, duration)
    }

    fun formatClock(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = total % 3600 / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    /** mpv is installed and its player screen can be opened (not only the package present). */
    private fun hasMpv(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("http://127.0.0.1/"), "video/*")
            .setClassName(MPV, "$MPV.MPVActivity")
        return intent.resolveActivity(context.packageManager) != null
    }
}
