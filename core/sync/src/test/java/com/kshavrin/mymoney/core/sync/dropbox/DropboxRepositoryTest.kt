package com.kshavrin.mymoney.core.sync.dropbox

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class DropboxRepositoryTest {
    // --- mapDropboxError -----------------------------------------------------
    //
    // The seam matches on class.simpleName for SDK types (so it stays SDK-free
    // and unit-testable). These local throwaway subclasses reproduce only the
    // simpleName the seam keys on. They extend Exception (NOT IOException) on
    // purpose: mapDropboxError checks `t is IOException` BEFORE the simpleName
    // branch, so an IOException-derived class would short-circuit to Network and
    // never reach the name match we want to exercise.
    private class InvalidAccessTokenException : Exception()

    private class SpaceError : Exception()

    private class RateLimitException : Exception()

    private class ServerException : Exception()

    private class DbxException : Exception()

    private class NetworkIOException : Exception()

    private class SomethingNobodyMaps : Exception()

    @Test
    fun `mapDropboxError maps a plain IOException to Network`() {
        assertEquals(SyncError.Network, mapDropboxError(IOException("offline")))
    }

    @Test
    fun `mapDropboxError maps any IOException subtype to Network`() {
        assertEquals(SyncError.Network, mapDropboxError(SocketTimeoutException()))
    }

    @Test
    fun `mapDropboxError passes through the inner error of a SyncException`() {
        for (error in SyncError.entries) {
            assertEquals(error, mapDropboxError(SyncException(error)))
        }
    }

    @Test
    fun `mapDropboxError maps InvalidAccessTokenException to Auth`() {
        assertEquals(SyncError.Auth, mapDropboxError(InvalidAccessTokenException()))
    }

    @Test
    fun `mapDropboxError maps SpaceError to Quota`() {
        assertEquals(SyncError.Quota, mapDropboxError(SpaceError()))
    }

    @Test
    fun `mapDropboxError maps a name-only NetworkIOException to Network`() {
        assertEquals(SyncError.Network, mapDropboxError(NetworkIOException()))
    }

    @Test
    fun `mapDropboxError maps RateLimitException to Server`() {
        assertEquals(SyncError.Server, mapDropboxError(RateLimitException()))
    }

    @Test
    fun `mapDropboxError maps ServerException to Server`() {
        assertEquals(SyncError.Server, mapDropboxError(ServerException()))
    }

    @Test
    fun `mapDropboxError maps DbxException to Server`() {
        assertEquals(SyncError.Server, mapDropboxError(DbxException()))
    }

    @Test
    fun `mapDropboxError maps an unrecognised throwable to Unknown`() {
        assertEquals(SyncError.Unknown, mapDropboxError(SomethingNobodyMaps()))
        assertEquals(SyncError.Unknown, mapDropboxError(RuntimeException("boom")))
    }

    @Test
    fun `runOnIo rethrows CancellationException without mapping`() =
        runTest {
            val storage = ThrowingSecureStorage(CancellationException("cancelled"))

            try {
                repository(secureStorage = storage).accountLabel()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
            }

            assertEquals(1, storage.readCalls)
        }

    @Test
    fun `runOnIo keeps existing IOException mapping after retry exhaustion`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val storage = ThrowingSecureStorage(IOException("offline"))

            val result =
                async {
                    repository(secureStorage = storage, dispatcher = dispatcher).accountLabel()
                }
            advanceUntilIdle()

            val failure = result.await().exceptionOrNull()
            assertTrue(failure is SyncException)
            assertEquals(SyncError.Network, (failure as SyncException).syncError)
            assertEquals(3, storage.readCalls)
        }

    private fun repository(
        secureStorage: SecureStorage = FakeSecureStorage(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher(),
    ): DropboxRepository =
        DropboxRepository(
            secureStorage = secureStorage,
            ioDispatcher = dispatcher,
        )

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

    private class ThrowingSecureStorage(
        private val throwable: Throwable,
    ) : SecureStorage {
        var readCalls: Int = 0

        override fun read(): SecureSettings {
            readCalls++
            throw throwable
        }

        override fun writeDropboxRefreshToken(token: String?) = Unit

        override fun writeGdriveAccountEmail(email: String?) = Unit

        override fun writePinHash(hash: String?) = Unit

        override fun clearAll() = Unit
    }
}
