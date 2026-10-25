package com.vuegram.presentation.onboarding

import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vuegram.R
import com.vuegram.data.login.ApiKeys
import com.vuegram.data.login.KeyDrop
import com.vuegram.data.login.Login
import com.vuegram.data.login.Login.State
import com.vuegram.presentation.settings.LanguageDialog
import com.vuegram.presentation.ui.GlowView
import com.vuegram.presentation.ui.QrCode
import com.vuegram.presentation.ui.StatusText
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Getting started, in three moves: a welcome, the viewer's Telegram API keys (sent from their phone by scanning a QR
 * code, or typed with the remote), then signing in by scanning a QR code with Telegram (or with a phone number and
 * code). Follows [Login.state]; MainActivity swaps in the library once it is Ready.
 */
class OnboardingFragment : Fragment(R.layout.fragment_onboarding) {

    private lateinit var step: TextView
    private lateinit var title: TextView
    private lateinit var body: TextView
    private lateinit var error: TextView
    private lateinit var logo: ImageView
    private lateinit var primary: Button
    private lateinit var secondary: Button
    private lateinit var progress: View
    private lateinit var card: View
    private lateinit var qrFrame: View
    private lateinit var qr: ImageView
    private lateinit var qrWait: View
    private lateinit var qrCaption: TextView
    private lateinit var cardNote: TextView
    private lateinit var field1: EditText
    private lateinit var field2: EditText

    /** Past the welcome screen (kept across rotation and process restore). */
    private var welcomed = false
    /** On the keys step: typing with the remote instead of sending from the phone. */
    private var typingKeys = false
    /** A request is on its way; cleared when Telegram answers (a new state or an error). */
    private var waiting = false
    private var keyDrop: KeyDrop? = null
    private var keyDropUrl: String? = null
    private var shownQr: String? = null
    private var shownScreen: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        welcomed = savedInstanceState?.getBoolean(WELCOMED) ?: false
        typingKeys = savedInstanceState?.getBoolean(TYPING) ?: false
        step = view.findViewById(R.id.onboarding_step)
        title = view.findViewById(R.id.onboarding_title)
        body = view.findViewById(R.id.onboarding_body)
        error = view.findViewById(R.id.onboarding_error)
        logo = view.findViewById(R.id.onboarding_logo)
        primary = view.findViewById(R.id.onboarding_primary)
        secondary = view.findViewById(R.id.onboarding_secondary)
        progress = view.findViewById(R.id.onboarding_progress)
        card = view.findViewById(R.id.onboarding_card)
        qrFrame = view.findViewById(R.id.onboarding_qr_frame)
        qr = view.findViewById(R.id.onboarding_qr)
        qrWait = view.findViewById(R.id.onboarding_qr_wait)
        qrCaption = view.findViewById(R.id.onboarding_qr_caption)
        cardNote = view.findViewById(R.id.onboarding_card_note)
        field1 = view.findViewById(R.id.onboarding_field1)
        field2 = view.findViewById(R.id.onboarding_field2)
        view.findViewById<GlowView>(R.id.onboarding_glow).setGlowColor(BRAND)

