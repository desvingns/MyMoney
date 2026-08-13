package com.kshavrin.mymoney.core.domain.supporter

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome

interface SupporterSync {
    suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit>

    suspend fun syncPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit>

    suspend fun restore(): Result<Unit>
}
