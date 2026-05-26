package com.kshavrin.mymoney.core.domain.model

sealed interface DomainEvent {
    data class BudgetAlert(
        val budgetId: Long,
        val categoryId: Long?,
        val over: Boolean,
        val overage: Money? = null,
    ) : DomainEvent
}
