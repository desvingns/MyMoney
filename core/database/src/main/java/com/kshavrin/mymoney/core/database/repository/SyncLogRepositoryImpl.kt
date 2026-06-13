package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.SyncLogDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.domain.repository.SyncLogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncLogRepositoryImpl
    @Inject
    constructor(
        private val dao: SyncLogDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SyncLogRepository {
        override suspend fun insert(entry: SyncLogEntry): Long =
            withContext(ioDispatcher) {
                dao.insert(entry.toEntity())
            }

        override suspend fun recentByTarget(
            target: String,
            limit: Int,
        ): List<SyncLogEntry> =
            withContext(ioDispatcher) {
                dao.recentByTarget(target, limit).map { it.toDomain() }
            }

        override suspend fun pruneOld(target: String) =
            withContext(ioDispatcher) {
                dao.pruneOld(target)
            }
    }
