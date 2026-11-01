package com.vuegram.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test

/** Subtitle files reach the player as UTF-8, whatever they were saved in. */
class ChannelSubtitlesTest {
    private val line = "שלום, עולם"

    @Test
    fun utf8StaysAsItIs() {
        assertEquals(line, ChannelSubtitles.decode(line.toByteArray(Charsets.UTF_8), "en"))
    }

    @Test
    fun byteOrderMarksAreRead_andDropped() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(line, ChannelSubtitles.decode(bom + line.toByteArray(Charsets.UTF_8), "en"))
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + line.toByteArray(Charsets.UTF_16LE)
        assertEquals(line, ChannelSubtitles.decode(utf16, "en"))
    }

    @Test
    fun anOldCodePageIsGuessedFromTheViewersLanguage() {
        assertEquals(line, ChannelSubtitles.decode(line.toByteArray(charset("windows-1255")), "iw"))
        val russian = "Привет"
        assertEquals(russian, ChannelSubtitles.decode(russian.toByteArray(charset("windows-1251")), "ru"))
    }

    @get:Rule val folder = TemporaryFolder()

    private fun file(text: String) = folder.newFile().apply { writeText(text) }

    @Test
    fun aVttWithoutItsHeaderIsReadAsSubRip_orDropped() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nHello\n"
        assertEquals("application/x-subrip", ChannelSubtitles.readable(file(srt), "text/vtt", "en")?.second)
        assertEquals("text/vtt", ChannelSubtitles.readable(file("WEBVTT\n\n$srt"), "text/vtt", "en")?.second)
        assertNull(ChannelSubtitles.readable(file("not subtitles at all"), "text/vtt", "en"))
    }

    @Test
    fun emptyAndOversizedFilesAreLeftOut() {
        assertNull(ChannelSubtitles.readable(folder.newFile(), "application/x-subrip", "en"))
        val big = folder.newFile().apply { writeBytes(ByteArray(2 * 1024 * 1024) { 'a'.code.toByte() }) }
        assertNull(ChannelSubtitles.readable(big, "application/x-subrip", "en"))
    }
}
