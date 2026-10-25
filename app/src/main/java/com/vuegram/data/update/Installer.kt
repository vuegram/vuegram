package com.vuegram.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.vuegram.R
import java.io.File
import java.util.concurrent.Executors

/**
 * Hands a verified APK to Android's PackageInstaller. Android shows its own confirmation screen; the update only
 * goes through because the APK is signed with the same key as the installed app.
 */
object Installer {

    /** Android 8+: has the viewer allowed VueGram to install apps ("Install unknown apps")? */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** Opens the "Install unknown apps" switch for VueGram, where the TV has that screen. */
    fun openInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return false
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("VueGram", "no Install unknown apps screen on this TV", e)
            false
        }
    }

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /**
     * Copies [apk] into a PackageInstaller session and commits it, on a background thread (a 45 MB copy on a TV's
     * slow storage would freeze the screen). [onResult] runs on the main thread with null once Android has the update,
     * or the reason it failed; a failed session is abandoned so nothing half-written is left behind.
     */
    fun install(context: Context, apk: File, onResult: (String?) -> Unit) {
        val app = context.applicationContext
        worker.execute {
            val installer = app.packageManager.packageInstaller
            var sessionId = -1
            val error = if (!Updates.signedLikeThisApp(app, apk)) {
                // Checked again right before installing, off the main thread (reading a 45 MB APK takes a moment).
                app.getString(R.string.update_not_signed)
            } else try {
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(app.packageName)
                    // Android 12+: once VueGram installed itself (it is the "installer of record"), its updates go
                    // through without Android's confirmation screen. The first update after a sideload still asks.
                    if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    apk.inputStream().use { input ->
                        session.openWrite("vuegram.apk", 0, apk.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val status = Intent(app, InstallStatusReceiver::class.java)
                    // Mutable: the installer adds the status and, when it needs the viewer's OK, the confirmation intent.
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(app, sessionId, status, flags).intentSender)
                }
                null
            } catch (e: Exception) {
                Log.w("VueGram", "update not handed to the installer", e)
                if (sessionId != -1) {
                    try { installer.abandonSession(sessionId) } catch (_: Exception) { }
                }
                e.message ?: e.javaClass.simpleName
            }
            main.post { onResult(error) }
        }
    }
}

/** Receives PackageInstaller's answer: shows Android's confirmation screen, or says why the update failed. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit   // Android replaces the app; it opens as the new version next time
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Log.w("VueGram", "update not installed: $why")
                Toast.makeText(context, context.getString(R.string.update_install_failed, why), Toast.LENGTH_LONG).show()
            }
        }
    }
}
