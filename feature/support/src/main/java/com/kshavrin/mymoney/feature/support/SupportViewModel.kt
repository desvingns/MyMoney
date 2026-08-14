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
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

        private val recordedPurchaseTokens = mutableSetOf<String>()
        private var hasUnresolvedPendingPurchase = false

        init {
            log(AnalyticsEvent.SupportOpened)
            observeSupporterState()
            initializeBilling()
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

        private fun initializeBilling() {
            viewModelScope.launch {
                val hasPendingPurchase = restorePendingPurchases()
                try {
                    billingGateway.availability().collect { availability ->
                        applyAvailability(availability, preservePending = hasPendingPurchase)
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                    showNetworkError()
                }
            }
        }

        private fun refreshBilling() {
            viewModelScope.launch {
                val hasPendingPurchase = restorePendingPurchases()
                try {
                    applyAvailability(
                        availability = billingGateway.availability().first(),
                        preservePending = hasPendingPurchase,
                    )
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                    showNetworkError()
                }
            }
        }

        private suspend fun applyAvailability(
            availability: BillingAvailability,
            preservePending: Boolean = false,
        ) {
            when (availability) {
                BillingAvailability.Available -> loadProducts(preservePending)
                BillingAvailability.NetworkUnavailable,
                BillingAvailability.ServiceUnavailable,
                -> showNetworkError()

                BillingAvailability.DisabledInBuild ->
                    showUnavailable(SupportUnavailableReason.DisabledInBuild)
                BillingAvailability.UnavailableOnDevice ->
                    showUnavailable(SupportUnavailableReason.UnavailableOnDevice)
                BillingAvailability.UnavailableInRegion ->
                    showUnavailable(SupportUnavailableReason.UnavailableInRegion)
                is BillingAvailability.UnknownFailure ->
                    showUnavailable(SupportUnavailableReason.Unavailable)
            }
        }

        private suspend fun loadProducts(preservePending: Boolean) {
            val result = withContext(ioDispatcher) { billingGateway.products() }
            result.fold(
                onSuccess = { products -> showAvailableProducts(products, preservePending) },
                onFailure = { throwable ->
                    throwable.reportToSentry()
                    showNetworkError()
                },
            )
        }

        private fun showAvailableProducts(
            products: List<SupportProduct>,
            preservePending: Boolean,
        ) {
            val coffeeProducts =
                products
                    .filter { product -> product.id == COFFEE_SMALL_PRODUCT_ID || product.id == COFFEE_LARGE_PRODUCT_ID }
                    .sortedBy { product -> productOrder(product.id) }
            if (coffeeProducts.isEmpty()) {
                showUnavailable(SupportUnavailableReason.Unavailable)
            } else if (preservePending || hasUnresolvedPendingPurchase) {
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
                    currentState.products.none { product -> product.id == productId }
            ) {
                return
            }
            log(AnalyticsEvent.SupportPurchaseStarted(productId))
            viewModelScope.launch {
                _state.value = _state.value.copy(isPurchaseInProgress = true)
                var completionLogged = false
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
                        handlePurchaseOutcome(outcome)
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    throwable.reportToSentry()
                    if (!completionLogged) {
                        log(AnalyticsEvent.SupportPurchaseCompleted(productId, "network_error"))
                    }
                    showNetworkError()
                }
            }
        }

        private suspend fun handlePurchaseOutcome(outcome: PurchaseOutcome) {
            when (outcome) {
                is PurchaseOutcome.Purchased -> {
                    hasUnresolvedPendingPurchase = false
                    if (recordPurchase(outcome)) {
                        _state.value =
                            _state.value.copy(
                                billingState = SupportBillingState.Available,
                                isPurchaseInProgress = false,
                            )
                    } else {
                        showNetworkError()
                    }
                }

                PurchaseOutcome.Pending -> {
                    hasUnresolvedPendingPurchase = true
                    showPendingPurchase()
                }

                PurchaseOutcome.Cancelled -> {
                    hasUnresolvedPendingPurchase = false
                    _state.value =
                        _state.value.copy(
                            billingState = SupportBillingState.Available,
                            isPurchaseInProgress = false,
                        )
                }

                PurchaseOutcome.NetworkError -> showNetworkError()
                is PurchaseOutcome.Unavailable -> {
                    hasUnresolvedPendingPurchase = false
                    refreshBilling()
                }
            }
        }

        private suspend fun restorePendingPurchases(): Boolean {
            val result = withContext(ioDispatcher) { billingGateway.resolvePendingPurchases() }
            result.onFailure { throwable ->
                throwable.reportToSentry()
                showNetworkError()
            }
            val outcomes = result.getOrNull().orEmpty()
            val hasPendingOutcome = outcomes.any { outcome -> outcome == PurchaseOutcome.Pending }
            val hasNetworkError = outcomes.any { outcome -> outcome == PurchaseOutcome.NetworkError }
            if (hasPendingOutcome) {
                hasUnresolvedPendingPurchase = true
            } else if (!hasNetworkError && result.isSuccess) {
                hasUnresolvedPendingPurchase = false
            }
            outcomes.forEach { outcome ->
                when (outcome) {
                    is PurchaseOutcome.Purchased -> recordPurchase(outcome)
                    PurchaseOutcome.Pending,
                    PurchaseOutcome.NetworkError,
                    PurchaseOutcome.Cancelled,
                    is PurchaseOutcome.Unavailable,
                    -> Unit
                }
            }
            if (hasUnresolvedPendingPurchase) showPendingPurchase()
            else if (hasNetworkError) showNetworkError()
            return hasUnresolvedPendingPurchase
        }

        private suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Boolean {
            if (!recordedPurchaseTokens.add(outcome.purchaseToken)) return true
            val result = supporterRepository.recordPurchase(outcome)
            result.exceptionOrNull()?.reportToSentry()
            if (result.isFailure) recordedPurchaseTokens.remove(outcome.purchaseToken)
            return result.isSuccess
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