        // The keyboard's Done (or Enter) on the last field does what the primary button does.
        val submit = TextView.OnEditorActionListener { _, actionId, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                primary.performClick()
                true
            } else {
                false
            }
        }
        field2.setOnEditorActionListener(submit)
        field1.setOnEditorActionListener { v, actionId, event ->
            if (field2.visibility == View.VISIBLE) false else submit.onEditorAction(v, actionId, event)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                Login.state.combine(Login.error) { state, error -> state to error }.collect { (state, error) ->
                    render(state, error)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(WELCOMED, welcomed)
        outState.putBoolean(TYPING, typingKeys)
    }

    override fun onDestroyView() {
        stopKeyDrop()
        super.onDestroyView()
    }

    private fun rerender() = render(Login.state.value, Login.error.value)

    private fun render(state: State, loginError: String?) {
        // Telegram answered: with an error, or by moving to another step.
        val screen = state.javaClass.simpleName + welcomed + typingKeys
        if (loginError != null || screen != shownScreen) waiting = false
        if (state !is State.NeedKeys || !welcomed || typingKeys) stopKeyDrop()
        reset()
        when (state) {
            State.Starting, State.LoggingOut, State.Ready -> {
                title.setText(if (state == State.Ready) R.string.onboarding_ready else R.string.onboarding_wait)
                progress.visibility = View.VISIBLE
            }
            State.NeedKeys -> if (!welcomed) welcome() else keys()
            is State.ScanQr -> scanQr(state.link)
            State.EnterPhone -> input(
                R.string.onboarding_phone_title, getString(R.string.onboarding_phone_body),
                R.string.auth_phone_hint, InputType.TYPE_CLASS_PHONE, R.string.onboarding_send_code
            ) { phone -> Login.submitPhone(phone) }
            is State.EnterCode -> input(
                R.string.auth_code_title,
                getString(when (state.sentTo) {
                    Login.CodeSentTo.TelegramApp -> R.string.onboarding_code_app
                    Login.CodeSentTo.Sms -> R.string.onboarding_code_sms
                    Login.CodeSentTo.Other -> R.string.onboarding_code_other
                }),
                R.string.auth_code_hint, InputType.TYPE_CLASS_NUMBER, R.string.onboarding_sign_in
            ) { code -> Login.submitCode(code) }
            is State.EnterPassword -> input(
                R.string.auth_password_title,
                getString(R.string.onboarding_password_body) +
                    (if (state.hint.isNotBlank()) "\n" + getString(R.string.onboarding_password_hint_is, state.hint) else ""),
                R.string.auth_password_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
                R.string.onboarding_sign_in, showQrFallback = false
            ) { password -> Login.submitPassword(password) }
            is State.Blocked -> {
                stepText(2)
                title.setText(R.string.onboarding_blocked_title)
                body.setText(when (state.reason) {
                    Login.Blocked.LoginEmail -> R.string.auth_blocked_email
                    Login.Blocked.PaidSms -> R.string.auth_blocked_sms_fee
                    Login.Blocked.NoAccount -> R.string.onboarding_blocked_no_account
                })
                button(primary, R.string.auth_qr_button) { Login.requestQr() }
            }
            State.BadKeys -> {
                stepText(1)
                title.setText(R.string.onboarding_bad_keys_title)
                body.setText(R.string.auth_bad_keys)
                button(primary, R.string.auth_change_keys) { Login.changeKeys() }
            }
        }
        if (loginError != null && state !is State.BadKeys) {
            error.text = errorText(loginError)
            error.visibility = View.VISIBLE
        }
        if (waiting) {
            progress.visibility = View.VISIBLE
            primary.isEnabled = false
        }
        // Focus moves only when the screen itself changes, not on every refresh (a new QR link, an error).
        if (screen != shownScreen) {
            shownScreen = screen
            when {
                field1.visibility == View.VISIBLE -> field1.requestFocus()
                primary.visibility == View.VISIBLE -> primary.requestFocus()
            }
        }
    }

    private fun reset() {
        logo.visibility = View.GONE
        step.text = null
        body.text = null
        error.visibility = View.GONE
        progress.visibility = View.GONE
        card.visibility = View.GONE
        qrFrame.visibility = View.GONE
        qrCaption.visibility = View.GONE
        cardNote.visibility = View.GONE
        field1.visibility = View.GONE
        field2.visibility = View.GONE
        primary.visibility = View.GONE
        secondary.visibility = View.GONE
        primary.isEnabled = true
    }

    private fun welcome() {
        logo.visibility = View.VISIBLE
        title.setText(R.string.onboarding_welcome_title)
        body.setText(R.string.onboarding_welcome_body)
        button(primary, R.string.onboarding_get_started) {
            welcomed = true
            rerender()
        }
        secondary.text = getString(R.string.onboarding_language, LanguageDialog.currentName(requireContext()))
        secondary.visibility = View.VISIBLE
        secondary.setOnClickListener { LanguageDialog.show(requireContext()) }
    }

    /** Step 1: the API keys, sent from the phone (a QR code to a page the TV serves) or typed with the remote. */
    private fun keys() {
        stepText(1)
        title.setText(R.string.onboarding_keys_title)
        body.setText(R.string.onboarding_keys_body)
        card.visibility = View.VISIBLE
        val url = if (typingKeys) null else startKeyDrop()
        if (url != null) {
            showQr(url, getString(R.string.onboarding_keys_qr_caption))
            cardNote.setText(R.string.onboarding_keys_wifi)
            cardNote.visibility = View.VISIBLE
            button(primary, R.string.onboarding_type_keys) {
                typingKeys = true
                rerender()
            }
            return
        }
        // Typing: also the fallback when the TV has no home-network address for the phone page.
        field(field1, R.string.auth_api_id_hint, InputType.TYPE_CLASS_NUMBER)
        field(field2, R.string.auth_api_hash_hint, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        button(primary, R.string.onboarding_continue) { submitKeys(field1.text.toString(), field2.text.toString()) }
        if (KeyDrop.lanAddress() != null) {
            button(secondary, R.string.onboarding_send_from_phone) {
                typingKeys = false
                rerender()
            }
        }
    }

    private fun submitKeys(id: String, hash: String) {
        when (Login.submitKeys(id, hash)) {
            ApiKeys.Problem.Id -> showError(R.string.onboarding_error_api_id)
            ApiKeys.Problem.Hash -> showError(R.string.onboarding_error_api_hash)
            null -> hideKeyboard()
        }
    }

    /** Step 2: scan with Telegram on the phone. The link refreshes by itself until it is scanned. */
    private fun scanQr(link: String?) {
        stepText(2)
        title.setText(R.string.auth_qr_title)
        body.setText(R.string.auth_qr_steps)
        card.visibility = View.VISIBLE
        showQr(link, getString(R.string.onboarding_qr_caption))
        button(primary, R.string.onboarding_use_phone) { Login.usePhoneNumber() }
    }

    /** A step with one field (phone number, code, password) and its button; "Scan a QR code instead" below it. */
    private fun input(
        titleRes: Int, bodyText: String, hint: Int, type: Int, action: Int,
        showQrFallback: Boolean = true, onSubmit: (String) -> Unit
    ) {
        stepText(2)
        title.setText(titleRes)
        body.text = bodyText
        card.visibility = View.VISIBLE
        field(field1, hint, type)
        button(primary, action) {
            val value = field1.text.toString()
            if (value.isBlank()) return@button
            waiting = true
            hideKeyboard()
            rerender()
            onSubmit(value)
        }
        if (showQrFallback) button(secondary, R.string.auth_qr_button) { Login.requestQr() }
    }

    private fun showQr(link: String?, caption: String) {
        qrFrame.visibility = View.VISIBLE
        qrCaption.visibility = View.VISIBLE
        qrCaption.text = caption
        qrWait.visibility = if (link == null) View.VISIBLE else View.GONE
        if (link == null) {
            qr.setImageDrawable(null)
            shownQr = null
        } else if (link != shownQr) {
            shownQr = link
            qr.setImageDrawable(QrCode.drawable(resources, link, QR_PIXELS))
        }
    }

    private fun field(view: EditText, hint: Int, type: Int) {
        view.visibility = View.VISIBLE
        view.setHint(hint)
        if (view.inputType != type) view.inputType = type
    }

    private fun button(view: Button, text: Int, onClick: () -> Unit) {
        view.visibility = View.VISIBLE
        view.setText(text)
        view.setOnClickListener { onClick() }
    }

    private fun stepText(number: Int) {
        step.text = getString(R.string.onboarding_step, number, 2)
    }

    private fun showError(text: Int) {
        error.setText(text)
        error.visibility = View.VISIBLE
    }

    /** Telegram's login errors in plain words; anything else as the app's usual error text. */
    private fun errorText(raw: String): String = when {
        raw.contains("PHONE_NUMBER_INVALID") || raw.contains("PHONE_NUMBER_BANNED") -> getString(R.string.onboarding_error_phone)
        raw.contains("PHONE_CODE_INVALID") || raw.contains("PHONE_CODE_EMPTY") -> getString(R.string.onboarding_error_code)
        raw.contains("PHONE_CODE_EXPIRED") -> getString(R.string.onboarding_error_code_expired)
        raw.contains("PASSWORD_HASH_INVALID") -> getString(R.string.onboarding_error_password)
        else -> StatusText.error(requireContext(), raw)
    }

    private fun startKeyDrop(): String? {
        keyDropUrl?.let { return it }
        val drop = KeyDrop(page()) { id, hash ->
            if (!isAdded) return@KeyDrop
            field1.setText(id)
            field2.setText(hash)
            keyDrop = null
            keyDropUrl = null
            submitKeys(id, hash)
        }
        val url = drop.start() ?: return null
        keyDrop = drop
        keyDropUrl = url
        return url
    }

    private fun stopKeyDrop() {
        keyDrop?.stop()
        keyDrop = null
        keyDropUrl = null
    }

    private fun page() = KeyDrop.Page(
        title = getString(R.string.keydrop_title),
        intro = getString(R.string.keydrop_intro),
        openSite = getString(R.string.keydrop_open_site),
        siteSteps = getString(R.string.keydrop_site_steps),
        idLabel = getString(R.string.auth_api_id_hint),
        hashLabel = getString(R.string.auth_api_hash_hint),
        send = getString(R.string.keydrop_send),
        sent = getString(R.string.keydrop_sent),
        invalid = getString(R.string.keydrop_invalid)
    )

    private fun hideKeyboard() {
        requireContext().getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(requireView().windowToken, 0)
    }

    companion object {
        private const val WELCOMED = "welcomed"
        private const val TYPING = "typing_keys"
        private const val QR_PIXELS = 560
        /** VueGram's violet, for the glow behind onboarding. */
        private const val BRAND = 0xFF7C3AED.toInt()
    }
}
