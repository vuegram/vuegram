package com.vuegram.app

import android.content.Context
import android.content.Intent

/**
 * Starts VueGram again from its first screen in a fresh process. Used after logging out: TDLib closes its client then,
 * and a closed client can't be reused, so the simplest safe way to a new login is a new process.
 */
object AppRestart {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val component = launch.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
