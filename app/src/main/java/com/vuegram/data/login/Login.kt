package com.vuegram.data.login

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.vuegram.BuildConfig
import com.vuegram.app.AppRestart
import com.vuegram.core.tdlib.client.TdLibClient
import com.vuegram.data.diagnostics.CrashLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale

/**
 * Signing in to Telegram, as one state the onboarding screen follows. It drives TDLib's authorization states:
 * API keys first, then a QR code to scan with Telegram on a phone (the phone number is the fallback), then the
 * 2-step verification password if the account has one.
 *
 * TDLib can't go from the QR code back to the phone-number step, so [usePhoneNumber] resets the unfinished login
 * (TDLib's logOut, then a fresh process) and starts again at the phone number.
 */
// Holds only the application context, which lives as long as the process: no activity can leak through it.
@SuppressLint("StaticFieldLeak")
object Login {
    private const val TAG = "Login"
    private const val PREFS = "settings"
    private const val PREFER_PHONE = "login_with_phone"
    /** What belongs to the logged-in account and is wiped when it logs out (WatchHistory, PinnedChats, HiddenChats). */
    private val ACCOUNT_PREFS = listOf("watch_history", "pinned_chats", "hidden_chats")

    enum class Blocked { LoginEmail, PaidSms, NoAccount }
    enum class CodeSentTo { TelegramApp, Sms, Other }

    sealed interface State {
        /** TDLib is starting, or a request is on its way. */
        object Starting : State
        object NeedKeys : State
        /** [link] is the tg://login link to show as a QR code; null while TDLib prepares it. */
        data class ScanQr(val link: String?) : State
        object EnterPhone : State
        data class EnterCode(val sentTo: CodeSentTo) : State
        data class EnterPassword(val hint: String) : State
        /** Telegram wants something a TV can't do on the phone-number route; the QR code still works. */
        data class Blocked(val reason: Login.Blocked) : State
        /** Telegram rejected the API ID or hash. */
        object BadKeys : State
        object Ready : State
        object LoggingOut : State
    }

    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state

    /** The last error Telegram returned for something the viewer did (raw TDLib message), or null. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private lateinit var app: Context
    private val handler: (TdApi.Object?) -> Unit = { update ->
        if (update is TdApi.UpdateAuthorizationState) onAuthState(update.authorizationState)
    }

    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        TdLibClient.addUpdateHandler(handler)
        send(TdApi.GetAuthorizationState()) { result -> if (result is TdApi.AuthorizationState) onAuthState(result) }
    }

    /** Saves the keys (if they look right) and hands them to TDLib. Returns what is wrong with them, or null. */
    fun submitKeys(id: String, hash: String): ApiKeys.Problem? {
        ApiKeys.problem(id, hash)?.let { return it }
        ApiKeys.save(app, id, hash)
        _state.value = State.Starting
        send(TdApi.GetAuthorizationState()) { result -> if (result is TdApi.AuthorizationState) onAuthState(result) }
        return null
    }

    fun requestQr() {
        preferPhone(false)
        _state.value = State.ScanQr(null)
        send(TdApi.RequestQrCodeAuthentication(longArrayOf()))
    }

    /** Switches to signing in with a phone number; from the QR code that takes a quick restart (see the class doc). */
    fun usePhoneNumber() {
        preferPhone(true)
        if (_state.value is State.ScanQr) {
            _state.value = State.Starting
            send(TdApi.LogOut())
        } else {
            _state.value = State.EnterPhone
        }
    }

