package com.kshavrin.mymoney.feature.support.paywall

sealed interface PaywallAction {
    data object NavigateBack : PaywallAction

    data object RequestNotificationPermission : PaywallAction
}
