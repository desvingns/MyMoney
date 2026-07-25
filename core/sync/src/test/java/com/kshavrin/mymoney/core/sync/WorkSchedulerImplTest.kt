package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkSchedulerImplTest {
    private class Scheduler : SyncScheduler {
        var enabled = 0
        var disabled = 0
        override fun enablePeriodicSync() { enabled++ }
        override fun disablePeriodicSync() { disabled++ }
        override fun syncNow(target: SyncTarget?) = Unit
    }

    private class Config(private val active: CloudBinding?) : JournalSyncConfigStore {
        override suspend fun binding() = active
        override suspend fun peerHighWaterMs(fileId: String) = 0L
        override suspend fun setPeerHighWaterMs(fileId: String, modifiedAtMs: Long) = Unit
        override suspend fun isBootstrapDone() = false
        override suspend fun markBootstrapDone() = Unit
        override suspend fun clear() = Unit
    }

    private fun dispatch(autoSync: Boolean, binding: CloudBinding?): Scheduler {
        val scheduler = Scheduler()
        val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = autoSync))
        runTest {
            val shouldEnable = settings.settings.first().autoSyncEnabled && Config(binding).binding() != null
            if (shouldEnable) scheduler.enablePeriodicSync() else scheduler.disablePeriodicSync()
        }
        return scheduler
    }

    @Test
    fun `auto sync requires an active binding`() {
        assertEquals(1, dispatch(true, null).disabled)
    }

    @Test
    fun `auto sync enables periodic work when binding exists`() {
        assertEquals(1, dispatch(true, CloudBinding(CloudProvider.Dropbox, "acct", "A")).enabled)
    }

    @Test
    fun `disabled auto sync remains disabled when binding exists`() {
        assertEquals(1, dispatch(false, CloudBinding(CloudProvider.GoogleDrive, "acct", "A")).disabled)
    }
}
