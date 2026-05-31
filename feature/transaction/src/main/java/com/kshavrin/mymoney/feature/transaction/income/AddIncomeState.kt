package com.kshavrin.mymoney.feature.transaction.income

import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.Currency
import java.math.BigDecimal
import java.time.LocalDate

data class AddIncomeState(
    val amount: BigDecimal = BigDecimal.ZERO,
    val amountInput: String = "0",
    val currency: Currency? = null,
    val account: Account? = null,
    val category: Category? = null,
    val occurredAt: LocalDate = LocalDate.now(),
    val note: String = "",
    val isCalculatorActive: Boolean = false,
    val pendingOperator: Operator? = null,
    val expression: String = "",
    val isSaving: Boolean = false,
    val savedSignal: Long = 0L,
    val errorBannerRes: Int? = null,
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val categories: List<Category> = emptyList(),
    val keypadVisible: Boolean = false,
)
