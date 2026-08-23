package com.kshavrin.mymoney.core.sync.supporter

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PlusCatalogState
import com.kshavrin.mymoney.core.domain.billing.PlusPlanId
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseState
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionCoordinator
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionState
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.usecase.ObserveEntitlementUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlusSubscriptionCoordinatorImpl
    @Inject
    constructor(
        private val billingGateway: BillingGateway,
        private val observeEntitlement: ObserveEntitlementUseCase,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope private val applicationScope: CoroutineScope,
    ) : PlusSubscriptionCoordinator {
        private val _state = MutableStateFlow(PlusSubscriptionState())
        override val state: StateFlow<PlusSubscriptionState> = _state.asStateFlow()

        private var catalogRefreshJob: Job? = null
        private val purchaseMutex = Mutex()

        init {
            observeEntitlementInternal()
        }

        override suspend fun refreshCatalog() {
            catalogRefreshJob?.cancel()
            catalogRefreshJob =
                applicationScope.launch {
                    val currentState = _state.value
                    _state.value =
                        currentState.copy(
                            catalog = PlusCatalogState.Loading,
                            // The coordinator is a Singleton and outlives any single Paywall
                            // screen visit. A stalled reconciliation (Pending family-approval,
                            // or retries exhausted with no local entitlement confirmation) must
                            // not block purchasing forever just because the user left and came
                            // back — this call only runs on screen (re)open, so it mirrors the
                            // fresh-ViewModel-instance reset the pre-extraction code got for
                            // free. Idle/InProgress/ReconcilingEntitlement are left untouched:
                            // the latter two reflect a purchase actively in flight right now.
                            purchase =
                                if (currentState.purchase.isStalledAwaitingEntitlement()) {
                                    PlusPurchaseState.Idle
                                } else {
                                    currentState.purchase
                                },
                        )
                    try {
                        applyAvailability(billingGateway.availability().first())
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        throwable.reportToSentry()
                        showCatalogError()
                    }
                }
        }

        override suspend fun purchase(planId: PlusPlanId): PlusPurchaseOutcome {
            if (!purchaseMutex.tryLock()) return PlusPurchaseOutcome.NotStarted
            val currentState = _state.value
            if (
                currentState.catalog != PlusCatalogState.Available ||
                    currentState.purchase != PlusPurchaseState.Idle
            ) {
                purchaseMutex.unlock()
                return PlusPurchaseOutcome.NotStarted
            }
            _state.value = currentState.copy(purchase = PlusPurchaseState.InProgress)
            var lockTransferred = false
            var outcomeSignal = PlusPurchaseOutcome.Cancelled
            try {
                billingGateway.launchSubscriptionFlow(planId.productId).collect { outcome ->
                    when (outcome) {
                        is PurchaseOutcome.Purchased -> {
                            outcomeSignal = PlusPurchaseOutcome.Purchased
                            lockTransferred = true
                            applicationScope.launch {
                                try {
                                    reconcileEntitlement()
                                } finally {
                                    purchaseMutex.unlock()
                                }
                            }
                        }

                        PurchaseOutcome.Pending -> {
                            outcomeSignal = PlusPurchaseOutcome.Pending
                            _state.value = _state.value.copy(purchase = PlusPurchaseState.Pending)
                        }

                        PurchaseOutcome.Cancelled -> {
                            outcomeSignal = PlusPurchaseOutcome.Cancelled
                            _state.value = _state.value.copy(purchase = PlusPurchaseState.Idle)
                        }

                        PurchaseOutcome.NetworkError -> {
                            outcomeSignal = PlusPurchaseOutcome.Failed
                            _state.value = _state.value.copy(purchase = PlusPurchaseState.Idle)
                        }

                        is PurchaseOutcome.Unavailable -> {
                            outcomeSignal = PlusPurchaseOutcome.Unavailable
                            _state.value = _state.value.copy(purchase = PlusPurchaseState.Idle)
                            refreshCatalog()
                        }
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    if (!lockTransferred) {
                        _state.value = _state.value.copy(purchase = PlusPurchaseState.Idle)
                    }
                    throw throwable
                }
                throwable.reportToSentry()
                outcomeSignal = PlusPurchaseOutcome.Failed
                _state.value = _state.value.copy(purchase = PlusPurchaseState.Idle)
            } finally {
                if (!lockTransferred) purchaseMutex.unlock()
            }
            return outcomeSignal
        }

        private fun observeEntitlementInternal() {
            applicationScope.launch {
                try {
                    observeEntitlement.entitlement.collect { entitlement ->
                        val currentState = _state.value
                        _state.value =
                            currentState.copy(
                                entitlement = entitlement,
                                purchase =
                                    if (
                                        entitlement.hasActivePlus() &&
                                            currentState.purchase.isAwaitingEntitlement()
                                    ) {
                                        PlusPurchaseState.Idle
                                    } else {
                                        currentState.purchase
                                    },
                            )
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                }
            }
        }

        private suspend fun applyAvailability(availability: BillingAvailability) {
            when (availability) {
                BillingAvailability.Available -> loadCatalog()
                BillingAvailability.UnavailableInRegion ->
                    _state.value =
                        _state.value.copy(catalog = PlusCatalogState.UnavailableInRegion, prices = emptyMap())

                BillingAvailability.DisabledInBuild,
                BillingAvailability.UnavailableOnDevice,
                -> _state.value = _state.value.copy(catalog = PlusCatalogState.Unavailable, prices = emptyMap())

                BillingAvailability.NetworkUnavailable,
                BillingAvailability.ServiceUnavailable,
                is BillingAvailability.UnknownFailure,
                -> showCatalogError()
            }
        }

        private suspend fun loadCatalog() {
            val result = withContext(ioDispatcher) { billingGateway.querySubscriptions() }
            result.fold(
                onSuccess = { products ->
                    val pricesByProductId = products.associate { product -> product.id to product.formattedPrice }
                    if (PlusPlanId.entries.all { plan -> plan.productId in pricesByProductId }) {
                        _state.value =
                            _state.value.copy(
                                catalog = PlusCatalogState.Available,
                                prices =
                                    PlusPlanId.entries.associateWith { plan ->
                                        pricesByProductId.getValue(plan.productId)
                                    },
                            )
                    } else {
                        showCatalogError()
                    }
                },
                onFailure = { throwable ->
                    throwable.reportToSentry()
                    showCatalogError()
                },
            )
        }

        private suspend fun reconcileEntitlement() {
            _state.value = _state.value.copy(purchase = PlusPurchaseState.ReconcilingEntitlement)
            for (delayMillis in ENTITLEMENT_RECONCILIATION_DELAYS_MILLIS) {
                refreshEntitlementForConfirmation()
                if (completeReconciliationIfEntitled()) return
                delay(delayMillis)
            }
            refreshEntitlementForConfirmation()
            if (completeReconciliationIfEntitled()) return
            _state.value = _state.value.copy(purchase = PlusPurchaseState.AwaitingEntitlement)
        }

        private fun completeReconciliationIfEntitled(): Boolean {
            val entitlement = observeEntitlement.entitlement.value
            if (!entitlement.hasActivePlus()) return false
            _state.value =
                _state.value.copy(
                    entitlement = entitlement,
                    purchase = PlusPurchaseState.Idle,
                )
            return true
        }

        private suspend fun refreshEntitlementForConfirmation() {
            try {
                withTimeoutOrNull(ENTITLEMENT_REFRESH_TIMEOUT_MILLIS) {
                    observeEntitlement.refresh()
                }?.exceptionOrNull()?.reportToSentry()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                throwable.reportToSentry()
            }
        }

        private fun showCatalogError() {
            _state.value =
                _state.value.copy(
                    catalog = PlusCatalogState.Error,
                    prices = emptyMap(),
                    purchase = PlusPurchaseState.Idle,
                )
        }
    }

private fun UserEntitlement.hasActivePlus(): Boolean =
    this is UserEntitlement.Plus && state != EntitlementState.NONE && state != EntitlementState.EXPIRED

private fun PlusPurchaseState.isAwaitingEntitlement(): Boolean =
    this == PlusPurchaseState.ReconcilingEntitlement ||
        this == PlusPurchaseState.AwaitingEntitlement ||
        this == PlusPurchaseState.Pending

// Unlike isAwaitingEntitlement(), excludes ReconcilingEntitlement: that state has a live
// coroutine actively ticking through the retry loop right now and must not be reset out from
// under it. Pending/AwaitingEntitlement have no such in-flight worker — nothing but a future
// entitlement observation (which may never arrive) can move them off this value otherwise.
private fun PlusPurchaseState.isStalledAwaitingEntitlement(): Boolean =
    this == PlusPurchaseState.AwaitingEntitlement || this == PlusPurchaseState.Pending

private val ENTITLEMENT_RECONCILIATION_DELAYS_MILLIS = listOf(500L, 1_000L, 2_000L)
private const val ENTITLEMENT_REFRESH_TIMEOUT_MILLIS = 10_000L
