package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.RemoteSnapshot
import com.kshavrin.mymoney.core.sync.SyncTarget
import java.io.File

/**
 * In-memory [CloudSyncBackend] for SnapshotSyncRepository orchestration tests.
 *
 * Records every upload/prune/disconnect call and serves a mutable snapshot list
 * to listSnapshots/downloadNewest. Each operation can be flipped to
 * Result.failure(SyncException(error)) via the simulate* hooks so the
 * orchestrator's failure path (log row + Sentry + re-thrown SyncException) is
 * exercisable without any real SDK.
 */
class FakeCloudSyncBackend(
    override val target: SyncTarget,
    private var connected: Boolean = true,
) : CloudSyncBackend {

    var snapshots: MutableList<RemoteSnapshot> = mutableListOf()

    data class Upload(val localFile: File, val remoteName: String)

    val uploads: MutableList<Upload> = mutableListOf()
    val pruneKeeps: MutableList<Int> = mutableListOf()
    var disconnectCalls: Int = 0
    var downloadCalls: Int = 0

    private var uploadError: SyncError? = null
    private var listError: SyncError? = null
    private var downloadError: SyncError? = null
    private var pruneError: SyncError? = null

    fun setConnected(value: Boolean) {
        connected = value
    }

    fun seedSnapshots(items: List<RemoteSnapshot>) {
        snapshots = items.toMutableList()
    }

    fun simulateUploadFailure(error: SyncError) {
        uploadError = error
    }

    fun simulateListFailure(error: SyncError) {
        listError = error
    }

    fun simulateDownloadFailure(error: SyncError) {
        downloadError = error
    }

    fun simulatePruneFailure(error: SyncError) {
        pruneError = error
    }

    override fun isConnected(): Boolean = connected

    override suspend fun accountLabel(): Result<String> = Result.success(target.name)

    override suspend fun upload(localFile: File, remoteName: String): Result<Unit> {
        uploads += Upload(localFile, remoteName)
        return uploadError.asFailureOr(Unit)
    }

    override suspend fun listSnapshots(): Result<List<RemoteSnapshot>> {
        return listError.asFailureOr(snapshots.sortedByDescending { it.modifiedAtEpochMs })
    }

    override suspend fun downloadNewest(destFile: File): Result<RemoteSnapshot?> {
        downloadCalls++
        downloadError?.let { return Result.failure(SyncException(it)) }
        return Result.success(snapshots.maxByOrNull { it.modifiedAtEpochMs })
    }

    override suspend fun prune(keep: Int): Result<Unit> {
        pruneKeeps += keep
        return pruneError.asFailureOr(Unit)
    }

    override fun disconnect() {
        disconnectCalls++
    }

    private fun <T> SyncError?.asFailureOr(value: T): Result<T> =
        this?.let { Result.failure(SyncException(it)) } ?: Result.success(value)
}
