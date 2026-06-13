package com.kshavrin.mymoney.feature.transaction.rate

sealed interface CurrencyRateAction {
    data object NavigateBack : CurrencyRateAction

    data class NavigateBackWithRate(
        val rate: Double,
    ) : CurrencyRateAction
}
