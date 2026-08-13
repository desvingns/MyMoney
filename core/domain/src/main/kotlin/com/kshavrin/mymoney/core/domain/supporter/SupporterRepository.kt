package com.kshavrin.mymoney.core.domain.supporter

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.flow.Flow

interface SupporterRepository {
    fun state(): Flow<SupporterState>

    suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit>

    suspend fun mergeRemote(
        remoteCount: Int,
        remoteBadge: Boolean,
    ): Result<Unit>
}
