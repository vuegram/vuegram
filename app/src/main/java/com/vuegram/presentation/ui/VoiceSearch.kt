package com.vuegram.presentation.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.vuegram.R
import java.util.Locale

/**
 * Search by voice with the TV's own speech recognition (on Google TV, Google's): the mic button next to a search
 * field, and the remote's Search key. VueGram records nothing itself; the recognizer hands back the words.
 * The mic button only shows when the TV has a recognizer.
 */
class VoiceSearch(fragment: Fragment, private val onText: (String) -> Unit) {

    private val launcher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val words = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
            if (!words.isNullOrEmpty()) onText(words)
        }

    /** Shows [mic] only where voice search works, and makes it start listening. */
    fun attach(mic: View) {
        mic.visibility = if (available(mic.context)) View.VISIBLE else View.GONE
        mic.setOnClickListener { listen(mic.context) }
    }

    fun listen(context: Context) {
        if (!available(context)) return
        try {
            launcher.launch(intent(context))
        } catch (_: Exception) {
            // The recognizer went away between the check and the press: nothing to do.
        }
    }

    companion object {
        fun available(context: Context) = intent(context).resolveActivity(context.packageManager) != null

        private fun intent(context: Context) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_prompt))
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
}
