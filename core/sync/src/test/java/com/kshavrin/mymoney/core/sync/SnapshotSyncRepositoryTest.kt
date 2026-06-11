package com.kshavrin.mymoney.core.sync

import android.content.Context
import android.content.ContextWrapper
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.sync.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.core.sync.fake.FakeBackupRepository
import com.kshavrin.mymoney.core.sync.fake.FakeCloudSyncBackend
import com.kshavrin.mymoney.core.sync.fake.FakeSyncLogRepository
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Pure-JVM orchestration tests for [SnapshotSyncRepository].
 *
 * No Robolectric (no alias in the version catalog). The only Android dependency
 * is context.cacheDir, satisfied with a minimal ContextWrapper(null) override
 * pointing at a real temp dir — the same seam pattern BackupRestoreViewModelTest
 * uses for getDatabasePath. Every collaborator is a hand-written fake (project
 * rule: fakes only, no mocking framework). Sentry static calls no-op without
 * init, so the failure path runs cleanly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotSyncRepositoryTest {

    private val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()

    private val cacheDir: File = File.createTempFile("sync_cache", "").let { tmp ->
        tmp.delete()
        tmp.mkdirs()
        tmp.deleteOnExit()
        tmp
    }

    private val context: Context = object : ContextWrapper(null) {
        override fun getCacheDir(): File = this@SnapshotSyncRepositoryTest.cacheDir
    }

    private val dropbox = FakeCloudSyncBackend(SyncTarget.Dropbox)
    private val gdrive = FakeCloudSyncBackend(SyncTarget.GoogleDrive)
    private val backup = FakeBackupRepository()
    private val syncLog = FakeSyncLogRepository()
    private val settings = FakeAppSettingsRepository()

    private fun repository(
        backends: Set<CloudSyncBackend> = setOf(dropbox, gdrive),
    ): SnapshotSyncRepository = SnapshotSyncRepository(
        context = context,
        backends = backends,
        backup = backup,
        syncLog = syncLog,
        settings = settings,
        ioDispatcher = dispatcher,
    )

    private fun snapshot(name: String, modifiedAtEpochMs: Long): RemoteSnapshot =
        RemoteSnapshot(id = "/Apps/MyMoney/$name", name = name, modifiedAtEpochMs = modifiedAtEpochMs)

    // --- 1. push success -----------------------------------------------------

    @Test
    fun `push uploads pruning to three writes a success log and stamps lastSyncAt`() = runTest {
        val before = System.currentTimeMillis()

        val result = repository().push(SyncTarget.Dropbox)

        assertEquals(SyncOutcome.Pushed, result.getOrNull())
        assertEquals(1, dropbox.uploads.size)
        assertTrue(dropbox.uploads.single().remoteName.startsWith("monefy_backup_"))
        assertTrue(dropbox.uploads.single().remoteName.endsWith(".db"))
        assertEquals(listOf(3), dropbox.pruneKeeps)

        val stamp = settings.current().lastSyncAt
        assertTrue(stamp != null && stamp >= before)

        val row = syncLog.inserted.single()
        assertEquals(SyncTarget.Dropbox.name, row.target)
        assertEquals("push", row.event)
        assertEquals("success", row.status)
        assertNull(row.errorMessage)
    }

    @Test
    fun `push exports a temp file before uploading`() = runTest {
        repository().push(SyncTarget.Dropbox)

        assertEquals(1, backup.exportedPaths.size)
    }

    // --- 2. push failure -----------------------------------------------------

    @Test
    fun `push failure returns SyncException carrying the backend error`() = runTest {
        dropbox.simulateUploadFailure(SyncError.Server)

        val result = repository().push(SyncTarget.Dropbox)

        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertTrue(cause is SyncException)
        assertEquals(SyncError.Server, (cause as SyncException).syncError)
    }

    @Test
    fun `push failure writes a failure log row with the error name`() = runTest {
        dropbox.simulateUploadFailure(SyncError.Server)

        repository().push(SyncTarget.Dropbox)

        val row = syncLog.inserted.single()
        assertEquals("push", row.event)
        assertEquals("failure", row.status)
        assertEquals("Server", row.errorMessage)
    }

    @Test
    fun `push failure does not update lastSyncAt`() = runTest {
        settings.seed(AppSettings(lastSyncAt = 7_000L))
        dropbox.simulateUploadFailure(SyncError.Server)

        repository().push(SyncTarget.Dropbox)

        assertEquals(7_000L, settings.current().lastSyncAt)
    }

    @Test
    fun `push cancellation rethrows without writing failure log or changing lastSyncAt`() = runTest {
        settings.seed(AppSettings(lastSyncAt = 7_000L))
        backup.simulateExportFailure(CancellationException("cancelled"))

        try {
            repository().push(SyncTarget.Dropbox)
            fail("Expected CancellationException")
        } catch (_: CancellationException) {
        }

        assertTrue(syncLog.inserted.isEmpty())
        assertEquals(7_000L, settings.current().lastSyncAt)
        assertTrue(dropbox.pruneKeeps.isEmpty())
    }

    @Test
    fun `push failure never prunes`() = runTest {
        dropbox.simulateUploadFailure(SyncError.Server)

        repository().push(SyncTarget.Dropbox)

        assertTrue(dropbox.pruneKeeps.isEmpty())
    }

    // --- 3. syncNow conflict -------------------------------------------------

    @Test
    fun `syncNow reports a conflict when remote is newer than the sync window`() = runTest {
        settings.seed(AppSettings(lastSyncAt = 1_000L))
        dropbox.seedSnapshots(listOf(snapshot("remote", modifiedAtEpochMs = 1_000L + 60_000L + 1L)))

        val result = repository().syncNow(SyncTarget.Dropbox)

        assertEquals(
            SyncOutcome.ConflictDetected(remoteModifiedMs = 61_001L, localLastSyncMs = 1_000L),
            result.getOrNull(),
        )
    }

    @Test
    fun `syncNow conflict performs no upload, no log row and leaves lastSyncAt`() = runTest {
        settings.seed(AppSettings(lastSyncAt = 1_000L))
        dropbox.seedSnapshots(listOf(snapshot("remote", modifiedAtEpochMs = 61_001L)))

        repository().syncNow(SyncTarget.Dropbox)

        assertTrue(dropbox.uploads.isEmpty())
        assertTrue(syncLog.inserted.isEmpty())
        assertEquals(1_000L, settings.current().lastSyncAt)
    }

    // --- 4. syncNow no-conflict delegates to push ----------------------------

    @Test
    fun `syncNow pushes when remote is older than the sync window`() = runTest {
        settings.seed(AppSettings(lastSyncAt = 100_000L))
        dropbox.seedSnapshots(listOf(snapshot("remote", modifiedAtEpochMs = 50_000L)))

        val result = repository().syncNow(SyncTarget.Dropbox)

        assertEquals(SyncOutcome.Pushed, result.getOrNull())
        assertEquals(1, dropbox.uploads.size)
    }

    @Test
    fun `syncNow pushes when there is no remote snapshot`() = runTest {
        val result = repository().syncNow(SyncTarget.Dropbox)

        assertEquals(SyncOutcome.Pushed, result.getOrNull())
        assertEquals(1, dropbox.uploads.size)
    }

    // --- 5. keepLocal == push ------------------------------------------------

    @Test
    fun `keepLocal pushes and writes a push success log`() = runTest {
        val result = repository().keepLocal(SyncTarget.Dropbox)

        assertEquals(SyncOutcome.Pushed, result.getOrNull())
        assertEquals(1, dropbox.uploads.size)
        val row = syncLog.inserted.single()
        assertEquals("push", row.event)
        assertEquals("success", row.status)
    }

    // --- 6. keepRemote -------------------------------------------------------

    @Test
    fun `keepRemote with an empty remote is UpToDate without importing`() = runTest {
        settings.seed(AppSettings(lastSyncAt = 4_000L))

        val result = repository().keepRemote(SyncTarget.Dropbox)

        assertEquals(SyncOutcome.UpToDate, result.getOrNull())
        assertTrue(backup.importedPaths.isEmpty())
        assertTrue(syncLog.inserted.isEmpty())
        assertEquals(4_000L, settings.current().lastSyncAt)
    }

    @Test
    fun `keepRemote with a remote snapshot imports and pulls`() = runTest {
        dropbox.seedSnapshots(listOf(snapshot("remote", modifiedAtEpochMs = 555_000L)))

        val result = repository().keepRemote(SyncTarget.Dropbox)

        assertEquals(SyncOutcome.Pulled, result.getOrNull())
        assertEquals(1, backup.importedPaths.size)
        assertEquals(555_000L, settings.current().lastSyncAt)
        val row = syncLog.inserted.single()
        assertEquals("pull", row.event)
        assertEquals("success", row.status)
    }

    // --- 7. isConnected / connectedTargets -----------------------------------

    @Test
    fun `isConnected reflects each backend connected flag`() {
        dropbox.setConnected(true)
        gdrive.setConnected(false)
        val repo = repository()

        assertTrue(repo.isConnected(SyncTarget.Dropbox))
        assertFalse(repo.isConnected(SyncTarget.GoogleDrive))
    }

    @Test
    fun `connectedTargets lists only the connected backends`() {
        dropbox.setConnected(false)
        gdrive.setConnected(true)

        assertEquals(listOf(SyncTarget.GoogleDrive), repository().connectedTargets())
    }

    // --- 8. autoSyncConnected ------------------------------------------------

    @Test
    fun `autoSyncConnected pushes every connected backend and succeeds`() = runTest {
        dropbox.setConnected(true)
        gdrive.setConnected(true)

        val result = repository().autoSyncConnected()

        assertTrue(result.isSuccess)
        assertEquals(1, dropbox.uploads.size)
        assertEquals(1, gdrive.uploads.size)
        assertEquals(2, syncLog.inserted.count { it.status == "success" })
    }

    @Test
    fun `autoSyncConnected skips disconnected backends`() = runTest {
        dropbox.setConnected(true)
        gdrive.setConnected(false)

        repository().autoSyncConnected()

        assertEquals(1, dropbox.uploads.size)
        assertTrue(gdrive.uploads.isEmpty())
    }

    @Test
    fun `autoSyncConnected fails retryably when a backend push hits Network`() = runTest {
        dropbox.setConnected(true)
        gdrive.setConnected(true)
        dropbox.simulateUploadFailure(SyncError.Network)

        val result = repository().autoSyncConnected()

        assertTrue(result.isFailure)
        val cause = result.exceptionOrNull()
        assertTrue(cause is SyncException)
        assertEquals(SyncError.Network, (cause as SyncException).syncError)
    }

    // --- 9. sentryLevelFor (pure) --------------------------------------------

    @Test
    fun `sentryLevelFor maps each SyncError to its Sentry level`() {
        assertEquals(SentryLevel.WARNING, sentryLevelFor(SyncError.Auth))
        assertEquals(SentryLevel.WARNING, sentryLevelFor(SyncError.Quota))
        assertEquals(SentryLevel.ERROR, sentryLevelFor(SyncError.Server))
        assertEquals(SentryLevel.INFO, sentryLevelFor(SyncError.Conflict))
        assertNull(sentryLevelFor(SyncError.Network))
        assertEquals(SentryLevel.ERROR, sentryLevelFor(SyncError.Unknown))
    }
}
