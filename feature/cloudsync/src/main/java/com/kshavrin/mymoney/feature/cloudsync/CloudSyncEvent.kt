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
        val target: SyncTarget? = null,
    ) : CloudSyncEvent

    data class FolderIdChanged(
        val folderId: String,
    ) : CloudSyncEvent

    data class AutoSyncToggled(
        val enabled: Boolean,
    ) : CloudSyncEvent

    data object DismissError : CloudSyncEvent

    data object BackClicked : CloudSyncEvent
}
