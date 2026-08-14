package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveEntitlementUseCase
    @Inject
    constructor(
        private val entitlementRepository: EntitlementRepository,
    ) {
        val entitlement: StateFlow<UserEntitlement>
            get() = entitlementRepository.entitlement

        suspend fun refresh(): Result<Unit> = entitlementRepository.refresh()
    }
