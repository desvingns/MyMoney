package com.kshavrin.mymoney.core.domain.billing

sealed interface PurchaseOutcome {
    data class Purchased(
        val productId: String,
        val purchaseToken: String,
        val purchasedAtMillis: Long,
    ) : PurchaseOutcome

    data object Pending : PurchaseOutcome

    data object Cancelled : PurchaseOutcome

    data object NetworkError : PurchaseOutcome

    data class Unavailable(
        val reason: String,
    ) : PurchaseOutcome
}
