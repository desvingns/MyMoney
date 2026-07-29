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
    fun `SharedSignInClicked when other provider active shows disconnect-required error`() = runTest {
        val config = Config(CloudBinding(CloudProvider.Dropbox, "id", "a"))
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler())
        vm.onEvent(CloudSyncEvent.SharedSignInClicked)
        assertEquals(R.string.sync_err_disconnect_required, vm.state.value.errorBannerRes)
    }

    @Test
    fun `SharedSignInClicked emits the shared Google sign-in action when no provider is active`() = runTest {
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())

        vm.actions.test {
            vm.onEvent(CloudSyncEvent.SharedSignInClicked)
            assertEquals(CloudSyncAction.LaunchSharedGoogleSignIn, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SharedSignInFailed clears progress and shows the authentication error`() = runTest {
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())

        vm.onEvent(CloudSyncEvent.SharedSignInClicked)
        runCurrent()
        assertTrue(vm.state.value.isConnecting)

        vm.onEvent(CloudSyncEvent.SharedSignInFailed)

        assertFalse(vm.state.value.isConnecting)
        assertEquals(R.string.sync_err_auth, vm.state.value.errorBannerRes)
    }

    @Test
    fun `SharedSetupClicked when not signed in shows sign-in-required error`() = runTest {
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())
        vm.onEvent(CloudSyncEvent.SharedSetupClicked)
        assertEquals(R.string.sync_shared_sign_in_required, vm.state.value.errorBannerRes)
    }

    @Test
    fun `SharedSignInCompleted forwards token and nonce to the coordinator`() = runTest {
        val shared = SharedCoordinator()
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

        vm.onEvent(CloudSyncEvent.SharedSignInCompleted("google-id-token", "request-nonce"))
        runCurrent()

        assertEquals("google-id-token" to "request-nonce", shared.lastSignIn)
    }

    @Test
    fun `SharedSignInCompleted maps coordinator authentication failure to an error banner`() = runTest {
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
    fun `SharedSetupClicked when signed in and no binding opens setup dialog with importLocalData false`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true }
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedSetupClicked)
        runCurrent()
        assertEquals(SharedDialog.Setup, vm.state.value.sharedDialog)
        assertFalse("no-import must be the default", vm.state.value.importLocalData)
    }

    @Test
    fun `SharedSetupClicked when binding exists shows disconnect-required error`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true }
        val config = Config(CloudBinding(CloudProvider.Dropbox, "id", "a"))
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedSetupClicked)
        assertEquals(R.string.sync_err_disconnect_required, vm.state.value.errorBannerRes)
    }

    @Test
    fun `SharedImportChoiceChanged toggles importLocalData flag`() = runTest {
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
    fun `SharedCreateWorkspace success dismisses dialog and activates shared binding`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true }
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedSetupClicked)
        vm.onEvent(CloudSyncEvent.SharedCreateWorkspace("My Budget"))
        runCurrent()
        assertNull("Dialog must dismiss on success", vm.state.value.sharedDialog)
        assertTrue(shared.createCalls > 0)
    }

    @Test
    fun `SharedJoinWorkspace trims invite token before delegating`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true }
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedSetupClicked)
        vm.onEvent(CloudSyncEvent.SharedJoinWorkspace("  invite-abc  "))
        runCurrent()
        assertEquals("invite-abc", shared.lastJoinToken)
    }

    @Test
    fun `SharedLeaveClicked shows confirm-leave dialog`() = runTest {
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler())
        vm.onEvent(CloudSyncEvent.SharedLeaveClicked)
        assertEquals(SharedDialog.ConfirmLeave, vm.state.value.sharedDialog)
    }

    @Test
    fun `SharedConfirmLeave delegates to coordinator and clears isConnecting`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true; workspaceSummary = SharedWorkspaceSummary("ws-1", "Budget") }
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedConfirmLeave)
        runCurrent()
        assertTrue(shared.leaveCalls > 0)
        assertFalse(vm.state.value.isConnecting)
    }

    @Test
    fun `SharedConfirmLeave emits credential-state cleanup after a successful leave`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true }
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), Config(null), Scheduler(), shared = shared)

        vm.actions.test {
            vm.onEvent(CloudSyncEvent.SharedConfirmLeave)
            assertEquals(CloudSyncAction.ClearSharedGoogleCredentialState, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SharedConflictsClicked populates conflict list and opens conflicts dialog`() = runTest {
        val shared = SharedCoordinator().apply {
            signedIn = true
            conflicts = listOf(fakeConflict("c-1"))
        }
        val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedConflictsClicked)
        runCurrent()
        assertEquals(SharedDialog.Conflicts, vm.state.value.sharedDialog)
        assertEquals(1, vm.state.value.conflicts.size)
        assertEquals("c-1", vm.state.value.conflicts.first().conflictId)
    }

    @Test
    fun `SharedResolveConflict delegates to coordinator and refreshes conflict list`() = runTest {
        val shared = SharedCoordinator().apply { signedIn = true }
        val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler(), shared = shared)
        vm.onEvent(CloudSyncEvent.SharedResolveConflict("c-1", "op-winner"))
        runCurrent()
        assertEquals("c-1" to "op-winner", shared.lastResolve)
        assertFalse(vm.state.value.isConnecting)
    }

    @Test
    fun `SwitchClicked from Shared-active binding shows leave-first error`() = runTest {
        val config = Config(CloudBinding(CloudProvider.Shared, "ws-1", "Budget"))
        val vm = viewModel(SnapshotFake(), RecordingJournalSync(), config, Scheduler())
        vm.onEvent(CloudSyncEvent.SwitchClicked(SyncTarget.Dropbox))
        assertEquals(R.string.sync_shared_leave_first, vm.state.value.errorBannerRes)
    }

    private fun viewModel(
        snapshot: SnapshotSync,
        journal: JournalSync,
        config: Config,
        scheduler: Scheduler,
        backup: BackupRepository = BackupFake(),
        shared: SharedSyncCoordinator = SharedCoordinator(),
    ) = CloudSyncViewModel(snapshot, journal, config, scheduler, FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true)), backup, FakeRemoteConfigRepository(), shared)

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

        override suspend fun createInternalBackup() = Result.success("/internal/backup.db")
    }

    private inner class SharedCoordinator : SharedSyncCoordinator {
        var signedIn = false
        var workspaceSummary: SharedWorkspaceSummary? = null
        var conflicts: List<SharedConflict> = emptyList()
        var createCalls = 0
        var leaveCalls = 0
        var lastJoinToken: String? = null
        var createInviteResult: Result<SharedWorkspaceInvite> = Result.failure(RuntimeException("unused"))
        var lastResolve: Pair<String, String>? = null
        var lastSignIn: Pair<String, String>? = null
        var signInResult: Result<Unit> = Result.success(Unit)

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
        override suspend fun activeWorkspace() = workspaceSummary
        override suspend fun createWorkspace(name: String, importLocalData: Boolean): Result<SharedWorkspaceSummary> {
            createCalls++
            return Result.success(SharedWorkspaceSummary("ws-new", name))
        }
        override suspend fun joinWorkspace(inviteToken: String, importLocalData: Boolean): Result<SharedWorkspaceSummary> {
            lastJoinToken = inviteToken
            return Result.success(SharedWorkspaceSummary("ws-joined", "Joined"))
        }
        override suspend fun createInvite() = createInviteResult
        override suspend fun syncNow() = Result.success(Unit)
        override suspend fun listConflicts(): Result<List<SharedConflict>> = Result.success(conflicts)
        override suspend fun resolveConflict(conflictId: String, winnerOperationId: String): Result<Unit> {
            lastResolve = conflictId to winnerOperationId
            conflicts = emptyList()
            return Result.success(Unit)
        }
        override suspend fun leaveWorkspace(): Result<Unit> {
            leaveCalls++
            return Result.success(Unit)
        }
    }

    private fun fakeConflict(id: String): SharedConflict {
        val now = java.time.Instant.ofEpochMilli(1_700_000_000_000L)
        val op = com.kshavrin.mymoney.core.domain.sync.SharedOperation(
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
