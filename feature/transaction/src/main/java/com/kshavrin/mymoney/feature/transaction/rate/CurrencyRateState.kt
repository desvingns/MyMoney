package com.kshavrin.mymoney.feature.transaction.rate

import com.kshavrin.mymoney.core.domain.model.Currency

data class CurrencyRateState(
    val fromCurrency: Currency? = null,
    val toCurrency: Currency? = null,
    val rateInput: String = "",
    val rate: Double? = null,
    val isSaving: Boolean = false,
    val errorBannerRes: Int? = null,
    val isValid: Boolean = false,
)
