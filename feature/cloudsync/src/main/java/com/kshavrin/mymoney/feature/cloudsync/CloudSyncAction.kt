package com.kshavrin.mymoney.feature.cloudsync

import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint

sealed interface CloudSyncAction {
    data object NavigateBack : CloudSyncAction

    data class NavigateToPaywall(
        val entryPoint: PaywallEntryPoint,
    ) : CloudSyncAction

    data object LaunchDropboxAuth : CloudSyncAction

    data object LaunchGoogleDriveAuth : CloudSyncAction

    data object RequestMigrationBackupDirectory : CloudSyncAction

    data object LaunchSharedGoogleSignIn : CloudSyncAction

    data object ClearSharedGoogleCredentialState : CloudSyncAction

    data object RestartAfterInternalBackupRestore : CloudSyncAction

    data class CopySharedInvite(
        val token: String,
    ) : CloudSyncAction
}
