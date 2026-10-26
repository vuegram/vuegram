package com.vuegram.domain.model

import java.io.Serializable

/** What a card holds. Video files, GIFs and round video messages all count as [Video]: they play the same way. */
enum class MediaType { Video, Photo }

/**
 * A video or photo from a chat, as the grids, the player and the photo viewer use it. Serializable so a list of them
 * can travel to the player (Up next) and the photo viewer.
 */
data class MediaItem(
    /** Where it was posted: the chat and the message. */
    val chatId: Long,
    val messageId: Long,
    /** When it was posted, in Unix seconds. */
    val date: Int,
    val type: MediaType,
    /** The card's title: the caption's first line, else the file's name, else a word for the kind of media. */
    val title: String,
    /** The whole caption, for the photo viewer and the message feed; [title] is only its first line. */
    val caption: String = "",
    /** The file to play or open (TDLib's id), when there is one. */
    val fileId: Int?,
    val fileSizeBytes: Long = 0,
    val durationSeconds: Int = 0,
    /** The card's picture: a thumbnail file to download, its size, and Telegram's tiny blurred preview shown first. */
    val thumbnailFileId: Int? = null,
    val thumbnailPath: String? = null,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    val miniThumbnailBytes: ByteArray? = null
) : Serializable
