package com.kshavrin.mymoney.feature.cloudsync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedGoogleNonceTest {
    @Test
    fun `Credential Manager nonce is hashed while the Supabase nonce remains raw`() {
        val rawNonce = "abc"

        val credentialNonce = sharedGoogleCredentialNonce(rawNonce)

        assertNotEquals(rawNonce, credentialNonce)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            credentialNonce,
        )
    }

    @Test
    fun `Shared Google client id strips template brackets copied into local config`() {
        val clientId = "<123456789012-example.apps.googleusercontent.com>"

        assertEquals(
            "123456789012-example.apps.googleusercontent.com",
            normalizeSharedGoogleWebClientId(clientId),
        )
    }

    @Test
    fun `Shared Google client id rejects empty template brackets and whitespace`() {
        assertNull(sharedGoogleWebClientIdOrNull("<>"))
        assertNull(sharedGoogleWebClientIdOrNull(" \t "))
    }
}
