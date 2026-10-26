package com.vuegram.data.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The check behind both the TV's fields and the phone page: an API ID is a number, an API hash 32 hex characters. */
class ApiKeysTest {
    private val hash = "0123456789abcdef0123456789ABCDEF"

    @Test
    fun goodKeys() {
        assertNull(ApiKeys.problem("1234567", hash))
        assertNull(ApiKeys.problem(" 1234567 ", " $hash "))   // pasted with spaces
    }

    @Test
    fun badId() {
        assertEquals(ApiKeys.Problem.Id, ApiKeys.problem("", hash))
        assertEquals(ApiKeys.Problem.Id, ApiKeys.problem("12a45", hash))
        assertEquals(ApiKeys.Problem.Id, ApiKeys.problem("0", hash))
        assertEquals(ApiKeys.Problem.Id, ApiKeys.problem("99999999999", hash))   // beyond what TDLib takes
    }

    @Test
    fun badHash() {
        assertEquals(ApiKeys.Problem.Hash, ApiKeys.problem("1234567", ""))
        assertEquals(ApiKeys.Problem.Hash, ApiKeys.problem("1234567", hash.dropLast(1)))
        assertEquals(ApiKeys.Problem.Hash, ApiKeys.problem("1234567", hash.dropLast(1) + "g"))
    }
}
