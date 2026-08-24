package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationCoordinator
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationState
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import kotlinx.coroutines.CancellationException
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
        private val supporterRepository: SupporterRepository,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SupportPurchaseReconciliationCoordinator {
        private val reconciliationMutex = Mutex()
        private val purchasePersistenceMutex = Mutex()
        private val recordedPurchaseTokens = mutableSetOf<String>()
        private val _state =
            MutableStateFlow<SupportPurchaseReconciliationState>(
                SupportPurchaseReconciliationState.Loading,
            )

        override val state: StateFlow<SupportPurchaseReconciliationState> = _state.asStateFlow()

        override suspend fun reconcile() {
            if (!reconciliationMutex.tryLock()) return
            try {
                withContext(dispatcher) {
                    _state.value = SupportPurchaseReconciliationState.Loading
                    val result = billingGateway.resolvePendingPurchases()
                    result.exceptionOrNull()?.let { throwable ->
                        if (throwable is CancellationException) throw throwable
                        throwable.reportToSentry()
                    }
                    val outcomes = result.getOrNull()
                    _state.value =
                        if (outcomes == null) {
                            SupportPurchaseReconciliationState.NetworkError
                        } else {
                            reconciliationState(outcomes)
                        }
                }
            } finally {
                reconciliationMutex.unlock()
            }
        }

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            withContext(dispatcher) {
                purchasePersistenceMutex.withLock {
                    if (outcome.purchaseToken in recordedPurchaseTokens) {
                        return@withLock Result.success(Unit)
                    }
                    supporterRepository
                        .recordPurchase(outcome)
                        .onSuccess { recordedPurchaseTokens += outcome.purchaseToken }
                        .onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            throwable.reportToSentry()
                        }
                }
            }

        private suspend fun reconciliationState(
            outcomes: List<PurchaseOutcome>,
        ): SupportPurchaseReconciliationState =
            when {
                outcomes
                    .filterIsInstance<PurchaseOutcome.Purchased>()
                    .any { outcome -> recordPurchase(outcome).isFailure } ->
                    SupportPurchaseReconciliationState.NetworkError
                outcomes.any { outcome -> outcome == PurchaseOutcome.Pending } ->
                    SupportPurchaseReconciliationState.Pending
                outcomes.any { outcome -> outcome == PurchaseOutcome.NetworkError } ->
                    SupportPurchaseReconciliationState.NetworkError
                else -> SupportPurchaseReconciliationState.Ready
            }
    }
