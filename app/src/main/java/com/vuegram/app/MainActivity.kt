package com.vuegram.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vuegram.R
import com.vuegram.core.AppLanguage
import com.vuegram.data.stream.TdStreamServer
import com.vuegram.data.login.Login
import com.vuegram.presentation.library.LibraryFragment
import com.vuegram.presentation.onboarding.OnboardingFragment
import com.vuegram.presentation.player.Player
import kotlinx.coroutines.launch

/** A screen that wants Back first, e.g. to clear a search before leaving. Returns true when it used it. */
interface BackHandler {
    fun onBack(): Boolean
}

/** A screen with a search field: the remote's Search key starts voice search there. */
interface VoiceSearchHost {
    fun startVoiceSearch()
}

class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    /** Opens mpv and records where it stopped when it closes. */
    val playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Player.onResult(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android ended VueGram in the background and is bringing it back: its saved screens would ask TDLib for
        // chats before TDLib is signed in again. Start clean instead; the library shows as soon as TDLib is ready.
        // (Recreated in a running VueGram, after a language change say, TDLib is ready and the screens are kept.)
        startedClean = savedInstanceState != null && Login.state.value != Login.State.Ready
        super.onCreate(if (startedClean) null else savedInstanceState)
        setContentView(R.layout.activity_main)
        setupBackHandler()
        // Signed in: the library. Anything else but the first moments of start-up: onboarding. While TDLib starts,
        // the screen stays black instead of flashing onboarding before the library.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                Login.state.collect { state ->
                    val current = supportFragmentManager.findFragmentById(R.id.main_browse_fragment)
                    when {
                        state == Login.State.Ready -> if (current == null || current is OnboardingFragment) showLibrary()
                        state == Login.State.Starting && current !is OnboardingFragment -> Unit
                        current !is OnboardingFragment -> {
                            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                            supportFragmentManager.beginTransaction()
                                .replace(R.id.main_browse_fragment, OnboardingFragment())
                                .commit()
                        }
                    }
                }
            }
        }
    }

    /** Set when [onCreate] dropped the saved screens; the old window's saved focus then goes too. */
    private var startedClean = false

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        if (!startedClean) super.onRestoreInstanceState(savedInstanceState)
    }

    // Lint's RestrictedApi check misfires on super.dispatchKeyEvent from a ComponentActivity subclass; it is public API.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_SEARCH && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val top = supportFragmentManager.fragments.lastOrNull { it.isVisible }
            if (top is VoiceSearchHost) {
                top.startVoiceSearch()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        // Back from the player: the stream is over, so its downloaded parts go.
        TdStreamServer.release(this)
    }

    fun showLibrary() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, LibraryFragment())
            .commit()
    }

    /** When Back on the library last asked "press again to exit" (SystemClock.elapsedRealtime), or 0. */
    private var exitAskedAt = 0L

    /**
     * Back returns to the screen before (from a chat, Settings or search results) and closes VueGram only when pressed
     * twice on the library, so one stray press doesn't quit. A screen can use Back first ([BackHandler]).
     */
    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fragments = supportFragmentManager
                val shown = fragments.fragments.lastOrNull { it.isVisible }
                when {
                    shown is BackHandler && shown.onBack() -> Unit
                    fragments.backStackEntryCount > 0 -> fragments.popBackStack()
                    fragments.findFragmentById(R.id.main_browse_fragment) is LibraryFragment -> exitOnSecondPress()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun exitOnSecondPress() {
        val now = SystemClock.elapsedRealtime()
        if (exitAskedAt != 0L && now - exitAskedAt <= EXIT_WINDOW_MS) {
            finish()
            return
        }
        exitAskedAt = now
        Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
    }
}

/** How long after the first Back on the library a second one closes VueGram. */
private const val EXIT_WINDOW_MS = 2_000L
