package com.kshavrin.mymoney.feature.cloudsync

import app.cash.turbine.test
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.sync.CloudAccountIdentity
import com.kshavrin.mymoney.core.sync.JournalMigrationPreview
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinator
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceInvite
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceSummary
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeRemoteConfigRepository
import com.kshavrin.mymoney.feature.cloudsync.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ── Shared mode state machine ──────────────────────────────────────────

    @Test
    fun `SharedSignInClicked when other provider active shows disconnect-required error`() =
        runTest {
            val config = Config(CloudBinding(CloudProvider.Dropbox, "id", "a"))
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler())
            vm.onEvent(CloudSyncEvent.SharedSignInClicked)
            assertEquals(R.string.sync_err_disconnect_required, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedSignInClicked emits the shared Google sign-in action when no provider is active`() =
        runTest {
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())

            vm.actions.test {
                vm.onEvent(CloudSyncEvent.SharedSignInClicked)
                assertEquals(CloudSyncAction.LaunchSharedGoogleSignIn, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SharedSignInFailed clears progress and shows the authentication error`() =
        runTest {
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())

            vm.onEvent(CloudSyncEvent.SharedSignInClicked)
            runCurrent()
            assertTrue(vm.state.value.isConnecting)

            vm.onEvent(CloudSyncEvent.SharedSignInFailed)

            assertFalse(vm.state.value.isConnecting)
            assertEquals(R.string.sync_err_auth, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedSetupClicked when not signed in shows sign-in-required error`() =
        runTest {
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())
            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            assertEquals(R.string.sync_shared_sign_in_required, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedSignInCompleted forwards token and nonce to the coordinator`() =
        runTest {
            val shared = SharedCoordinator()
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedSignInCompleted("google-id-token", "request-nonce"))
            runCurrent()

            assertEquals("google-id-token" to "request-nonce", shared.lastSignIn)
        }

    @Test
    fun `SharedSignInCompleted maps coordinator authentication failure to an error banner`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signInResult = Result.failure(SyncException(SyncError.Auth))
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedSignInCompleted("google-id-token", "request-nonce"))
            runCurrent()

            assertFalse(vm.state.value.isConnecting)
            assertEquals(R.string.sync_err_auth, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedSetupClicked when signed in and no binding opens setup dialog with importLocalData false`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            runCurrent()
            assertEquals(SharedDialog.Setup, vm.state.value.sharedDialog)
            assertFalse("no-import must be the default", vm.state.value.importLocalData)
        }

    @Test
    fun `SharedSetupClicked discovers remote workspace before offering create or join`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    remoteWorkspace = SharedWorkspaceSummary("ws-remote", "Family budget")
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            runCurrent()

            assertEquals(
                SharedDialog.RecoverRemoteWorkspace(SharedWorkspaceSummary("ws-remote", "Family budget")),
                vm.state.value.sharedDialog,
            )
            assertFalse(vm.state.value.importLocalData)
            assertEquals(1, shared.discoverRemoteWorkspaceCalls)
            assertEquals(0, shared.recoverRemoteWorkspaceCalls)
        }

    @Test
    fun `remote workspace recovery adopts only after explicit confirmation with selected import choice`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    remoteWorkspace = SharedWorkspaceSummary("ws-remote", "Family budget")
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            runCurrent()
            vm.onEvent(CloudSyncEvent.SharedImportChoiceChanged(true))
            vm.onEvent(CloudSyncEvent.SharedConfirmRemoteWorkspaceRecovery)
            runCurrent()

            assertEquals(1, shared.recoverRemoteWorkspaceCalls)
            assertTrue(shared.lastRecoveryImportLocalData)
            assertNull(vm.state.value.sharedDialog)
        }

    @Test
    fun `SharedSetupClicked when binding exists shows disconnect-required error`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val config = Config(CloudBinding(CloudProvider.Dropbox, "id", "a"))
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            assertEquals(R.string.sync_err_disconnect_required, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedImportChoiceChanged toggles importLocalData flag`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            assertFalse(vm.state.value.importLocalData)
            vm.onEvent(CloudSyncEvent.SharedImportChoiceChanged(true))
            assertTrue(vm.state.value.importLocalData)
            vm.onEvent(CloudSyncEvent.SharedImportChoiceChanged(false))
            assertFalse(vm.state.value.importLocalData)
        }

    @Test
    fun `SharedCreateWorkspace success dismisses dialog and activates shared binding`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            vm.onEvent(CloudSyncEvent.SharedCreateWorkspace("My Budget"))
            runCurrent()
            assertNull("Dialog must dismiss on success", vm.state.value.sharedDialog)
            assertTrue(shared.createCalls > 0)
        }

    @Test
    fun `SharedJoinWorkspace trims invite token before delegating`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            vm.onEvent(CloudSyncEvent.SharedJoinWorkspace("  invite-abc  "))
            runCurrent()
            assertEquals("invite-abc", shared.lastJoinToken)
        }

    @Test
    fun `SharedCreateInviteClicked shows the one-time code returned by the coordinator`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    createInviteResult = Result.success(SharedWorkspaceInvite("invite-token"))
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget")), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedCreateInviteClicked)
            runCurrent()

            assertEquals(SharedDialog.Invite("invite-token"), vm.state.value.sharedDialog)
            assertFalse(vm.state.value.isConnecting)
        }

    @Test
    fun `SharedCreateInviteClicked failure leaves no code in the state and shows an error`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    createInviteResult = Result.failure(SyncException(SyncError.Network))
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget")), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedCreateInviteClicked)
            runCurrent()

            assertNull(vm.state.value.sharedDialog)
            assertFalse(vm.state.value.isConnecting)
            assertEquals(R.string.sync_err_network, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedCopyInviteClicked emits the token only after an invite is displayed`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    createInviteResult = Result.success(SharedWorkspaceInvite("invite-token"))
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget")), Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedCreateInviteClicked)
            runCurrent()

            vm.actions.test {
                vm.onEvent(CloudSyncEvent.SharedCopyInviteClicked)
                assertEquals(CloudSyncAction.CopySharedInvite("invite-token"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            vm.onEvent(CloudSyncEvent.SharedDialogDismissed)
            assertNull(vm.state.value.sharedDialog)
        }

    @Test
    fun `invite failure refreshes cleared membership and removes stale invite state`() =
        runTest {
            val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    workspaceSummary = SharedWorkspaceSummary("ws-1", "Budget")
                    createInviteResult = Result.success(SharedWorkspaceInvite("invite-token"))
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedCreateInviteClicked)
            runCurrent()
            assertEquals(SharedDialog.Invite("invite-token"), vm.state.value.sharedDialog)

            shared.createInviteResult = Result.failure(SyncException(SyncError.Auth))
            shared.onCreateInvite = {
                shared.signedIn = false
                shared.workspaceSummary = null
                config.clearForTest()
            }

            vm.onEvent(CloudSyncEvent.SharedCreateInviteClicked)
            runCurrent()

            assertNull(vm.state.value.sharedDialog)
            assertFalse(vm.state.value.shared.signedIn)
            assertFalse(vm.state.value.shared.active)
            assertNull(vm.state.value.shared.accountEmail)
            assertNull(vm.state.value.shared.workspaceName)
            assertNull(config.binding())
            assertEquals(R.string.sync_err_auth, vm.state.value.errorBannerRes)
        }

    @Test
    fun `SharedLeaveClicked shows confirm-leave dialog`() =
        runTest {
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())
            vm.onEvent(CloudSyncEvent.SharedLeaveClicked)
            assertEquals(SharedDialog.ConfirmLeave, vm.state.value.sharedDialog)
        }

    @Test
    fun `SharedConfirmLeave delegates to coordinator and clears isConnecting`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    workspaceSummary = SharedWorkspaceSummary("ws-1", "Budget")
                }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedConfirmLeave)
            runCurrent()
            assertTrue(shared.leaveCalls > 0)
            assertFalse(vm.state.value.isConnecting)
        }

    @Test
    fun `SharedConfirmLeave emits credential-state cleanup after a successful leave`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

            vm.actions.test {
                vm.onEvent(CloudSyncEvent.SharedConfirmLeave)
                assertEquals(CloudSyncAction.ClearSharedGoogleCredentialState, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SharedInternalBackupsClicked loads backups and opens the recovery dialog`() =
        runTest {
            val backup = BackupFile("shared-1.db", "/internal/shared-1.db", 1_700_000_000_000L)
            val backups = BackupFake().apply { internalBackups = listOf(backup) }
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), backup = backups)

            vm.onEvent(CloudSyncEvent.SharedInternalBackupsClicked)
            runCurrent()

            assertEquals(listOf(backup), vm.state.value.internalBackups)
            assertEquals(SharedDialog.InternalBackups, vm.state.value.sharedDialog)
            assertFalse(vm.state.value.isConnecting)
        }

    @Test
    fun `SharedInternalBackupRestoreClicked confirms and restores through the shared coordinator`() =
        runTest {
            val backup = BackupFile("shared-1.db", "/internal/shared-1.db", 1_700_000_000_000L)
            val shared = SharedCoordinator()
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

            vm.onEvent(CloudSyncEvent.SharedInternalBackupRestoreClicked(backup))
            assertEquals(SharedDialog.ConfirmInternalBackupRestore(backup), vm.state.value.sharedDialog)

            vm.actions.test {
                vm.onEvent(CloudSyncEvent.SharedConfirmInternalBackupRestore)
                assertEquals(CloudSyncAction.RestartAfterInternalBackupRestore, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            runCurrent()

            assertEquals(listOf(backup.uriString), shared.restorePaths)
            assertNull(vm.state.value.sharedDialog)
            assertFalse(vm.state.value.isConnecting)
        }

    @Test
    fun `SharedConflictsClicked populates conflict list and opens conflicts dialog`() =
        runTest {
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    conflicts = listOf(fakeConflict("c-1"))
                }
            val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedConflictsClicked)
            runCurrent()
            assertEquals(SharedDialog.Conflicts, vm.state.value.sharedDialog)
            assertEquals(1, vm.state.value.conflicts.size)
            assertEquals(
                "c-1",
                vm.state.value.conflicts
                    .first()
                    .conflictId,
            )
        }

    @Test
    fun `SharedResolveConflict delegates to coordinator and refreshes conflict list`() =
        runTest {
            val shared = SharedCoordinator().apply { signedIn = true }
            val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)
            vm.onEvent(CloudSyncEvent.SharedResolveConflict("c-1", "op-winner"))
            runCurrent()
            assertEquals("c-1" to "op-winner", shared.lastResolve)
            assertFalse(vm.state.value.isConnecting)
        }

    @Test
    fun `SwitchClicked from Shared-active binding shows leave-first error`() =
        runTest {
            val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
            val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler())
            vm.onEvent(CloudSyncEvent.SwitchClicked(SyncTarget.Dropbox))
            assertEquals(R.string.sync_shared_leave_first, vm.state.value.errorBannerRes)
        }

    @Test
    fun `disabled Shared gate does not perform remote reads for a persisted Shared binding`() =
        runTest {
            val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    workspaceSummary = SharedWorkspaceSummary("ws-1", "Budget")
                }
            val remoteConfig = FakeRemoteConfigRepository(sharedEnabled = false)
            val vm =
                viewModel(
                    SnapshotFake(),
                    RecordingJournalSync(),
                    config,
                    Scheduler(),
                    shared = shared,
                    remoteConfig = remoteConfig,
                )

            runCurrent()

            assertFalse(vm.state.value.shared.enabled)
            assertEquals(0, shared.activeWorkspaceCalls)
            assertEquals(0, shared.activeWorkspaceOwnershipCalls)
            assertEquals(0, shared.listConflictsCalls)
            assertTrue(shared.stopRealtimeCalls > 0)
        }

    @Test
    fun `foreground realtime cannot start after lifecycle stop while shared setup is finishing`() =
        runTest {
            val config = Config(null)
            val shared =
                SharedCoordinator().apply {
                    signedIn = true
                    createGate = CompletableDeferred()
                }
            shared.onCreateWorkspace = { config.setForTest(CloudBinding(CloudProvider.Shared, "ws-new", "Budget")) }
            val remoteConfig = FakeRemoteConfigRepository(sharedEnabled = true)
            val vm =
                viewModel(
                    SnapshotFake(),
                    RecordingJournalSync(),
                    config,
                    Scheduler(),
                    shared = shared,
                    remoteConfig = remoteConfig,
                )

            vm.onEvent(CloudSyncEvent.SharedSetupClicked)
            runCurrent()
            vm.onEvent(CloudSyncEvent.SharedCreateWorkspace("Budget"))
            runCurrent()
            vm.onEvent(CloudSyncEvent.SharedRealtimeForegroundStopped)
            runCurrent()

            shared.createGate?.complete(Unit)
            runCurrent()

            assertEquals(0, shared.startRealtimeCalls)
        }

    private fun viewModel(
        snapshot: SnapshotSync,
        journal: JournalSync,
        config: Config,
        scheduler: Scheduler,
        backup: BackupRepository = BackupFake(),
        shared: SharedSyncCoordinator = SharedCoordinator(),
        remoteConfig: FakeRemoteConfigRepository = FakeRemoteConfigRepository(),
    ) = CloudSyncViewModel(snapshot, journal, config, scheduler, FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true)), backup, remoteConfig, shared)

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

        fun clearForTest() {
            current = null
        }

        fun setForTest(binding: CloudBinding) {
            current = binding
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
        var internalBackups: List<BackupFile> = emptyList()

        override suspend fun exportDb(treeUriString: String): Result<Unit> {
            exports++
            return Result.success(Unit)
        }

        override suspend fun importDb(documentUriString: String) = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

        override suspend fun rotateBackups(treeUriString: String) = Result.success(Unit)

        override suspend fun exportToFile(destAbsolutePath: String) = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String) = Result.success(Unit)

        override suspend fun createInternalBackup() = Result.success("/internal/backup.db")

        override suspend fun listInternalBackups(): List<BackupFile> = internalBackups
    }

    private inner class SharedCoordinator : SharedSyncCoordinator {
        var signedIn = false
        var workspaceSummary: SharedWorkspaceSummary? = null
        var conflicts: List<SharedConflict> = emptyList()
        var createCalls = 0
        var createGate: CompletableDeferred<Unit>? = null
        var onCreateWorkspace: (() -> Unit)? = null
        var leaveCalls = 0
        var startRealtimeCalls = 0
        var stopRealtimeCalls = 0
        var activeWorkspaceCalls = 0
        var activeWorkspaceOwnershipCalls = 0
        var listConflictsCalls = 0
        var lastJoinToken: String? = null
        var createInviteResult: Result<SharedWorkspaceInvite> = Result.failure(RuntimeException("unused"))
        var onCreateInvite: (() -> Unit)? = null
        var lastResolve: Pair<String, String>? = null
        var lastSignIn: Pair<String, String>? = null
        var signInResult: Result<Unit> = Result.success(Unit)
        var restorePaths: MutableList<String> = mutableListOf()
        var restoreResult: Result<Unit> = Result.success(Unit)
        var remoteWorkspace: SharedWorkspaceSummary? = null
        var discoverRemoteWorkspaceCalls = 0
        var recoverRemoteWorkspaceCalls = 0
        var lastRecoveryImportLocalData = false

        override fun isSignedIn() = signedIn

        override fun accountEmail(): String? = if (signedIn) "user@example.com" else null

        override suspend fun signIn(
            googleIdToken: String,
            nonce: String,
        ): Result<Unit> {
            lastSignIn = googleIdToken to nonce
            return signInResult
        }

        override suspend fun signOut() = Result.success(Unit)

        override suspend fun activeWorkspace(): SharedWorkspaceSummary? {
            activeWorkspaceCalls++
            return workspaceSummary
        }

        override suspend fun activeWorkspaceOwnership(): Result<com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceOwnership> {
            activeWorkspaceOwnershipCalls++
            return Result.success(com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceOwnership())
        }

        override suspend fun discoverRemoteWorkspace(): Result<SharedWorkspaceSummary?> {
            discoverRemoteWorkspaceCalls++
            return Result.success(remoteWorkspace)
        }

        override suspend fun recoverRemoteWorkspace(importLocalData: Boolean): Result<SharedWorkspaceSummary> {
            recoverRemoteWorkspaceCalls++
            lastRecoveryImportLocalData = importLocalData
            return Result.success(remoteWorkspace ?: SharedWorkspaceSummary("ws-recovered", "Recovered"))
        }

        override suspend fun createWorkspace(
            name: String,
            importLocalData: Boolean,
        ): Result<SharedWorkspaceSummary> {
            createCalls++
            createGate?.await()
            onCreateWorkspace?.invoke()
            return Result.success(SharedWorkspaceSummary("ws-new", name))
        }

        override suspend fun joinWorkspace(
            inviteToken: String,
            importLocalData: Boolean,
        ): Result<SharedWorkspaceSummary> {
            lastJoinToken = inviteToken
            return Result.success(SharedWorkspaceSummary("ws-joined", "Joined"))
        }

        override suspend fun createInvite(): Result<SharedWorkspaceInvite> {
            onCreateInvite?.invoke()
            return createInviteResult
        }

        override suspend fun syncNow() = Result.success(Unit)

        override suspend fun startForegroundRealtime(): Result<Unit> {
            startRealtimeCalls++
            return Result.success(Unit)
        }

        override fun stopForegroundRealtime() {
            stopRealtimeCalls++
        }

        override suspend fun listConflicts(): Result<List<SharedConflict>> {
            listConflictsCalls++
            return Result.success(conflicts)
        }

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<Unit> {
            lastResolve = conflictId to winnerOperationId
            conflicts = emptyList()
            return Result.success(Unit)
        }

        override suspend fun restoreInternalBackup(backupPath: String): Result<Unit> {
            restorePaths += backupPath
            return restoreResult
        }

        override suspend fun leaveWorkspace(): Result<Unit> {
            leaveCalls++
            return Result.success(Unit)
        }
    }

    private fun fakeConflict(id: String): SharedConflict {
        val now = java.time.Instant.ofEpochMilli(1_700_000_000_000L)
        val op =
            com.kshavrin.mymoney.core.domain.sync.SharedOperation(
                id = "op-$id",
                workspaceId = "ws-1",
                idempotencyKey = "key",
                serverSequence = 1L,
                baseSequence = 0L,
                deviceId = "device",
                entityKind = com.kshavrin.mymoney.core.domain.sync.EntityKind.Account,
                entityId = "e-uuid",
                payload = "{}",
                tombstone = false,
                createdAt = now,
            )
        return SharedConflict(
            id = id,
            workspaceId = "ws-1",
            entityKind = com.kshavrin.mymoney.core.domain.sync.EntityKind.Account,
            entityId = "e-uuid",
            operationA = op,
            operationB = op.copy(id = "op-b"),
            authorAId = "user-a",
            authorBId = "user-b",
            status = com.kshavrin.mymoney.core.domain.sync.ConflictStatus.Pending,
            resolverId = null,
            resolvedIntoId = null,
            createdAt = now,
            resolvedAt = null,
        )
    }
}
