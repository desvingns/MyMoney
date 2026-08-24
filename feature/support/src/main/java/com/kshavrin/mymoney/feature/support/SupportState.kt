package com.kshavrin.mymoney.feature.support

import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import com.kshavrin.mymoney.core.domain.billing.COFFEE_LARGE_PRODUCT_ID as domainCoffeeLargeProductId
import com.kshavrin.mymoney.core.domain.billing.COFFEE_SMALL_PRODUCT_ID as domainCoffeeSmallProductId

data class SupportState(
    val billingState: SupportBillingState = SupportBillingState.Loading,
    val products: List<SupportProduct> = emptyList(),
    val supporterState: SupporterState = SupporterState(badgeEarned = false, purchaseCount = 0),
    val adsWatchedTotal: Int = 0,
    val hasSupportActivity: Boolean = false,
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

internal const val COFFEE_SMALL_PRODUCT_ID = domainCoffeeSmallProductId
internal const val COFFEE_LARGE_PRODUCT_ID = domainCoffeeLargeProductId
