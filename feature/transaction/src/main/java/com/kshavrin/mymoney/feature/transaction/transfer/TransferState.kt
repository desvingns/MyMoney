package com.kshavrin.mymoney.feature.transaction.transfer

import com.kshavrin.mymoney.core.designsystem.dialog.RateRow
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import java.math.BigDecimal
import java.time.LocalDate

data class TransferState(
    val accounts: List<Account> = emptyList(),
    val currencies: List<Currency> = emptyList(),
    val sourceAccount: Account? = null,
    val targetAccount: Account? = null,
    val sourceCurrency: Currency? = null,
    val targetCurrency: Currency? = null,
    val amount: BigDecimal = BigDecimal.ZERO,
    val amountInput: String = "0",
    val expression: String = "",
    val pendingOperator: Operator? = null,
    val note: String = "",
    val occurredAt: LocalDate = LocalDate.now(),
    val currentRate: CurrencyRate? = null,
    val ratePreviewText: String = "",
    // Rate-confirm dialog (D5): non-null while the every-time dialog is shown for a cross-currency
    // transfer. rateDialogRow drives the dialog; rateDialogFullRate is the unrounded cross-rate
    // from ResolveRateUseCase used for the actual toAmount when the user confirms without editing.
    val rateDialogRow: RateRow? = null,
    val rateDialogFullRate: BigDecimal? = null,
    val sameAccountsError: Boolean = false,
    val isSaving: Boolean = false,
    val errorBannerRes: Int? = null,
    val savedSignal: Long = 0L,
)
