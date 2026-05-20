package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface RecurringTemplateRepository {
    suspend fun findDue(now: Instant): List<RecurringTemplate>
    fun observeAll(): Flow<List<RecurringTemplate>>
    suspend fun upsert(template: RecurringTemplate): Long
    suspend fun updateNextRun(id: Long, nextRunAt: Instant)
    suspend fun deactivate(id: Long)
}
