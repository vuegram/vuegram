package com.vuegram.core.tdlib.client

import android.util.Log
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The app's one TDLib client. Updates arrive on TDLib's own thread and are passed to every registered handler;
 * the connection state is kept here for the header's status pill.
 */
object TdLibClient {
    private const val TAG = "TdLib"
    private val updateHandlers = CopyOnWriteArrayList<(TdApi.Object?) -> Unit>()
    private val connectionListeners = CopyOnWriteArrayList<(Connection) -> Unit>()

    /** How VueGram is connected to Telegram, simplified for the header's status line. */
    enum class Connection { Ready, Connecting, Updating, WaitingForNetwork }

    /** The latest connection state TDLib reported; Ready until it says otherwise. */
    @Volatile var connection = Connection.Ready
        private set

    val client: Client by lazy {
        Client.create({ update -> dispatch(update) }, { error -> Log.e(TAG, "TDLib exception", error) }, null)
    }

    private fun dispatch(update: TdApi.Object?) {
        if (update is TdApi.UpdateConnectionState) {
            connection = when (update.state) {
                is TdApi.ConnectionStateWaitingForNetwork -> Connection.WaitingForNetwork
                is TdApi.ConnectionStateConnecting, is TdApi.ConnectionStateConnectingToProxy -> Connection.Connecting
                is TdApi.ConnectionStateUpdating -> Connection.Updating
                else -> Connection.Ready
            }
            connectionListeners.forEach { listener -> safely { listener(connection) } }
        }
        updateHandlers.forEach { handler -> safely { handler(update) } }
    }

    /** One misbehaving handler must not stop the others, or TDLib's thread. */
    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.e(TAG, "update handler failed", e)
        }
    }

    fun addUpdateHandler(handler: (TdApi.Object?) -> Unit) = updateHandlers.add(handler)
    fun removeUpdateHandler(handler: (TdApi.Object?) -> Unit) = updateHandlers.remove(handler)

    /** [listener] runs on TDLib's thread whenever the connection state changes. */
    fun addConnectionListener(listener: (Connection) -> Unit) = connectionListeners.add(listener)
    fun removeConnectionListener(listener: (Connection) -> Unit) = connectionListeners.remove(listener)
}
