package com.kshavrin.mymoney.feature.cloudsync

sealed interface CloudSyncAction {
    data object NavigateBack : CloudSyncAction
    data object LaunchDropboxAuth : CloudSyncAction
    data object LaunchGoogleSignIn : CloudSyncAction
}
