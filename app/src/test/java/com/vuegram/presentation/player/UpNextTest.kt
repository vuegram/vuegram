package com.vuegram.presentation.player

import com.vuegram.domain.model.MediaItem
import com.vuegram.domain.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** "Up next" follows the order episodes are posted in: the videos after the current one, oldest first. */
class UpNextTest {
    private fun video(id: Long, date: Int, chat: Long = 1, file: Int? = id.toInt()) =
        MediaItem(chatId = chat, messageId = id, date = date, type = MediaType.Video, title = "E$id", fileId = file, miniThumbnailBytes = byteArrayOf(1))

    @Test
    fun laterVideosOldestFirst() {
        // The chat grid lists newest first; the queue after episode 2 is 3, 4, 5.
        val grid = listOf(video(5, 500), video(4, 400), video(3, 300), video(2, 200), video(1, 100))
        assertEquals(listOf(3L, 4L, 5L), Player.upNext(video(2, 200), grid).map { it.messageId })
    }

    @Test
    fun nothingAfterTheNewest() {
        assertEquals(emptyList<Long>(), Player.upNext(video(5, 500), listOf(video(5, 500), video(4, 400))).map { it.messageId })
    }

    @Test
    fun otherChatsAndFilelessItemsAreLeftOut_andPreviewsDropped() {
        val queue = Player.upNext(video(1, 100), listOf(video(2, 200, chat = 9), video(3, 300, file = null), video(4, 400)))
        assertEquals(listOf(4L), queue.map { it.messageId })
        assertNull(queue.single().miniThumbnailBytes)
    }
}
