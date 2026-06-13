package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.sync.SyncTarget

sealed interface CloudSyncEvent {
    data class ConnectClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class DisconnectClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class SyncNowClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class AutoSyncToggled(
        val enabled: Boolean,
    ) : CloudSyncEvent

    data object ConflictKeepRemote : CloudSyncEvent

    data object ConflictKeepLocal : CloudSyncEvent

    data object DismissConflict : CloudSyncEvent

    data object DismissError : CloudSyncEvent

    data object BackClicked : CloudSyncEvent
}
