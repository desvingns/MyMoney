package com.kshavrin.mymoney.feature.cloudsync

import androidx.annotation.StringRes
import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.sync.SyncTarget

data class CloudSyncState(
    val dropbox: TargetCardState = TargetCardState(SyncTarget.Dropbox),
    val drive: TargetCardState = TargetCardState(SyncTarget.GoogleDrive),
    val autoSyncEnabled: Boolean = false,
    @StringRes val errorBannerRes: Int? = null,
    val conflict: ConflictPrompt? = null,
)

data class TargetCardState(
    val target: SyncTarget,
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val accountLabel: String? = null,
    val lastSyncAtMs: Long? = null,
    val recentLog: List<SyncLogEntry> = emptyList(),
    val syncing: Boolean = false,
)

data class ConflictPrompt(
    val target: SyncTarget,
    val remoteMs: Long,
    val localMs: Long,
)
