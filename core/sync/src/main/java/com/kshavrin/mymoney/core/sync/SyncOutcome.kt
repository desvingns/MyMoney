package com.kshavrin.mymoney.core.sync

sealed interface SyncOutcome {
    data object Pushed : SyncOutcome

    data object Pulled : SyncOutcome

    data object UpToDate : SyncOutcome

    data class ConflictDetected(
        val remoteModifiedMs: Long,
        val localLastSyncMs: Long,
    ) : SyncOutcome
}
