package com.vuegram.data.library

import com.vuegram.core.Texts
import com.vuegram.domain.model.MediaItem
import com.vuegram.domain.model.MediaType
import org.drinkless.tdlib.TdApi

/** What a message in the "all messages" view holds; it decides the card and what OK does. */
enum class FeedKind { Text, Photo, Video, File, Other }

/**
 * One message in a chat's "all messages" view (Settings › Show messages). [media] is set for photos and videos, so
 * they open in the photo viewer or the player; [text] is the message text or the media's caption.
 */
data class FeedItem(
    val chatId: Long,
    val messageId: Long,
    val date: Int,
    val kind: FeedKind,
    val text: String,
    val media: MediaItem? = null,
    val senderId: Long = 0L,
    val sender: String = ""
)

/** Where the next page of a chat's messages starts; [done] once Telegram has nothing older. */
data class FeedCursor(val fromMessageId: Long = 0L, val done: Boolean = false)

/**
 * Turns a message into a feed card, or null for what is not worth a card on a TV (service messages such as "joined
 * the group", and empty messages). Files, audio, polls and the like get a one-line label; they can't be opened here.
 */
internal fun mapMessageToFeed(message: TdApi.Message): FeedItem? {
    val content = message.content ?: return null
    val sender = when (val s = message.senderId) {
        is TdApi.MessageSenderUser -> s.userId
        is TdApi.MessageSenderChat -> s.chatId
        else -> 0L
    }
    fun item(kind: FeedKind, text: String, media: MediaItem? = null) = FeedItem(
        chatId = message.chatId,
        messageId = message.id,
        date = message.date,
        kind = kind,
        text = text.trim(),
        media = media,
        senderId = sender
    )
    val caption = captionOf(content)
    return when (content) {
        is TdApi.MessageText -> content.text?.text?.takeIf { it.isNotBlank() }?.let { item(FeedKind.Text, it) }
        is TdApi.MessagePhoto -> mapMessageToMedia(message)?.let { item(FeedKind.Photo, caption, it) }
        is TdApi.MessageVideo, is TdApi.MessageVideoNote, is TdApi.MessageAnimation ->
            mapMessageToMedia(message)?.let { item(FeedKind.Video, caption, it) }
        is TdApi.MessageDocument -> {
            val media = mapMessageToMedia(message)
            if (media != null) {
                item(FeedKind.Video, caption, media)
            } else {
                val name = content.document?.fileName?.takeIf { it.isNotBlank() }
                val label = if (name != null) Texts.file(name) else Texts.fileUnnamed()
                item(FeedKind.File, listOf(label, caption).filter { it.isNotBlank() }.joinToString("\n"))
            }
        }
        is TdApi.MessageAudio -> {
            val audio = content.audio
            val name = listOfNotNull(audio?.performer, audio?.title).filter { it.isNotBlank() }.joinToString(" – ")
                .ifBlank { audio?.fileName.orEmpty() }
            val label = if (name.isNotBlank()) Texts.audio(name) else Texts.audioUnnamed()
            item(FeedKind.File, listOf(label, caption).filter { it.isNotBlank() }.joinToString("\n"))
        }
        is TdApi.MessageVoiceNote -> item(FeedKind.Other, listOf(Texts.voice(), caption).filter { it.isNotBlank() }.joinToString("\n"))
        is TdApi.MessageSticker -> item(FeedKind.Other, Texts.sticker(content.sticker?.emoji.orEmpty()))
        is TdApi.MessagePoll -> item(FeedKind.Other, Texts.poll(content.poll?.question?.text.orEmpty()).trim())
        is TdApi.MessageLocation, is TdApi.MessageVenue -> item(FeedKind.Other, Texts.location())
        is TdApi.MessageContact -> item(FeedKind.Other, Texts.contact(content.contact?.firstName.orEmpty()).trim())
        else -> null
    }
}

private fun captionOf(content: TdApi.MessageContent): String = when (content) {
    is TdApi.MessagePhoto -> content.caption?.text
    is TdApi.MessageVideo -> content.caption?.text
    is TdApi.MessageAnimation -> content.caption?.text
    is TdApi.MessageDocument -> content.caption?.text
    is TdApi.MessageAudio -> content.caption?.text
    is TdApi.MessageVoiceNote -> content.caption?.text
    else -> null
}.orEmpty()

/** A user's or chat's display name for the sender line. */
internal fun displayName(user: TdApi.User): String =
    listOf(user.firstName, user.lastName).filter { !it.isNullOrBlank() }.joinToString(" ")
