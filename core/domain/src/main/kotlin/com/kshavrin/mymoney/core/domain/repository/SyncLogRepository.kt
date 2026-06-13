package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.SyncLogEntry

interface SyncLogRepository {
    suspend fun insert(entry: SyncLogEntry): Long

    suspend fun recentByTarget(
        target: String,
        limit: Int = 100,
    ): List<SyncLogEntry>

    suspend fun pruneOld(target: String)
}
