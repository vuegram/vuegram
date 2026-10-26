package com.vuegram.data.library

import com.vuegram.core.Texts
import com.vuegram.domain.model.MediaItem
import com.vuegram.domain.model.MediaType
import org.drinkless.tdlib.TdApi

/**
 * The video or photo VueGram shows for [message], or null when it holds neither: text, stickers, voice notes and
 * documents that aren't videos are left out.
 */
internal fun mapMessageToMedia(message: TdApi.Message): MediaItem? = when (val content = message.content) {
    is TdApi.MessageVideo -> content.video?.let { video ->
        message.media(
            title = headline(content.caption?.text) ?: fileTitle(video.fileName) ?: Texts.untitledVideo(message.date),
            caption = content.caption,
            file = video.video,
            seconds = video.duration,
            thumbnail = video.thumbnail,
            preview = video.minithumbnail
        )
    }
    is TdApi.MessageDocument -> content.document?.takeIf(::isVideoDocument)?.let { document ->
        message.media(
            title = headline(content.caption?.text) ?: fileTitle(document.fileName) ?: Texts.untitledVideo(message.date),
            caption = content.caption,
            file = document.document,
            thumbnail = document.thumbnail,
            preview = document.minithumbnail
        )
    }
    is TdApi.MessageAnimation -> content.animation?.let { gif ->
        message.media(
            title = headline(content.caption?.text) ?: fileTitle(gif.fileName) ?: Texts.gif(),
            caption = content.caption,
            file = gif.animation,
            seconds = gif.duration,
            thumbnail = gif.thumbnail,
            preview = gif.minithumbnail
        )
    }
    is TdApi.MessageVideoNote -> content.videoNote?.let { note ->
        message.media(
            title = Texts.videoMessage(),
            caption = null,
            file = note.video,
            seconds = note.duration,
            thumbnail = note.thumbnail,
            preview = note.minithumbnail
        )
    }
    is TdApi.MessagePhoto -> photoOf(message, content)
    else -> null
}

/** Everything a playable message shares: where it is, its file, its length and its card picture. */
private fun TdApi.Message.media(
    title: String,
    caption: TdApi.FormattedText?,
    file: TdApi.File?,
    seconds: Int = 0,
    thumbnail: TdApi.Thumbnail?,
    preview: TdApi.Minithumbnail?
) = MediaItem(
    chatId = chatId,
    messageId = id,
    date = date,
    type = MediaType.Video,
    title = title,
    caption = caption?.text?.trim().orEmpty(),
    fileId = file?.id,
    fileSizeBytes = file?.size ?: 0L,
    durationSeconds = seconds,
    thumbnailFileId = thumbnail?.file?.id,
    thumbnailWidth = thumbnail?.width ?: 0,
    thumbnailHeight = thumbnail?.height ?: 0,
    miniThumbnailBytes = preview?.data
)

/** A photo opens at its largest size; its card loads a smaller one, about [CARD_PHOTO_WIDTH] wide. */
private fun photoOf(message: TdApi.Message, content: TdApi.MessagePhoto): MediaItem {
    val sizes = content.photo.sizes.orEmpty()
    val largest = sizes.maxByOrNull { it.width * it.height }
    val forCard = sizes.filter { it.width >= CARD_PHOTO_WIDTH }.minByOrNull { it.width } ?: largest
    return MediaItem(
        chatId = message.chatId,
        messageId = message.id,
        date = message.date,
        type = MediaType.Photo,
        title = headline(content.caption?.text) ?: Texts.photo(),
        caption = content.caption?.text?.trim().orEmpty(),
        fileId = largest?.photo?.id,
        fileSizeBytes = largest?.photo?.size ?: 0L,
        thumbnailFileId = forCard?.photo?.id,
        thumbnailWidth = largest?.width ?: 0,
        thumbnailHeight = largest?.height ?: 0,
        miniThumbnailBytes = content.photo.minithumbnail?.data
    )
}

private const val CARD_PHOTO_WIDTH = 640

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

/** Container formats people send as files; a document counts as a video when its type or name says so. */
private val VIDEO_EXTENSIONS = setOf(
    "mp4", "m4v", "mkv", "webm", "mov", "avi", "wmv", "flv", "f4v", "mpg", "mpeg", "ts", "m2ts", "mxf", "vob",
    "ogv", "3gp", "3g2", "rm", "rmvb"
)

internal fun isVideoDocument(document: TdApi.Document): Boolean =
    document.mimeType.orEmpty().trim().startsWith("video/", ignoreCase = true) ||
        document.fileName.orEmpty().substringAfterLast('.', "").trim().lowercase() in VIDEO_EXTENSIONS
