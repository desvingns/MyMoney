package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.datastore.CloudProvider

enum class SyncTarget { Dropbox, GoogleDrive }

fun SyncTarget.toCloudProvider(): CloudProvider =
    when (this) {
        SyncTarget.Dropbox -> CloudProvider.Dropbox
        SyncTarget.GoogleDrive -> CloudProvider.GoogleDrive
    }

fun CloudProvider.toSyncTarget(): SyncTarget =
    when (this) {
        CloudProvider.Dropbox -> SyncTarget.Dropbox
        CloudProvider.GoogleDrive -> SyncTarget.GoogleDrive
    }
