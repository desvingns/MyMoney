package com.kshavrin.mymoney.core.domain.billing

import kotlinx.coroutines.flow.Flow

interface BillingGateway {
    fun availability(): Flow<BillingAvailability>

    fun products(): Result<List<SupportProduct>>

    fun purchase(productId: String): Flow<PurchaseOutcome>

    fun resolvePendingPurchases(): Result<List<PurchaseOutcome>>

    fun querySubscriptions(): Result<List<SupportProduct>>

    fun launchSubscriptionFlow(productId: String): Flow<PurchaseOutcome>

    suspend fun acknowledge(purchaseToken: String): Result<Unit>

    fun resolveSubscriptionPurchases(): Result<List<PurchaseOutcome>>
}
