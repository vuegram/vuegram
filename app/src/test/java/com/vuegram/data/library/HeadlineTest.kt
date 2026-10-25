package com.vuegram.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadlineTest {
    @Test
    fun firstLineWithTextIsTheTitle() {
        assertEquals("France changes course?", headline("\n  France changes course?  \n\nThe story goes on here..."))
    }

    @Test
    fun oneLineCaptionStaysAsItIs() {
        assertEquals("Match highlights", headline("Match highlights"))
    }

    @Test
    fun noTextGivesNull() {
        assertNull(headline(null))
        assertNull(headline(" \n \n"))
    }
}
