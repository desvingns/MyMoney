package com.kshavrin.mymoney.feature.support.paywall

import androidx.annotation.StringRes
import com.kshavrin.mymoney.core.domain.model.UserEntitlement

enum class PaywallPlanId(
    val productId: String,
) {
    Monthly(productId = "plus_monthly"),
    Yearly(productId = "plus_yearly"),
}

data class PaywallPlan(
    val id: PaywallPlanId,
    val formattedPrice: String? = null,
)

data class PaywallState(
    val catalogState: PaywallCatalogState = PaywallCatalogState.Loading,
    val plans: List<PaywallPlan> = PaywallPlanId.entries.map { PaywallPlan(it) },
    val purchaseState: PaywallPurchaseState = PaywallPurchaseState.Idle,
    val entitlement: UserEntitlement = UserEntitlement.Free,
    @StringRes val errorMessageRes: Int? = null,
)

sealed interface PaywallCatalogState {
    data object Loading : PaywallCatalogState

    data object Available : PaywallCatalogState

    data object UnavailableInRegion : PaywallCatalogState

    data object Unavailable : PaywallCatalogState

    data object Error : PaywallCatalogState
}

sealed interface PaywallPurchaseState {
    data object Idle : PaywallPurchaseState

    data object InProgress : PaywallPurchaseState

    data object ReconcilingEntitlement : PaywallPurchaseState

    data object AwaitingEntitlement : PaywallPurchaseState

    data object Pending : PaywallPurchaseState
}
