package com.kshavrin.mymoney.feature.support.paywall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.billing.PlusCatalogState
import com.kshavrin.mymoney.core.domain.billing.PlusPlanId
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseState
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionCoordinator
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionState
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import com.kshavrin.mymoney.feature.support.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel
    @Inject
    constructor(
        private val coordinator: PlusSubscriptionCoordinator,
        private val analytics: AnalyticsGateway,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val entryPoint = savedStateHandle.toRoute<Destinations.Paywall>().entryPoint

        private val errorMessageRes = MutableStateFlow<Int?>(null)

        val state: StateFlow<PaywallState> =
            combine(coordinator.state, errorMessageRes) { subscriptionState, errorRes ->
                subscriptionState.toPaywallState(errorRes)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = coordinator.state.value.toPaywallState(errorMessageRes.value),
            )

        private val _actions =
            MutableSharedFlow<PaywallAction>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<PaywallAction> = _actions.asSharedFlow()

        init {
            analytics.log(AnalyticsEvent.PaywallShown(entryPoint.name))
            refreshCatalog()
        }

        fun onEvent(event: PaywallEvent) {
            when (event) {
                PaywallEvent.BackClicked -> viewModelScope.launch { _actions.emit(PaywallAction.NavigateBack) }
                is PaywallEvent.PlanSelected -> purchase(event.planId)
                PaywallEvent.RetryClicked -> refreshCatalog()
            }
        }

        private fun refreshCatalog() {
            errorMessageRes.value = null
            viewModelScope.launch { coordinator.refreshCatalog() }
        }

        private fun purchase(planId: PaywallPlanId) {
            errorMessageRes.value = null
            viewModelScope.launch {
                when (coordinator.purchase(planId.toDomain())) {
                    PlusPurchaseOutcome.Purchased -> _actions.emit(PaywallAction.RequestNotificationPermission)
                    PlusPurchaseOutcome.Failed -> errorMessageRes.value = R.string.paywall_purchase_error
                    PlusPurchaseOutcome.Pending,
                    PlusPurchaseOutcome.Cancelled,
                    PlusPurchaseOutcome.Unavailable,
                    PlusPurchaseOutcome.NotStarted,
                    -> Unit
                }
            }
        }
    }

private fun PlusSubscriptionState.toPaywallState(
    @androidx.annotation.StringRes errorMessageRes: Int?,
): PaywallState =
    PaywallState(
        catalogState = catalog.toPaywallCatalogState(),
        plans =
            PaywallPlanId.entries.map { plan ->
                PaywallPlan(id = plan, formattedPrice = prices[plan.toDomain()])
            },
        purchaseState = purchase.toPaywallPurchaseState(),
        entitlement = entitlement,
        errorMessageRes = errorMessageRes,
    )

private fun PlusCatalogState.toPaywallCatalogState(): PaywallCatalogState =
    when (this) {
        PlusCatalogState.Loading -> PaywallCatalogState.Loading
        PlusCatalogState.Available -> PaywallCatalogState.Available
        PlusCatalogState.UnavailableInRegion -> PaywallCatalogState.UnavailableInRegion
        PlusCatalogState.Unavailable -> PaywallCatalogState.Unavailable
        PlusCatalogState.Error -> PaywallCatalogState.Error
    }

private fun PlusPurchaseState.toPaywallPurchaseState(): PaywallPurchaseState =
    when (this) {
        PlusPurchaseState.Idle -> PaywallPurchaseState.Idle
        PlusPurchaseState.InProgress -> PaywallPurchaseState.InProgress
        PlusPurchaseState.ReconcilingEntitlement -> PaywallPurchaseState.ReconcilingEntitlement
        PlusPurchaseState.AwaitingEntitlement -> PaywallPurchaseState.AwaitingEntitlement
        PlusPurchaseState.Pending -> PaywallPurchaseState.Pending
    }

private fun PaywallPlanId.toDomain(): PlusPlanId =
    when (this) {
        PaywallPlanId.Monthly -> PlusPlanId.Monthly
        PaywallPlanId.Yearly -> PlusPlanId.Yearly
    }
