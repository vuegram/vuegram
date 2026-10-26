package com.vuegram.data.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import com.vuegram.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash on the TV, so Settings can show what happened. Nothing is sent anywhere: VueGram has no crash
 * reporting service. The report holds the app and Android version, the TV model and the stack trace, never messages,
 * chat names or account details.
 */
object CrashLog {
    private const val TAG = "VueGram"
    private const val FILE = "last_crash.txt"
    private const val MAX_CHARS = 32_000

    private lateinit var file: File

    fun install(context: Context) {
        file = File(context.applicationContext.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file.writeText(report(thread, error).take(MAX_CHARS))
            } catch (e: Exception) {
                Log.e(TAG, "could not save the crash report", e)
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The saved report, or null when VueGram has not crashed since it was last cleared. */
    fun read(): String? = if (::file.isInitialized && file.exists()) file.readText() else null

    /** When the saved crash happened (ms), or 0. */
    fun time(): Long = if (::file.isInitialized && file.exists()) file.lastModified() else 0L

    fun clear() {
        if (::file.isInitialized) file.delete()
    }

    internal fun report(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("VueGram ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Time: $time")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(trace)
        }
    }
}
