package com.kshavrin.mymoney.core.domain.billing

import kotlinx.coroutines.flow.Flow

interface BillingGateway {
    fun availability(): Flow<BillingAvailability>

    fun products(): Result<List<SupportProduct>>

    fun purchase(productId: String): Flow<PurchaseOutcome>

    fun resolvePendingPurchases(): Result<List<PurchaseOutcome>>
}
