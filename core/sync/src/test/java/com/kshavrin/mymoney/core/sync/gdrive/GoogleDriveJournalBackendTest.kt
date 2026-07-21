package com.kshavrin.mymoney.core.sync.gdrive

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleDriveJournalBackendTest {
    // --- authorizer failure propagation ---------------------------------------
    //
    // client() resolves a token from GoogleDriveAuthorizer before ever building
    // a Drive request, so a failing authorizer exercises every JournalBackend
    // method's error path without a live Drive client (OQ-3, no mocking allowed).
    // This is also the regression seam for the NEED_REMOTE_CONSENT bug: a Picker
    // grant on GoogleDriveAuthorizer's ledger must reach the caller as a normal
    // SyncError.Auth banner, not a silent auth failure.

    @Test
    fun `isFolder propagates the authorizer failure and calls it with the passed account`() =
        runTest {
            val authorizer = FailingAuthorizer(SyncError.Auth)
            val backend = backend(authorizer = authorizer)

            val result = backend.isFolder("user@example.com", "folder-1")

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
            assertEquals(listOf("user@example.com"), authorizer.calls)
        }

    @Test
    fun `uploadJournal propagates the authorizer failure for the stored account`() =
        runTest {
            val authorizer = FailingAuthorizer(SyncError.Auth)
            val backend = backend(authorizer = authorizer, secureStorage = storedEmail("user@example.com"))

            val result = backend.uploadJournal("folder-1", "device-1", byteArrayOf())

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
            assertEquals(listOf("user@example.com"), authorizer.calls)
        }

    @Test
    fun `listPeerJournals propagates the authorizer failure for the stored account`() =
        runTest {
            val authorizer = FailingAuthorizer(SyncError.Network)
            val backend = backend(authorizer = authorizer, secureStorage = storedEmail("user@example.com"))

            val result = backend.listPeerJournals("folder-1")

            assertTrue(result.isFailure)
            assertEquals(SyncError.Network, (result.exceptionOrNull() as SyncException).syncError)
            assertEquals(listOf("user@example.com"), authorizer.calls)
        }

    @Test
    fun `downloadJournal propagates the authorizer failure for the stored account`() =
        runTest {
            val authorizer = FailingAuthorizer(SyncError.Auth)
            val backend = backend(authorizer = authorizer, secureStorage = storedEmail("user@example.com"))

            val result = backend.downloadJournal("file-1")

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
            assertEquals(listOf("user@example.com"), authorizer.calls)
        }

    @Test
    fun `uploadJournal fails with Auth without ever calling the authorizer when no account is stored`() =
        runTest {
            val authorizer = FailingAuthorizer(SyncError.Auth)
            val backend = backend(authorizer = authorizer)

            val result = backend.uploadJournal("folder-1", "device-1", byteArrayOf())

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
            assertTrue("the authorizer must never be reached without a resolved account", authorizer.calls.isEmpty())
        }

    // --- query builders --------------------------------------------------------
    //
    // Regression coverage for dropping corpora=allDrives: Google's own Drive API
    // docs warn it can return incomplete results for a plain 'in parents' query,
    // and it isn't needed once includeItemsFromAllDrives+supportsAllDrives are set.

    @Test
    fun `peerJournalsQuery scopes to the folder and the ops- prefix without a corpora hint`() {
        val query = backend().peerJournalsQuery("folder-1")

        assertEquals("'folder-1' in parents and trashed = false and name contains 'ops-'", query)
    }

    @Test
    fun `ownFileQuery scopes to the folder and the exact file name without a corpora hint`() {
        val query = backend().ownFileQuery("folder-1", "ops-device-1.jsonl")

        assertEquals("'folder-1' in parents and trashed = false and name = 'ops-device-1.jsonl'", query)
    }

    private fun backend(
        authorizer: GoogleDriveAuthorizer = FailingAuthorizer(SyncError.Auth),
        secureStorage: SecureStorage = FakeSecureStorage(),
    ): GoogleDriveJournalBackend =
        GoogleDriveJournalBackend(
            secureStorage = secureStorage,
            authorizer = authorizer,
            ioDispatcher = UnconfinedTestDispatcher(),
        )

    private fun storedEmail(email: String): SecureStorage = FakeSecureStorage(SecureSettings(gdriveAccountEmail = email))

    private class FailingAuthorizer(
        private val error: SyncError,
    ) : GoogleDriveAuthorizer {
        val calls: MutableList<String> = mutableListOf()

        override suspend fun accessToken(accountEmail: String): Result<String> {
            calls += accountEmail
            return Result.failure(SyncException(error))
        }
    }

    private class FakeSecureStorage(
        private var settings: SecureSettings = SecureSettings(),
    ) : SecureStorage {
        override fun read(): SecureSettings = settings

        override fun writeDropboxRefreshToken(token: String?) {
            settings = settings.copy(dropboxRefreshToken = token)
        }

        override fun writeGdriveAccountEmail(email: String?) {
            settings = settings.copy(gdriveAccountEmail = email)
        }

        override fun writePinHash(hash: String?) {
            settings = settings.copy(pinHash = hash)
        }

        override fun clearAll() {
            settings = SecureSettings()
        }
    }
}
