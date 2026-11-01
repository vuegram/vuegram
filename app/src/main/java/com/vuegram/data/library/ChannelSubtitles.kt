package com.vuegram.data.library

import android.content.Context
import androidx.core.os.ConfigurationCompat
import com.vuegram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors
import kotlin.math.abs

/** A subtitle file posted in a chat next to a video, downloaded and rewritten as UTF-8 for the player. */
data class SubtitleFile(val name: String, val path: String, val mimeType: String)

/**
 * Subtitle files (.srt, .vtt, .ass) that a chat posted next to a video: channels often post a film and then its
 * subtitles, or the other way round. Up to [MAX] of them from the few messages around the video, nearest first.
 */
object ChannelSubtitles {
    /** Messages looked at on each side of the video. */
    private const val AROUND = 4
    private const val MAX = 3
    /** A film's subtitles are a few hundred KB at most: anything bigger is not read (1 GB TVs). */
    private const val MAX_BYTES = 1024L * 1024
    /** The player's names for each kind (Media3's MimeTypes). */
    private val TYPES = mapOf("srt" to "application/x-subrip", "vtt" to "text/vtt", "ass" to "text/x-ssa", "ssa" to "text/x-ssa")

    private val client get() = TdLibClient.client
    /** Files are read and rewritten here, not on TDLib's thread, whose other answers (the stream's) would wait. */
    private val io = Executors.newSingleThreadExecutor { task -> Thread(task, "ChannelSubtitles").apply { isDaemon = true } }

    /** Deletes the rewritten copies (when the player closes). */
    fun clear(context: Context) {
        io.execute { File(context.applicationContext.cacheDir, "subtitles").listFiles()?.forEach { it.delete() } }
    }

    /** [onResult] gets the files found (often none), on a TDLib thread. One request after the other, never a burst. */
    fun find(context: Context, chatId: Long, messageId: Long, onResult: (List<SubtitleFile>) -> Unit) {
        val app = context.applicationContext
        val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
        client.send(TdApi.GetChatHistory(chatId, messageId, -AROUND, AROUND * 2 + 1, false)) { result ->
            val found = (result as? TdApi.Messages)?.messages.orEmpty()
                .mapNotNull { message ->
                    val document = (message.content as? TdApi.MessageDocument)?.document ?: return@mapNotNull null
                    val type = TYPES[extension(document.fileName)] ?: return@mapNotNull null
                    Triple(message.id, document, type)
                }
                .filter { (_, document, _) -> document.document.size in 1..MAX_BYTES }
                .sortedBy { (id, _, _) -> abs(id - messageId) }
                .take(MAX)
            val folder = File(app.cacheDir, "subtitles").apply {
                mkdirs()
                listFiles()?.forEach { it.delete() }
            }
            val files = mutableListOf<SubtitleFile>()
            fun next(index: Int) {
                if (index >= found.size) return onResult(files)
                val (_, document, type) = found[index]
                client.send(TdApi.DownloadFile(document.document.id, 16, 0, 0, true)) { file ->
                    val local = (file as? TdApi.File)?.local
                    val path = local?.path?.takeIf { local.isDownloadingCompleted && it.isNotBlank() }
                    io.execute {
                        if (path != null) readable(File(path), type, locale?.language.orEmpty())?.let { (text, kind) ->
                            val name = document.fileName.orEmpty()
                            val copy = File(folder, "${document.document.id}.${extension(name)}")
                            runCatching { copy.writeText(text, Charsets.UTF_8) }
                                .onSuccess { files += SubtitleFile(label(name), copy.path, kind) }
                        }
                        next(index + 1)
                    }
                }
            }
            next(0)
        }
    }

    private fun extension(name: String?) = name.orEmpty().substringAfterLast('.', "").trim().lowercase()

    /**
     * The file's text and the kind the player should read it as, or null when it is not a subtitle file after all. A
     * .vtt must begin with WEBVTT (Media3 stops the video on one that doesn't); SubRip text renamed to .vtt is read as
     * SubRip.
     */
    internal fun readable(file: File, type: String, language: String): Pair<String, String>? {
        if (file.length() !in 1..MAX_BYTES) return null
        val text = runCatching { decode(file.readBytes(), language) }.getOrNull() ?: return null
        if (type != "text/vtt" || text.trimStart().startsWith("WEBVTT")) return text to type
        return if ("-->" in text) text to "application/x-subrip" else null
    }

    /** The track's name in the subtitle menu: the file name, without its extension, bidi or control characters. */
    private fun label(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }
            .filterNot { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() }
            .take(80)

    /**
     * The file's text. UTF-8 (with or without its mark) and UTF-16 are read as such; anything else was saved in an
     * older code page, guessed from the viewer's language: Hebrew subtitles are often Windows-1255, Arabic and Persian
     * Windows-1256, Russian Windows-1251. The player reads only UTF-8, so these would otherwise show as gibberish.
     */
    internal fun decode(bytes: ByteArray, language: String): String {
        val b = bytes
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte()) return String(b, 3, b.size - 3, Charsets.UTF_8)
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        try {
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (_: CharacterCodingException) {
        }
        val legacy = when (language) {
            "he", "iw", "yi" -> "windows-1255"
            "ar", "fa", "ur" -> "windows-1256"
            "ru", "uk", "be", "bg", "sr", "mk" -> "windows-1251"
            "tr", "az" -> "windows-1254"
            "el" -> "windows-1253"
            "zh" -> "GB18030"
            else -> "windows-1252"
        }
        return String(b, runCatching { Charset.forName(legacy) }.getOrDefault(Charsets.ISO_8859_1))
    }
}
