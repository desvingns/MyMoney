package com.kshavrin.mymoney.core.network.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class InviteTokenFactoryTest {
    private val factory = InviteTokenFactory(SecureRandom())

    // --- newToken structure ---

    @Test
    fun `newToken plaintext is 64 hex characters`() {
        // 32 random bytes × 2 hex digits per byte = 64 chars
        val token = factory.newToken()
        assertEquals(64, token.plaintext.length)
    }

    @Test
    fun `newToken plaintext is lowercase hex only`() {
        val token = factory.newToken()
        assertTrue(
            "plaintext must match [0-9a-f]+",
            token.plaintext.matches(Regex("[0-9a-f]+")),
        )
    }

    @Test
    fun `newToken hash is 64 hex characters`() {
        // SHA-256 produces 32 bytes → 64 lowercase hex chars
        val token = factory.newToken()
        assertEquals(64, token.hash.length)
    }

    @Test
    fun `newToken hash is lowercase hex only`() {
        val token = factory.newToken()
        assertTrue(
            "hash must match [0-9a-f]{64}",
            token.hash.matches(Regex("[0-9a-f]{64}")),
        )
    }

    // --- internal consistency: hash stored in InviteToken equals hash(plaintext) ---

    @Test
    fun `newToken hash equals hash of its own plaintext`() {
        val token = factory.newToken()
        assertEquals(factory.hash(token.plaintext), token.hash)
    }

    @Test
    fun `newToken plaintext differs from its hash`() {
        // Plaintext is raw random bytes; the hash is SHA-256 of those bytes.
        // They must be different values so callers cannot confuse the two.
        val token = factory.newToken()
        assertNotEquals(token.plaintext, token.hash)
    }

    // --- determinism and uniqueness ---

    @Test
    fun `hash is deterministic for the same input`() {
        val input = "hello-world"
        assertEquals(factory.hash(input), factory.hash(input))
    }

    @Test
    fun `successive newToken calls produce different plaintexts`() {
        // 32 bytes of SecureRandom — collision is cosmologically improbable
        val first = factory.newToken().plaintext
        val second = factory.newToken().plaintext
        assertNotEquals(first, second)
    }

    // --- known SHA-256 vectors that pin the format expected by join_workspace()
    //     PostgreSQL: encode(digest(p_token, 'sha256'), 'hex') → lowercase hex ---

    @Test
    fun `hash of empty string matches SHA-256 known vector`() {
        // NIST SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, factory.hash(""))
    }

    @Test
    fun `hash of abc matches SHA-256 known vector`() {
        // NIST SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(expected, factory.hash("abc"))
    }

    @Test
    fun `hash output is always lowercase`() {
        val hash = factory.hash("MixedCase Input 123!")
        assertEquals(hash, hash.lowercase())
    }

    @Test
    fun `hash changes when input changes`() {
        assertNotEquals(factory.hash("token-A"), factory.hash("token-B"))
    }
}
