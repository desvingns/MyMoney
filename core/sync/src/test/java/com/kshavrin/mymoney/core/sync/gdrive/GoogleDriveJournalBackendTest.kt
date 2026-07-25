package com.kshavrin.mymoney.core.sync.gdrive

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveJournalBackendTest {
    @Test
    fun `peer query uses app data space and ops prefix without parent folder`() {
        assertEquals("trashed = false and name contains 'ops-'", backend().peerJournalsQuery())
    }

    @Test
    fun `own query uses exact journal file name without parent folder`() {
        assertEquals("trashed = false and name = 'ops-device.jsonl'", backend().ownFileQuery("ops-device.jsonl"))
    }

    @Test
    fun `journal operations require a stored account`() = runTest {
        val result = backend().uploadJournal("device", byteArrayOf())
        assertTrue(result.isFailure)
        assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
    }

    private fun backend(
        authorizer: GoogleDriveAuthorizer = FailingAuthorizer,
        storage: SecureStorage = FakeSecureStorage(),
    ) = GoogleDriveJournalBackend(storage, authorizer, UnconfinedTestDispatcher())

    private object FailingAuthorizer : GoogleDriveAuthorizer {
        override suspend fun accessToken(accountEmail: String): Result<String> = Result.failure(SyncException(SyncError.Auth))
    }

    private class FakeSecureStorage(private var settings: SecureSettings = SecureSettings()) : SecureStorage {
        override fun read() = settings
        override fun writeDropboxRefreshToken(token: String?) { settings = settings.copy(dropboxRefreshToken = token) }
        override fun writeGdriveAccountEmail(email: String?) { settings = settings.copy(gdriveAccountEmail = email) }
        override fun writePinHash(hash: String?) { settings = settings.copy(pinHash = hash) }
        override fun clearAll() { settings = SecureSettings() }
    }
}
