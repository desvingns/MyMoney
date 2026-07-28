package com.kshavrin.mymoney.feature.cloudsync

sealed interface CloudSyncAction {
    data object NavigateBack : CloudSyncAction

    data object LaunchDropboxAuth : CloudSyncAction

    data object LaunchGoogleDriveAuth : CloudSyncAction

    data object RequestMigrationBackupDirectory : CloudSyncAction

    data object LaunchSharedGoogleSignIn : CloudSyncAction
}
