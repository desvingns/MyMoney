package com.kshavrin.mymoney.feature.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationCoordinator
import com.kshavrin.mymoney.core.domain.supporter.SupportPurchaseReconciliationState
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SupportViewModel
    @Inject
    constructor(
        private val billingGateway: BillingGateway,
        private val supporterRepository: SupporterRepository,
        private val supportPurchaseReconciliationCoordinator: SupportPurchaseReconciliationCoordinator,
        private val analyticsGateway: AnalyticsGateway,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SupportState())
        val state: StateFlow<SupportState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<SupportAction>(
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<SupportAction> = _actions.asSharedFlow()

        private var hasUnresolvedPendingPurchase = false
        private var isForegroundPurchaseActive = false
        private var availabilityRefreshJob: Job? = null
        private var availabilityRefreshGeneration = 0L

        init {
            log(AnalyticsEvent.SupportOpened)
            observeSupporterState()
            observePurchaseReconciliation()
            refreshBilling()
        }

        fun onScreenResumed() {
            refreshBilling()
        }

        fun onEvent(event: SupportEvent) {
            when (event) {
                SupportEvent.BackClicked -> viewModelScope.launch { _actions.emit(SupportAction.NavigateBack) }
                is SupportEvent.PurchaseClicked -> purchase(event.productId)
                SupportEvent.RetryClicked -> refreshBilling()
            }
        }

        private fun observeSupporterState() {
            viewModelScope.launch {
                try {
                    supporterRepository.state().collect { supporterState ->
                        _state.value = _state.value.copy(supporterState = supporterState)
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                }
            }
        }

        private fun observePurchaseReconciliation() {
            viewModelScope.launch {
                supportPurchaseReconciliationCoordinator.state.collect { reconciliationState ->
                    when (reconciliationState) {
                        SupportPurchaseReconciliationState.Loading -> {
                            invalidateAvailabilityRefresh()
                            showLoading()
                        }
                        SupportPurchaseReconciliationState.Ready -> {
                            hasUnresolvedPendingPurchase = false
                            refreshBillingAvailability()
                        }
                        SupportPurchaseReconciliationState.Pending -> {
                            invalidateAvailabilityRefresh()
                            hasUnresolvedPendingPurchase = true
                            showPendingPurchase()
                        }
                        SupportPurchaseReconciliationState.NetworkError -> {
                            invalidateAvailabilityRefresh()
                            hasUnresolvedPendingPurchase = false
                            showNetworkError()
                        }
                    }
                }
            }
        }

        private fun refreshBilling() {
            if (isForegroundPurchaseActive) return
            viewModelScope.launch {
                supportPurchaseReconciliationCoordinator.reconcile()
            }
        }

        private fun refreshBillingAvailability() {
            availabilityRefreshJob?.cancel()
            val generation = ++availabilityRefreshGeneration
            availabilityRefreshJob =
                viewModelScope.launch {
                    try {
                        val availability = billingGateway.availability().first()
                        if (isCurrentAvailabilityRefresh(generation)) {
                            applyAvailability(availability, generation)
                        }
                    } catch (throwable: Throwable) {
                        if (throwable is CancellationException) throw throwable
                        throwable.reportToSentry()
                        if (isCurrentAvailabilityRefresh(generation)) {
                            showNetworkError()
                        }
                    }
                }
        }

        private fun invalidateAvailabilityRefresh() {
            availabilityRefreshGeneration++
            availabilityRefreshJob?.cancel()
            availabilityRefreshJob = null
        }

        private fun isCurrentAvailabilityRefresh(generation: Long): Boolean =
            generation == availabilityRefreshGeneration

        private suspend fun applyAvailability(
            availability: BillingAvailability,
            generation: Long,
        ) {
            when (availability) {
                BillingAvailability.Available -> loadProducts(generation)
                BillingAvailability.NetworkUnavailable,
                BillingAvailability.ServiceUnavailable,
                -> if (isCurrentAvailabilityRefresh(generation)) showNetworkError()

                BillingAvailability.DisabledInBuild ->
                    if (isCurrentAvailabilityRefresh(generation)) {
                        showUnavailable(SupportUnavailableReason.DisabledInBuild)
                    }
                BillingAvailability.UnavailableOnDevice ->
                    if (isCurrentAvailabilityRefresh(generation)) {
                        showUnavailable(SupportUnavailableReason.UnavailableOnDevice)
                    }
                BillingAvailability.UnavailableInRegion ->
                    if (isCurrentAvailabilityRefresh(generation)) {
                        showUnavailable(SupportUnavailableReason.UnavailableInRegion)
                    }
                is BillingAvailability.UnknownFailure ->
                    if (isCurrentAvailabilityRefresh(generation)) {
                        showUnavailable(SupportUnavailableReason.Unavailable)
                    }
            }
        }

        private suspend fun loadProducts(generation: Long) {
            val result = withContext(ioDispatcher) { billingGateway.products() }
            if (!isCurrentAvailabilityRefresh(generation)) return
            result.fold(
                onSuccess = ::showAvailableProducts,
                onFailure = { throwable ->
                    throwable.reportToSentry()
                    showNetworkError()
                },
            )
        }

        private fun showAvailableProducts(products: List<SupportProduct>) {
            val coffeeProducts =
                products
                    .filter { product -> product.id == COFFEE_SMALL_PRODUCT_ID || product.id == COFFEE_LARGE_PRODUCT_ID }
                    .sortedBy { product -> productOrder(product.id) }
            if (coffeeProducts.isEmpty()) {
                showUnavailable(SupportUnavailableReason.Unavailable)
            } else if (hasUnresolvedPendingPurchase) {
                _state.value = _state.value.copy(products = coffeeProducts, isPurchaseInProgress = false)
            } else {
                _state.value =
                    _state.value.copy(
                        billingState = SupportBillingState.Available,
                        products = coffeeProducts,
                        isPurchaseInProgress = false,
                    )
            }
        }

        private fun purchase(productId: String) {
            val currentState = _state.value
            if (
                currentState.billingState != SupportBillingState.Available ||
                    currentState.isPurchaseInProgress ||
                    isForegroundPurchaseActive ||
                    currentState.products.none { product -> product.id == productId }
            ) {
                return
            }
            log(AnalyticsEvent.SupportPurchaseStarted(productId))
            isForegroundPurchaseActive = true
            invalidateAvailabilityRefresh()
            viewModelScope.launch {
                _state.value = _state.value.copy(isPurchaseInProgress = true)
                var completionLogged = false
                var refreshBillingAfterPurchase = false
                try {
                    billingGateway.purchase(productId).collect { outcome ->
                        if (outcome != PurchaseOutcome.Pending && !completionLogged) {
                            log(
                                AnalyticsEvent.SupportPurchaseCompleted(
                                    productId = productId,
                                    outcome = outcome.analyticsOutcome(),
                                ),
                            )
                            completionLogged = true
                        }
                        refreshBillingAfterPurchase =
                            handlePurchaseOutcome(outcome) || refreshBillingAfterPurchase
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                    if (!completionLogged) {
                        log(AnalyticsEvent.SupportPurchaseCompleted(productId, "network_error"))
                    }
                    hasUnresolvedPendingPurchase = false
                    showNetworkError()
                } finally {
                    isForegroundPurchaseActive = false
                    if (refreshBillingAfterPurchase || hasUnresolvedPendingPurchase) {
                        refreshBilling()
                    }
                }
            }
        }

        private suspend fun handlePurchaseOutcome(outcome: PurchaseOutcome): Boolean =
            when (outcome) {
                is PurchaseOutcome.Purchased -> {
                    hasUnresolvedPendingPurchase = false
                    if (supportPurchaseReconciliationCoordinator.recordPurchase(outcome).isSuccess) {
                        _state.value =
                            _state.value.copy(
                                billingState = SupportBillingState.Available,
                                isPurchaseInProgress = false,
                            )
                    } else {
                        showNetworkError()
                    }
                    false
                }

                PurchaseOutcome.Pending -> {
                    hasUnresolvedPendingPurchase = true
                    showPendingPurchase()
                    false
                }

                PurchaseOutcome.Cancelled -> {
                    hasUnresolvedPendingPurchase = false
                    _state.value =
                        _state.value.copy(
                            billingState = SupportBillingState.Available,
                            isPurchaseInProgress = false,
                        )
                    false
                }

                PurchaseOutcome.NetworkError -> {
                    hasUnresolvedPendingPurchase = false
                    showNetworkError()
                    false
                }
                is PurchaseOutcome.Unavailable -> {
                    hasUnresolvedPendingPurchase = false
                    _state.value = _state.value.copy(isPurchaseInProgress = false)
                    true
                }
            }

        private fun showUnavailable(reason: SupportUnavailableReason) {
            if (hasUnresolvedPendingPurchase) {
                showPendingPurchase()
                return
            }
            _state.value =
                _state.value.copy(
                    billingState = SupportBillingState.Unavailable(reason),
                    products = emptyList(),
                    isPurchaseInProgress = false,
                )
        }

        private fun showNetworkError() {
            if (hasUnresolvedPendingPurchase) {
                showPendingPurchase()
                return
            }
            _state.value =
                _state.value.copy(
                    billingState = SupportBillingState.NetworkError,
                    isPurchaseInProgress = false,
                )
        }

        private fun showPendingPurchase() {
            _state.value =
                _state.value.copy(
                    billingState = SupportBillingState.Pending,
                    isPurchaseInProgress = false,
                )
        }

        private fun showLoading() {
            _state.value =
                _state.value.copy(
                    billingState = SupportBillingState.Loading,
                    isPurchaseInProgress = false,
                )
        }

        private fun log(event: AnalyticsEvent) {
            runCatching { analyticsGateway.log(event) }.exceptionOrNull()?.reportToSentry()
        }
    }

private fun productOrder(productId: String): Int =
    when (productId) {
        COFFEE_SMALL_PRODUCT_ID -> 0
        COFFEE_LARGE_PRODUCT_ID -> 1
        else -> Int.MAX_VALUE
    }

private fun PurchaseOutcome.analyticsOutcome(): String =
    when (this) {
        is PurchaseOutcome.Purchased -> "purchased"
        PurchaseOutcome.Cancelled -> "cancelled"
        PurchaseOutcome.NetworkError -> "network_error"
        is PurchaseOutcome.Unavailable -> "unavailable"
        PurchaseOutcome.Pending -> error("Pending purchases do not complete an attempt")
    }
