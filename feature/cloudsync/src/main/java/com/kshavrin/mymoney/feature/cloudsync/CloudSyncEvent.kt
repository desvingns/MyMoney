package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget

sealed interface CloudSyncEvent {
    data class ConnectClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class UseConnectedProviderClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class SwitchClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class AuthenticationCompleted(
        val target: SyncTarget,
        val payload: String,
    ) : CloudSyncEvent

    data object AuthenticationFailed : CloudSyncEvent

    data class DisconnectClicked(
        val target: SyncTarget,
    ) : CloudSyncEvent

    data class MigrationBackupDirectorySelected(
        val treeUriString: String,
    ) : CloudSyncEvent

    data class ConfirmMigration(
        val resolution: MigrationResolution,
    ) : CloudSyncEvent

    data object CancelMigration : CloudSyncEvent

    data object DismissError : CloudSyncEvent

    data object BackClicked : CloudSyncEvent
}
