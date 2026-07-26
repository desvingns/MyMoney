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
import com.kshavrin.mymoney.core.sync.JournalMigrationPreview
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.toCloudProvider
import com.kshavrin.mymoney.core.sync.toSyncTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                CloudSyncState(
                    dropbox = TargetCardState(SyncTarget.Dropbox, enabled = remoteConfig.dropboxSyncEnabled()),
                    drive = TargetCardState(SyncTarget.GoogleDrive, enabled = remoteConfig.gdriveSyncEnabled()),
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

        init {
            refresh()
            observeSettings()
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
            viewModelScope.launch {
                val binding = journalSyncConfig.binding()
                val dropbox = verifiedCard(_state.value.dropbox)
                val drive = verifiedCard(_state.value.drive)
                val activeError =
                    when (binding?.provider) {
                        CloudProvider.Dropbox -> dropbox.errorRes
                        CloudProvider.GoogleDrive -> drive.errorRes
                        null -> null
                    }
                _state.value =
                    _state.value.copy(
                        binding = binding,
                        dropbox = dropbox.card,
                        drive = drive.card,
                        requiresProviderChoice = binding == null && dropbox.card.connected && drive.card.connected,
                        errorBannerRes = activeError ?: _state.value.errorBannerRes,
                    )
            }
        }

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
