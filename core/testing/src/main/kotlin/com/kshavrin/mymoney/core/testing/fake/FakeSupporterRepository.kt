package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.billing.COFFEE_LARGE_PRODUCT_ID
import com.kshavrin.mymoney.core.domain.billing.COFFEE_SMALL_PRODUCT_ID
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSupporterRepository(
    initialState: SupporterState = SupporterState(badgeEarned = false, purchaseCount = 0),
) : SupporterRepository {
    private val supporterState = MutableStateFlow(initialState)
    private val recordedPurchaseTokens = mutableSetOf<String>()

    override fun state(): Flow<SupporterState> = supporterState.asStateFlow()

    override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> {
        if (recordedPurchaseTokens.add(outcome.purchaseToken)) {
            val current = supporterState.value
            supporterState.value =
                current.copy(
                    badgeEarned = true,
                    purchaseCount = current.purchaseCount + 1,
                    smallCoffeeCount =
                        current.smallCoffeeCount + if (outcome.productId == COFFEE_SMALL_PRODUCT_ID) 1 else 0,
                    largeCoffeeCount =
                        current.largeCoffeeCount + if (outcome.productId == COFFEE_LARGE_PRODUCT_ID) 1 else 0,
                )
        }
        return Result.success(Unit)
    }

    override suspend fun mergeRemote(
        remoteCount: Int,
        remoteBadge: Boolean,
    ): Result<Unit> {
        supporterState.value =
            supporterState.value.copy(
                badgeEarned = supporterState.value.badgeEarned || remoteBadge,
                purchaseCount = maxOf(supporterState.value.purchaseCount, remoteCount),
            )
        return Result.success(Unit)
    }
}
