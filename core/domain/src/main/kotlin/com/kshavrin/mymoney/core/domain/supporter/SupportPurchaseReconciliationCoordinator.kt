package com.kshavrin.mymoney.core.domain.supporter

import kotlinx.coroutines.flow.StateFlow

interface SupportPurchaseReconciliationCoordinator {
    val state: StateFlow<SupportPurchaseReconciliationState>

    suspend fun reconcile()
}

sealed interface SupportPurchaseReconciliationState {
    data object Loading : SupportPurchaseReconciliationState

    data object Ready : SupportPurchaseReconciliationState

    data object Pending : SupportPurchaseReconciliationState

    data object NetworkError : SupportPurchaseReconciliationState
}
