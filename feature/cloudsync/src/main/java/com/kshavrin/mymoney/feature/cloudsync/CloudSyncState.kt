package com.kshavrin.mymoney.feature.cloudsync

import androidx.annotation.StringRes
import com.kshavrin.mymoney.core.sync.SyncTarget

data class CloudSyncState(
    val dropbox: TargetCardState = TargetCardState(SyncTarget.Dropbox),
    val drive: TargetCardState = TargetCardState(SyncTarget.GoogleDrive),
    val autoSyncEnabled: Boolean = false,
    val folderId: String = "",
    val lastSyncAtMs: Long? = null,
    val peerStatuses: List<PeerJournalState> = emptyList(),
    val isSyncing: Boolean = false,
    @StringRes val errorBannerRes: Int? = null,
)

data class TargetCardState(
    val target: SyncTarget,
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val accountLabel: String? = null,
)

data class PeerJournalState(
    val deviceId: String,
    val modifiedAtMs: Long,
    val pulledThroughMs: Long,
) {
    val upToDate: Boolean
        get() = pulledThroughMs >= modifiedAtMs
}
