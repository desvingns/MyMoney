package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.sync.MigrationResolution
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint

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

    data object WarningDismissed : CloudSyncEvent

    data object WarningActionClicked : CloudSyncEvent

    data object AdPlusExpiryRenewClicked : CloudSyncEvent

    data object AdPlusExpiryDeclined : CloudSyncEvent

    data class PaywallRequested(
        val entryPoint: PaywallEntryPoint,
    ) : CloudSyncEvent

    data object BackClicked : CloudSyncEvent

    data object SharedSignInClicked : CloudSyncEvent

    data class SharedSignInCompleted(
        val googleIdToken: String,
        val nonce: String,
    ) : CloudSyncEvent

    data object SharedSignInFailed : CloudSyncEvent

    data object SharedSetupClicked : CloudSyncEvent

    data class SharedImportChoiceChanged(
        val importLocalData: Boolean,
    ) : CloudSyncEvent

    data class SharedCreateWorkspace(
        val name: String,
    ) : CloudSyncEvent

    data class SharedJoinWorkspace(
        val inviteToken: String,
    ) : CloudSyncEvent

    data object SharedConfirmRemoteWorkspaceRecovery : CloudSyncEvent

    data object SharedCreateInviteClicked : CloudSyncEvent

    data object SharedCopyInviteClicked : CloudSyncEvent

    data object SharedSyncNowClicked : CloudSyncEvent

    data object SharedRealtimeForegroundStarted : CloudSyncEvent

    data object SharedRealtimeForegroundStopped : CloudSyncEvent

    data object SharedRetryRealtimeClicked : CloudSyncEvent

    data object SharedConflictsClicked : CloudSyncEvent

    data class SharedResolveConflict(
        val conflictId: String,
        val winnerOperationId: String,
    ) : CloudSyncEvent

    data object SharedDisconnectClicked : CloudSyncEvent

    data object SharedConfirmDisconnectKeepServerData : CloudSyncEvent

    data object SharedConfirmDisconnectDeleteWorkspace : CloudSyncEvent

    data object SharedLeaveClicked : CloudSyncEvent

    data object SharedConfirmLeave : CloudSyncEvent

    data object SharedDeleteAccountClicked : CloudSyncEvent

    data object SharedConfirmDeleteAccount : CloudSyncEvent

    data object SharedInternalBackupsClicked : CloudSyncEvent

    data class SharedInternalBackupRestoreClicked(
        val backup: BackupFile,
    ) : CloudSyncEvent

    data object SharedConfirmInternalBackupRestore : CloudSyncEvent

    data object SharedDialogDismissed : CloudSyncEvent
}
