package com.kshavrin.mymoney.core.sync.fake

import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.domain.repository.SyncLogRepository

/**
 * Records every inserted [SyncLogEntry] (public [inserted]) so orchestration
 * tests can assert event / status / errorMessage. Assigns ascending ids so the
 * orchestrator's `.also { pruneOld(...) }` chain works; pruneOld targets are
 * recorded but no row is removed.
 */
class FakeSyncLogRepository : SyncLogRepository {

    val inserted: MutableList<SyncLogEntry> = mutableListOf()
    val prunedTargets: MutableList<String> = mutableListOf()

    private var nextId = 1L

    override suspend fun insert(entry: SyncLogEntry): Long {
        val id = nextId++
        inserted += entry.copy(id = id)
        return id
    }

    override suspend fun recentByTarget(target: String, limit: Int): List<SyncLogEntry> =
        inserted.filter { it.target == target }.takeLast(limit)

    override suspend fun pruneOld(target: String) {
        prunedTargets += target
    }
}
