package com.kshavrin.mymoney.feature.cloudsync

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.domain.repository.SyncLogRepository
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncOutcome
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel
    @Inject
    constructor(
        private val snapshotSync: SnapshotSync,
        private val syncScheduler: SyncScheduler,
        private val appSettings: AppSettingsRepository,
        private val syncLog: SyncLogRepository,
        private val remoteConfig: RemoteConfigRepository,
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
        }

        fun onEvent(event: CloudSyncEvent) {
            when (event) {
                is CloudSyncEvent.ConnectClicked -> connect(event.target)
                is CloudSyncEvent.DisconnectClicked -> disconnect(event.target)
                is CloudSyncEvent.SyncNowClicked -> syncNow(event.target)
                is CloudSyncEvent.AutoSyncToggled -> toggleAutoSync(event.enabled)
                CloudSyncEvent.ConflictKeepRemote -> resolveConflict(keepRemote = true)
                CloudSyncEvent.ConflictKeepLocal -> resolveConflict(keepRemote = false)
                CloudSyncEvent.DismissConflict -> _state.value = _state.value.copy(conflict = null)
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

        private fun syncNow(target: SyncTarget) {
            if (!snapshotSync.isConnected(target)) return
            viewModelScope.launch {
                updateCard(target) { it.copy(syncing = true) }
                snapshotSync
                    .syncNow(target)
                    .onSuccess { outcome -> applyOutcome(target, outcome) }
                    .onFailure { _state.value = _state.value.copy(errorBannerRes = mapError(it.toSyncError())) }
                updateCard(target) { it.copy(syncing = false) }
            }
        }

        private fun resolveConflict(keepRemote: Boolean) {
            val prompt = _state.value.conflict ?: return
            viewModelScope.launch {
                val result =
                    if (keepRemote) {
                        snapshotSync.keepRemote(prompt.target)
                    } else {
                        snapshotSync.keepLocal(prompt.target)
                    }
                result
                    .onSuccess { outcome ->
                        _state.value = _state.value.copy(conflict = null)
                        applyOutcome(prompt.target, outcome)
                    }.onFailure { _state.value = _state.value.copy(errorBannerRes = mapError(it.toSyncError())) }
            }
        }

        private fun toggleAutoSync(enabled: Boolean) {
            viewModelScope.launch {
                appSettings.update { it.copy(autoSyncEnabled = enabled) }
                if (enabled) syncScheduler.enablePeriodicSync() else syncScheduler.disablePeriodicSync()
            }
        }

        private fun applyOutcome(
            target: SyncTarget,
            outcome: SyncOutcome,
        ) {
            when (outcome) {
                is SyncOutcome.ConflictDetected ->
                    _state.value =
                        _state.value.copy(
                            conflict =
                                ConflictPrompt(
                                    target = target,
                                    remoteMs = outcome.remoteModifiedMs,
                                    localMs = outcome.localLastSyncMs,
                                ),
                        )
                SyncOutcome.PulledRequiresRestart ->
                    viewModelScope.launch {
                        _actions.emit(CloudSyncAction.RestartAfterRestore)
                    }
                SyncOutcome.Pushed,
                SyncOutcome.Pulled,
                SyncOutcome.UpToDate,
                -> refresh(target)
            }
        }

        private fun refresh(target: SyncTarget) {
            viewModelScope.launch {
                val connected = snapshotSync.isConnected(target)
                val label = if (connected) snapshotSync.accountLabel(target).getOrNull() else null
                val log = syncLog.recentByTarget(target.name, RECENT_LOG_LIMIT)
                updateCard(target) {
                    it.copy(connected = connected, accountLabel = label, recentLog = log)
                }
            }
        }

        private fun observeSettings() {
            viewModelScope.launch {
                appSettings.settings.collect { settings ->
                    _state.value =
                        _state.value.copy(
                            autoSyncEnabled = settings.autoSyncEnabled,
                            dropbox = _state.value.dropbox.copy(lastSyncAtMs = settings.lastSyncAt),
                            drive = _state.value.drive.copy(lastSyncAtMs = settings.lastSyncAt),
                        )
                }
            }
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

        private companion object {
            const val RECENT_LOG_LIMIT = 5
        }
    }
