package com.kshavrin.mymoney.feature.support.paywall

import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeEntitlementRepository(
    initialEntitlement: UserEntitlement = UserEntitlement.Free,
) : EntitlementRepository {
    private val entitlementState = MutableStateFlow(initialEntitlement)

    override val entitlement: StateFlow<UserEntitlement> = entitlementState.asStateFlow()

    var refreshCalls: Int = 0
        private set

    var refreshResult: Result<Unit> = Result.success(Unit)

    var onRefresh: () -> Unit = {}

    override suspend fun refresh(): Result<Unit> {
        refreshCalls++
        onRefresh()
        return refreshResult
    }

    fun seed(entitlement: UserEntitlement) {
        entitlementState.value = entitlement
    }
}
