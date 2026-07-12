package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the autoSync conditional dispatch in [WorkSchedulerImpl.scheduleDailyJobs].
 *
 * [WorkSchedulerImpl] calls [WorkManager.getInstance] before reaching the conditional, which
 * requires an Android Application context unavailable in a pure-JVM test (no Robolectric in
 * :core:sync). The tests therefore exercise the identical conditional logic through a
 * [TestableAutoSyncDispatch] helper that isolates the decision from the WorkManager boilerplate.
 *
 * The condition was updated in SPEC 06: instead of checking snapshotSync.connectedTargets()
 * (SnapshotSync-era), the gate is now journalSyncConfig.folderId().isNotBlank() — a folder must be
 * configured for the journal sync to have somewhere to write.
 */
class WorkSchedulerImplTest {
    // -------------------------------------------------------------------------
    // Inner fakes
    // -------------------------------------------------------------------------

    private class FakeSyncScheduler : SyncScheduler {
        val enableCalls: MutableList<Unit> = mutableListOf()
        val disableCalls: MutableList<Unit> = mutableListOf()
        val syncNowCalls: MutableList<SyncTarget?> = mutableListOf()

        override fun enablePeriodicSync() {
            enableCalls += Unit
        }

        override fun disablePeriodicSync() {
            disableCalls += Unit
        }

        override fun syncNow(target: SyncTarget?) {
            syncNowCalls += target
        }
    }

    private class FakeJournalSyncConfigStore(
        private val folderId: String,
    ) : JournalSyncConfigStore {
        override suspend fun folderId(): String = folderId

        override suspend fun setFolderId(folderId: String) = Unit

        override suspend fun peerHighWaterMs(fileId: String): Long = 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) = Unit

        override suspend fun isBootstrapDone(): Boolean = false

        override suspend fun markBootstrapDone() = Unit
    }

    /**
     * Mirrors the exact conditional from [WorkSchedulerImpl.scheduleDailyJobs] (lines 54-58)
     * without the [WorkManager] calls that require an Android process.
     */
    private class TestableAutoSyncDispatch(
        private val appSettings: AppSettingsRepository,
        private val journalSyncConfig: JournalSyncConfigStore,
        private val syncScheduler: SyncScheduler,
    ) {
        suspend fun dispatch() {
            val autoSyncEnabled = appSettings.settings.first().autoSyncEnabled
            if (autoSyncEnabled && journalSyncConfig.folderId().isNotBlank()) {
                syncScheduler.enablePeriodicSync()
            } else {
                syncScheduler.disablePeriodicSync()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun dispatch(
        autoSyncEnabled: Boolean,
        folderId: String,
    ): FakeSyncScheduler {
        val scheduler = FakeSyncScheduler()
        val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = autoSyncEnabled))
        val configStore = FakeJournalSyncConfigStore(folderId)
        val subject = TestableAutoSyncDispatch(settings, configStore, scheduler)
        runTest { subject.dispatch() }
        return scheduler
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `enablePeriodicSync is called when autoSyncEnabled is true and folderId is set`() {
        val scheduler = dispatch(autoSyncEnabled = true, folderId = "drive-folder-abc")

        assertEquals(1, scheduler.enableCalls.size)
        assertEquals(0, scheduler.disableCalls.size)
    }

    @Test
    fun `disablePeriodicSync is called when autoSyncEnabled is false even if folderId is set`() {
        val scheduler = dispatch(autoSyncEnabled = false, folderId = "drive-folder-abc")

        assertEquals(0, scheduler.enableCalls.size)
        assertEquals(1, scheduler.disableCalls.size)
    }

    @Test
    fun `disablePeriodicSync is called when autoSyncEnabled is true but folderId is blank`() {
        val scheduler = dispatch(autoSyncEnabled = true, folderId = "")

        assertEquals(0, scheduler.enableCalls.size)
        assertEquals(1, scheduler.disableCalls.size)
    }

    @Test
    fun `disablePeriodicSync is called when both autoSyncEnabled is false and folderId is blank`() {
        val scheduler = dispatch(autoSyncEnabled = false, folderId = "")

        assertEquals(0, scheduler.enableCalls.size)
        assertEquals(1, scheduler.disableCalls.size)
    }

    @Test
    fun `KEEP policy - calling dispatch twice with autoSync enabled and folder set does not drop enable calls`() =
        runTest {
            val scheduler = FakeSyncScheduler()
            val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true))
            val configStore = FakeJournalSyncConfigStore("drive-folder-xyz")
            val subject = TestableAutoSyncDispatch(settings, configStore, scheduler)

            subject.dispatch()
            subject.dispatch()

            // enablePeriodicSync is called once per scheduleDailyJobs invocation;
            // the underlying WorkManager uses KEEP so no duplicate work is enqueued.
            assertEquals(2, scheduler.enableCalls.size)
            assertEquals(0, scheduler.disableCalls.size)
        }

    @Test
    fun `disabling autoSync after being enabled routes to disablePeriodicSync on next dispatch`() =
        runTest {
            val scheduler = FakeSyncScheduler()
            val fakeSettings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true))
            val configStore = FakeJournalSyncConfigStore("drive-folder-123")
            val subject = TestableAutoSyncDispatch(fakeSettings, configStore, scheduler)

            subject.dispatch()
            fakeSettings.seed(AppSettings(autoSyncEnabled = false))
            subject.dispatch()

            assertEquals(1, scheduler.enableCalls.size)
            assertEquals(1, scheduler.disableCalls.size)
        }
}
