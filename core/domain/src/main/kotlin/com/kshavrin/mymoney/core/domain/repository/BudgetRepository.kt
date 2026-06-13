package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.Budget
import kotlinx.coroutines.flow.Flow

interface BudgetRepository {
    fun observeActive(): Flow<List<Budget>>

    suspend fun findForCategory(categoryId: Long): Budget?

    suspend fun findTotalBudget(): Budget?

    suspend fun upsert(budget: Budget): Long

    suspend fun deactivate(id: Long)
}
