package com.vuegram.data.login

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Lets the viewer send the API ID and hash from their phone instead of typing a 32-character hash with a remote.
 * The TV shows a QR code with [start]'s address; the phone opens a small page there, with a link to my.telegram.org
 * and two fields, and the keys arrive in [onKeys].
 *
 * Kept tight, since it listens on the home network for a few minutes:
 * - only on the TV's own Wi-Fi or Ethernet address, at a random port;
 * - only for a path with 128 random bits (in the QR code); anything else gets 404, and 20 wrong tries stop it;
 * - one small request at a time (headers and body capped, 10 s timeouts);
 * - it stops once the keys arrive, after [LIFETIME_MS], or when the onboarding screen calls [stop].
 * The page is plain HTML with no scripts, served with a strict Content-Security-Policy. It travels unencrypted on the
 * local network, which is acceptable for an API ID and hash (they identify an app, not the account).
 */
class KeyDrop(private val page: Page, private val onKeys: (String, String) -> Unit) {

    /** The page's wording, in the viewer's language. */
    data class Page(
        val title: String,
        val intro: String,
        val openSite: String,
        val siteSteps: String,
        val idLabel: String,
        val hashLabel: String,
        val send: String,
        val sent: String,
        val invalid: String
    )

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var server: ServerSocket? = null
    private val token = ByteArray(16).also { SecureRandom().nextBytes(it) }
        .let { Base64.encodeToString(it, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) }
    private val badRequests = AtomicInteger()
    private val clients = AtomicInteger()
    /** The keys arrive once: a second valid POST (a double tap, or anyone else on the Wi-Fi) is not passed on. */
    private val delivered = AtomicBoolean(false)

    /** Starts listening and returns the address for the QR code, or null when the TV has no home-network address. */
    fun start(): String? {
        val address = lanAddress() ?: return null
        val socket = try {
            ServerSocket(0, 4, address)
        } catch (e: IOException) {
            Log.w(TAG, "key drop not started", e)
            return null
        }
        server = socket
        thread(name = "key-drop", isDaemon = true) { serve(socket) }
        main.postDelayed({ stop() }, LIFETIME_MS)
        return "http://${address.hostAddress}:${socket.localPort}/k/$token"
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        val socket = server ?: return
        server = null
        try { socket.close() } catch (_: IOException) { }
    }

