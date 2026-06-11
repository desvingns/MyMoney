package com.kshavrin.mymoney.core.sync.dropbox

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.core.sync.RemoteSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.lang.reflect.InvocationTargetException
import java.net.SocketTimeoutException
import java.time.Instant
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
    fun `mapDropboxError maps DropboxQuotaException to Quota`() {
        assertEquals(SyncError.Quota, mapDropboxError(DropboxQuotaException(IOException())))
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
    fun `runOnIo rethrows CancellationException without mapping`() = runTest {
        var attempts = 0

        try {
            invokeRunOnIo(repository()) {
                attempts++
                throw CancellationException("cancelled")
            }
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
        }

        assertEquals(1, attempts)
    }

    @Test
    fun `runOnIo keeps existing IOException mapping after retry exhaustion`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        var attempts = 0

        val result = async {
            invokeRunOnIo(repository(dispatcher = dispatcher)) {
                attempts++
                throw IOException("offline")
            }
        }
        advanceUntilIdle()

        val failure = result.await().exceptionOrNull()
        assertTrue(failure is SyncException)
        assertEquals(SyncError.Network, (failure as SyncException).syncError)
        assertEquals(3, attempts)
    }

    // --- snapshotsToDelete ---------------------------------------------------

    private fun snapshot(name: String, modifiedAtEpochMs: Long): RemoteSnapshot =
        RemoteSnapshot(
            id = "/Apps/MyMoney/$name",
            name = name,
            modifiedAtEpochMs = modifiedAtEpochMs,
        )

    @Test
    fun `snapshotsToDelete keeps the 3 newest and returns the 2 oldest of 5`() {
        val s1 = snapshot("a", 1_000L)
        val s2 = snapshot("b", 2_000L)
        val s3 = snapshot("c", 3_000L)
        val s4 = snapshot("d", 4_000L)
        val s5 = snapshot("e", 5_000L)
        // Deliberately unsorted input — the seam must sort internally.
        val all = listOf(s3, s5, s1, s4, s2)

        val toDelete = snapshotsToDelete(all, keep = 3)

        // The two oldest (1_000, 2_000) are deleted; newest three are kept.
        assertEquals(listOf(s2, s1), toDelete)
    }

    @Test
    fun `snapshotsToDelete returns empty when keep equals the list size`() {
        val all = listOf(
            snapshot("a", 1_000L),
            snapshot("b", 2_000L),
            snapshot("c", 3_000L),
        )

        assertTrue(snapshotsToDelete(all, keep = 3).isEmpty())
    }

    @Test
    fun `snapshotsToDelete returns empty when keep exceeds the list size`() {
        val all = listOf(
            snapshot("a", 1_000L),
            snapshot("b", 2_000L),
        )

        assertTrue(snapshotsToDelete(all, keep = 10).isEmpty())
    }

    @Test
    fun `snapshotsToDelete returns empty for an empty input`() {
        assertTrue(snapshotsToDelete(emptyList(), keep = 3).isEmpty())
    }

    @Test
    fun `snapshotsToDelete returns everything when keep is zero`() {
        val s1 = snapshot("a", 1_000L)
        val s2 = snapshot("b", 2_000L)
        val all = listOf(s1, s2)

        // keep=0 -> nothing retained -> all returned, oldest-last by sort order.
        assertEquals(listOf(s2, s1), snapshotsToDelete(all, keep = 0))
    }

    // --- snapshotFileName ----------------------------------------------------

    @Test
    fun `snapshotFileName formats the instant as monefy_backup yyyyMMddHHmm in UTC`() {
        // 2026-05-24T07:09:30Z -> yyyyMMddHHmm = 202605240709 (UTC, seconds dropped).
        val instant = Instant.parse("2026-05-24T07:09:30Z")

        assertEquals("monefy_backup_202605240709.db", snapshotFileName(instant))
    }

    @Test
    fun `snapshotFileName uses UTC regardless of an offset-bearing instant`() {
        // 2026-01-01T00:30:00Z is still the same wall-clock minute in UTC.
        val instant = Instant.parse("2026-01-01T00:30:00Z")

        assertEquals("monefy_backup_202601010030.db", snapshotFileName(instant))
    }

    private fun repository(
        secureStorage: SecureStorage = FakeSecureStorage(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = UnconfinedTestDispatcher(),
    ): DropboxRepository = DropboxRepository(
        secureStorage = secureStorage,
        ioDispatcher = dispatcher,
    )

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> invokeRunOnIo(
        repository: DropboxRepository,
        block: () -> T,
    ): Result<T> = suspendCoroutine { continuation ->
        val method = DropboxRepository::class.java.getDeclaredMethod(
            "runOnIo",
            Function0::class.java,
            Continuation::class.java,
        )
        method.isAccessible = true
        try {
            val returned = method.invoke(repository, block, continuation)
            if (returned !== COROUTINE_SUSPENDED) {
                continuation.resume(returned as Result<T>)
            }
        } catch (error: InvocationTargetException) {
            continuation.resumeWithException(error.targetException ?: error)
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
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
