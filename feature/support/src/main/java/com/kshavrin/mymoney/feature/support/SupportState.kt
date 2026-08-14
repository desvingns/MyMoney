package com.kshavrin.mymoney.feature.support

import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.supporter.SupporterState

data class SupportState(
    val billingState: SupportBillingState = SupportBillingState.Loading,
    val products: List<SupportProduct> = emptyList(),
    val supporterState: SupporterState = SupporterState(badgeEarned = false, purchaseCount = 0),
    val isPurchaseInProgress: Boolean = false,
)

sealed interface SupportBillingState {
    data object Loading : SupportBillingState

    data object Available : SupportBillingState

    data object Pending : SupportBillingState

    data object NetworkError : SupportBillingState

    data class Unavailable(
        val reason: SupportUnavailableReason,
    ) : SupportBillingState
}

enum class SupportUnavailableReason {
    DisabledInBuild,
    UnavailableOnDevice,
    UnavailableInRegion,
    Unavailable,
}

sealed interface SupportEvent {
    data object BackClicked : SupportEvent

    data class PurchaseClicked(
        val productId: String,
    ) : SupportEvent

    data object RetryClicked : SupportEvent
}

sealed interface SupportAction {
    data object NavigateBack : SupportAction
}

internal const val COFFEE_SMALL_PRODUCT_ID = "coffee_small"
internal const val COFFEE_LARGE_PRODUCT_ID = "coffee_large"
