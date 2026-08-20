package com.kshavrin.mymoney.core.domain.repository

import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import kotlinx.coroutines.flow.StateFlow

interface EntitlementRepository {
    val entitlement: StateFlow<UserEntitlement>

    suspend fun bindGooglePlayPurchase(purchaseToken: String): Result<Unit> =
        Result.failure(UnsupportedOperationException())

    suspend fun refresh(): Result<Unit>
}
