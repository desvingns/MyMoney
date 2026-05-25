package com.kshavrin.mymoney.feature.lockscreen.setup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun `hash does not contain the plaintext pin`() {
        val stored = hasher.hash("1234")

        assertFalse(stored.contains("1234"))
    }

    @Test
    fun `hash produces a salt and hash separated by a single colon`() {
        val stored = hasher.hash("1234")

        val parts = stored.split(":")
        assertEquals(2, parts.size)
        assertTrue(parts[0].isNotEmpty())
        assertTrue(parts[1].isNotEmpty())
    }

    @Test
    fun `verify returns true for the pin that produced the stored hash`() {
        val stored = hasher.hash("1234")

        assertTrue(hasher.verify("1234", stored))
    }

    @Test
    fun `verify returns false for a different pin`() {
        val stored = hasher.hash("1234")

        assertFalse(hasher.verify("5678", stored))
    }

    @Test
    fun `verify returns false when one digit differs`() {
        val stored = hasher.hash("1234")

        assertFalse(hasher.verify("1235", stored))
    }

    @Test
    fun `two hashes of the same pin differ because the salt is random`() {
        val first = hasher.hash("1234")
        val second = hasher.hash("1234")

        assertNotEquals(first, second)
    }

    @Test
    fun `verify accepts a pin against either of two independently salted hashes`() {
        val first = hasher.hash("1234")
        val second = hasher.hash("1234")

        assertTrue(hasher.verify("1234", first))
        assertTrue(hasher.verify("1234", second))
    }

    @Test
    fun `round trip through the stored salt and hash format succeeds`() {
        val pin = "0000"
        val stored = hasher.hash(pin)

        assertTrue(hasher.verify(pin, stored))
    }

    @Test
    fun `verify returns false for a malformed stored value without a separator`() {
        assertFalse(hasher.verify("1234", "not-a-valid-stored-hash"))
    }

    @Test
    fun `verify returns false for an empty stored value`() {
        assertFalse(hasher.verify("1234", ""))
    }
}
