package com.kshavrin.mymoney.core.domain.supporter

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.flow.StateFlow

interface SupportPurchaseReconciliationCoordinator {
    val state: StateFlow<SupportPurchaseReconciliationState>

    suspend fun reconcile()

    suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit>
}

sealed interface SupportPurchaseReconciliationState {
    data object Loading : SupportPurchaseReconciliationState

    data object Ready : SupportPurchaseReconciliationState

    data object Pending : SupportPurchaseReconciliationState

    data object NetworkError : SupportPurchaseReconciliationState
}
