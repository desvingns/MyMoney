package com.kshavrin.mymoney.core.designsystem.icon

import androidx.annotation.DrawableRes
import com.kshavrin.mymoney.core.designsystem.R

@DrawableRes
fun accountNeonIconResOrNull(iconKey: String): Int? =
    accountNeonIconAssets[iconKey]

private val accountNeonIconAssets: Map<String, Int> =
    mapOf(
        "ic_account_wallet" to R.drawable.category_neon_wallet,
        "ic_account_cash" to R.drawable.category_neon_cash,
        "ic_account_cash_bills" to R.drawable.category_neon_cash,
        "ic_account_coins" to R.drawable.category_neon_savings,
        "ic_account_card" to R.drawable.category_neon_credit_card,
        "ic_account_debit_card" to R.drawable.category_neon_credit_card,
        "ic_account_credit_score" to R.drawable.category_neon_credit_card,
        "ic_account_ewallet" to R.drawable.category_neon_wallet,
        "ic_account_bank" to R.drawable.category_neon_bank,
        "ic_account_atm" to R.drawable.category_neon_cash,
        "ic_account_savings" to R.drawable.category_neon_savings,
        "ic_account_safe" to R.drawable.category_neon_security,
        "ic_account_investment" to R.drawable.category_neon_investment_growth,
        "ic_account_crypto" to R.drawable.category_neon_currency_exchange,
        "ic_account_currency_exchange" to R.drawable.category_neon_currency_exchange,
        "ic_account_loan" to R.drawable.category_neon_notebook,
        "ic_account_cheque" to R.drawable.category_neon_notebook,
        "ic_account_business" to R.drawable.category_neon_briefcase,
        "ic_account_pension" to R.drawable.category_neon_care,
        "ic_account_insurance" to R.drawable.category_neon_insurance,
        "ic_account_rewards" to R.drawable.category_neon_discount,
        "ic_account_gift_card" to R.drawable.category_neon_gift,
        "ic_account_transit_card" to R.drawable.category_neon_train,
        "ic_account_family" to R.drawable.category_neon_care,
    )
