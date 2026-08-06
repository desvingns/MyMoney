package com.kshavrin.mymoney.feature.cloudsync

import androidx.annotation.StringRes
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.shared.SharedRealtimeStatus
import com.kshavrin.mymoney.core.sync.shared.SharedWorkspaceSummary

data class CloudSyncState(
    val binding: CloudBinding? = null,
    val dropbox: TargetCardState = TargetCardState(SyncTarget.Dropbox),
    val drive: TargetCardState = TargetCardState(SyncTarget.GoogleDrive),
    val shared: SharedCardState = SharedCardState(),
    val lastSyncAtMs: Long? = null,
    val requiresProviderChoice: Boolean = false,
    val migration: MigrationUiState? = null,
    val sharedDialog: SharedDialog? = null,
    val importLocalData: Boolean = false,
    val conflicts: List<ConflictUi> = emptyList(),
    val internalBackups: List<BackupFile> = emptyList(),
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

data class SharedCardState(
    val enabled: Boolean = true,
    val signedIn: Boolean = false,
    val accountEmail: String? = null,
    val active: Boolean = false,
    val workspaceName: String? = null,
    val conflictCount: Int = 0,
    val isSoleOwner: Boolean = false,
    val realtimeStatus: SharedRealtimeStatus = SharedRealtimeStatus.Inactive,
)

sealed interface SharedDialog {
    data object Setup : SharedDialog

    data class RecoverRemoteWorkspace(
        val workspace: SharedWorkspaceSummary,
    ) : SharedDialog

    data object Conflicts : SharedDialog

    data object ConfirmDisconnect : SharedDialog

    data object ConfirmLeave : SharedDialog

    data object InternalBackups : SharedDialog

    data class ConfirmInternalBackupRestore(
        val backup: BackupFile,
    ) : SharedDialog

    data class Invite(
        val token: String,
    ) : SharedDialog
}

data class ConflictUi(
    val conflictId: String,
    val entityKind: String,
    val localOperationId: String,
    val localAuthorId: String,
    val localSummary: ConflictSummaryUi,
    val remoteOperationId: String,
    val remoteAuthorId: String,
    val remoteSummary: ConflictSummaryUi,
) {
    constructor(
        conflictId: String,
        entityKind: String,
        localOperationId: String,
        localAuthorId: String,
        localSummary: String,
        remoteOperationId: String,
        remoteAuthorId: String,
        remoteSummary: String,
    ) : this(
        conflictId = conflictId,
        entityKind = entityKind,
        localOperationId = localOperationId,
        localAuthorId = localAuthorId,
        localSummary = ConflictSummaryUi.Text(localSummary),
        remoteOperationId = remoteOperationId,
        remoteAuthorId = remoteAuthorId,
        remoteSummary = ConflictSummaryUi.Text(remoteSummary),
    )
}

sealed interface ConflictSummaryUi {
    data class Text(
        val value: String,
    ) : ConflictSummaryUi

    data object Deleted : ConflictSummaryUi
}
