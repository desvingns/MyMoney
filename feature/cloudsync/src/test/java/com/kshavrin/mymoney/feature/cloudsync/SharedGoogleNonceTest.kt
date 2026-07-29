package com.kshavrin.mymoney.feature.cloudsync

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedGoogleNonceTest {
    @Test
    fun `Credential Manager nonce is SHA-256 hex of the raw Supabase nonce`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sharedGoogleCredentialNonce("abc"),
        )
    }
}
