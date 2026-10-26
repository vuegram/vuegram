package com.vuegram.presentation.ui

import android.content.Context
import android.view.View
import android.widget.TextView
import com.vuegram.R
import com.vuegram.core.Errors
import com.vuegram.core.tdlib.client.TdLibClient
import com.vuegram.core.tdlib.client.TdLibClient.Connection

/** On-screen wording for errors and for the connection to Telegram. */
object StatusText {

    fun error(context: Context, message: String?): String = when (val kind = Errors.classify(message)) {
        is Errors.Kind.SlowDown -> kind.seconds.coerceAtLeast(1).let { context.resources.getQuantityString(R.plurals.error_slow_down, it, it) }
        Errors.Kind.Network -> context.getString(R.string.error_network)
        is Errors.Kind.Other -> context.getString(R.string.error_other, kind.message)
    }

    /**
     * Keeps [pill] showing "Connecting…", "Waiting for network…" or "Updating…", and hides it once VueGram is
     * connected. It follows the view: listening starts when the view is attached and stops when it is detached.
     */
    fun followConnection(pill: TextView) {
        val listener: (Connection) -> Unit = { state -> pill.post { show(pill, state) } }
        pill.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                TdLibClient.addConnectionListener(listener)
                show(pill, TdLibClient.connection)
            }

            override fun onViewDetachedFromWindow(v: View) {
                TdLibClient.removeConnectionListener(listener)
            }
        })
        show(pill, TdLibClient.connection)
    }

    private fun show(pill: TextView, state: Connection) {
        val text = when (state) {
            Connection.Ready -> null
            Connection.Connecting -> R.string.connection_connecting
            Connection.Updating -> R.string.connection_updating
            Connection.WaitingForNetwork -> R.string.connection_waiting
        }
        pill.visibility = if (text == null) View.GONE else View.VISIBLE
        if (text != null) pill.setText(text)
    }
}
