package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationCoordinator
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupportPurchaseReconciliationCoordinatorImpl
    @Inject
    constructor(
        private val billingGateway: BillingGateway,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SupportPurchaseReconciliationCoordinator {
        private val reconciliationMutex = Mutex()
        private val _state =
            MutableStateFlow<SupportPurchaseReconciliationState>(
                SupportPurchaseReconciliationState.Loading,
            )

        override val state: StateFlow<SupportPurchaseReconciliationState> = _state.asStateFlow()

        override suspend fun reconcile() {
            withContext(dispatcher) {
                reconciliationMutex.withLock {
                    _state.value = SupportPurchaseReconciliationState.Loading
                    val state =
                        billingGateway
                            .resolvePendingPurchases()
                            .fold(
                                onSuccess = ::reconciliationState,
                                onFailure = { throwable ->
                                    throwable.reportToSentry()
                                    SupportPurchaseReconciliationState.NetworkError
                                },
                            )
                    _state.value = state
                }
            }
        }

        private fun reconciliationState(
            outcomes: List<PurchaseOutcome>,
        ): SupportPurchaseReconciliationState =
            when {
                outcomes.any { outcome -> outcome == PurchaseOutcome.Pending } ->
                    SupportPurchaseReconciliationState.Pending
                outcomes.any { outcome -> outcome == PurchaseOutcome.NetworkError } ->
                    SupportPurchaseReconciliationState.NetworkError
                else -> SupportPurchaseReconciliationState.Ready
            }
    }
