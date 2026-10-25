package com.vuegram.data.library

import com.vuegram.domain.model.MediaType
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** TdApi objects are plain Java here; only their toString() is native, so the tests never print them. */
class MessageFeedTest {

    private fun message(content: TdApi.MessageContent, sender: TdApi.MessageSender = TdApi.MessageSenderUser(5)) =
        TdApi.Message().apply {
            id = 100
            chatId = -42
            date = 1_700_000_000
            senderId = sender
            this.content = content
        }

    private fun text(value: String) = TdApi.FormattedText(value, arrayOf())

    @Test
    fun textMessage() {
        val item = mapMessageToFeed(message(TdApi.MessageText().apply { text = text("  Hello TV  ") }))!!
        assertEquals(FeedKind.Text, item.kind)
        assertEquals("Hello TV", item.text)
        assertEquals(5L, item.senderId)
        assertNull(item.media)
    }

    @Test
    fun blankTextIsLeftOut() {
        assertNull(mapMessageToFeed(message(TdApi.MessageText().apply { text = text("   ") })))
    }

    @Test
    fun photoKeepsItsCaptionAndBiggestSize() {
        val small = TdApi.PhotoSize().apply { width = 320; height = 180; photo = TdApi.File().apply { id = 1; size = 10 } }
        val big = TdApi.PhotoSize().apply { width = 1280; height = 720; photo = TdApi.File().apply { id = 2; size = 99 } }
        val content = TdApi.MessagePhoto().apply {
            photo = TdApi.Photo().apply { sizes = arrayOf(small, big) }
            caption = text("Sunset")
        }
        val item = mapMessageToFeed(message(content))!!
        assertEquals(FeedKind.Photo, item.kind)
        assertEquals("Sunset", item.text)
        assertEquals(MediaType.Photo, item.media?.type)
        assertEquals(2, item.media?.fileId)
    }

    @Test
    fun videoDocumentIsAVideo() {
        val content = TdApi.MessageDocument().apply {
            document = TdApi.Document().apply {
                fileName = "episode.mkv"
                mimeType = "application/octet-stream"
                document = TdApi.File().apply { id = 9; size = 1000 }
            }
            caption = text("")
        }
        val item = mapMessageToFeed(message(content))!!
        assertEquals(FeedKind.Video, item.kind)
        assertNotNull(item.media)
    }

    @Test
    fun otherDocumentIsAFileLabel() {
        val content = TdApi.MessageDocument().apply {
            document = TdApi.Document().apply {
                fileName = "menu.pdf"
                mimeType = "application/pdf"
                document = TdApi.File().apply { id = 3 }
            }
            caption = text("Tonight")
        }
        val item = mapMessageToFeed(message(content))!!
        assertEquals(FeedKind.File, item.kind)
        assertEquals("📄 menu.pdf\nTonight", item.text)
    }

    @Test
    fun channelPostsCarryTheChatAsSender() {
        val item = mapMessageToFeed(message(TdApi.MessageText().apply { text = text("News") }, TdApi.MessageSenderChat(-77)))!!
        assertEquals(-77L, item.senderId)
    }

    @Test
    fun serviceMessagesAreLeftOut() {
        assertNull(mapMessageToFeed(message(TdApi.MessageChatJoinByLink())))
    }

    @Test
    fun chatKindsAndPhotos() {
        val chat = TdApi.Chat().apply {
            id = 7
            title = "Movies"
            photo = TdApi.ChatPhotoInfo().apply { small = TdApi.File().apply { id = 11 } }
        }
        val group = LibraryRepository.toGroup(chat, ChatKind.Channel)
        assertEquals(ChatKind.Channel, group.kind)
        assertEquals(true, group.isChannel)
        assertEquals(11, group.photoFileId)   // no big photo: falls back to the small one
        assertEquals(false, LibraryRepository.toGroup(chat, ChatKind.Person).isChannel)
    }
}
