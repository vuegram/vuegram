package com.vuegram.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.vuegram.BuildConfig
import com.vuegram.core.AppLanguage
import com.vuegram.core.Texts
import com.vuegram.data.diagnostics.CrashLog
import com.vuegram.data.history.WatchHistory
import com.vuegram.data.library.ChatOrder
import com.vuegram.data.library.Folders
import com.vuegram.data.library.HiddenChats
import com.vuegram.data.library.PinnedChats
import com.vuegram.data.login.Login
import com.vuegram.data.settings.AppSettings
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class VueGramApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        Texts.init(this)
        CrashLog.install(this)
        if (BuildConfig.DEBUG) {
            // Debug builds flag disk and network work on the main thread, and leaked resources, in logcat.
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().penaltyLog().build())
        }
        System.loadLibrary("tdjni")
        // TDLib logs everything at its default level, which costs a TV's CPU; errors (and warnings in debug) are enough.
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(if (BuildConfig.DEBUG) 2 else 1))
        } catch (e: Exception) {
            Log.w("VueGram", "could not lower TDLib's log level", e)
        }
        AppSettings.init(this)
        WatchHistory.init(this)
        HiddenChats.init(this)
        PinnedChats.init(this)
        ChatOrder.init(this)
        Folders.init()
        Login.init(this)
    }
}
