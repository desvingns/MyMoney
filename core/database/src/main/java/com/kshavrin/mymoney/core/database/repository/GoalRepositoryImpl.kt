package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.GoalDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl
    @Inject
    constructor(
        private val dao: GoalDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : GoalRepository {
        override fun observeActive(): Flow<List<Goal>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

        override suspend fun findById(id: Long): Goal? =
            withContext(ioDispatcher) {
                dao.findById(id)?.toDomain()
            }

        override suspend fun upsert(goal: Goal): Long =
            withContext(ioDispatcher) {
                dao.upsert(goal.toEntity())
            }

        override suspend fun archive(id: Long) =
            withContext(ioDispatcher) {
                dao.archive(id, Instant.now().toEpochMilli())
            }
    }
