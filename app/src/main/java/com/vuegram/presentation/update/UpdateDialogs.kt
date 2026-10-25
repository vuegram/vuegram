package com.vuegram.presentation.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.vuegram.BuildConfig
import com.vuegram.R
import com.vuegram.data.update.Installer
import com.vuegram.data.update.Release
import com.vuegram.data.update.Updates
import java.io.File

/**
 * The update dialogs. Usually there is one: an update was downloaded quietly and is ready ([ready]: Install now or
 * Later). With automatic updates off, or on a metered network, [offer] downloads it first, with a progress bar.
 * [whatsNew] shows the release notes on the first start of a new version.
 */
object UpdateDialogs {
    private const val THEME = android.R.style.Theme_DeviceDefault_Dialog_Alert
    private const val NOTES_MAX = 700

    /** "VueGram 0.2.0 is ready to install": the update is downloaded and verified; Later hides it for this version. */
    fun ready(context: Context, update: Updates.Ready) {
        if (!alive(context) || Updates.busy) return
        AlertDialog.Builder(context, THEME)
            .setTitle(context.getString(R.string.update_ready_title, update.version))
            .setMessage(update.notes.take(NOTES_MAX).ifBlank { context.getString(R.string.update_no_notes) })
            .setPositiveButton(R.string.update_install_now) { _, _ -> install(context, update.file, update.version) }
            .setNegativeButton(R.string.update_later) { _, _ -> Updates.dismiss(context, update.version) }
            .show()
    }

    /** "VueGram 0.2.0 is available (you have 0.1.0)": Update downloads it (with progress), then installs it. */
    fun offer(context: Context, release: Release) {
        if (!alive(context) || Updates.busy) return
        val notes = release.notes.take(NOTES_MAX).ifBlank { context.getString(R.string.update_no_notes) }
        AlertDialog.Builder(context, THEME)
            .setTitle(context.getString(R.string.update_title, release.version, BuildConfig.VERSION_NAME))
            .setMessage(notes)
            .setPositiveButton(R.string.update_now) { _, _ -> download(context, release) }
            .setNegativeButton(R.string.update_later) { _, _ -> Updates.dismiss(context, release.version) }
            .show()
    }

    /** "What's new in VueGram 0.2.0", once, on the first start after an update. */
    fun whatsNew(context: Context, notes: String) {
        if (!alive(context)) return
        AlertDialog.Builder(context, THEME)
            .setTitle(context.getString(R.string.whats_new_title, BuildConfig.VERSION_NAME))
            .setMessage(notes.take(NOTES_MAX).ifBlank { context.getString(R.string.whats_new_fallback, BuildConfig.VERSION_NAME) })
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun download(context: Context, release: Release) {
        if (!alive(context) || Updates.busy || !hasPermission(context)) return
        val (dialog, label, bar) = progressDialog(context, context.getString(R.string.update_downloading_title, release.version))
        label.setText(R.string.update_downloading)
        Updates.busy = true
        Updates.download(context, release,
            onProgress = { percent -> bar.progress = percent },
            onResult = { file, problem ->
                Updates.busy = false
                if (alive(context)) dialog.dismiss()
                if (file == null) error(context, problem ?: "") else install(context, file, release.version)
            })
    }

    /** Hands the verified APK to Android. Where Android allows it, it installs without a confirmation screen. */
    private fun install(context: Context, file: File, version: String) {
        if (!alive(context) || Updates.busy || !hasPermission(context)) return
        val (dialog, label, bar) = progressDialog(context, context.getString(R.string.update_installing_title, version))
        label.setText(R.string.update_installing)
        bar.isIndeterminate = true
        Updates.busy = true
        Installer.install(context, file) { installError ->
            Updates.busy = false
            if (alive(context)) dialog.dismiss()
            if (installError != null) error(context, installError)
        }
    }

    /** Android asks once whether VueGram may install apps; this explains it and opens that switch. */
    private fun hasPermission(context: Context): Boolean {
        if (Installer.canInstall(context)) return true
        AlertDialog.Builder(context, THEME)
            .setTitle(R.string.update_permission_title)
            .setMessage(R.string.update_permission_message)
            .setPositiveButton(R.string.update_open_settings) { _, _ ->
                if (!Installer.openInstallPermission(context)) error(context, context.getString(R.string.update_no_settings))
            }
            .setNegativeButton(R.string.menu_cancel, null)
            .show()
        return false
    }

    private fun progressDialog(context: Context, title: String): Triple<AlertDialog, TextView, ProgressBar> {
        val bar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val label = TextView(context)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            addView(label)
            addView(bar)
        }
        val dialog = AlertDialog.Builder(context, THEME).setTitle(title).setView(box).setCancelable(false).show()
        return Triple(dialog, label, bar)
    }

    /** The screen the dialogs belong to still exists: showing a dialog on a closed screen would crash the app. */
    private fun alive(context: Context): Boolean {
        val activity = context as? Activity ?: return false
        return !activity.isFinishing && !activity.isDestroyed
    }

    private fun error(context: Context, problem: String) {
        if (!alive(context)) return
        AlertDialog.Builder(context, THEME)
            .setTitle(R.string.update_failed_title)
            .setMessage(problem)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
