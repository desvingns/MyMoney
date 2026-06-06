package com.kshavrin.mymoney.feature.transactionslist.detail

import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import java.math.BigDecimal
import java.time.LocalDate

data class TransactionDetailState(
    val transactionId: Long = 0L,
    val kind: TransactionKind = TransactionKind.Expense,
    val amount: BigDecimal = BigDecimal.ZERO,
    val amountInput: String = "0",
    val expression: String = "",
    val pendingOperator: Operator? = null,
    val note: String = "",
    val occurredAt: LocalDate = LocalDate.now(),
    val account: Account? = null,
    val currency: Currency? = null,
    val category: Category? = null,
    val targetAccount: Account? = null,
    val targetCurrency: Currency? = null,
    val exchangeRate: Double? = null,
    val rateInput: String = "",
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryStep: Boolean = false,
    val isLoaded: Boolean = false,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val confirmDeleteVisible: Boolean = false,
    val errorBannerRes: Int? = null,
) {
    val isTransfer: Boolean get() = kind == TransactionKind.Transfer

    val isCrossCurrency: Boolean
        get() = isTransfer &&
            currency != null &&
            targetCurrency != null &&
            currency.id != targetCurrency.id

    val canSave: Boolean
        get() = isLoaded && !isSaving && isDirty && amount > BigDecimal.ZERO
}
