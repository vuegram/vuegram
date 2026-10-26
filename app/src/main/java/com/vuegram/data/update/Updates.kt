package com.vuegram.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import com.vuegram.BuildConfig
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** A published VueGram release: its version, notes, APK and the APK's published SHA-256. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256Url: String?
)

/**
 * Checks GitHub for a newer VueGram release, downloads its APK and refuses it unless its SHA-256 matches the one
 * published with the release and it is signed with the same key as the installed app. The only request is GitHub's
 * public releases API for [BuildConfig.UPDATE_REPO]; nothing about the TV or the viewer is sent.
 */
object Updates {
    private const val PREFS = "updates"
    private const val LAST_CHECK = "last_check"
    private const val DISMISSED = "dismissed_version"
    private const val AUTO = "auto_update"
    private const val READY_VERSION = "ready_version"
    private const val READY_NOTES = "ready_notes"
    private const val LAST_RUN = "last_run_version"
    private const val WHATS_NEW = "whats_new_notes"
    private const val CHECK_EVERY_MS = 12 * 60 * 60 * 1000L
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    // Lazy, so isNewer can run in plain unit tests where Android's Looper doesn't exist.
    private val worker by lazy { Executors.newSingleThreadExecutor() }
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Release builds check by themselves at most every 12 hours; debug builds only when asked. */
    fun dueForCheck(context: Context): Boolean {
        if (BuildConfig.DEBUG) return false
        val last = prefs(context).getLong(LAST_CHECK, 0L)
        return System.currentTimeMillis() - last >= CHECK_EVERY_MS
    }

    /** The newest release if it is newer than this app, or null. [onResult] runs on the main thread. */
    fun check(context: Context, onResult: (Release?, String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val (release, error) = try {
                latest() to null
            } catch (e: Exception) {
                null to (e.message ?: e.javaClass.simpleName)
            }
            // Only a successful check counts: a failed one (no network at boot) is tried again on the next start.
            if (error == null) prefs(app).edit().putLong(LAST_CHECK, System.currentTimeMillis()).apply()
            val newer = release?.takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
            main.post { onResult(newer, error) }
        }
    }

    /** Settings › Automatic updates: download new versions in the background and ask once to install (on by default). */
    fun automatic(context: Context) = prefs(context).getBoolean(AUTO, true)
    fun setAutomatic(context: Context, on: Boolean) = prefs(context).edit().putBoolean(AUTO, on).apply()

    /** A downloaded, verified update, newer than the running app, waiting for the viewer's OK. */
    data class Ready(val version: String, val notes: String, val file: File)

    fun ready(context: Context): Ready? {
        val prefs = prefs(context)
        val version = prefs.getString(READY_VERSION, null) ?: return null
        val file = apkFile(context)
        if (!file.exists() || !isNewer(version, BuildConfig.VERSION_NAME)) return null
        return Ready(version, prefs.getString(READY_NOTES, "").orEmpty(), file)
    }

    /**
     * The quiet path, run when the library opens. An update already downloaded is reported at once ([onReady]).
     * Otherwise, when a check is due: a newer release is downloaded and verified in the background and then reported
     * ready, or only reported as available ([onAvailable]) when automatic updates are off or the network is metered.
     * Both callbacks run on the main thread; nothing is shown while it works.
     */
    fun checkQuietly(context: Context, onReady: (Ready) -> Unit, onAvailable: (Release) -> Unit) {
        val app = context.applicationContext
        ready(app)?.let {
            onReady(it)
            return
        }
        if (busy || !dueForCheck(app)) return
        check(app) { release, _ ->
            if (release == null) return@check
            if (!automatic(app) || metered(app) || busy) {
                onAvailable(release)
                return@check
            }
            busy = true
            download(app, release, onProgress = { }) { file, _ ->
                busy = false
                if (file == null) {
                    onAvailable(release)
                } else {
                    prefs(app).edit().putString(READY_VERSION, release.version).putString(READY_NOTES, release.notes).apply()
                    onReady(Ready(release.version, release.notes, file))
                }
            }
        }
    }

    /**
     * Once per start: forgets an update that is now installed (and its file), and returns the notes to show as
     * "What's new" on the first start of a new version ("" when there are none), or null on any other start.
     */
    fun afterStart(context: Context): String? {
        val prefs = prefs(context)
        val readyVersion = prefs.getString(READY_VERSION, null)
        if (readyVersion != null && !isNewer(readyVersion, BuildConfig.VERSION_NAME)) {
            val editor = prefs.edit().remove(READY_VERSION).remove(READY_NOTES)
            if (readyVersion == BuildConfig.VERSION_NAME) editor.putString(WHATS_NEW, prefs.getString(READY_NOTES, ""))
            editor.apply()
            apkFile(context).delete()
        }
        val last = prefs.getString(LAST_RUN, null)
        if (last == BuildConfig.VERSION_NAME) return null
        val notes = prefs.getString(WHATS_NEW, "").orEmpty()
        prefs.edit().putString(LAST_RUN, BuildConfig.VERSION_NAME).remove(WHATS_NEW).apply()
        // The very first start of a fresh install has nothing to announce.
        return if (last == null) null else notes
    }

    private fun apkFile(context: Context) = File(File(context.cacheDir, "updates"), "update.apk")

