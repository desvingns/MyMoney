package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.common.di.DefaultDispatcher
import com.kshavrin.mymoney.core.domain.model.DomainEvent
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.BudgetRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.time.Duration
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.inject.Inject

class ObserveBudgetAlertsUseCase
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val budgetRepository: BudgetRepository,
        private val balanceCalculator: BalanceCalculator,
        private val budgetEvaluator: BudgetEvaluator,
        @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        operator fun invoke(
            accountId: Long,
            currentMonth: () -> YearMonth = { YearMonth.now() },
            monthRollovers: Flow<Unit> = calendarMonthRollovers(),
        ): Flow<List<DomainEvent.BudgetAlert>> =
            combine(
                transactionRepository.observeAll(),
                budgetRepository.observeActive(),
                monthRollovers.onStart { emit(Unit) },
            ) { _, budgets, _ -> budgets }
                .map { budgets ->
                    val snapshot = balanceCalculator(accountId, Period.Month(currentMonth()))
                    budgetEvaluator
                        .evaluate(snapshot, budgets)
                        .filter { it.state != BudgetState.Under }
                        .map { status ->
                            DomainEvent.BudgetAlert(
                                budgetId = status.budgetId,
                                categoryId = status.categoryId,
                                over = status.state == BudgetState.Over,
                                overage =
                                    if (status.state == BudgetState.Over) {
                                        status.spent - status.limit
                                    } else {
                                        null
                                    },
                            )
                        }
                }.distinctUntilChanged()
                .flowOn(defaultDispatcher)

        private fun calendarMonthRollovers(): Flow<Unit> =
            flow {
                while (true) {
                    val now = ZonedDateTime.now()
                    val nextMonthStart =
                        now
                            .toLocalDate()
                            .withDayOfMonth(1)
                            .plusMonths(1)
                            .atStartOfDay(now.zone)
                    val waitMillis = Duration.between(now, nextMonthStart).toMillis().coerceAtLeast(1L)
                    delay(waitMillis)
                    emit(Unit)
                }
            }
    }
