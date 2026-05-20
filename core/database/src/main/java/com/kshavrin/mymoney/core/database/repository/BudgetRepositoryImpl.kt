package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.BudgetDao
import com.kshavrin.mymoney.core.database.mapper.toDomain
import com.kshavrin.mymoney.core.database.mapper.toEntity
import com.kshavrin.mymoney.core.domain.model.Budget
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val dao: BudgetDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BudgetRepository {

    override fun observeActive(): Flow<List<Budget>> = dao.observeActive().map { list -> list.map { it.toDomain() } }

    override suspend fun findForCategory(categoryId: Long): Budget? = withContext(ioDispatcher) {
        dao.findForCategory(categoryId)?.toDomain()
    }

    override suspend fun findTotalBudget(): Budget? = withContext(ioDispatcher) {
        dao.findTotalBudget()?.toDomain()
    }

    override suspend fun upsert(budget: Budget): Long = withContext(ioDispatcher) {
        require(budget.amount.signum() > 0) { "budget.amount must be > 0; got ${budget.amount}" }
        require(budget.alertThresholdPct in 1..100) { "alertThresholdPct must be in 1..100" }
        dao.upsert(budget.toEntity())
    }

    override suspend fun deactivate(id: Long) = withContext(ioDispatcher) {
        dao.deactivate(id)
    }
}
