package com.kshavrin.mymoney.feature.support.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.feature.support.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel
    @Inject
    constructor(
        private val billingGateway: BillingGateway,
        private val entitlementRepository: EntitlementRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PaywallState())
        val state: StateFlow<PaywallState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<PaywallAction>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<PaywallAction> = _actions.asSharedFlow()

        private var catalogRefreshJob: Job? = null
        private val purchaseMutex = Mutex()

        init {
            observeEntitlement()
            refreshCatalog()
        }

        fun onEvent(event: PaywallEvent) {
            when (event) {
                PaywallEvent.BackClicked -> viewModelScope.launch { _actions.emit(PaywallAction.NavigateBack) }
                is PaywallEvent.PlanSelected -> purchase(event.planId)
                PaywallEvent.RetryClicked -> refreshCatalog()
            }
        }

        private fun observeEntitlement() {
            viewModelScope.launch {
                try {
                    entitlementRepository.entitlement.collect { entitlement ->
                        val currentState = _state.value
                        _state.value =
                            currentState.copy(
                                entitlement = entitlement,
                                purchaseState =
                                    if (
                                        entitlement.hasActivePlus() &&
                                            currentState.purchaseState.isAwaitingEntitlement()
                                    ) {
                                        PaywallPurchaseState.Idle
                                    } else {
                                        currentState.purchaseState
                                    },
                            )
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                }
            }
        }

        private fun refreshCatalog() {
            catalogRefreshJob?.cancel()
            catalogRefreshJob =
                viewModelScope.launch {
                    _state.value =
                        _state.value.copy(
                            catalogState = PaywallCatalogState.Loading,
                            errorMessageRes = null,
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

        private suspend fun applyAvailability(availability: BillingAvailability) {
            when (availability) {
                BillingAvailability.Available -> loadCatalog()
                BillingAvailability.UnavailableInRegion ->
                    _state.value = _state.value.copy(catalogState = PaywallCatalogState.UnavailableInRegion)

                BillingAvailability.DisabledInBuild,
                BillingAvailability.UnavailableOnDevice,
                -> _state.value = _state.value.copy(catalogState = PaywallCatalogState.Unavailable)

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
                    if (PaywallPlanId.entries.all { plan -> plan.productId in pricesByProductId }) {
                        _state.value =
                            _state.value.copy(
                                catalogState = PaywallCatalogState.Available,
                                plans =
                                    PaywallPlanId.entries.map { plan ->
                                        PaywallPlan(
                                            id = plan,
                                            formattedPrice = pricesByProductId.getValue(plan.productId),
                                        )
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

        private fun purchase(planId: PaywallPlanId) {
            if (!purchaseMutex.tryLock()) return
            val currentState = _state.value
            if (
                currentState.catalogState != PaywallCatalogState.Available ||
                    currentState.purchaseState != PaywallPurchaseState.Idle
            ) {
                purchaseMutex.unlock()
                return
            }
            _state.value =
                currentState.copy(
                    purchaseState = PaywallPurchaseState.InProgress,
                    errorMessageRes = null,
                )
            viewModelScope.launch {
                try {
                    billingGateway.launchSubscriptionFlow(planId.productId).collect(::handlePurchaseOutcome)
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                    showPurchaseError()
                } finally {
                    purchaseMutex.unlock()
                }
            }
        }

        private suspend fun handlePurchaseOutcome(outcome: PurchaseOutcome) {
            when (outcome) {
                is PurchaseOutcome.Purchased -> reconcileEntitlement()

                PurchaseOutcome.Pending ->
                    _state.value = _state.value.copy(purchaseState = PaywallPurchaseState.Pending)

                PurchaseOutcome.Cancelled ->
                    _state.value = _state.value.copy(purchaseState = PaywallPurchaseState.Idle)

                PurchaseOutcome.NetworkError -> showPurchaseError()
                is PurchaseOutcome.Unavailable -> {
                    _state.value = _state.value.copy(purchaseState = PaywallPurchaseState.Idle)
                    refreshCatalog()
                }
            }
        }

        private suspend fun reconcileEntitlement() {
            _state.value =
                _state.value.copy(
                    purchaseState = PaywallPurchaseState.ReconcilingEntitlement,
                    errorMessageRes = null,
                )
            for (delayMillis in ENTITLEMENT_RECONCILIATION_DELAYS_MILLIS) {
                refreshEntitlementForConfirmation()
                if (completeReconciliationIfEntitled()) return
                delay(delayMillis)
            }
            refreshEntitlementForConfirmation()
            if (completeReconciliationIfEntitled()) return
            _state.value = _state.value.copy(purchaseState = PaywallPurchaseState.AwaitingEntitlement)
        }

        private fun completeReconciliationIfEntitled(): Boolean {
            val entitlement = entitlementRepository.entitlement.value
            if (!entitlement.hasActivePlus()) return false
            _state.value =
                _state.value.copy(
                    entitlement = entitlement,
                    purchaseState = PaywallPurchaseState.Idle,
                )
            return true
        }

        private suspend fun refreshEntitlementForConfirmation() {
            try {
                withTimeoutOrNull(ENTITLEMENT_REFRESH_TIMEOUT_MILLIS) {
                    entitlementRepository.refresh()
                }?.exceptionOrNull()?.reportToSentry()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                throwable.reportToSentry()
            }
        }

        private fun showCatalogError() {
            _state.value =
                _state.value.copy(
                    catalogState = PaywallCatalogState.Error,
                    plans = PaywallPlanId.entries.map { PaywallPlan(it) },
                    purchaseState = PaywallPurchaseState.Idle,
                )
        }

        private fun showPurchaseError() {
            _state.value =
                _state.value.copy(
                    purchaseState = PaywallPurchaseState.Idle,
                    errorMessageRes = R.string.paywall_purchase_error,
                )
        }
    }

private fun UserEntitlement.hasActivePlus(): Boolean =
    this is UserEntitlement.Plus && state != EntitlementState.NONE && state != EntitlementState.EXPIRED

private fun PaywallPurchaseState.isAwaitingEntitlement(): Boolean =
    this == PaywallPurchaseState.ReconcilingEntitlement || this == PaywallPurchaseState.AwaitingEntitlement

private val ENTITLEMENT_RECONCILIATION_DELAYS_MILLIS = listOf(500L, 1_000L, 2_000L)
private const val ENTITLEMENT_REFRESH_TIMEOUT_MILLIS = 10_000L
