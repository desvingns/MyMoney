package com.kshavrin.mymoney.core.sync

interface SnapshotSync {
    fun isConnected(target: SyncTarget): Boolean

    fun connectedTargets(): List<SyncTarget>

    fun connect(
        target: SyncTarget,
        payload: String,
    )

    fun disconnect(target: SyncTarget)

    suspend fun accountLabel(target: SyncTarget): Result<String>
}
