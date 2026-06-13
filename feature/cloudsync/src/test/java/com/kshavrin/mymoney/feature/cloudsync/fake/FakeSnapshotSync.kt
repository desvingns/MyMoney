package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncOutcome
import com.kshavrin.mymoney.core.sync.SyncTarget

/**
 * Hand-written fake for [SnapshotSync]. Per-target connection / label state and
 * per-target results for the suspend operations are configured up front; the
 * fake records connect / disconnect / keepLocal / keepRemote / syncNow calls so
 * tests can assert the ViewModel drove the right seam.
 */
class FakeSnapshotSync : SnapshotSync {
    private val connected = mutableMapOf<SyncTarget, Boolean>()
    private val labels = mutableMapOf<SyncTarget, Result<String>>()

    private val syncNowResults = mutableMapOf<SyncTarget, Result<SyncOutcome>>()
    private val keepLocalResults = mutableMapOf<SyncTarget, Result<SyncOutcome>>()
    private val keepRemoteResults = mutableMapOf<SyncTarget, Result<SyncOutcome>>()

    private val pushResults = mutableMapOf<SyncTarget, Result<SyncOutcome>>()

    val connectCalls = mutableListOf<Pair<SyncTarget, String>>()
    val disconnectCalls = mutableListOf<SyncTarget>()
    val syncNowCalls = mutableListOf<SyncTarget>()
    val pushCalls = mutableListOf<SyncTarget>()
    val keepLocalCalls = mutableListOf<SyncTarget>()
    val keepRemoteCalls = mutableListOf<SyncTarget>()
    var autoSyncConnectedCalls = 0

    fun setConnected(
        target: SyncTarget,
        value: Boolean,
    ) {
        connected[target] = value
    }

    fun setAccountLabel(
        target: SyncTarget,
        result: Result<String>,
    ) {
        labels[target] = result
    }

    fun setSyncNowResult(
        target: SyncTarget,
        result: Result<SyncOutcome>,
    ) {
        syncNowResults[target] = result
    }

    fun setPushResult(
        target: SyncTarget,
        result: Result<SyncOutcome>,
    ) {
        pushResults[target] = result
    }

    fun setKeepLocalResult(
        target: SyncTarget,
        result: Result<SyncOutcome>,
    ) {
        keepLocalResults[target] = result
    }

    fun setKeepRemoteResult(
        target: SyncTarget,
        result: Result<SyncOutcome>,
    ) {
        keepRemoteResults[target] = result
    }

    override fun isConnected(target: SyncTarget): Boolean = connected[target] ?: false

    override fun connectedTargets(): List<SyncTarget> =
        connected.filterValues { it }.keys.toList()

    override suspend fun syncNow(target: SyncTarget): Result<SyncOutcome> {
        syncNowCalls += target
        return syncNowResults[target] ?: Result.success(SyncOutcome.UpToDate)
    }

    override suspend fun push(target: SyncTarget): Result<SyncOutcome> {
        pushCalls += target
        return pushResults[target] ?: Result.success(SyncOutcome.Pushed)
    }

    override suspend fun autoSyncConnected(): Result<Unit> {
        autoSyncConnectedCalls++
        return Result.success(Unit)
    }

    override suspend fun keepLocal(target: SyncTarget): Result<SyncOutcome> {
        keepLocalCalls += target
        return keepLocalResults[target] ?: Result.success(SyncOutcome.Pushed)
    }

    override suspend fun keepRemote(target: SyncTarget): Result<SyncOutcome> {
        keepRemoteCalls += target
        return keepRemoteResults[target] ?: Result.success(SyncOutcome.Pulled)
    }

    override fun connect(
        target: SyncTarget,
        payload: String,
    ) {
        connectCalls += target to payload
    }

    override fun disconnect(target: SyncTarget) {
        disconnectCalls += target
        connected[target] = false
    }

    override suspend fun accountLabel(target: SyncTarget): Result<String> =
        labels[target] ?: Result.success("account@example.com")
}
