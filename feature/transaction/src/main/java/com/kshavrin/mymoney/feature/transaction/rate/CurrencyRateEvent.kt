package com.kshavrin.mymoney.feature.transaction.rate

sealed interface CurrencyRateEvent {
    data class RateInputChanged(val text: String) : CurrencyRateEvent
    data object SaveClicked : CurrencyRateEvent
    data object BackClicked : CurrencyRateEvent
    data object DismissError : CurrencyRateEvent
}
