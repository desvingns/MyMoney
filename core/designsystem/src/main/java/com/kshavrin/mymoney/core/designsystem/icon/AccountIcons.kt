package com.kshavrin.mymoney.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.BusinessCenter
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Commute
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.CreditScore
import androidx.compose.material.icons.outlined.CurrencyBitcoin
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payment
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.ui.graphics.vector.ImageVector

fun accountIcon(iconKey: String?): ImageVector = when (iconKey) {
    "ic_account_cash" -> Icons.Outlined.Payments
    "ic_account_card" -> Icons.Outlined.CreditCard
    "ic_account_bank" -> Icons.Outlined.AccountBalance
    "ic_account_savings" -> Icons.Outlined.Savings
    "ic_account_wallet" -> Icons.Outlined.AccountBalanceWallet
    "ic_account_debit_card" -> Icons.Outlined.Payment
    "ic_account_credit_score" -> Icons.Outlined.CreditScore
    "ic_account_cash_bills" -> Icons.Outlined.AttachMoney
    "ic_account_coins" -> Icons.Outlined.Toll
    "ic_account_ewallet" -> Icons.Outlined.Wallet
    "ic_account_crypto" -> Icons.Outlined.CurrencyBitcoin
    "ic_account_safe" -> Icons.Outlined.Lock
    "ic_account_investment" -> Icons.Outlined.TrendingUp
    "ic_account_loan" -> Icons.Outlined.RequestQuote
    "ic_account_business" -> Icons.Outlined.BusinessCenter
    "ic_account_currency_exchange" -> Icons.Outlined.CurrencyExchange
    "ic_account_atm" -> Icons.Outlined.LocalAtm
    "ic_account_cheque" -> Icons.Outlined.ReceiptLong
    "ic_account_rewards" -> Icons.Outlined.Stars
    "ic_account_insurance" -> Icons.Outlined.Shield
    "ic_account_pension" -> Icons.Outlined.Elderly
    "ic_account_gift_card" -> Icons.Outlined.CardGiftcard
    "ic_account_transit_card" -> Icons.Outlined.Commute
    "ic_account_family" -> Icons.Outlined.Group
    else -> Icons.Outlined.AccountBalanceWallet
}
