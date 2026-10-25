package com.vuegram.data.library

import com.vuegram.core.tdlib.client.TdLibClient
import org.drinkless.tdlib.TdApi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The viewer's Telegram chat folders (Work, News, Series… as set up in Telegram), which the library shows as tabs.
 * TDLib sends them by itself after sign-in and whenever they change; nothing is requested.
 */
object Folders {
    data class Folder(val id: Int, val name: String)

    @Volatile var all: List<Folder> = emptyList()
        private set
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    /** Starts listening; call before TDLib starts (from the app's onCreate), so the first update isn't missed. */
    fun init() {
        TdLibClient.addUpdateHandler { update ->
            if (update is TdApi.UpdateChatFolders) {
                all = update.chatFolders.orEmpty().map { Folder(it.id, it.name?.text?.text.orEmpty().ifBlank { "#${it.id}" }) }
                listeners.forEach { it() }
            }
        }
    }

    /** [listener] runs on TDLib's thread whenever the folders change. */
    fun addListener(listener: () -> Unit) = listeners.add(listener)
    fun removeListener(listener: () -> Unit) = listeners.remove(listener)
}
