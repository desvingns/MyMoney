package com.kshavrin.mymoney.feature.cloudsync

import androidx.annotation.StringRes
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.sync.SyncTarget

data class CloudSyncState(
    val binding: CloudBinding? = null,
    val dropbox: TargetCardState = TargetCardState(SyncTarget.Dropbox),
    val drive: TargetCardState = TargetCardState(SyncTarget.GoogleDrive),
    val lastSyncAtMs: Long? = null,
    val requiresProviderChoice: Boolean = false,
    val migration: MigrationUiState? = null,
    val isConnecting: Boolean = false,
    @StringRes val errorBannerRes: Int? = null,
)

sealed interface MigrationUiState {
    val target: SyncTarget

    data class AwaitingBackup(
        override val target: SyncTarget,
    ) : MigrationUiState

    data class Reviewing(
        override val target: SyncTarget,
        val conflictCount: Int,
    ) : MigrationUiState
}

data class TargetCardState(
    val target: SyncTarget,
    val enabled: Boolean = false,
    val connected: Boolean = false,
    val accountLabel: String? = null,
)
