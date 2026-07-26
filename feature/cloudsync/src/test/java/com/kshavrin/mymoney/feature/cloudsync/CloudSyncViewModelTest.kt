package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.sync.CloudAccountIdentity
import com.kshavrin.mymoney.core.sync.JournalMigrationPreview
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeRemoteConfigRepository
import com.kshavrin.mymoney.feature.cloudsync.util.MainDispatcherRule
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CloudSyncViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun `connecting validates identity and persists one binding`() =
        runTest {
            val snapshot = SnapshotFake().apply { setConnected(SyncTarget.Dropbox, true) }
            val config = Config(null)
            val journal = RecordingJournalSync()
            val vm = viewModel(snapshot, journal, config, Scheduler())
            vm.onEvent(CloudSyncEvent.UseConnectedProviderClicked(SyncTarget.Dropbox))
            runCurrent()
            assertEquals(CloudBinding(CloudProvider.Dropbox, "dropbox-id", "dropbox@example.com"), config.binding())
            assertEquals(1, journal.syncNowCalls)
        }

    @Test
    fun `connect is blocked until active binding is disconnected`() =
        runTest {
            val config = Config(CloudBinding(CloudProvider.Dropbox, "id", "a"))
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler())
            vm.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.GoogleDrive))
            assertEquals(R.string.sync_err_disconnect_required, vm.state.value.errorBannerRes)
        }

    @Test
    fun `authentication failure and invalid identity never create binding`() =
        runTest {
            val snapshot = SnapshotFake().apply { identityResult = Result.failure(IllegalStateException("invalid")) }
            val config = Config(null)
            val vm = viewModel(snapshot, RecordingJournalSync(), config, Scheduler())
            vm.onEvent(CloudSyncEvent.AuthenticationFailed)
            vm.onEvent(CloudSyncEvent.AuthenticationCompleted(SyncTarget.Dropbox, "credential"))
            runCurrent()
            assertNull(config.binding())
        }

    @Test
    fun `disconnect failure retains binding and scheduling`() =
        runTest {
            val snapshot = SnapshotFake().apply { disconnectError = IllegalStateException("offline") }
            val config = Config(CloudBinding(CloudProvider.Dropbox, "id", "a"))
            val scheduler = Scheduler().apply { enabled = 1 }
            val vm = viewModel(snapshot, RecordingJournalSync(), config, scheduler)
            vm.onEvent(CloudSyncEvent.DisconnectClicked(SyncTarget.Dropbox))
            runCurrent()
            assertEquals(CloudProvider.Dropbox, config.binding()?.provider)
            assertEquals(1, scheduler.enabled)
            assertEquals(R.string.sync_err_disconnect_failed, vm.state.value.errorBannerRes)
        }

    @Test
    fun `migration requires backup preview and explicit resolution before commit`() =
        runTest {
            val snapshot = SnapshotFake().apply { setConnected(SyncTarget.GoogleDrive, true) }
            val config = Config(CloudBinding(CloudProvider.Dropbox, "source", "source@example.com"))
            val journal =
                RecordingJournalSync().apply {
                    previewResult = Result.success(JournalMigrationPreview(SyncTarget.GoogleDrive, emptyList(), setOf("conflict")))
                }
            val backup = BackupFake()
            val vm = viewModel(snapshot, journal, config, Scheduler(), backup)
            vm.onEvent(CloudSyncEvent.SwitchClicked(SyncTarget.GoogleDrive))
            runCurrent()
            assertTrue(vm.state.value.migration is MigrationUiState.AwaitingBackup)
            vm.onEvent(CloudSyncEvent.MigrationBackupDirectorySelected("content://backup"))
            runCurrent()
            assertEquals(1, backup.exports)
            assertTrue(vm.state.value.migration is MigrationUiState.Reviewing)
            vm.onEvent(CloudSyncEvent.ConfirmMigration(MigrationResolution.KeepLocal))
            runCurrent()
            assertEquals(MigrationResolution.KeepLocal, journal.appliedResolution)
            assertEquals(CloudProvider.GoogleDrive, config.binding()?.provider)
        }

    private fun viewModel(
        snapshot: SnapshotSync,
        journal: JournalSync,
        config: Config,
        scheduler: Scheduler,
        backup: BackupRepository = BackupFake(),
    ) = CloudSyncViewModel(snapshot, journal, config, scheduler, FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true)), backup, FakeRemoteConfigRepository())

    private class Config(
        private var current: CloudBinding?,
    ) : JournalSyncConfigStore {
        override suspend fun binding() = current

        override suspend fun setBinding(binding: CloudBinding) {
            current = binding
        }

        override suspend fun clearBinding() {
            current = null
        }

        override suspend fun peerHighWaterMs(fileId: String) = 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) = Unit

        override suspend fun isBootstrapDone() = true

        override suspend fun markBootstrapDone() = Unit

        override suspend fun clear() {
            current = null
        }
    }

    private class SnapshotFake : SnapshotSync {
        private val connected = mutableMapOf<SyncTarget, Boolean>()
        var identityResult: Result<CloudAccountIdentity> = Result.success(CloudAccountIdentity("dropbox-id", "dropbox@example.com"))
        var disconnectError: Throwable? = null

        fun setConnected(
            target: SyncTarget,
            value: Boolean,
        ) {
            connected[target] = value
        }

        override fun isConnected(target: SyncTarget) = connected[target] == true

        override fun connectedTargets() = connected.filterValues { it }.keys.toList()

        override fun connect(
            target: SyncTarget,
            payload: String,
        ) {
            connected[target] = true
        }

        override fun disconnect(target: SyncTarget) {
            disconnectError?.let { throw it }
            connected[target] = false
        }

        override suspend fun accountLabel(target: SyncTarget) = identityResult.map { it.label }

        override suspend fun accountIdentity(target: SyncTarget) = identityResult
    }

    private class RecordingJournalSync : JournalSync {
        var syncNowCalls = 0
        var previewResult: Result<JournalMigrationPreview> = Result.success(JournalMigrationPreview(SyncTarget.GoogleDrive, emptyList(), emptySet()))
        var appliedResolution: MigrationResolution? = null

        override suspend fun push() = Unit

        override suspend fun pull() = Unit

        override suspend fun syncNow() {
            syncNowCalls++
        }

        override suspend fun previewMigration(target: SyncTarget) = previewResult

        override suspend fun applyMigration(
            preview: JournalMigrationPreview,
            resolution: MigrationResolution,
        ): Result<Unit> {
            appliedResolution = resolution
            return Result.success(Unit)
        }
    }

    private class Scheduler : SyncScheduler {
        var enabled = 0

        override fun enablePeriodicSync() {
            enabled++
        }

        override fun disablePeriodicSync() {
            enabled = 0
        }

        override fun syncNow(target: SyncTarget?) = Unit
    }

    private class BackupFake : BackupRepository {
        var exports = 0

        override suspend fun exportDb(treeUriString: String): Result<Unit> {
            exports++
            return Result.success(Unit)
        }

        override suspend fun importDb(documentUriString: String) = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

        override suspend fun rotateBackups(treeUriString: String) = Result.success(Unit)

        override suspend fun exportToFile(destAbsolutePath: String) = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String) = Result.success(Unit)
    }
}
