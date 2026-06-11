package com.kshavrin.mymoney.core.sync

interface SnapshotSync {
    fun isConnected(target: SyncTarget): Boolean
    fun connectedTargets(): List<SyncTarget>
    suspend fun syncNow(target: SyncTarget): Result<SyncOutcome>
    suspend fun push(target: SyncTarget): Result<SyncOutcome>
    suspend fun autoSyncConnected(): Result<Unit>
    suspend fun keepLocal(target: SyncTarget): Result<SyncOutcome>
    suspend fun keepRemote(target: SyncTarget): Result<SyncOutcome>
    fun connect(target: SyncTarget, payload: String)
    fun disconnect(target: SyncTarget)
    suspend fun accountLabel(target: SyncTarget): Result<String>
}
