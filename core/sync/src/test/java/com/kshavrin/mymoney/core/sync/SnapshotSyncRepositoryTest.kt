package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.sync.fake.FakeCloudSyncBackend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM orchestration tests for [SnapshotSyncRepository].
 *
 * SnapshotSyncRepository is account-connection bookkeeping only (isConnected /
 * connectedTargets / connect / disconnect / accountLabel) — actual data sync moved to
 * JournalSync (SPEC 06, operations-journal-sync epic). Every collaborator is a
 * hand-written fake (project rule: fakes only, no mocking framework).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotSyncRepositoryTest {
    private val dropbox = FakeCloudSyncBackend(SyncTarget.Dropbox)
    private val gdrive = FakeCloudSyncBackend(SyncTarget.GoogleDrive)

    private fun repository(
        backends: Set<CloudSyncBackend> = setOf(dropbox, gdrive),
    ): SnapshotSyncRepository = SnapshotSyncRepository(backends = backends)

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

    @Test
    fun `connect delegates to the target backend with the payload`() {
        repository().connect(SyncTarget.Dropbox, "token-123")

        assertEquals(listOf("token-123"), dropbox.connectCalls)
        assertTrue(gdrive.connectCalls.isEmpty())
    }

    @Test
    fun `disconnect delegates to the target backend`() {
        repository().disconnect(SyncTarget.GoogleDrive)

        assertEquals(1, gdrive.disconnectCalls)
        assertEquals(0, dropbox.disconnectCalls)
    }

    @Test
    fun `accountLabel delegates to the target backend`() =
        runTest {
            val result = repository().accountLabel(SyncTarget.GoogleDrive)

            assertEquals(SyncTarget.GoogleDrive.name, result.getOrNull())
        }

    @Test
    fun `accountLabel surfaces the backend failure`() =
        runTest {
            val error = RuntimeException("boom")
            gdrive.simulateAccountLabelFailure(error)

            val result = repository().accountLabel(SyncTarget.GoogleDrive)

            assertEquals(error, result.exceptionOrNull())
        }
}
