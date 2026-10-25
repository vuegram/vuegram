package com.vuegram.presentation.settings

import android.app.AlertDialog
import android.content.Context
import com.vuegram.R
import com.vuegram.app.AppRestart
import com.vuegram.core.AppLanguage

/** The language list, from Settings and from the welcome screen: the TV's language first, then each translation. */
object LanguageDialog {

    /** What the current choice is called: "The TV's language", or the language's own name. */
    fun currentName(context: Context): String {
        val current = AppLanguage.get(context)
        return if (current.isEmpty()) context.getString(R.string.settings_language_system) else AppLanguage.nativeName(current)
    }

    /** Shows the list; a new choice restarts VueGram in that language. */
    fun show(context: Context) {
        val current = AppLanguage.get(context)
        val tags = listOf("") + AppLanguage.SUPPORTED
        val names = tags.map { if (it.isEmpty()) context.getString(R.string.settings_language_system) else AppLanguage.nativeName(it) }
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(names.toTypedArray(), tags.indexOf(current).coerceAtLeast(0)) { dialog, which ->
                dialog.dismiss()
                val chosen = tags[which]
                if (chosen != current) {
                    AppLanguage.set(context, chosen)
                    AppRestart.restart(context)
                }
            }
            .setNegativeButton(R.string.menu_cancel, null)
            .show()
    }
}
