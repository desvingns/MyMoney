package com.kshavrin.mymoney.core.domain.repository

import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SearchHistoryRepository {
    fun observe(): Flow<List<String>>

    suspend fun add(
        query: String,
        now: Instant,
    )

    suspend fun pruneToLimit()
}
