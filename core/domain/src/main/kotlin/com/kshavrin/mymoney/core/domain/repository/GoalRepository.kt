package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Goal
import kotlinx.coroutines.flow.Flow

interface GoalRepository {
    fun observeActive(): Flow<List<Goal>>
    suspend fun findById(id: Long): Goal?
    suspend fun upsert(goal: Goal): Long
    suspend fun archive(id: Long)
}
