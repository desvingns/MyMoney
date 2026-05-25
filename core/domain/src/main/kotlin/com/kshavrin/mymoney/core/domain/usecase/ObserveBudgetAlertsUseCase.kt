package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.DomainEvent
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveBudgetAlertsUseCase @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    private val balanceCalculator: BalanceCalculator,
    private val budgetEvaluator: BudgetEvaluator,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(accountId: Long, period: Period): Flow<List<DomainEvent.BudgetAlert>> =
        combine(
            transactionRepository.observeAll(),
            budgetRepository.observeActive(),
        ) { _, budgets -> budgets }
            .map { budgets ->
                val snapshot = balanceCalculator(accountId, period)
                budgetEvaluator.evaluate(snapshot, budgets)
                    .filter { it.state != BudgetState.Under }
                    .map { status ->
                        DomainEvent.BudgetAlert(
                            budgetId = status.budgetId,
                            categoryId = status.categoryId,
                            over = status.state == BudgetState.Over,
                        )
                    }
            }
            .distinctUntilChanged()
            .flowOn(defaultDispatcher)
}
