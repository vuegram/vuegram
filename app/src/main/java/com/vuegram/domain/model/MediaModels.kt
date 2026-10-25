package com.vuegram.domain.model

import java.io.Serializable

enum class MediaType {
    Photo,
    Video
}

data class MediaItem(
    val chatId: Long,
    val messageId: Long,
    val date: Int,
    val type: MediaType,
    val title: String,
    val fileId: Int?,
    val thumbnailFileId: Int? = null,
    val thumbnailPath: String? = null,
    val miniThumbnailBytes: ByteArray? = null,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0,
    val durationSeconds: Int = 0,
    val fileSizeBytes: Long = 0,
    /** The whole caption; [title] is only its first line. */
    val caption: String = ""
) : Serializable
