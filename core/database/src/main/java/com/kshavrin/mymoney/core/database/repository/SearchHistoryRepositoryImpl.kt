package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.SearchHistoryDao
import com.kshavrin.mymoney.core.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryRepositoryImpl @Inject constructor(
    private val dao: SearchHistoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SearchHistoryRepository {

    override fun observe(): Flow<List<String>> = dao.observe().map { list -> list.map { it.query } }

    override suspend fun add(query: String, now: Instant) = withContext(ioDispatcher) {
        dao.upsertQuery(query, now.toEpochMilli())
    }

    override suspend fun pruneToLimit() = withContext(ioDispatcher) {
        dao.pruneToLimit()
    }
}
