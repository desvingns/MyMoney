package com.kshavrin.mymoney.core.domain.supporter

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.flow.Flow

interface SupporterStateSource {
    fun state(): Flow<SupporterState>
}

interface SupporterRepository : SupporterStateSource {
    override fun state(): Flow<SupporterState>

    suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit>

    suspend fun recordSupportActivity(): Result<Unit> = Result.success(Unit)

    suspend fun mergeRemote(
        remoteCount: Int,
        remoteBadge: Boolean,
    ): Result<Unit>
}