    fun submitPhone(phone: String) = send(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
    fun submitCode(code: String) = send(TdApi.CheckAuthenticationCode(code.trim()))
    fun submitPassword(password: String) = send(TdApi.CheckAuthenticationPassword(password))

    /** Forgets the API keys and starts again at the keys step (TDLib can't take new keys within a process). */
    fun changeKeys() {
        ApiKeys.clear(app)
        AppRestart.restart(app)
    }

    /** Signs this TV out of Telegram; VueGram restarts at the start of onboarding. */
    fun logOut(onError: (String) -> Unit) {
        TdLibClient.client.send(TdApi.LogOut()) { result -> if (result is TdApi.Error) onError(result.message.orEmpty()) }
    }

    private fun send(query: TdApi.Function<*>, onResult: (TdApi.Object?) -> Unit = {}) {
        _error.value = null
        TdLibClient.client.send(query) { result ->
            if (result is TdApi.Error) onError(result) else onResult(result)
        }
    }

    private fun onError(error: TdApi.Error) {
        Log.w(TAG, "login error ${error.code}: ${error.message}")
        // API_ID_INVALID, API_ID_PUBLISHED_FLOOD: the keys themselves are wrong or blocked.
        if (error.message.orEmpty().contains("API_ID", ignoreCase = true)) {
            _state.value = State.BadKeys
            return
        }
        _error.value = error.message
        // A request failed before TDLib moved on: show the step it is still at, with the error.
        send(TdApi.GetAuthorizationState()) { result -> if (result is TdApi.AuthorizationState) onAuthState(result, keepError = error.message) }
    }

    private fun onAuthState(state: TdApi.AuthorizationState?, keepError: String? = null) {
        if (keepError != null) _error.value = keepError
        _state.value = when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val keys = ApiKeys.get(app) ?: return run { _state.value = State.NeedKeys }
                setParameters(keys)
                State.Starting
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                if (preferPhone()) {
                    State.EnterPhone
                } else {
                    // The QR code first: no SMS, no login email, nothing to type with a remote.
                    TdLibClient.client.send(TdApi.RequestQrCodeAuthentication(longArrayOf())) { result ->
                        if (result is TdApi.Error) onError(result)
                    }
                    State.ScanQr(null)
                }
            }
            is TdApi.AuthorizationStateWaitOtherDeviceConfirmation -> State.ScanQr(state.link)
            is TdApi.AuthorizationStateWaitCode -> State.EnterCode(
                when (state.codeInfo?.type) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> CodeSentTo.TelegramApp
                    is TdApi.AuthenticationCodeTypeSms -> CodeSentTo.Sms
                    else -> CodeSentTo.Other
                }
            )
            is TdApi.AuthorizationStateWaitPassword -> State.EnterPassword(state.passwordHint.orEmpty())
            is TdApi.AuthorizationStateWaitEmailAddress, is TdApi.AuthorizationStateWaitEmailCode -> State.Blocked(Blocked.LoginEmail)
            is TdApi.AuthorizationStateWaitPremiumPurchase -> State.Blocked(Blocked.PaidSms)
            is TdApi.AuthorizationStateWaitRegistration -> State.Blocked(Blocked.NoAccount)
            is TdApi.AuthorizationStateReady -> {
                preferPhone(false)
                // TDLib trims its cache of photos and thumbnails by itself (videos are deleted after each stream).
                TdLibClient.client.send(TdApi.SetOption("use_storage_optimizer", TdApi.OptionValueBoolean(true))) { }
                State.Ready
            }
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> State.LoggingOut
            is TdApi.AuthorizationStateClosed -> {
                // After a logout (or the switch to phone-number login): the account's history, pinned and hidden
                // chats and the crash log go with it, then VueGram starts again in a fresh process (a closed TDLib
                // client can't be reused). Settings and the API keys stay.
                for (name in ACCOUNT_PREFS) app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                CrashLog.clear()
                AppRestart.restart(app)
                State.LoggingOut
            }
            else -> _state.value
        }
    }

    private fun setParameters(keys: ApiKeys.Keys) {
        val parameters = TdApi.SetTdlibParameters(
            false,
            File(app.filesDir, "tdlib").absolutePath,
            File(app.filesDir, "tdlib_files").absolutePath,
            byteArrayOf(),
            true,
            true,
            true,
            false,
            keys.id,
            keys.hash,
            Locale.getDefault().language.ifEmpty { "en" },
            "Android TV",
            Build.VERSION.RELEASE ?: "Android",
            BuildConfig.VERSION_NAME
        )
        send(parameters)
    }

    private fun preferPhone() = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREFER_PHONE, false)

    private fun preferPhone(value: Boolean) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREFER_PHONE, value).commit()
    }
}