    /** A metered connection (a phone hotspot, a capped plan): no 45 MB downloads without asking. */
    private fun metered(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true

    /** The viewer said "Later" to this version: don't offer it by itself again (the header pill stays). */
    fun dismiss(context: Context, version: String) = prefs(context).edit().putString(DISMISSED, version).apply()
    fun wasDismissed(context: Context, version: String) = prefs(context).getString(DISMISSED, null) == version

    private fun latest(): Release {
        val json = JSONObject(get("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest"))
        val version = json.getString("tag_name").trimStart('v', 'V')
        val assets = (0 until json.getJSONArray("assets").length()).map { json.getJSONArray("assets").getJSONObject(it) }
        val byName = assets.associateBy { it.getString("name") }
        val apkName = chooseApk(byName.keys.toList(), Build.SUPPORTED_ABIS.toList())
            ?: throw IOException("release $version has no APK with a checksum")
        val apk = byName.getValue(apkName)
        val sha = byName[apkName + ".sha256"]
        return Release(
            version = version,
            notes = json.optString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size"),
            sha256Url = sha?.getString("browser_download_url")
        )
    }

    /** True while an update is being downloaded or installed; a second Update press is ignored meanwhile. */
    @Volatile var busy = false

    /**
     * Downloads [release]'s APK into the app's cache and checks it. [onProgress] gets 0..100; [onResult] gets the
     * verified file or an error message. Both run on the main thread.
     */
    fun download(
        context: Context,
        release: Release,
        onProgress: (Int) -> Unit,
        onResult: (File?, String?) -> Unit
    ) {
        val app = context.applicationContext
        worker.execute {
            // Whatever was downloaded before is no longer the ready update, even if this download is cut short.
            prefs(app).edit().remove(READY_VERSION).remove(READY_NOTES).commit()
            val dir = File(app.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }   // only the newest download is ever kept
            // Fixed names: nothing from the release (its tag) ever becomes part of a path. The download goes to a
            // ".part" file and becomes the update only once checked, so a download cut short is never taken for one.
            val part = File(dir, "update.apk.part")
            val file = apkFile(app)
            val result = try {
                val expected = release.sha256Url?.let { get(it).trim().split(Regex("\\s+")).first().lowercase() }
                    ?: throw IOException("the release has no SHA-256 checksum, so it can't be checked")
                val actual = fetch(release.apkUrl, part, release.apkSize) { percent -> main.post { onProgress(percent) } }
                when {
                    actual != expected -> {
                        part.delete()
                        null to "the download does not match the release's SHA-256 checksum"
                    }
                    !signedLikeThisApp(app, part) -> {
                        part.delete()
                        null to "the download is not signed with VueGram's key"
                    }
                    !part.renameTo(file) -> {
                        part.delete()
                        null to "the download could not be saved"
                    }
                    else -> file to null
                }
            } catch (e: Exception) {
                part.delete()
                null to (e.message ?: e.javaClass.simpleName)
            }
            main.post { onResult(result.first, result.second) }
        }
    }

    /** Same package name and the same signing certificate(s) as the installed app. */
    fun signedLikeThisApp(context: Context, apk: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        val theirs = certificates(archive)
        return theirs.isNotEmpty() && theirs == certificates(installed)
    }

    private fun certificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        }
        return signatures.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    /**
     * Which APK of a release this TV should download: the one for its processor (vuegram-arm64-v8a.apk,
     * vuegram-armeabi-v7a.apk), tried in [abis] order (the TV's preferred ABI first), else the universal one. Only an
     * APK published with its checksum (x.apk with x.apk.sha256) counts, so extra files can't be mixed up.
     */
    fun chooseApk(names: List<String>, abis: List<String>): String? {
        val apks = names.filter { it.endsWith(".apk") && (it + ".sha256") in names }
        for (abi in abis) apks.firstOrNull { it.endsWith("-$abi.apk") }?.let { return it }
        return apks.firstOrNull { name -> SPLIT_ABIS.none { name.endsWith("-$it.apk") } }
    }

    private val SPLIT_ABIS = listOf("armeabi-v7a", "arm64-v8a")

    private val VERSION = Regex("""(\d{1,2})\.(\d{1,2})\.(\d{1,2})""")

    /**
     * Release numbers are exactly major.minor.patch, each below 100, like appVersion in build.gradle.kts (versionCode
     * is made from those three parts). True when [remote] is newer than [local]; anything else is never offered.
     */
    fun isNewer(remote: String, local: String): Boolean {
        val r = VERSION.matchEntire(remote.trim())?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
        val l = VERSION.matchEntire(local.trim())?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
        for (i in 0..2) if (r[i] != l[i]) return r[i] > l[i]
        return false
    }

    private fun get(url: String): String {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) throw IOException("GitHub answered ${connection.responseCode}")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Streams [url] into [target] and returns its SHA-256 (hex). */
    private fun fetch(url: String, target: File, expectedSize: Long, onProgress: (Int) -> Unit): String {
        val connection = open(url)
        try {
            if (connection.responseCode !in 200..299) throw IOException("download failed (${connection.responseCode})")
            // contentLength (an Int) works on Android 6 too; contentLengthLong needs Android 7. APKs stay far below 2 GB.
            val length = connection.contentLength.toLong().takeIf { it > 0 } ?: expectedSize
            if (length > MAX_APK_BYTES) throw IOException("the download is unexpectedly large")
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (done > MAX_APK_BYTES) throw IOException("the download is unexpectedly large")
                        val percent = if (length > 0) (done * 100 / length).toInt() else 0
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true   // release assets redirect to GitHub's download host
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VueGram/${BuildConfig.VERSION_NAME}")
        }

    private fun sha256Hex(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
