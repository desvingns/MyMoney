package com.kshavrin.mymoney.feature.cloudsync
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.sync.JournalMigrationPreview
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinator
import com.kshavrin.mymoney.core.sync.toCloudProvider
import com.kshavrin.mymoney.core.sync.toSyncTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel
    @Inject
    constructor(
        private val snapshotSync: SnapshotSync,
        private val journalSync: JournalSync,
        private val journalSyncConfig: JournalSyncConfigStore,
        private val syncScheduler: SyncScheduler,
        private val appSettings: AppSettingsRepository,
        private val backupRepository: BackupRepository,
        private val remoteConfig: RemoteConfigRepository,
        private val sharedCoordinator: SharedSyncCoordinator,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                CloudSyncState(
                    dropbox = TargetCardState(SyncTarget.Dropbox, enabled = remoteConfig.dropboxSyncEnabled()),
                    drive = TargetCardState(SyncTarget.GoogleDrive, enabled = remoteConfig.gdriveSyncEnabled()),
                    shared = SharedCardState(enabled = remoteConfig.sharedSyncEnabled()),
                ),
            )
        val state: StateFlow<CloudSyncState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<CloudSyncAction>(
                extraBufferCapacity = 2,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<CloudSyncAction> = _actions.asSharedFlow()

        private var pendingMigration: PendingMigration? = null
        private var pendingMigrationAuthentication: SyncTarget? = null
        private var refreshGeneration = 0L
        private var foregroundRealtimeGeneration = 0L
        private var foregroundRealtimeActive = false
        private var foregroundRealtimeStartJob: Job? = null

        init {
            refresh()
            observeSettings()
            observeForegroundRealtimeStatus()
        }

        fun onEvent(event: CloudSyncEvent) {
            when (event) {
                is CloudSyncEvent.ConnectClicked -> requestConnection(event.target)
                is CloudSyncEvent.UseConnectedProviderClicked -> activateConnectedProvider(event.target)
                is CloudSyncEvent.SwitchClicked -> requestMigration(event.target)
                is CloudSyncEvent.AuthenticationCompleted -> storeCredentialAndContinue(event)
                CloudSyncEvent.AuthenticationFailed -> {
                    pendingMigrationAuthentication = null
                    _state.value = _state.value.copy(isConnecting = false)
                    showError(R.string.sync_err_auth)
                }
                is CloudSyncEvent.DisconnectClicked -> disconnect(event.target)
                is CloudSyncEvent.MigrationBackupDirectorySelected -> createMigrationBackup(event.treeUriString)
                is CloudSyncEvent.ConfirmMigration -> commitMigration(event.resolution)
                CloudSyncEvent.CancelMigration -> cancelMigration()
                CloudSyncEvent.DismissError -> _state.value = _state.value.copy(errorBannerRes = null)
                CloudSyncEvent.BackClicked -> viewModelScope.launch { _actions.emit(CloudSyncAction.NavigateBack) }
                CloudSyncEvent.SharedSignInClicked -> launchSharedSignIn()
                is CloudSyncEvent.SharedSignInCompleted -> completeSharedSignIn(event.googleIdToken, event.nonce)
                CloudSyncEvent.SharedSignInFailed -> {
                    _state.value = _state.value.copy(isConnecting = false)
                    showError(R.string.sync_err_auth)
                }
                CloudSyncEvent.SharedSetupClicked -> openSharedSetup()
                is CloudSyncEvent.SharedImportChoiceChanged ->
                    _state.value = _state.value.copy(importLocalData = event.importLocalData)
                is CloudSyncEvent.SharedCreateWorkspace -> createSharedWorkspace(event.name)
                is CloudSyncEvent.SharedJoinWorkspace -> joinSharedWorkspace(event.inviteToken)
                CloudSyncEvent.SharedConfirmRemoteWorkspaceRecovery -> recoverRemoteWorkspace()
                CloudSyncEvent.SharedCreateInviteClicked -> createSharedInvite()
                CloudSyncEvent.SharedCopyInviteClicked -> copySharedInvite()
                CloudSyncEvent.SharedSyncNowClicked -> sharedSyncNow()
                CloudSyncEvent.SharedRealtimeForegroundStarted -> foregroundRealtimeStarted()
                CloudSyncEvent.SharedRealtimeForegroundStopped -> foregroundRealtimeStopped()
                CloudSyncEvent.SharedRetryRealtimeClicked -> restartForegroundRealtime()
                CloudSyncEvent.SharedConflictsClicked -> openSharedConflicts()
                is CloudSyncEvent.SharedResolveConflict -> resolveSharedConflict(event.conflictId, event.winnerOperationId)
                CloudSyncEvent.SharedLeaveClicked ->
                    _state.value =
                        _state.value.copy(
                            sharedDialog =
                                if (_state.value.shared.isSoleOwner) {
                                    SharedDialog.ConfirmWorkspaceDeletion
                                } else {
                                    SharedDialog.ConfirmLeave
                                },
                        )
                CloudSyncEvent.SharedConfirmLeave -> leaveSharedWorkspace()
                CloudSyncEvent.SharedConfirmWorkspaceDeletion -> deleteSharedWorkspace()
                CloudSyncEvent.SharedInternalBackupsClicked -> openInternalBackups()
                is CloudSyncEvent.SharedInternalBackupRestoreClicked ->
                    requestInternalBackupRestore(event.backup)
                CloudSyncEvent.SharedConfirmInternalBackupRestore -> restoreInternalBackup()
                CloudSyncEvent.SharedDialogDismissed -> _state.value = _state.value.copy(sharedDialog = null)
            }
        }

        private fun requestConnection(target: SyncTarget) {
            if (!card(target).enabled) {
                showError(R.string.sync_not_configured)
                return
            }
            if (_state.value.binding != null) {
                showError(R.string.sync_err_disconnect_required)
                return
            }
            launchAuthentication(target)
        }

        private fun launchAuthentication(target: SyncTarget) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                _actions.emit(
                    when (target) {
                        SyncTarget.Dropbox -> CloudSyncAction.LaunchDropboxAuth
                        SyncTarget.GoogleDrive -> CloudSyncAction.LaunchGoogleDriveAuth
                        SyncTarget.Shared -> CloudSyncAction.LaunchSharedGoogleSignIn
                    },
                )
            }
        }

        private fun storeCredentialAndContinue(event: CloudSyncEvent.AuthenticationCompleted) {
            if (event.payload.isBlank()) {
                showError(R.string.sync_err_auth)
                return
            }
            viewModelScope.launch {
                try {
                    snapshotSync.connect(event.target, event.payload)
                    if (pendingMigrationAuthentication == event.target) {
                        pendingMigrationAuthentication = null
                        prepareMigration(event.target)
                    } else {
                        activateNewBinding(event.target)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    snapshotSync.disconnect(event.target)
                    pendingMigrationAuthentication = null
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    refresh()
                }
            }
        }

        private fun activateConnectedProvider(target: SyncTarget) {
            if (_state.value.binding != null) {
                showError(R.string.sync_err_disconnect_required)
                return
            }
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    activateNewBinding(target)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    refresh()
                }
            }
        }

        private suspend fun activateNewBinding(target: SyncTarget) {
            check(journalSyncConfig.binding() == null) { "Disconnect the active cloud binding first" }
            val identity = snapshotSync.accountIdentity(target).getOrThrow()
            journalSyncConfig.setBinding(
                CloudBinding(
                    provider = target.toCloudProvider(),
                    stableAccountId = identity.stableId,
                    accountLabel = identity.label,
                ),
            )
            try {
                journalSync.syncNow()
                if (appSettings.settings.first().autoSyncEnabled) syncScheduler.enablePeriodicSync()
            } catch (t: Throwable) {
                journalSyncConfig.clearBinding()
                throw t
            }
        }

        private fun requestMigration(target: SyncTarget) {
            if (!card(target).enabled) {
                showError(R.string.sync_not_configured)
                return
            }
            val active = _state.value.binding ?: return
            if (active.provider == CloudProvider.Shared) {
                showError(R.string.sync_shared_leave_first)
                return
            }
            if (active.provider == target.toCloudProvider()) {
                showError(R.string.sync_err_disconnect_required)
                return
            }
            if (snapshotSync.isConnected(target)) {
                viewModelScope.launch {
                    try {
                        prepareMigration(target)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        reportAndShow(t)
                    }
                }
            } else {
                pendingMigrationAuthentication = target
                launchAuthentication(target)
            }
        }

        private suspend fun prepareMigration(target: SyncTarget) {
            val sourceBinding = journalSyncConfig.binding() ?: return
            check(sourceBinding.provider != target.toCloudProvider()) { "Target must differ from active binding" }
            val identity = snapshotSync.accountIdentity(target).getOrThrow()
            pendingMigration =
                PendingMigration(
                    sourceBinding = sourceBinding,
                    targetBinding =
                        CloudBinding(
                            provider = target.toCloudProvider(),
                            stableAccountId = identity.stableId,
                            accountLabel = identity.label,
                        ),
                )
            _state.value =
                _state.value.copy(
                    migration = MigrationUiState.AwaitingBackup(target),
                    errorBannerRes = null,
                )
            _actions.emit(CloudSyncAction.RequestMigrationBackupDirectory)
        }

        private fun createMigrationBackup(treeUriString: String) {
            viewModelScope.launch {
                val pending = pendingMigration ?: return@launch
                try {
                    _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                    backupRepository.exportDb(treeUriString).getOrThrow()
                    val preview = journalSync.previewMigration(pending.targetBinding.provider.toSyncTarget()).getOrThrow()
                    pendingMigration = pending.copy(preview = preview)
                    _state.value =
                        _state.value.copy(
                            migration =
                                MigrationUiState.Reviewing(
                                    target = preview.target,
                                    conflictCount = preview.conflictingEntityUuids.size,
                                ),
                        )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                    cancelMigration()
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                }
            }
        }

        private fun commitMigration(resolution: MigrationResolution) {
            viewModelScope.launch {
                val pending = pendingMigration ?: return@launch
                val preview = pending.preview ?: return@launch
                try {
                    _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                    journalSync.applyMigration(preview, resolution).getOrThrow()
                    journalSyncConfig.setBinding(pending.targetBinding)
                    try {
                        journalSync.syncNow()
                        if (appSettings.settings.first().autoSyncEnabled) syncScheduler.enablePeriodicSync()
                    } catch (t: Throwable) {
                        journalSyncConfig.setBinding(pending.sourceBinding)
                        throw t
                    }
                    pendingMigration = null
                    _state.value = _state.value.copy(migration = null)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    refresh()
                }
            }
        }

        private fun cancelMigration() {
            pendingMigration = null
            pendingMigrationAuthentication = null
            _state.value = _state.value.copy(migration = null, isConnecting = false)
        }

        private fun disconnect(target: SyncTarget) {
            viewModelScope.launch {
                try {
                    snapshotSync.disconnect(target)
                    if (journalSyncConfig.binding()?.provider == target.toCloudProvider()) {
                        journalSyncConfig.clearBinding()
                        syncScheduler.disablePeriodicSync()
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    showError(R.string.sync_err_disconnect_failed)
                } finally {
                    refresh()
                }
            }
        }

        private fun refresh() {
            val generation = ++refreshGeneration
            viewModelScope.launch {
                val binding = journalSyncConfig.binding()
                val dropbox = verifiedCard(_state.value.dropbox)
                val drive = verifiedCard(_state.value.drive)
                val activeError =
                    when (binding?.provider) {
                        CloudProvider.Dropbox -> dropbox.errorRes
                        CloudProvider.GoogleDrive -> drive.errorRes
                        CloudProvider.Shared -> null
                        null -> null
                    }
                if (generation != refreshGeneration) return@launch
                _state.value =
                    _state.value.copy(
                        binding = binding,
                        dropbox = dropbox.card,
                        drive = drive.card,
                        requiresProviderChoice = binding == null && dropbox.card.connected && drive.card.connected,
                        errorBannerRes = activeError ?: _state.value.errorBannerRes,
                    )
                refreshShared(binding?.provider == CloudProvider.Shared, generation)
            }
        }

        private suspend fun refreshShared(
            active: Boolean,
            generation: Long,
        ) {
            val sharedEnabled = remoteConfig.sharedSyncEnabled()
            if (!sharedEnabled || !active) sharedCoordinator.stopForegroundRealtime()
            val canReadShared = sharedEnabled && active
            val workspace = if (canReadShared) sharedCoordinator.activeWorkspace() else null
            val ownership =
                if (canReadShared) {
                    sharedCoordinator.activeWorkspaceOwnership().getOrNull()
                } else {
                    null
                }
            val conflictCount =
                if (canReadShared) {
                    sharedCoordinator.listConflicts().getOrNull()?.size ?: _state.value.shared.conflictCount
                } else {
                    0
                }
            if (generation != refreshGeneration) return
            val currentBinding = journalSyncConfig.binding()
            val stillActive = currentBinding?.provider == CloudProvider.Shared
            if (!sharedEnabled || !stillActive) sharedCoordinator.stopForegroundRealtime()
            if (!active && stillActive) {
                refresh()
                return
            }
            _state.value =
                _state.value.copy(
                    binding = currentBinding,
                    sharedDialog =
                        if (!stillActive && _state.value.sharedDialog is SharedDialog.Invite) {
                            null
                        } else {
                            _state.value.sharedDialog
                        },
                    shared =
                        _state.value.shared.copy(
                            enabled = sharedEnabled,
                            signedIn = sharedCoordinator.isSignedIn(),
                            accountEmail = sharedCoordinator.accountEmail(),
                            active = stillActive,
                            workspaceName = if (stillActive) workspace?.name else null,
                            conflictCount = if (stillActive) conflictCount else 0,
                            isSoleOwner = stillActive && ownership?.isSoleOwner == true,
                            realtimeStatus =
                                if (sharedEnabled && stillActive) {
                                    _state.value.shared.realtimeStatus
                                } else {
                                    SharedRealtimeStatus.Inactive
                                },
                        ),
                )
        }

        private fun observeForegroundRealtimeStatus() {
            viewModelScope.launch {
                sharedCoordinator.foregroundRealtimeStatus.collect { status ->
                    _state.value =
                        _state.value.copy(
                            shared = _state.value.shared.copy(realtimeStatus = status),
                        )
                }
            }
        }

        private fun launchSharedSignIn() {
            if (_state.value.binding != null && _state.value.binding?.provider != CloudProvider.Shared) {
                showError(R.string.sync_err_disconnect_required)
                return
            }
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                _actions.emit(CloudSyncAction.LaunchSharedGoogleSignIn)
            }
        }

        private fun completeSharedSignIn(
            googleIdToken: String,
            nonce: String,
        ) {
            viewModelScope.launch {
                try {
                    sharedCoordinator.signIn(googleIdToken, nonce).getOrThrow()
                    openSharedSetupOrRemoteRecovery()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    refresh()
                }
            }
        }

        private fun openSharedSetup() {
            if (!_state.value.shared.signedIn) {
                showError(R.string.sync_shared_sign_in_required)
                return
            }
            if (_state.value.binding != null) {
                showError(R.string.sync_err_disconnect_required)
                return
            }
            openSharedSetupOrRemoteRecovery()
        }

        private fun openSharedSetupOrRemoteRecovery() {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    val remoteWorkspace = sharedCoordinator.discoverRemoteWorkspace().getOrThrow()
                    _state.value =
                        _state.value.copy(
                            sharedDialog =
                                remoteWorkspace?.let(SharedDialog::RecoverRemoteWorkspace)
                                    ?: SharedDialog.Setup,
                            importLocalData = false,
                        )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                }
            }
        }

        private fun createSharedWorkspace(name: String) {
            runSharedSetup { sharedCoordinator.createWorkspace(name.trim(), _state.value.importLocalData) }
        }

        private fun joinSharedWorkspace(inviteToken: String) {
            runSharedSetup { sharedCoordinator.joinWorkspace(inviteToken.trim(), _state.value.importLocalData) }
        }

        private fun recoverRemoteWorkspace() {
            if (_state.value.sharedDialog !is SharedDialog.RecoverRemoteWorkspace) return
            runSharedSetup { sharedCoordinator.recoverRemoteWorkspace(_state.value.importLocalData) }
        }

        private fun createSharedInvite() {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null, sharedDialog = null)
                try {
                    val invite = sharedCoordinator.createInvite().getOrThrow()
                    _state.value = _state.value.copy(sharedDialog = SharedDialog.Invite(invite.token))
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    refresh()
                }
            }
        }

        private fun copySharedInvite() {
            val invite = _state.value.sharedDialog as? SharedDialog.Invite ?: return
            viewModelScope.launch { _actions.emit(CloudSyncAction.CopySharedInvite(invite.token)) }
        }

        private fun runSharedSetup(block: suspend () -> Result<*>) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    block().getOrThrow()
                    _state.value = _state.value.copy(sharedDialog = null)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    if (sharedCoordinator.consumeRestartRequiredAfterAdoptionRecovery()) {
                        withContext(NonCancellable) {
                            _actions.emit(CloudSyncAction.RestartAfterInternalBackupRestore)
                        }
                    } else {
                        refresh()
                        startForegroundRealtime()
                    }
                }
            }
        }

        private fun sharedSyncNow() {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    sharedCoordinator.syncNow().getOrThrow()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    refresh()
                }
            }
        }

        private fun startForegroundRealtime() {
            if (!foregroundRealtimeActive || !remoteConfig.sharedSyncEnabled()) return
            val generation = ++foregroundRealtimeGeneration
            foregroundRealtimeStartJob?.cancel()
            foregroundRealtimeStartJob =
                viewModelScope.launch {
                    try {
                        if (
                            foregroundRealtimeActive &&
                            generation == foregroundRealtimeGeneration &&
                            journalSyncConfig.binding()?.provider == CloudProvider.Shared
                        ) {
                            sharedCoordinator.startForegroundRealtime()
                        }
                    } finally {
                        if (generation == foregroundRealtimeGeneration) {
                            foregroundRealtimeStartJob = null
                        }
                    }
                }
        }

        private fun foregroundRealtimeStarted() {
            foregroundRealtimeActive = true
            startForegroundRealtime()
        }

        private fun foregroundRealtimeStopped() {
            foregroundRealtimeActive = false
            foregroundRealtimeGeneration += 1
            foregroundRealtimeStartJob?.cancel()
            foregroundRealtimeStartJob = null
            sharedCoordinator.stopForegroundRealtime()
        }

        private fun restartForegroundRealtime() {
            if (!foregroundRealtimeActive) return
            foregroundRealtimeStartJob?.cancel()
            foregroundRealtimeStartJob = null
            sharedCoordinator.stopForegroundRealtime()
            startForegroundRealtime()
        }

        private fun openSharedConflicts() {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    val conflicts = sharedCoordinator.listConflicts().getOrThrow().map { it.toUi() }
                    _state.value =
                        _state.value.copy(
                            conflicts = conflicts,
                            sharedDialog = SharedDialog.Conflicts,
                            shared = _state.value.shared.copy(conflictCount = conflicts.size),
                        )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                }
            }
        }

        private fun resolveSharedConflict(
            conflictId: String,
            winnerOperationId: String,
        ) {
            viewModelScope.launch {
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    sharedCoordinator.resolveConflict(conflictId, winnerOperationId).getOrThrow()
                    val conflicts = sharedCoordinator.listConflicts().getOrThrow().map { it.toUi() }
                    _state.value =
                        _state.value.copy(
                            conflicts = conflicts,
                            sharedDialog = if (conflicts.isEmpty()) null else SharedDialog.Conflicts,
                            shared = _state.value.shared.copy(conflictCount = conflicts.size),
                        )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                }
            }
        }

        private fun leaveSharedWorkspace() {
            viewModelScope.launch {
                var detached = false
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null, sharedDialog = null)
                try {
                    sharedCoordinator.leaveWorkspace().getOrThrow()
                    detached = true
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    if (detached) _actions.emit(CloudSyncAction.ClearSharedGoogleCredentialState)
                    refresh()
                }
            }
        }

        private fun deleteSharedWorkspace() {
            viewModelScope.launch {
                var detached = false
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null, sharedDialog = null)
                try {
                    sharedCoordinator.deleteWorkspace().getOrThrow()
                    detached = true
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    reportAndShow(t)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    if (detached) _actions.emit(CloudSyncAction.ClearSharedGoogleCredentialState)
                    refresh()
                }
            }
        }

        private fun openInternalBackups() {
            viewModelScope.launch {
                if (!canAccessInternalBackups()) {
                    showError(R.string.sync_shared_restore_unavailable)
                    return@launch
                }
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    _state.value =
                        _state.value.copy(
                            internalBackups = backupRepository.listInternalBackups(),
                            sharedDialog = SharedDialog.InternalBackups,
                        )
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    showError(R.string.sync_shared_restore_backup_error)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                }
            }
        }

        private fun requestInternalBackupRestore(backup: com.kshavrin.mymoney.core.domain.model.BackupFile) {
            viewModelScope.launch {
                if (!canAccessInternalBackups()) {
                    showError(R.string.sync_shared_restore_unavailable)
                    return@launch
                }
                _state.value = _state.value.copy(sharedDialog = SharedDialog.ConfirmInternalBackupRestore(backup))
            }
        }

        private fun restoreInternalBackup() {
            val backup = (_state.value.sharedDialog as? SharedDialog.ConfirmInternalBackupRestore)?.backup ?: return
            viewModelScope.launch {
                var restoreAttempted = false
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null)
                try {
                    if (!canAccessInternalBackups()) {
                        showError(R.string.sync_shared_restore_unavailable)
                        return@launch
                    }
                    restoreAttempted = true
                    sharedCoordinator.restoreInternalBackup(backup.uriString).getOrThrow()
                    _state.value = _state.value.copy(sharedDialog = null)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    showError(R.string.sync_shared_restore_backup_error)
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    if (restoreAttempted) {
                        withContext(NonCancellable) {
                            _actions.emit(CloudSyncAction.RestartAfterInternalBackupRestore)
                        }
                    } else {
                        refresh()
                    }
                }
            }
        }

        private suspend fun canAccessInternalBackups(): Boolean =
            journalSyncConfig.binding()?.provider.let { provider ->
                provider == null || provider == CloudProvider.Shared
            }

        private fun SharedConflict.toUi(): ConflictUi =
            ConflictUi(
                conflictId = id,
                entityKind = entityKind.name,
                localOperationId = operationA.id,
                localAuthorId = authorAId,
                localSummary = operationA.payload.toConflictSummary(),
                remoteOperationId = operationB.id,
                remoteAuthorId = authorBId,
                remoteSummary = operationB.payload.toConflictSummary(),
            )

        private fun String?.toConflictSummary(): ConflictSummaryUi =
            this?.let(ConflictSummaryUi::Text) ?: ConflictSummaryUi.Deleted

        private suspend fun verifiedCard(card: TargetCardState): VerifiedCard {
            if (!snapshotSync.isConnected(card.target)) return VerifiedCard(card.copy(connected = false, accountLabel = null))
            return snapshotSync.accountIdentity(card.target).fold(
                onSuccess = { identity -> VerifiedCard(card.copy(connected = true, accountLabel = identity.label)) },
                onFailure = { error ->
                    error.reportToSentry()
                    VerifiedCard(
                        card = card.copy(connected = false, accountLabel = null),
                        errorRes = mapError((error as? SyncException)?.syncError ?: SyncError.Auth),
                    )
                },
            )
        }

        private fun observeSettings() {
            viewModelScope.launch {
                appSettings.settings
                    .catch { t -> reportAndShow(t) }
                    .collect { settings ->
                        _state.value = _state.value.copy(lastSyncAtMs = settings.lastSyncAt)
                    }
            }
        }

        private fun card(target: SyncTarget): TargetCardState =
            when (target) {
                SyncTarget.Dropbox -> _state.value.dropbox
                SyncTarget.GoogleDrive -> _state.value.drive
                SyncTarget.Shared -> error("Shared mode does not use the file-exchange provider card flow")
            }

        private fun reportAndShow(t: Throwable) {
            t.reportToSentry()
            showError(mapError((t as? SyncException)?.syncError ?: SyncError.Unknown))
        }

        private fun showError(
            @StringRes error: Int,
        ) {
            _state.value = _state.value.copy(errorBannerRes = error)
        }

        @StringRes
        private fun mapError(error: SyncError): Int =
            when (error) {
                SyncError.Network -> R.string.sync_err_network
                SyncError.Auth -> R.string.sync_err_auth
                SyncError.Quota -> R.string.sync_err_quota
                SyncError.Server -> R.string.sync_err_server
                SyncError.Conflict -> R.string.sync_err_account_mismatch
                SyncError.Unknown -> R.string.sync_err_unknown
            }

        private data class PendingMigration(
            val sourceBinding: CloudBinding,
            val targetBinding: CloudBinding,
            val preview: JournalMigrationPreview? = null,
        )

        private data class VerifiedCard(
            val card: TargetCardState,
            @StringRes val errorRes: Int? = null,
        )
    }
