package com.vuegram.data.library

import com.vuegram.core.Texts
import com.vuegram.domain.model.MediaItem
import com.vuegram.domain.model.MediaType
import org.drinkless.tdlib.TdApi

internal fun mapMessageToMedia(message: TdApi.Message): MediaItem? {
    return when (val content = message.content) {
        is TdApi.MessageVideo -> {
            val video = content.video
            val title = headline(content.caption?.text)
                ?: fileTitle(video?.fileName)
                ?: Texts.video()
            val file = video?.video
            val thumb = video?.thumbnail
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = title,
                caption = content.caption?.text?.trim().orEmpty(),
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = video?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = video?.duration ?: 0,
                fileSizeBytes = file?.size ?: 0
            )
        }
        is TdApi.MessageVideoNote -> {
            val videoNote = content.videoNote
            val file = videoNote?.video
            val thumb = videoNote?.thumbnail
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = Texts.videoMessage(),
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = videoNote?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = videoNote?.duration ?: 0,
                fileSizeBytes = file?.size ?: 0
            )
        }
        is TdApi.MessageDocument -> {
            val document = content.document
            if (!isVideoDocument(document)) return null
            val title = headline(content.caption?.text) ?: fileTitle(document?.fileName) ?: Texts.video()
            val file = document?.document
            val thumb = document?.thumbnail
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = title,
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = document?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = 0,
                fileSizeBytes = file?.size ?: 0
            )
        }
        is TdApi.MessageAnimation -> {
            val animation = content.animation
            val file = animation?.animation
            val thumb = animation?.thumbnail
            val title = headline(content.caption?.text)
                ?: fileTitle(animation?.fileName)
                ?: Texts.gif()
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Video,
                title = title,
                fileId = file?.id,
                thumbnailFileId = thumb?.file?.id,
                miniThumbnailBytes = animation?.minithumbnail?.data,
                thumbnailWidth = thumb?.width ?: 0,
                thumbnailHeight = thumb?.height ?: 0,
                durationSeconds = animation?.duration ?: 0,
                fileSizeBytes = file?.size ?: 0
            )
        }
        is TdApi.MessagePhoto -> {
            val photo = content.photo
            val bestSize = photo.sizes?.maxByOrNull { it.width * it.height }
            val title = headline(content.caption?.text) ?: Texts.photo()
            MediaItem(
                chatId = message.chatId,
                messageId = message.id,
                date = message.date,
                type = MediaType.Photo,
                title = title,
                caption = content.caption?.text?.trim().orEmpty(),
                fileId = bestSize?.photo?.id,
                // The card needs about 640 px, not the 2560 px original the viewer opens.
                thumbnailFileId = (photo.sizes?.filter { it.width >= CARD_PHOTO_WIDTH }?.minByOrNull { it.width } ?: bestSize)?.photo?.id,
                miniThumbnailBytes = photo.minithumbnail?.data,
                thumbnailWidth = bestSize?.width ?: 0,
                thumbnailHeight = bestSize?.height ?: 0,
                durationSeconds = 0,
                fileSizeBytes = bestSize?.photo?.size?.toLong() ?: 0L
            )
        }
        else -> null
    }
}

/**
 * A caption's first line with text, as the title: captions often run "headline, blank line, story", and the whole
 * caption in a two-line title showed the headline and then a line of just "…". Null when there is no text.
 */
internal fun headline(caption: String?): String? =
    caption?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }

/** A file name as a title: "Match_highlights_2025.mp4" reads "Match highlights 2025". Null when there is none. */
internal fun fileTitle(fileName: String?): String? {
    val name = fileName?.trim().orEmpty()
    val base = name.substringBeforeLast('.', name).takeIf { it.isNotBlank() } ?: name
    return base.replace('_', ' ').replace(Regex(" {2,}"), " ").trim().takeIf { it.isNotEmpty() }
}

private const val CARD_PHOTO_WIDTH = 640

internal fun isVideoDocument(document: TdApi.Document?): Boolean {
    val mime = document?.mimeType?.lowercase()?.trim().orEmpty()
    val fileName = document?.fileName?.lowercase()?.trim().orEmpty()
    return mime.startsWith("video/") || fileName.endsWith(".mkv") || fileName.endsWith(".mp4") ||
        fileName.endsWith(".mov") || fileName.endsWith(".webm") || fileName.endsWith(".avi") ||
        fileName.endsWith(".m4v") || fileName.endsWith(".mpeg") || fileName.endsWith(".mpg") ||
        fileName.endsWith(".wmv") || fileName.endsWith(".flv") || fileName.endsWith(".3gp") ||
        fileName.endsWith(".3g2") || fileName.endsWith(".ts") || fileName.endsWith(".m2ts") ||
        fileName.endsWith(".mxf") || fileName.endsWith(".f4v") || fileName.endsWith(".vob") ||
        fileName.endsWith(".ogv") || fileName.endsWith(".rm") || fileName.endsWith(".rmvb")
}