    /**
     * A few requests at once, each with a fixed time in all: a device on the Wi-Fi sending a byte every few seconds
     * can't hold the page while the viewer's phone waits.
     */
    private fun serve(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try { socket.accept() } catch (_: IOException) { break }
            if (clients.incrementAndGet() > MAX_CLIENTS) {
                clients.decrementAndGet()
                closeQuietly(client)
                continue
            }
            val deadline = deadlines.schedule({ closeQuietly(client) }, REQUEST_DEADLINE_MS, TimeUnit.MILLISECONDS)
            thread(name = "key-drop-request", isDaemon = true) {
                try {
                    client.use { handle(it) }
                } catch (e: Exception) {
                    Log.d(TAG, "key drop request ended: ${e.message}")
                } finally {
                    deadline.cancel(false)
                    clients.decrementAndGet()
                }
            }
        }
    }

    private fun closeQuietly(socket: Socket) {
        try { socket.close() } catch (_: IOException) { }
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = TIMEOUT_MS
        val input = socket.getInputStream()
        val requestLine = readLine(input) ?: return
        var length = 0
        var lines = 0
        while (true) {
            val header = readLine(input) ?: return
            if (header.isEmpty()) break
            if (++lines > MAX_HEADER_LINES) return
            if (header.startsWith("Content-Length:", ignoreCase = true)) length = header.substringAfter(':').trim().toIntOrNull() ?: 0
        }
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty()
        val out = BufferedOutputStream(socket.getOutputStream())

        val tokenOk = MessageDigest.isEqual(path.toByteArray(), "/k/$token".toByteArray())
        if (!tokenOk) {
            // Only guesses at the page count: a scan of the network asking for "/" or a favicon does not close it.
            if (path.startsWith("/k/") && badRequests.incrementAndGet() >= MAX_BAD_REQUESTS) main.post { stop() }
            respond(out, 404, "Not found")
            return
        }
        when (method) {
            "GET" -> respond(out, 200, form(null))
            "POST" -> {
                if (length <= 0 || length > MAX_BODY) return respond(out, 413, form(page.invalid))
                val body = ByteArray(length)
                var read = 0
                while (read < length) {
                    val n = input.read(body, read, length - read)
                    if (n < 0) return
                    read += n
                }
                val fields = String(body, Charsets.UTF_8).split('&').associate { pair ->
                    val key = URLDecoder.decode(pair.substringBefore('='), "UTF-8")
                    val value = URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                    key to value
                }
                val id = fields["api_id"].orEmpty().trim()
                val hash = fields["api_hash"].orEmpty().trim()
                if (ApiKeys.problem(id, hash) != null) {
                    respond(out, 200, form(page.invalid))
                } else if (!delivered.compareAndSet(false, true)) {
                    respond(out, 200, done())
                } else {
                    respond(out, 200, done())
                    main.post {
                        stop()
                        onKeys(id, hash)
                    }
                }
            }
            else -> respond(out, 405, "Method not allowed")
        }
    }

    private fun respond(out: BufferedOutputStream, status: Int, html: String) {
        val body = html.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Payload Too Large" }
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'\r\n" +
            "Connection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.US_ASCII))
        out.write(body)
        out.flush()
    }

    private fun form(error: String?) = shell(
        """
        <h1>${esc(page.title)}</h1>
        <p>${esc(page.intro)}</p>
        <a class="site" href="https://my.telegram.org/apps" target="_blank" rel="noopener noreferrer">${esc(page.openSite)} ↗</a>
        <p class="dim">${esc(page.siteSteps)}</p>
        ${if (error != null) "<p class=\"err\">${esc(error)}</p>" else ""}
        <form method="post" autocomplete="off">
          <label>${esc(page.idLabel)}<input name="api_id" inputmode="numeric" pattern="[0-9]*" maxlength="12" required></label>
          <label>${esc(page.hashLabel)}<input name="api_hash" maxlength="32" minlength="32" spellcheck="false" autocapitalize="off" required></label>
          <button type="submit">${esc(page.send)}</button>
        </form>
        """
    )

    private fun done() = shell("<h1>✓</h1><p>${esc(page.sent)}</p>")

    private fun shell(content: String) = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>VueGram</title><style>
body{margin:0;background:#000;color:#fff;font:17px/1.5 system-ui,sans-serif;padding:28px 20px;max-width:520px;margin:auto}
h1{font-size:26px;margin:0 0 12px}p{margin:0 0 16px}.dim{color:#8e8e93;font-size:15px}.err{color:#ff8a80}
a.site{display:block;text-align:center;padding:14px;border-radius:14px;background:#1c1c1f;color:#c4b5fd;text-decoration:none;margin:0 0 10px}
label{display:block;margin:18px 0 0;color:#8e8e93;font-size:14px}
input{display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:14px;border-radius:12px;border:1px solid #333;background:#111;color:#fff;font:17px ui-monospace,monospace}
button{width:100%;margin-top:24px;padding:16px;border:0;border-radius:14px;background:#fff;color:#000;font-size:17px;font-weight:600}
</style></head><body>$content</body></html>"""

    private fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

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

    companion object {
        private const val TAG = "KeyDrop"
        private const val LIFETIME_MS = 10 * 60 * 1000L
        private const val TIMEOUT_MS = 10_000
        private const val MAX_LINE = 2048
        private const val MAX_HEADER_LINES = 40
        private const val MAX_BODY = 2048
        private const val MAX_BAD_REQUESTS = 20
        private const val MAX_CLIENTS = 3
        private const val REQUEST_DEADLINE_MS = 15_000L

        /** Closes requests that run past [REQUEST_DEADLINE_MS]; one thread for every KeyDrop. */
        private val deadlines = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "key-drop-deadlines").apply { isDaemon = true }
        }

        /** The TV's IPv4 address on the home network (Wi-Fi or Ethernet), or null. */
        fun lanAddress(): InetAddress? = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth")) 0 else 1 }
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        } catch (e: Exception) {
            null
        }
    }
}
