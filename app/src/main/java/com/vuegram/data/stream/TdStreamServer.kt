package com.vuegram.data.stream

import android.content.Context
import android.util.Log
import com.vuegram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Streams a Telegram video straight from TDLib to a local player (mpv) over http://127.0.0.1, without a bot.
 *
 * TDLib can only download to disk, so the parts that are played land in its files directory. Only one video is
 * streamed at a time, and its file is deleted when the next one starts, when the player hands back to VueGram, and
 * (with every cached video) when VueGram opens. The server listens on 127.0.0.1 only, and each video gets a new
 * random token, so other devices can't reach it and other apps can't guess the URL.
 */
object TdStreamServer {
    private const val TAG = "TdStream"
    private const val CHUNK = 512L * 1024
    private const val DOWNLOAD_TIMEOUT_S = 120L
    private const val PRIORITY = 32
    /** A player needs one or two connections at a time; anything beyond this is turned away. */
    private const val MAX_CONNECTIONS = 6
    private const val HEADER_DEADLINE_MS = 5_000L
    /** Closes connections whose request hasn't arrived in time (see [serve]). */
    private val deadlines = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "stream-deadlines").apply { isDaemon = true }
    }
    /** How long a connection may take to send its request headers, and to accept the next part. */
    private const val SOCKET_TIMEOUT_MS = 15_000
    private const val MAX_HEADER_LINES = 50
    private const val MAX_LINE = 8192

    private var server: ServerSocket? = null
    private val readLock = Any()
    @Volatile private var cacheCleared = false

    /** Connection threads: a few, reused, with no queue; a connection that finds them all busy is closed. */
    private val pool = ThreadPoolExecutor(
        0, MAX_CONNECTIONS, 30, TimeUnit.SECONDS, SynchronousQueue()
    ) { runnable -> Thread(runnable, "td-stream-conn-${connectionNumber.incrementAndGet()}").apply { isDaemon = true } }
    private val connectionNumber = AtomicInteger()

    @Volatile private var currentFileId = 0
    @Volatile private var currentToken = ""

    /** Returns the URL the player opens for [fileId]; the previous video's file is deleted first. */
    @Synchronized
    fun start(context: Context, fileId: Int): String {
        val socket = server ?: ServerSocket(0, 8, InetAddress.getByName("127.0.0.1")).also {
            server = it
            thread(name = "td-stream-accept", isDaemon = true) { acceptLoop(it) }
        }
        val previous = currentFileId
        if (previous != 0 && previous != fileId) deleteFile(previous)
        currentFileId = fileId
        currentToken = UUID.randomUUID().toString()
        StreamKeepAliveService.start(context)
        return "http://127.0.0.1:${socket.localPort}/stream/$currentToken"
    }

    /** The player is gone: stop serving and delete what was downloaded. */
    @Synchronized
    fun release(context: Context, onlyFor: String? = null) {
        // [onlyFor]: a player closing late releases the stream only if it is still the one it played, not the next.
        if (onlyFor != null && (currentToken.isEmpty() || !onlyFor.endsWith("/stream/$currentToken"))) return
        val fileId = currentFileId
        currentFileId = 0
        currentToken = ""
        StreamKeepAliveService.stop(context)
        if (fileId != 0) deleteFile(fileId)
    }

    /**
     * Deletes every cached video, document and animation, once per process: at the first start, and also when
     * Android brings VueGram back after killing it in the background. Thumbnails and photos stay (TDLib's storage
     * optimizer trims those).
     */
    fun clearVideoCacheOnce() {
        if (cacheCleared) return
        cacheCleared = true
        clearVideoCache()
    }

    private fun clearVideoCache() {
        val types = arrayOf<TdApi.FileType>(
            TdApi.FileTypeVideo(), TdApi.FileTypeDocument(), TdApi.FileTypeVideoNote(), TdApi.FileTypeAnimation()
        )
        TdLibClient.client.send(TdApi.OptimizeStorage(0, 0, 0, 0, types, null, null, false, 0)) { result ->
            if (result is TdApi.Error) Log.w(TAG, "clearing cached videos failed: ${result.message}")
        }
    }

    private fun deleteFile(fileId: Int) {
        TdLibClient.client.send(TdApi.CancelDownloadFile(fileId, false)) { }
        TdLibClient.client.send(TdApi.DeleteFile(fileId)) { result ->
            if (result is TdApi.Error) Log.w(TAG, "delete $fileId failed: ${result.message}")
        }
    }

    private fun acceptLoop(socket: ServerSocket) {
        var failures = 0
        while (!socket.isClosed) {
            val client = try {
                socket.accept().also { failures = 0 }
            } catch (e: IOException) {
                // Back off instead of spinning when accept keeps failing (for example, out of file descriptors).
                Log.w(TAG, "accept failed", e)
                Thread.sleep((100L shl minOf(++failures, 6)).coerceAtMost(5_000L))
                continue
            }
            try {
                pool.execute { serve(client) }
            } catch (e: RejectedExecutionException) {
                Log.w(TAG, "too many stream connections; closing one")
                try { client.close() } catch (_: IOException) { }
            }
        }
    }

    /** Never lets anything a connection does (or a client resetting it) escape: an uncaught error would end VueGram. */
    private fun serve(client: Socket) {
        // The request and its headers must arrive within a few seconds of connecting (the video itself may take as
        // long as it takes): another app dripping bytes can't hold the few connections the player needs.
        val headerDeadline = deadlines.schedule({ closeQuietly(client) }, HEADER_DEADLINE_MS, TimeUnit.MILLISECONDS)
        try {
            client.use {
                it.soTimeout = SOCKET_TIMEOUT_MS
                handle(it) { headerDeadline.cancel(false) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "stream connection ended: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            headerDeadline.cancel(false)
        }
    }

    private fun closeQuietly(socket: Socket) {
        try { socket.close() } catch (_: IOException) { }
    }

    private fun handle(socket: Socket, headersRead: () -> Unit) {
        val input = socket.getInputStream()
        val requestLine = readLine(input) ?: return
        // A request for anything but this stream ends here, before its headers.
        if (!requestLine.startsWith("GET /stream/") && !requestLine.startsWith("HEAD /stream/")) return
        var range: String? = null
        var lines = 0
        while (true) {
            val header = readLine(input) ?: return
            if (header.isEmpty()) break
            if (++lines > MAX_HEADER_LINES) return
            if (header.startsWith("Range:", ignoreCase = true)) range = header.substringAfter(':').trim()
        }
        headersRead()
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val token = parts.getOrNull(1).orEmpty().removePrefix("/stream/")
        val out = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

        val fileId = currentFileId
        // Constant-time comparison: another app on the TV must not be able to guess the token byte by byte.
        val tokenOk = MessageDigest.isEqual(token.toByteArray(), currentToken.toByteArray()) && currentToken.isNotEmpty()
        if (fileId == 0 || !tokenOk || (method != "GET" && method != "HEAD")) {
            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            return
        }
        val file = call(TdApi.GetFile(fileId), 30) as? TdApi.File
        val size = file?.let { if (it.size > 0) it.size else it.expectedSize } ?: 0L
        if (size <= 0L) {
            out.write("HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            return
        }

        var start = 0L
        var end = size - 1
        val bytes = range?.removePrefix("bytes=")?.split('-')
        if (bytes != null && bytes.size == 2) {
            if (bytes[0].isNotBlank()) {
                start = bytes[0].trim().toLongOrNull() ?: 0L
                bytes[1].trim().toLongOrNull()?.let { end = minOf(it, size - 1) }
            } else {
                // "bytes=-N": the last N bytes
                start = maxOf(0L, size - (bytes[1].trim().toLongOrNull() ?: 0L))
            }
        }
        if (start >= size || start > end) {
            out.write("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$size\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            out.flush()
            return
        }

        val header = StringBuilder()
        header.append(if (range != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        header.append("Content-Type: video/mp4\r\n")
        header.append("Accept-Ranges: bytes\r\n")
        header.append("Content-Length: ${end - start + 1}\r\n")
        if (range != null) header.append("Content-Range: bytes $start-$end/$size\r\n")
        header.append("Connection: close\r\n\r\n")
        out.write(header.toString().toByteArray())
        if (method == "HEAD") {
            out.flush()
            return
        }

        var pos = start
        try {
            while (pos <= end && fileId == currentFileId) {
                val data = readPart(fileId, pos, minOf(CHUNK, end - pos + 1))
                if (data.isEmpty()) break
                out.write(data)
                pos += data.size
            }
            out.flush()
        } catch (e: IOException) {
            // The player closed this connection, usually to seek; it opens a new one at the new position.
            Log.d(TAG, "stream $fileId stopped at $pos: ${e.message}")
        }
    }

    /**
     * Downloads [count] bytes at [offset] (if they are not on disk yet) and reads them back. Stops as soon as the
     * video is released, so a player that has gone never makes TDLib download (and keep) parts of a deleted file.
     */
    private fun readPart(fileId: Int, offset: Long, count: Long): ByteArray = synchronized(readLock) {
        repeat(3) {
            if (fileId != currentFileId) throw IOException("the video was released")
            val download = call(TdApi.DownloadFile(fileId, PRIORITY, offset, count, true), DOWNLOAD_TIMEOUT_S)
            if (fileId != currentFileId) throw IOException("the video was released")
            if (download is TdApi.Error) throw IOException("download failed: ${download.message}")
            val part = call(TdApi.ReadFilePart(fileId, offset, count), 30)
            if (part is TdApi.Data && part.data.isNotEmpty()) return part.data
        }
        throw IOException("could not read $count bytes at $offset")
    }

    private fun call(query: TdApi.Function<*>, timeoutSeconds: Long): TdApi.Object? {
        val latch = CountDownLatch(1)
        var result: TdApi.Object? = null
        TdLibClient.client.send(query) {
            result = it
            latch.countDown()
        }
        if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) throw IOException("TDLib did not answer in time")
        return result
    }

    private fun readLine(input: InputStream): String? {
        val line = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) return if (line.isEmpty()) null else line.toString()
            if (c == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length > MAX_LINE) return null
            line.append(c.toChar())
        }
    }
}
