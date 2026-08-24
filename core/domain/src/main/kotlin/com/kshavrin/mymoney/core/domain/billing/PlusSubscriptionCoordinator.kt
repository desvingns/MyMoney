package com.kshavrin.mymoney.core.domain.billing

import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import kotlinx.coroutines.flow.StateFlow

interface PlusSubscriptionCoordinator {
    val state: StateFlow<PlusSubscriptionState>

    // This coordinator is a Singleton shared by every screen that shows Plus plans. Concurrent
    // callers (e.g. Support's inline card and the separate Paywall screen both alive at once) can
    // race here — see the implementation for why that's accepted rather than guarded against.
    suspend fun refreshCatalog()

    suspend fun purchase(planId: PlusPlanId): PlusPurchaseOutcome
}

enum class PlusPlanId(
    val productId: String,
) {
    Monthly(productId = "plus_monthly"),
    Yearly(productId = "plus_yearly"),
}

data class PlusSubscriptionState(
    val catalog: PlusCatalogState = PlusCatalogState.Loading,
    val prices: Map<PlusPlanId, String> = emptyMap(),
    val purchase: PlusPurchaseState = PlusPurchaseState.Idle,
    val entitlement: UserEntitlement = UserEntitlement.Free,
)

sealed interface PlusCatalogState {
    data object Loading : PlusCatalogState

    data object Available : PlusCatalogState

    data object UnavailableInRegion : PlusCatalogState

    data object Unavailable : PlusCatalogState

    data object Error : PlusCatalogState
}

sealed interface PlusPurchaseState {
    data object Idle : PlusPurchaseState

    data object InProgress : PlusPurchaseState

    data object ReconcilingEntitlement : PlusPurchaseState

    data object AwaitingEntitlement : PlusPurchaseState

    data object Pending : PlusPurchaseState
}

enum class PlusPurchaseOutcome {
    Purchased,
    Pending,
    Cancelled,
    Unavailable,
    Failed,
    NotStarted,
}
