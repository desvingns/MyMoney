package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.RecurringTemplateDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.repository.RecurringTemplateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringTemplateRepositoryImpl
    @Inject
    constructor(
        private val dao: RecurringTemplateDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : RecurringTemplateRepository {
        override suspend fun findDue(now: Instant): List<RecurringTemplate> =
            withContext(ioDispatcher) {
                dao.findDue(now.toEpochMilli()).map { it.toDomain() }
            }

        override fun observeAll(): Flow<List<RecurringTemplate>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

        override suspend fun upsert(template: RecurringTemplate): Long =
            withContext(ioDispatcher) {
                require(template.amount.signum() > 0) { "amount must be > 0; got ${template.amount}" }
                require(template.interval >= 1) { "interval must be >= 1" }
                val endsAt = template.endsAt
                if (endsAt != null) {
                    require(endsAt.isAfter(template.startsAt)) { "endsAt must be after startsAt" }
                }
                dao.upsert(template.toEntity())
            }

        override suspend fun updateNextRun(
            id: Long,
            nextRunAt: Instant,
        ) =
            withContext(ioDispatcher) {
                dao.updateNextRun(id, nextRunAt.toEpochMilli())
            }

        override suspend fun deactivate(id: Long) =
            withContext(ioDispatcher) {
                dao.deactivate(id)
            }
    }
