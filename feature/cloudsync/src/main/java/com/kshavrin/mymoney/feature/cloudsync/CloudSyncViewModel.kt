package com.kshavrin.mymoney.feature.cloudsync
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.usecase.ObserveEntitlementUseCase
import com.kshavrin.mymoney.core.sync.JournalMigrationPreview
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinator
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceBillingState
import com.kshavrin.mymoney.core.sync.toCloudProvider
import com.kshavrin.mymoney.core.sync.toSyncTarget
import com.kshavrin.mymoney.core.sync.usecase.CloudSyncBackupsUseCase
import com.kshavrin.mymoney.core.sync.usecase.CloudSyncSettingsUseCase
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel
    @Inject
    constructor(
        private val snapshotSync: SnapshotSync,
        private val journalSync: JournalSync,
        private val journalSyncConfig: JournalSyncConfigStore,
        private val syncScheduler: SyncScheduler,
        private val cloudSyncSettings: CloudSyncSettingsUseCase,
        private val cloudSyncBackups: CloudSyncBackupsUseCase,
        private val sharedCoordinator: SharedSyncCoordinator,
        private val observeEntitlement: ObserveEntitlementUseCase? = null,
        private val clock: Clock = Clock.systemUTC(),
    ) : ViewModel() {
        private val availability = cloudSyncSettings.availability()
        private val _state =
            MutableStateFlow(
                CloudSyncState(
                    dropbox = TargetCardState(SyncTarget.Dropbox, enabled = availability.dropboxEnabled),
                    drive = TargetCardState(SyncTarget.GoogleDrive, enabled = availability.gdriveEnabled),
                    shared = SharedCardState(enabled = availability.sharedEnabled),
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
        private var refreshJob: Job? = null
        private val refreshMutex = Mutex()
        private var foregroundRealtimeGeneration = 0L
        private var foregroundRealtimeActive = false
        private var foregroundRealtimeStartJob: Job? = null
        private var latestEntitlement: UserEntitlement = UserEntitlement.Free
        private var serverEntitlementRequired = false

        init {
            latestEntitlement = observeEntitlement?.entitlement?.value ?: UserEntitlement.Free
            applyEntitlement(latestEntitlement)
            refresh()
            observeSettings()
            observeEntitlement()
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
                CloudSyncEvent.WarningDismissed ->
                    _state.value = _state.value.copy(shared = _state.value.shared.copy(warning = null))
                CloudSyncEvent.WarningActionClicked -> requestPaywall()
                is CloudSyncEvent.PaywallRequested -> requestPaywall(event.entryPoint)
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
                CloudSyncEvent.SharedDisconnectClicked -> requestSharedDisconnect()
                CloudSyncEvent.SharedConfirmDisconnectKeepServerData ->
                    disconnectSharedDevice()
                CloudSyncEvent.SharedConfirmDisconnectDeleteWorkspace -> deleteSharedWorkspace()
                CloudSyncEvent.SharedLeaveClicked ->
                    _state.value =
                        _state.value.copy(
                            sharedDialog = SharedDialog.ConfirmLeave,
                        )
                CloudSyncEvent.SharedConfirmLeave -> leaveSharedWorkspace()
                CloudSyncEvent.SharedDeleteAccountClicked ->
                    _state.value = _state.value.copy(sharedDialog = SharedDialog.ConfirmDeleteAccount)
                CloudSyncEvent.SharedConfirmDeleteAccount -> deleteSharedAccount()
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
                if (cloudSyncSettings.isAutoSyncEnabled()) syncScheduler.enablePeriodicSync()
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
                    cloudSyncBackups.exportMigrationBackup(treeUriString).getOrThrow()
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
                        if (cloudSyncSettings.isAutoSyncEnabled()) syncScheduler.enablePeriodicSync()
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
            applyEntitlement(latestEntitlement)
            val generation = ++refreshGeneration
            refreshJob?.cancel()
            refreshJob =
                viewModelScope.launch {
                    refreshMutex.withLock {
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
        }

        private suspend fun refreshShared(
            active: Boolean,
            generation: Long,
        ) {
            val sharedEnabled = cloudSyncSettings.availability().sharedEnabled
            if (!sharedEnabled || !active) sharedCoordinator.stopForegroundRealtime()
            val canReadShared = sharedEnabled && active
            val workspace = if (canReadShared) sharedCoordinator.activeWorkspace() else null
            val ownership =
                if (canReadShared) {
                    sharedCoordinator.activeWorkspaceOwnership().getOrNull()
                } else {
                    null
                }
            val accessResult =
                if (canReadShared) {
                    sharedCoordinator.activeWorkspaceAccess()
                } else {
                    null
                }
            val access = accessResult?.getOrNull()
            val conflictCount =
                if (canReadShared) {
                    sharedCoordinator.listConflicts().getOrNull()?.size ?: _state.value.shared.conflictCount
                } else {
                    0
                }
            if (generation != refreshGeneration) return
            val currentBinding = journalSyncConfig.binding()
            val stillActive = currentBinding?.provider == CloudProvider.Shared
            val previousShared = _state.value.shared
            val accessFailure = accessResult?.exceptionOrNull()
            if (accessFailure.isEntitlementRequired()) markServerEntitlementRequired()
            if (access?.billingState == SharedWorkspaceBillingState.Active) {
                serverEntitlementRequired = false
            }
            val hasKnownWorkspaceAccess = access != null || previousShared.isWorkspaceAccessKnown
            val workspaceBillingState =
                access?.billingState ?: previousShared.workspaceBillingState.takeIf { previousShared.isWorkspaceAccessKnown }
            val workspaceBillingStateUntil =
                access?.billingStateUntil ?: previousShared.workspaceBillingStateUntil.takeIf { previousShared.isWorkspaceAccessKnown }
            val isWorkspaceReadOnly =
                stillActive &&
                    when {
                        access != null -> access.isReadOnly || serverEntitlementRequired
                        previousShared.isWorkspaceAccessKnown ->
                            previousShared.isWorkspaceReadOnly || serverEntitlementRequired
                        else -> true
                    }
            if (isWorkspaceReadOnly) sharedCoordinator.stopForegroundRealtime()
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
                            isWorkspaceOwner =
                                stillActive &&
                                    (ownership?.isOwner ?: previousShared.isWorkspaceOwner),
                            isSoleOwner =
                                stillActive &&
                                    (ownership?.isSoleOwner ?: previousShared.isSoleOwner),
                            workspaceBillingState = if (stillActive) workspaceBillingState else null,
                            workspaceBillingStateUntil = if (stillActive) workspaceBillingStateUntil else null,
                            isWorkspaceAccessKnown = stillActive && hasKnownWorkspaceAccess,
                            isWorkspaceReadOnly = isWorkspaceReadOnly,
                            entitlementGraceEndsAt =
                                workspaceBillingStateUntil
                                    ?.takeIf { workspaceBillingState == SharedWorkspaceBillingState.Grace }
                                    ?: latestEntitlement.graceEndsAt(),
                            warning =
                                workspaceBillingState.warning(workspaceBillingStateUntil, clock)
                                    ?: latestEntitlement.warning(clock)
                                    ?: serverEntitlementWarning(),
                            realtimeStatus =
                                if (sharedEnabled && stillActive) {
                                    _state.value.shared.realtimeStatus
                                } else {
                                    SharedRealtimeStatus.Inactive
                                },
                        ),
                )
            if (sharedEnabled && stillActive && !isWorkspaceReadOnly && foregroundRealtimeActive) {
                startForegroundRealtime()
            }
        }

        private fun observeForegroundRealtimeStatus() {
            viewModelScope.launch {
                sharedCoordinator.foregroundRealtimeStatus.collect { status ->
                    if (status == SharedRealtimeStatus.EntitlementRequired) {
                        _state.value =
                            _state.value.copy(
                                shared = _state.value.shared.copy(realtimeStatus = SharedRealtimeStatus.Inactive),
                            )
                        markServerEntitlementRequired()
                        sharedCoordinator.stopForegroundRealtime()
                        refresh()
                    } else {
                        _state.value =
                            _state.value.copy(
                                shared = _state.value.shared.copy(realtimeStatus = status),
                            )
                    }
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
            if (isSharedWorkspaceCreationBlocked()) {
                requestPaywall()
                return
            }
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
            if (_state.value.shared.isWorkspaceReadOnly) return
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
                    }
                }
            }
        }

        private fun sharedSyncNow() {
            if (_state.value.shared.isWorkspaceReadOnly) return
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
            val shared = _state.value.shared
            if (
                !foregroundRealtimeActive ||
                    !cloudSyncSettings.availability().sharedEnabled ||
                    !shared.active ||
                    !shared.isWorkspaceAccessKnown ||
                    shared.isWorkspaceReadOnly
            ) {
                return
            }
            val generation = ++foregroundRealtimeGeneration
            foregroundRealtimeStartJob?.cancel()
            foregroundRealtimeStartJob =
                viewModelScope.launch {
                    try {
                        val currentShared = _state.value.shared
                        if (
                            foregroundRealtimeActive &&
                            generation == foregroundRealtimeGeneration &&
                            journalSyncConfig.binding()?.provider == CloudProvider.Shared &&
                            currentShared.active &&
                            currentShared.isWorkspaceAccessKnown &&
                            !currentShared.isWorkspaceReadOnly
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
            if (
                !foregroundRealtimeActive ||
                    !_state.value.shared.active ||
                    !_state.value.shared.isWorkspaceAccessKnown ||
                    _state.value.shared.isWorkspaceReadOnly
            ) {
                return
            }
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
            if (_state.value.shared.isWorkspaceReadOnly) return
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

        private fun requestSharedDisconnect() {
            if (_state.value.shared.isSoleOwner) {
                _state.value = _state.value.copy(sharedDialog = SharedDialog.ConfirmDisconnect)
            } else {
                disconnectSharedDevice()
            }
        }

        private fun disconnectSharedDevice() {
            viewModelScope.launch {
                var detached = false
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null, sharedDialog = null)
                try {
                    sharedCoordinator.disconnectFromDevice().getOrThrow()
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

        private fun deleteSharedAccount() {
            viewModelScope.launch {
                var accountDeleted = false
                _state.value = _state.value.copy(isConnecting = true, errorBannerRes = null, sharedDialog = null)
                try {
                    sharedCoordinator.deleteAccount().getOrThrow()
                    accountDeleted = true
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    showError(
                        if ((t as? SyncException)?.syncError == SyncError.Conflict) {
                            R.string.sync_shared_delete_account_workspace_conflict
                        } else {
                            R.string.sync_shared_delete_account_error
                        },
                    )
                } finally {
                    _state.value = _state.value.copy(isConnecting = false)
                    if (accountDeleted) _actions.emit(CloudSyncAction.ClearSharedGoogleCredentialState)
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
                            internalBackups = cloudSyncBackups.listInternalBackups(),
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
                cloudSyncSettings.observeLastSyncAt()
                    .catch { t -> reportAndShow(t) }
                    .collect { lastSyncAt ->
                        _state.value = _state.value.copy(lastSyncAtMs = lastSyncAt)
                    }
            }
        }

        private fun observeEntitlement() {
            val useCase = observeEntitlement ?: return
            viewModelScope.launch {
                try {
                    useCase.entitlement.collect { entitlement ->
                        val changed = latestEntitlement != entitlement
                        latestEntitlement = entitlement
                        applyEntitlement(entitlement)
                        if (changed) refresh()
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                }
            }
        }

        private fun applyEntitlement(entitlement: UserEntitlement) {
            _state.value =
                _state.value.copy(
                    shared =
                        _state.value.shared.copy(
                            entitlementState = entitlement.state(),
                            entitlementGraceEndsAt =
                                _state.value.shared.workspaceBillingStateUntil
                                    ?.takeIf {
                                        _state.value.shared.workspaceBillingState == SharedWorkspaceBillingState.Grace
                                    }
                                    ?: entitlement.graceEndsAt(),
                            warning =
                                _state.value.shared.workspaceBillingState.warning(
                                    _state.value.shared.workspaceBillingStateUntil,
                                    clock,
                                ) ?: entitlement.warning(clock) ?: serverEntitlementWarning(),
                        ),
                )
        }

        private fun requestPaywall(entryPoint: PaywallEntryPoint = PaywallEntryPoint.SharedSyncGate) {
            if (_state.value.shared.active && !_state.value.shared.isWorkspaceOwner) return
            viewModelScope.launch { _actions.emit(CloudSyncAction.NavigateToPaywall(entryPoint)) }
        }

        private fun isSharedWorkspaceCreationBlocked(): Boolean = !latestEntitlement.canCreateSharedWorkspace()

        private fun serverEntitlementWarning(): EntitlementWarning? =
            EntitlementWarning.EXPIRY_IMMINENT_1D.takeIf { serverEntitlementRequired }

        private fun showEntitlementWarning() {
            markServerEntitlementRequired()
            viewModelScope.launch {
                observeEntitlement?.refresh()?.exceptionOrNull()?.reportToSentry()
            }
        }

        private fun markServerEntitlementRequired() {
            serverEntitlementRequired = true
            if (_state.value.shared.active) {
                _state.value =
                    _state.value.copy(
                        shared = _state.value.shared.copy(isWorkspaceReadOnly = true),
                    )
            }
            applyEntitlement(latestEntitlement)
        }

        private fun card(target: SyncTarget): TargetCardState =
            when (target) {
                SyncTarget.Dropbox -> _state.value.dropbox
                SyncTarget.GoogleDrive -> _state.value.drive
                SyncTarget.Shared -> error("Shared mode does not use the file-exchange provider card flow")
            }

        private fun reportAndShow(t: Throwable) {
            t.reportToSentry()
            val syncError = (t as? SyncException)?.syncError ?: SyncError.Unknown
            if (syncError == SyncError.EntitlementRequired) {
                showEntitlementWarning()
            } else {
                showError(mapError(syncError))
            }
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
                SyncError.EntitlementRequired -> R.string.sync_err_server
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

private fun UserEntitlement.state(): EntitlementState =
    when (this) {
        UserEntitlement.Free -> EntitlementState.NONE
        is UserEntitlement.Plus -> state
    }

private fun UserEntitlement.canCreateSharedWorkspace(): Boolean =
    this is UserEntitlement.Plus &&
        (state == EntitlementState.TRIAL || state == EntitlementState.ACTIVE)

private fun UserEntitlement.graceEndsAt() = (this as? UserEntitlement.Plus)?.graceEndsAt

private fun UserEntitlement.warning(clock: Clock): EntitlementWarning? =
    (this as? UserEntitlement.Plus)?.let { entitlement ->
        when {
            entitlement.state == EntitlementState.GRACE &&
                entitlement.graceEndsAt.isWithin(clock, Duration.ofDays(1)) ->
                EntitlementWarning.EXPIRY_IMMINENT_1D

            entitlement.state == EntitlementState.GRACE -> EntitlementWarning.GRACE_ENTERED
            entitlement.state == EntitlementState.TRIAL &&
                entitlement.expiresAt.isWithin(clock, Duration.ofDays(3)) ->
                EntitlementWarning.TRIAL_ENDING_3D

            else -> null
        }
    }

private fun java.time.Instant?.isWithin(
    clock: Clock,
    threshold: Duration,
): Boolean =
    this != null &&
        Duration.between(clock.instant(), this).let { remaining ->
            remaining > Duration.ZERO && remaining <= threshold
        }

private fun SharedWorkspaceBillingState?.warning(
    billingStateUntil: java.time.Instant?,
    clock: Clock,
): EntitlementWarning? =
    when (this) {
        SharedWorkspaceBillingState.Grace ->
            if (billingStateUntil.isWithin(clock, Duration.ofDays(1))) {
                EntitlementWarning.EXPIRY_IMMINENT_1D
            } else {
                EntitlementWarning.GRACE_ENTERED
            }

        SharedWorkspaceBillingState.Expired -> EntitlementWarning.EXPIRY_IMMINENT_1D
        SharedWorkspaceBillingState.Active,
        null,
        -> null
    }

private fun Throwable?.isEntitlementRequired(): Boolean =
    (this as? SyncException)?.syncError == SyncError.EntitlementRequired
