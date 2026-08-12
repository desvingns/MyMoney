package com.kshavrin.mymoney.core.datastore

import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.core.datastore.model.SecureSharedSession
import com.kshavrin.mymoney.core.network.shared.StoredSharedSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EncryptedSharedSessionStoreTest {
    @Test
    fun `read returns null when secure storage has no shared session`() {
        val storage = FakeSecureStorage()

        assertNull(EncryptedSharedSessionStore(storage).readSharedSession())
    }

    @Test
    fun `read maps all five secure session fields`() {
        val secureSession = secureSession()
        val storage = FakeSecureStorage(sharedSession = secureSession)

        assertEquals(
            StoredSharedSession(
                userId = "user-123",
                userEmail = "user@example.com",
                accessToken = "access-token",
                refreshToken = "refresh-token",
                accessTokenExpiresAtEpochSeconds = 1_725_000_000L,
            ),
            EncryptedSharedSessionStore(storage).readSharedSession(),
        )
    }

    @Test
    fun `write maps all five stored session fields`() {
        val storage = FakeSecureStorage()
        val storedSession =
            StoredSharedSession(
                userId = "user-456",
                userEmail = "another@example.com",
                accessToken = "access-token-2",
                refreshToken = "refresh-token-2",
                accessTokenExpiresAtEpochSeconds = 1_725_000_001L,
            )

        EncryptedSharedSessionStore(storage).writeSharedSession(storedSession)

        assertEquals(
            SecureSharedSession(
                userId = "user-456",
                userEmail = "another@example.com",
                accessToken = "access-token-2",
                refreshToken = "refresh-token-2",
                accessTokenExpiresAtEpochSeconds = 1_725_000_001L,
            ),
            storage.lastWrittenSession,
        )
    }

    @Test
    fun `clear delegates to secure storage`() {
        val storage = FakeSecureStorage(sharedSession = secureSession())

        EncryptedSharedSessionStore(storage).clearSharedSession()

        assertEquals(1, storage.clearCalls)
    }

    private fun secureSession() =
        SecureSharedSession(
            userId = "user-123",
            userEmail = "user@example.com",
            accessToken = "access-token",
            refreshToken = "refresh-token",
            accessTokenExpiresAtEpochSeconds = 1_725_000_000L,
        )

    private class FakeSecureStorage(
        var sharedSession: SecureSharedSession? = null,
    ) : SecureStorage {
        var lastWrittenSession: SecureSharedSession? = null
        var clearCalls = 0

        override fun read() = SecureSettings()

        override fun writeDropboxRefreshToken(token: String?) = Unit

        override fun writeGdriveAccountEmail(email: String?) = Unit

        override fun writePinHash(hash: String?) = Unit

        override fun readSharedSession(): SecureSharedSession? = sharedSession

        override fun writeSharedSession(session: SecureSharedSession) {
            lastWrittenSession = session
            sharedSession = session
        }

        override fun clearSharedSession() {
            clearCalls++
            sharedSession = null
        }

        override fun clearAll() = Unit
    }
}
