package com.kshavrin.mymoney.core.sync

import java.io.File

data class RemoteSnapshot(
    val id: String,
    val name: String,
    val modifiedAtEpochMs: Long,
)

interface CloudSyncBackend {
    val target: SyncTarget

    fun isConnected(): Boolean

    suspend fun accountLabel(): Result<String>

    fun connect(payload: String) = Unit

    suspend fun upload(localFile: File, remoteName: String): Result<Unit>

    suspend fun listSnapshots(): Result<List<RemoteSnapshot>>

    suspend fun downloadNewest(destFile: File): Result<RemoteSnapshot?>

    suspend fun prune(keep: Int = 3): Result<Unit>

    fun disconnect()
}
