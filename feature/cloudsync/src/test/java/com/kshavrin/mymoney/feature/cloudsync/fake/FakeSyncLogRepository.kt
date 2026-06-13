package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.domain.repository.SyncLogRepository

class FakeSyncLogRepository : SyncLogRepository {
    private val byTarget = mutableMapOf<String, List<SyncLogEntry>>()

    val insertCalls = mutableListOf<SyncLogEntry>()
    val pruneCalls = mutableListOf<String>()
    val recentByTargetCalls = mutableListOf<Pair<String, Int>>()

    fun seedRecent(
        target: String,
        entries: List<SyncLogEntry>,
    ) {
        byTarget[target] = entries
    }

    override suspend fun insert(entry: SyncLogEntry): Long {
        insertCalls += entry
        return insertCalls.size.toLong()
    }

    override suspend fun recentByTarget(
        target: String,
        limit: Int,
    ): List<SyncLogEntry> {
        recentByTargetCalls += target to limit
        return byTarget[target].orEmpty()
    }

    override suspend fun pruneOld(target: String) {
        pruneCalls += target
    }
}
