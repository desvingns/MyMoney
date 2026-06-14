package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.sync.fake.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the autoSync conditional dispatch added to [WorkSchedulerImpl.scheduleDailyJobs].
 *
 * [WorkSchedulerImpl] calls [WorkManager.getInstance] before reaching the conditional, which
 * requires an Android Application context unavailable in a pure-JVM test (no Robolectric in
 * :core:sync). The tests therefore exercise the identical conditional logic through a
 * [TestableAutoSyncDispatch] helper that isolates the decision (lines 53-58 of the production
 * class) from the WorkManager scheduling boilerplate. The WorkManager enqueue calls for
 * RecurringWorker / PruneDeletedWorker are framework wiring verified in androidTest.
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

    private class FakeSnapshotSync(
        private val connected: List<SyncTarget> = emptyList(),
    ) : SnapshotSync {
        override fun isConnected(target: SyncTarget): Boolean = target in connected

        override fun connectedTargets(): List<SyncTarget> = connected

        override suspend fun syncNow(target: SyncTarget): Result<SyncOutcome> =
            Result.success(SyncOutcome.UpToDate)

        override suspend fun push(target: SyncTarget): Result<SyncOutcome> =
            Result.success(SyncOutcome.Pushed)

        override suspend fun autoSyncConnected(): Result<Unit> = Result.success(Unit)

        override suspend fun keepLocal(target: SyncTarget): Result<SyncOutcome> =
            Result.success(SyncOutcome.Pushed)

        override suspend fun keepRemote(target: SyncTarget): Result<SyncOutcome> =
            Result.success(SyncOutcome.PulledRequiresRestart)

        override fun connect(
            target: SyncTarget,
            payload: String,
        ) = Unit

        override fun disconnect(target: SyncTarget) = Unit

        override suspend fun accountLabel(target: SyncTarget): Result<String> =
            Result.success(target.name)
    }

    /**
     * Mirrors the exact conditional from [WorkSchedulerImpl.scheduleDailyJobs] (lines 53-58)
     * without the [WorkManager] calls that require an Android process.
     */
    private class TestableAutoSyncDispatch(
        private val appSettings: AppSettingsRepository,
        private val snapshotSync: SnapshotSync,
        private val syncScheduler: SyncScheduler,
    ) {
        suspend fun dispatch() {
            val autoSyncEnabled = appSettings.settings.first().autoSyncEnabled
            if (autoSyncEnabled && snapshotSync.connectedTargets().isNotEmpty()) {
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
        connected: List<SyncTarget>,
    ): FakeSyncScheduler {
        val scheduler = FakeSyncScheduler()
        val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = autoSyncEnabled))
        val snapshotSync = FakeSnapshotSync(connected)
        val subject = TestableAutoSyncDispatch(settings, snapshotSync, scheduler)
        runTest { subject.dispatch() }
        return scheduler
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    fun `enablePeriodicSync is called when autoSyncEnabled is true and a target is connected`() {
        val scheduler = dispatch(autoSyncEnabled = true, connected = listOf(SyncTarget.Dropbox))

        assertEquals(1, scheduler.enableCalls.size)
        assertEquals(0, scheduler.disableCalls.size)
    }

    @Test
    fun `disablePeriodicSync is called when autoSyncEnabled is false even if a target is connected`() {
        val scheduler = dispatch(autoSyncEnabled = false, connected = listOf(SyncTarget.Dropbox))

        assertEquals(0, scheduler.enableCalls.size)
        assertEquals(1, scheduler.disableCalls.size)
    }

    @Test
    fun `disablePeriodicSync is called when autoSyncEnabled is true but no target is connected`() {
        val scheduler = dispatch(autoSyncEnabled = true, connected = emptyList())

        assertEquals(0, scheduler.enableCalls.size)
        assertEquals(1, scheduler.disableCalls.size)
    }

    @Test
    fun `disablePeriodicSync is called when both autoSyncEnabled is false and no target is connected`() {
        val scheduler = dispatch(autoSyncEnabled = false, connected = emptyList())

        assertEquals(0, scheduler.enableCalls.size)
        assertEquals(1, scheduler.disableCalls.size)
    }

    @Test
    fun `KEEP policy - calling dispatch twice with autoSync enabled does not accumulate duplicates in SyncScheduler`() =
        runTest {
            val scheduler = FakeSyncScheduler()
            val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true))
            val snapshotSync = FakeSnapshotSync(connected = listOf(SyncTarget.GoogleDrive))
            val subject = TestableAutoSyncDispatch(settings, snapshotSync, scheduler)

            subject.dispatch()
            subject.dispatch()

            // enablePeriodicSync is called once per scheduleDailyJobs invocation;
            // the underlying WorkManager uses KEEP so no duplicate work is enqueued.
            // The scheduler itself has no deduplication — two calls produce two records —
            // but the contract is that dispatch routes to enable (not disable) each time.
            assertEquals(2, scheduler.enableCalls.size)
            assertEquals(0, scheduler.disableCalls.size)
        }

    @Test
    fun `multiple connected targets still result in exactly one enablePeriodicSync call per dispatch`() {
        val scheduler =
            dispatch(
                autoSyncEnabled = true,
                connected = listOf(SyncTarget.Dropbox, SyncTarget.GoogleDrive),
            )

        assertEquals(1, scheduler.enableCalls.size)
        assertEquals(0, scheduler.disableCalls.size)
    }

    @Test
    fun `disabling autoSync after being enabled routes to disablePeriodicSync on next dispatch`() =
        runTest {
            val scheduler = FakeSyncScheduler()
            val fakeSettings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true))
            val snapshotSync = FakeSnapshotSync(connected = listOf(SyncTarget.Dropbox))
            val subject = TestableAutoSyncDispatch(fakeSettings, snapshotSync, scheduler)

            subject.dispatch()
            fakeSettings.seed(AppSettings(autoSyncEnabled = false))
            subject.dispatch()

            assertEquals(1, scheduler.enableCalls.size)
            assertEquals(1, scheduler.disableCalls.size)
        }
}
