package com.vuegram.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorsTest {
    @Test
    fun floodWaitBecomesSlowDown() {
        assertEquals(Errors.Kind.SlowDown(37), Errors.classify("FLOOD_WAIT_37"))
        assertEquals(Errors.Kind.SlowDown(12), Errors.classify("Too Many Requests: retry after 12"))
    }

    @Test
    fun networkTrouble() {
        assertEquals(Errors.Kind.Network, Errors.classify("Network is unreachable"))
        assertEquals(Errors.Kind.Network, Errors.classify("Request timeout"))
    }

    @Test
    fun anythingElseIsShownAsWritten() {
        assertEquals(Errors.Kind.Other("CHAT_ID_INVALID"), Errors.classify("CHAT_ID_INVALID"))
        assertEquals(Errors.Kind.Other("Unknown error"), Errors.classify(null))
        assertEquals(Errors.Kind.Other("Unknown error"), Errors.classify("  "))
    }
}
