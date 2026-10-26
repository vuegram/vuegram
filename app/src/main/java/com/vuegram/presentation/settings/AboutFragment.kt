package com.vuegram.presentation.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.vuegram.BuildConfig
import com.vuegram.R
import com.vuegram.presentation.ui.QrCode

/** About VueGram, opened from the bottom of Settings: version, links, a QR code to the website, and the licences. */
class AboutFragment : Fragment(R.layout.fragment_about) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val back = view.findViewById<Button>(R.id.back)
        back.setText(R.string.settings_title)
        back.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }

        view.findViewById<TextView>(R.id.about_version).text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
        view.findViewById<TextView>(R.id.about_notes).text =
            getString(R.string.about_open_source) + " " + getString(R.string.about_unofficial)
        view.findViewById<TextView>(R.id.about_links).text = listOf(
            getString(R.string.about_website, WEBSITE),
            getString(R.string.about_help, HELP),
            getString(R.string.about_source, SOURCE)
        ).joinToString("\n")

        val qr = view.findViewById<ImageView>(R.id.about_qr)
        val size = (200 * resources.displayMetrics.density).toInt()
        qr.setImageDrawable(QrCode.drawable(resources, "https://$WEBSITE", size))

        val licences = view.findViewById<Button>(R.id.about_licences)
        licences.setOnClickListener {
            AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.about_licences)
                .setMessage(R.string.about_licences_text)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
        back.requestFocus()
    }

    private companion object {
        const val WEBSITE = "vuegram.com"
        const val HELP = "docs.vuegram.com"
        const val SOURCE = "github.com/vuegram/vuegram"
    }
}
