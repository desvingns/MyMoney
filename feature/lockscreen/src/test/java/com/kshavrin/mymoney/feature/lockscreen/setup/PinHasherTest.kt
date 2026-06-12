package com.kshavrin.mymoney.feature.lockscreen.setup

import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
    fun `hash writes the v2 format with 600000 iterations`() {
        val stored = hasher.hash("1234", salt = fixedSalt)

        val parts = stored.split(":")
        assertEquals(4, parts.size)
        assertEquals("v2", parts[0])
        assertEquals("600000", parts[1])
        assertTrue(parts[2].isNotEmpty())
        assertTrue(parts[3].isNotEmpty())
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
    fun `v2 round trip through the stored format succeeds`() {
        val pin = "0000"
        val stored = hasher.hash(pin)

        assertTrue(hasher.verify(pin, stored))
    }

    @Test
    fun `legacy salt and hash format verifies with 10000 iterations`() {
        val stored = legacyHash(pin = "1234", salt = fixedSalt)

        assertTrue(hasher.verify("1234", stored))
    }

    @Test
    fun `verify returns false for malformed and unsupported formats`() {
        assertFalse(hasher.verify("1234", "not-a-valid-stored-hash"))
        assertFalse(hasher.verify("1234", ""))
        assertFalse(hasher.verify("1234", "v3:600000:AAAA:BBBB"))
        assertFalse(hasher.verify("1234", "v2:not-a-number:AAAA:BBBB"))
    }

    @Test
    fun `verifyDetailed reports needsRehash for legacy hashes`() {
        val result = hasher.verifyDetailed("1234", legacyHash(pin = "1234", salt = fixedSalt))

        assertTrue(result.verified)
        assertTrue(result.needsRehash)
    }

    @Test
    fun `verifyDetailed reports no rehash needed for the current format`() {
        val result = hasher.verifyDetailed("1234", hasher.hash("1234", salt = fixedSalt))

        assertTrue(result.verified)
        assertFalse(result.needsRehash)
    }

    private fun legacyHash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return "${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(derived)}"
    }

    private companion object {
        val fixedSalt = byteArrayOf(
            0x01.toByte(),
            0x23.toByte(),
            0x45.toByte(),
            0x67.toByte(),
            0x11.toByte(),
            0x22.toByte(),
            0x33.toByte(),
            0x44.toByte(),
            0x55.toByte(),
            0x66.toByte(),
            0x77.toByte(),
            0x12.toByte(),
            0x34.toByte(),
            0x56.toByte(),
            0x78.toByte(),
            0x09.toByte(),
        )
    }
}
