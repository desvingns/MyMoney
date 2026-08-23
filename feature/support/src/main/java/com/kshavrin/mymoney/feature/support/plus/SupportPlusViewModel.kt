package com.kshavrin.mymoney.feature.support.plus

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.billing.PlusCatalogState
import com.kshavrin.mymoney.core.domain.billing.PlusPlanId
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.PlusPurchaseState
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionCoordinator
import com.kshavrin.mymoney.core.domain.billing.PlusSubscriptionState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.ui.navigation.PaywallEntryPoint
import com.kshavrin.mymoney.feature.support.R
import com.kshavrin.mymoney.feature.support.paywall.PaywallCatalogState
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlan
import com.kshavrin.mymoney.feature.support.paywall.PaywallPlanId
import com.kshavrin.mymoney.feature.support.paywall.PaywallPurchaseState
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

data class SupportPlusState(
    val catalogState: PaywallCatalogState = PaywallCatalogState.Loading,
    val plans: List<PaywallPlan> = PaywallPlanId.entries.map { PaywallPlan(it) },
    val purchaseState: PaywallPurchaseState = PaywallPurchaseState.Idle,
    val entitlement: UserEntitlement = UserEntitlement.Free,
    @StringRes val errorMessageRes: Int? = null,
)

sealed interface SupportPlusAction {
    data object RequestNotificationPermission : SupportPlusAction
}

@HiltViewModel
class SupportPlusViewModel
    @Inject
    constructor(
        private val coordinator: PlusSubscriptionCoordinator,
        private val analytics: AnalyticsGateway,
    ) : ViewModel() {
        private val errorMessageRes = MutableStateFlow<Int?>(null)

        val state: StateFlow<SupportPlusState> =
            combine(coordinator.state, errorMessageRes) { subscriptionState, errorRes ->
                subscriptionState.toSupportPlusState(errorRes)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = coordinator.state.value.toSupportPlusState(errorMessageRes.value),
            )

        private val _actions =
            MutableSharedFlow<SupportPlusAction>(
                replay = 0,
                extraBufferCapacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<SupportPlusAction> = _actions.asSharedFlow()

        init {
            analytics.log(AnalyticsEvent.PaywallShown(PaywallEntryPoint.SupportSection.name))
            refreshCatalog()
        }

        fun onPlanSelected(planId: PaywallPlanId) {
            errorMessageRes.value = null
            viewModelScope.launch {
                when (coordinator.purchase(planId.toDomain())) {
                    PlusPurchaseOutcome.Purchased -> _actions.emit(SupportPlusAction.RequestNotificationPermission)
                    PlusPurchaseOutcome.Failed -> errorMessageRes.value = R.string.paywall_purchase_error
                    PlusPurchaseOutcome.Pending,
                    PlusPurchaseOutcome.Cancelled,
                    PlusPurchaseOutcome.Unavailable,
                    PlusPurchaseOutcome.NotStarted,
                    -> Unit
                }
            }
        }

        fun onRetryClicked() {
            refreshCatalog()
        }

        private fun refreshCatalog() {
            errorMessageRes.value = null
            viewModelScope.launch { coordinator.refreshCatalog() }
        }
    }

private fun PlusSubscriptionState.toSupportPlusState(
    @StringRes errorMessageRes: Int?,
): SupportPlusState =
    SupportPlusState(
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
