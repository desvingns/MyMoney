package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.CloudProvider

enum class SyncTarget { Dropbox, GoogleDrive, Shared }

fun SyncTarget.toCloudProvider(): CloudProvider =
    when (this) {
        SyncTarget.Dropbox -> CloudProvider.Dropbox
        SyncTarget.GoogleDrive -> CloudProvider.GoogleDrive
        SyncTarget.Shared -> CloudProvider.Shared
    }

fun CloudProvider.toSyncTarget(): SyncTarget =
    when (this) {
        CloudProvider.Dropbox -> SyncTarget.Dropbox
        CloudProvider.GoogleDrive -> SyncTarget.GoogleDrive
        CloudProvider.Shared -> SyncTarget.Shared
    }
