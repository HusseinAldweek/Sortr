package com.sortr.domain.vault

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinPolicyTest {

    @Test
    fun `pins must be 4 to 12 digits`() {
        assertTrue(PinPolicy.isValid("1234"))
        assertTrue(PinPolicy.isValid("123456789012"))
        assertFalse(PinPolicy.isValid("123"))
        assertFalse(PinPolicy.isValid("1234567890123"))
        assertFalse(PinPolicy.isValid("12a4"))
        assertFalse(PinPolicy.isValid("12 34"))
        assertFalse(PinPolicy.isValid(""))
    }

    @Test
    fun `no wait for the first few wrong guesses`() {
        for (failures in 0 until PinPolicy.FREE_ATTEMPTS) {
            assertEquals(0L, PinPolicy.lockoutAfter(failures))
        }
    }

    @Test
    fun `waits start at 30 seconds and double`() {
        assertEquals(30_000L, PinPolicy.lockoutAfter(5))
        assertEquals(60_000L, PinPolicy.lockoutAfter(6))
        assertEquals(120_000L, PinPolicy.lockoutAfter(7))
    }

    @Test
    fun `waits are capped at 15 minutes even after many guesses`() {
        assertEquals(15 * 60_000L, PinPolicy.lockoutAfter(20))
        assertEquals(15 * 60_000L, PinPolicy.lockoutAfter(1_000))
    }

    @Test
    fun `attempts left counts down to zero`() {
        assertEquals(5, PinPolicy.attemptsBeforeLockout(0))
        assertEquals(1, PinPolicy.attemptsBeforeLockout(4))
        assertEquals(0, PinPolicy.attemptsBeforeLockout(5))
        assertEquals(0, PinPolicy.attemptsBeforeLockout(9))
    }
}
