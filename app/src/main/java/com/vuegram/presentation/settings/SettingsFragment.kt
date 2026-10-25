package com.vuegram.presentation.settings

import android.app.AlertDialog
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.vuegram.BuildConfig
import com.vuegram.R
import com.vuegram.data.diagnostics.CrashLog
import com.vuegram.data.login.Login
import com.vuegram.data.settings.AppSettings
import com.vuegram.data.update.Updates
import com.vuegram.presentation.ui.StatusText
import com.vuegram.presentation.update.UpdateDialogs

/**
 * Settings, opened from the library header. Up/down move between rows, OK changes or opens one, Back returns to the
 * library (which reloads if Show people changed).
 */
class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        build(view.findViewById(R.id.rows))
    }

    private fun build(rows: LinearLayout) {
        rows.removeAllViews()
        toggle(rows, R.string.settings_people, R.string.settings_people_summary, { AppSettings.showPeople }) {
            AppSettings.showPeople = it
        }
        toggle(rows, R.string.settings_messages, R.string.settings_messages_summary, { AppSettings.showMessages }) {
            AppSettings.showMessages = it
        }
        toggle(rows, R.string.settings_play_next, R.string.settings_play_next_summary, { AppSettings.playNext }) {
            AppSettings.playNext = it
        }
        row(rows, getString(R.string.settings_language), getString(R.string.settings_language_summary),
            LanguageDialog.currentName(requireContext())) {
            LanguageDialog.show(requireContext())
        }
        toggle(rows, R.string.settings_auto_update, R.string.settings_auto_update_summary, { Updates.automatic(requireContext()) }) {
            Updates.setAutomatic(requireContext(), it)
        }
        row(rows, getString(R.string.settings_update), getString(R.string.settings_update_summary, BuildConfig.VERSION_NAME), "") {
            checkForUpdate()
        }
        val crash = CrashLog.time()
        if (crash > 0) {
            val whenText = DateUtils.getRelativeTimeSpanString(crash).toString()
            row(rows, getString(R.string.settings_crash), getString(R.string.settings_crash_summary, whenText), "") {
                showCrash(rows)
            }
        }
        row(rows, getString(R.string.settings_logout), getString(R.string.settings_logout_summary), "") { confirmLogout() }
        rows.getChildAt(0)?.requestFocus()
    }

    private fun toggle(rows: LinearLayout, @StringRes title: Int, @StringRes summary: Int, get: () -> Boolean, set: (Boolean) -> Unit) {
        val value = { getString(if (get()) R.string.settings_on else R.string.settings_off) }
        lateinit var valueView: TextView
        val view = row(rows, getString(title), getString(summary), value()) {
            set(!get())
            valueView.text = value()
        }
        valueView = view.findViewById(R.id.setting_value)
    }

    private fun row(rows: LinearLayout, title: String, summary: String, value: String, onClick: () -> Unit): View {
        val view = LayoutInflater.from(rows.context).inflate(R.layout.item_setting, rows, false)
        view.findViewById<TextView>(R.id.setting_title).text = title
        view.findViewById<TextView>(R.id.setting_summary).text = summary
        view.findViewById<TextView>(R.id.setting_value).text = value
        view.setOnClickListener { onClick() }
        rows.addView(view)
        return view
    }

    private fun checkForUpdate() {
        val context = requireContext()
        // Already downloaded in the background: straight to Install now.
        Updates.ready(context)?.let { return UpdateDialogs.ready(context, it) }
        Toast.makeText(context, R.string.settings_update_checking, Toast.LENGTH_SHORT).show()
        Updates.check(context) { release, error ->
            if (!isAdded) return@check
            when {
                release != null -> UpdateDialogs.offer(requireContext(), release)
                error != null -> dialog(getString(R.string.update_failed_title), StatusText.error(requireContext(), error))
                else -> Toast.makeText(
                    requireContext(), getString(R.string.settings_update_latest, BuildConfig.VERSION_NAME), Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showCrash(rows: LinearLayout) {
        val report = CrashLog.read() ?: return
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.settings_crash)
            .setMessage(report)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.settings_crash_delete) { _, _ ->
                CrashLog.clear()
                build(rows)
            }
            .show()
    }

    /** Logs this TV out of Telegram. TDLib deletes the session; VueGram then restarts at the login screen. */
    private fun confirmLogout() {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.settings_logout)
            .setMessage(R.string.settings_logout_confirm)
            .setPositiveButton(R.string.settings_logout) { _, _ ->
                Login.logOut { message ->
                    view?.post {
                        if (isAdded) dialog(getString(R.string.settings_logout), StatusText.error(requireContext(), message))
                    }
                }
            }
            .setNegativeButton(R.string.menu_cancel, null)
            .show()
    }

    private fun dialog(title: String, message: String) {
        AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
