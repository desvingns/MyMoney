package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSupporterSync : SupporterSync {
    private val syncedPurchaseOutcomes = MutableStateFlow<List<PurchaseOutcome.Purchased>>(emptyList())

    val syncedPurchases = syncedPurchaseOutcomes.asStateFlow()

    var syncPurchaseResult: Result<Unit> = Result.success(Unit)
    var restoreResult: Result<Unit> = Result.success(Unit)

    override suspend fun syncPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> {
        syncedPurchaseOutcomes.value += outcome
        return syncPurchaseResult
    }

    override suspend fun restore(): Result<Unit> = restoreResult
}
