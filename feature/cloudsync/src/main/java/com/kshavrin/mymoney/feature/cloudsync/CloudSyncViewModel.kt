package com.kshavrin.mymoney.feature.cloudsync

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.RemoteJournalFile
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncTarget
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlin.coroutines.cancellation.CancellationException

@HiltViewModel
class CloudSyncViewModel
    @Inject
    constructor(
        private val snapshotSync: SnapshotSync,
        private val journalSync: JournalSync,
        private val journalBackend: JournalBackend,
        private val journalSyncConfig: JournalSyncConfigStore,
        private val syncScheduler: SyncScheduler,
        private val appSettings: AppSettingsRepository,
        private val remoteConfig: RemoteConfigRepository,
        private val deviceIdProvider: DeviceIdProvider,
    ) : ViewModel() {
        private val _state =
            MutableStateFlow(
                CloudSyncState(
                    dropbox =
                        TargetCardState(
                            target = SyncTarget.Dropbox,
                            enabled = remoteConfig.dropboxSyncEnabled(),
                        ),
                    drive =
                        TargetCardState(
                            target = SyncTarget.GoogleDrive,
                            enabled = remoteConfig.gdriveSyncEnabled(),
                        ),
                ),
            )
        val state: StateFlow<CloudSyncState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<CloudSyncAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<CloudSyncAction> = _actions.asSharedFlow()

        init {
            refresh(SyncTarget.Dropbox)
            refresh(SyncTarget.GoogleDrive)
            observeSettings()
            refreshJournalStatus()
        }

        fun onEvent(event: CloudSyncEvent) {
            when (event) {
                is CloudSyncEvent.ConnectClicked -> connect(event.target)
                is CloudSyncEvent.AuthenticationCompleted -> completeAuthentication(event)
                CloudSyncEvent.AuthenticationFailed -> showAuthenticationError()
                is CloudSyncEvent.DisconnectClicked -> disconnect(event.target)
                is CloudSyncEvent.SyncNowClicked -> syncNow()
                is CloudSyncEvent.FolderIdChanged -> setFolderId(event.folderId)
                is CloudSyncEvent.AutoSyncToggled -> toggleAutoSync(event.enabled)
                CloudSyncEvent.DismissError -> _state.value = _state.value.copy(errorBannerRes = null)
                CloudSyncEvent.BackClicked ->
                    viewModelScope.launch {
                        _actions.emit(CloudSyncAction.NavigateBack)
                    }
            }
        }

        private fun connect(target: SyncTarget) {
            val card = card(target)
            if (!card.enabled) {
                _state.value = _state.value.copy(errorBannerRes = R.string.sync_not_configured)
                return
            }
            viewModelScope.launch {
                _actions.emit(
                    when (target) {
                        SyncTarget.Dropbox -> CloudSyncAction.LaunchDropboxAuth
                        SyncTarget.GoogleDrive -> CloudSyncAction.LaunchGoogleSignIn
                    },
                )
            }
        }

        private fun disconnect(target: SyncTarget) {
            snapshotSync.disconnect(target)
            refresh(target)
        }

        private fun completeAuthentication(event: CloudSyncEvent.AuthenticationCompleted) {
            if (event.payload.isBlank()) {
                showAuthenticationError()
                return
            }
            try {
                snapshotSync.connect(event.target, event.payload)
                refresh(event.target)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.reportToSentry()
                showAuthenticationError()
            }
        }

        private fun showAuthenticationError() {
            _state.value = _state.value.copy(errorBannerRes = R.string.sync_err_auth)
        }

        private fun syncNow() {
            if (_state.value.isSyncing) return
            viewModelScope.launch {
                _state.value = _state.value.copy(isSyncing = true, errorBannerRes = null)
                try {
                    journalSync.syncNow()
                    refresh(SyncTarget.Dropbox)
                    refresh(SyncTarget.GoogleDrive)
                    reloadJournalStatus(_state.value.folderId)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    _state.value = _state.value.copy(errorBannerRes = mapError(t.toSyncError()))
                } finally {
                    _state.value = _state.value.copy(isSyncing = false)
                }
            }
        }

        private fun toggleAutoSync(enabled: Boolean) {
            viewModelScope.launch {
                appSettings.update { it.copy(autoSyncEnabled = enabled) }
                if (enabled) syncScheduler.enablePeriodicSync() else syncScheduler.disablePeriodicSync()
            }
        }

        private fun refresh(target: SyncTarget) {
            viewModelScope.launch {
                val connected = snapshotSync.isConnected(target)
                val label = if (connected) snapshotSync.accountLabel(target).getOrNull() else null
                updateCard(target) {
                    it.copy(connected = connected, accountLabel = label)
                }
            }
        }

        private fun setFolderId(folderId: String) {
            _state.value = _state.value.copy(folderId = folderId, errorBannerRes = null)
            viewModelScope.launch {
                val normalized = folderId.trim()
                try {
                    journalSyncConfig.setFolderId(normalized)
                    val settings = appSettings.settings.first()
                    if (settings.autoSyncEnabled) {
                        if (normalized.isBlank()) {
                            syncScheduler.disablePeriodicSync()
                        } else {
                            syncScheduler.enablePeriodicSync()
                        }
                    }
                    reloadJournalStatus(normalized)
                } catch (t: Throwable) {
                    setJournalStatusError(t)
                }
            }
        }

        private fun refreshJournalStatus() {
            viewModelScope.launch {
                try {
                    val folderId = journalSyncConfig.folderId()
                    _state.value = _state.value.copy(folderId = folderId)
                    reloadJournalStatus(folderId)
                } catch (t: Throwable) {
                    setJournalStatusError(t)
                }
            }
        }

        private suspend fun reloadJournalStatus(folderId: String) {
            try {
                if (folderId.isBlank()) {
                    _state.value = _state.value.copy(peerStatuses = emptyList())
                    return
                }
                val ownDeviceId = deviceIdProvider.deviceId()
                val files = journalBackend.listPeerJournals(folderId).getOrThrow()
                _state.value =
                    _state.value.copy(
                        peerStatuses = files.toPeerStates(ownDeviceId),
                    )
            } catch (t: Throwable) {
                setJournalStatusError(t)
            }
        }

        private suspend fun List<RemoteJournalFile>.toPeerStates(ownDeviceId: String?): List<PeerJournalState> =
            filterNot { file -> file.deviceId == ownDeviceId }
                .map { file ->
                    PeerJournalState(
                        deviceId = file.deviceId,
                        modifiedAtMs = file.modifiedAtEpochMs,
                        pulledThroughMs = journalSyncConfig.peerHighWaterMs(file.fileId),
                    )
                }

        private fun observeSettings() {
            viewModelScope.launch {
                appSettings.settings
                    .catch { t -> setGeneralError(t) }
                    .collect { settings ->
                        _state.value =
                            _state.value.copy(
                                autoSyncEnabled = settings.autoSyncEnabled,
                                lastSyncAtMs = settings.lastSyncAt,
                            )
                    }
            }
        }

        private fun setJournalStatusError(t: Throwable) {
            if (t is CancellationException) throw t
            t.reportToSentry()
            _state.value =
                _state.value.copy(
                    errorBannerRes = mapError(t.toSyncError()),
                    peerStatuses = emptyList(),
                )
        }

        private fun setGeneralError(t: Throwable) {
            if (t is CancellationException) throw t
            t.reportToSentry()
            _state.value = _state.value.copy(errorBannerRes = mapError(t.toSyncError()))
        }

        private fun card(target: SyncTarget): TargetCardState =
            when (target) {
                SyncTarget.Dropbox -> _state.value.dropbox
                SyncTarget.GoogleDrive -> _state.value.drive
            }

        private fun updateCard(
            target: SyncTarget,
            transform: (TargetCardState) -> TargetCardState,
        ) {
            _state.value =
                when (target) {
                    SyncTarget.Dropbox -> _state.value.copy(dropbox = transform(_state.value.dropbox))
                    SyncTarget.GoogleDrive -> _state.value.copy(drive = transform(_state.value.drive))
                }
        }

        @StringRes
        internal fun mapError(error: SyncError): Int =
            when (error) {
                SyncError.Network -> R.string.sync_err_network
                SyncError.Auth -> R.string.sync_err_auth
                SyncError.Quota -> R.string.sync_err_quota
                SyncError.Server -> R.string.sync_err_server
                SyncError.Conflict -> R.string.sync_err_unknown
                SyncError.Unknown -> R.string.sync_err_unknown
            }

        private fun Throwable.toSyncError(): SyncError =
            (this as? SyncException)?.syncError ?: SyncError.Unknown
    }
