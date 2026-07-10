package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import com.kshavrin.mymoney.core.domain.model.LoanProjection
import com.kshavrin.mymoney.core.domain.model.SavingsGoalInput
import com.kshavrin.mymoney.core.domain.model.SavingsProjection
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
import com.kshavrin.mymoney.core.domain.usecase.ContributionCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalLoanCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalSavingsProjector
import com.kshavrin.mymoney.core.domain.usecase.capitalVsBalanceDelta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class GoalEditViewModel
    @Inject
    constructor(
        private val goalRepository: GoalRepository,
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val savingsProjector: GoalSavingsProjector,
        private val loanCalculator: GoalLoanCalculator,
        private val contributionCalculator: ContributionCalculator,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val goalId: Long = savedStateHandle.get<Long>("id") ?: -1L

        private val _state = MutableStateFlow(GoalEditState(id = goalId, isCreateMode = goalId == -1L))
        val state: StateFlow<GoalEditState> = _state.asStateFlow()

        private val _actions = MutableSharedFlow<GoalEditAction>(extraBufferCapacity = 4)
        val actions: SharedFlow<GoalEditAction> = _actions.asSharedFlow()

        init {
            viewModelScope.launch {
                val accounts = accountRepository.observeActive().first()
                _state.value = _state.value.copy(accounts = accounts)
                if (goalId != -1L) {
                    goalRepository.findById(goalId)?.let { existing ->
                        val breakdown = existing.contributionBreakdown
                        _state.value =
                            _state.value.copy(
                                name = existing.name,
                                iconKey = existing.iconKey,
                                variant = existing.variant,
                                accountId = existing.accountId,
                                startingCapital = formatInput(existing.startingCapital),
                                monthlyContribution = formatInput(existing.monthlyContribution),
                                targetAmount = formatInput(existing.targetAmount),
                                annualRatePercent = existing.annualRatePercent?.let(::formatInput).orEmpty(),
                                downPayment = existing.downPayment?.let(::formatInput).orEmpty(),
                                termYears = existing.termMonths?.let { (it / 12).toString() }.orEmpty(),
                                isCreateMode = false,
                                createdAt = existing.createdAt,
                                advancedContribution = breakdown.enabled,
                                incomeRows = breakdown.incomes.map { it.toRowUi() },
                                expenseRows = breakdown.expenses.map { it.toRowUi() },
                            )
                        selectAccount(existing.accountId)
                    }
                } else {
                    accounts.firstOrNull()?.let { selectAccount(it.id) }
                }
            }
        }

        fun onEvent(event: GoalEditEvent) {
            when (event) {
                is GoalEditEvent.NameChanged ->
                    _state.value = _state.value.copy(name = event.value)
                is GoalEditEvent.IconSelected ->
                    _state.value = _state.value.copy(iconKey = event.iconKey)
                is GoalEditEvent.VariantChanged -> {
                    _state.value =
                        if (event.variant == GoalVariant.SAVINGS) {
                            _state.value.copy(
                                variant = event.variant,
                                annualRatePercent = "",
                                downPayment = "",
                                termYears = "",
                            )
                        } else {
                            _state.value.copy(variant = event.variant)
                        }
                    recompute()
                }
                is GoalEditEvent.RateChanged -> {
                    _state.value = _state.value.copy(annualRatePercent = event.value, errorMessage = null)
                    recompute()
                }
                is GoalEditEvent.DownPaymentChanged -> {
                    _state.value = _state.value.copy(downPayment = event.value, errorMessage = null)
                    recompute()
                }
                is GoalEditEvent.TermYearsChanged -> {
                    _state.value = _state.value.copy(termYears = event.value)
                    recompute()
                }
                is GoalEditEvent.AccountSelected ->
                    viewModelScope.launch { selectAccount(event.id) }
                is GoalEditEvent.StartingCapitalChanged -> {
                    _state.value = _state.value.copy(startingCapital = event.value, errorMessage = null)
                    recompute()
                }
                is GoalEditEvent.MonthlyChanged -> {
                    _state.value = _state.value.copy(monthlyContribution = event.value, errorMessage = null)
                    recompute()
                }
                is GoalEditEvent.TargetChanged -> {
                    _state.value = _state.value.copy(targetAmount = event.value, errorMessage = null)
                    recompute()
                }
                is GoalEditEvent.AdvancedToggled -> {
                    if (event.enabled) {
                        val seedIncomes =
                            _state.value.incomeRows.isEmpty() &&
                                _state.value.expenseRows.isEmpty()
                        _state.value =
                            _state.value.copy(
                                advancedContribution = true,
                                incomeRows = if (seedIncomes) listOf(ContributionRowUi()) else _state.value.incomeRows,
                                expenseRows = if (seedIncomes) listOf(ContributionRowUi()) else _state.value.expenseRows,
                            )
                        recompute()
                    } else {
                        _state.value = _state.value.copy(advancedContribution = false)
                    }
                }
                GoalEditEvent.IncomeAdded -> {
                    _state.value =
                        _state.value.copy(
                            incomeRows = _state.value.incomeRows + ContributionRowUi(),
                        )
                    recompute()
                }
                GoalEditEvent.ExpenseAdded -> {
                    _state.value =
                        _state.value.copy(
                            expenseRows = _state.value.expenseRows + ContributionRowUi(),
                        )
                    recompute()
                }
                is GoalEditEvent.IncomeRemoved -> {
                    _state.value =
                        _state.value.copy(
                            incomeRows = _state.value.incomeRows.removeAt(event.index),
                        )
                    recompute()
                }
                is GoalEditEvent.ExpenseRemoved -> {
                    _state.value =
                        _state.value.copy(
                            expenseRows = _state.value.expenseRows.removeAt(event.index),
                        )
                    recompute()
                }
                is GoalEditEvent.IncomeNameChanged -> {
                    _state.value =
                        _state.value.copy(
                            incomeRows =
                                _state.value.incomeRows.updateAt(event.index) {
                                    it.copy(name = event.value)
                                },
                        )
                    recompute()
                }
                is GoalEditEvent.IncomeAmountChanged -> {
                    _state.value =
                        _state.value.copy(
                            incomeRows =
                                _state.value.incomeRows.updateAt(event.index) {
                                    it.copy(amount = event.value)
                                },
                        )
                    recompute()
                }
                is GoalEditEvent.ExpenseNameChanged -> {
                    _state.value =
                        _state.value.copy(
                            expenseRows =
                                _state.value.expenseRows.updateAt(event.index) {
                                    it.copy(name = event.value)
                                },
                        )
                    recompute()
                }
                is GoalEditEvent.ExpenseAmountChanged -> {
                    _state.value =
                        _state.value.copy(
                            expenseRows =
                                _state.value.expenseRows.updateAt(event.index) {
                                    it.copy(amount = event.value)
                                },
                        )
                    recompute()
                }
                GoalEditEvent.SaveClicked -> save()
                GoalEditEvent.BackClicked ->
                    viewModelScope.launch { _actions.emit(GoalEditAction.NavigateBack) }
            }
        }

        private suspend fun selectAccount(accountId: Long) {
            val account =
                _state.value.accounts.firstOrNull { it.id == accountId }
                    ?: accountRepository.findById(accountId)
            val balance = accountRepository.computeBalance(accountId)
            val currency = account?.let { currencyRepository.findById(it.currencyId) }
            _state.value =
                _state.value.copy(
                    accountId = accountId,
                    currentBalance = balance,
                    currency = currency,
                    currencySymbol = currency?.symbol.orEmpty(),
                    currentBalanceFormatted = currency?.let { formatMoney(balance, it) }.orEmpty(),
                )
            recompute()
        }

        private fun recompute() {
            var s = _state.value
            if (s.advancedContribution) {
                val derived =
                    s.currency
                        ?.let { currency ->
                            contributionCalculator(s.toBreakdown(currency))
                                .toMoneyScale(currency)
                                .let(::formatInput)
                        }
                        ?: formatInput(contributionCalculator(s.toBreakdown()))
                s = s.copy(monthlyContribution = derived)
                _state.value = s
            }
            val target = s.targetAmount.parseMoney()
            val capital = s.startingCapital.parseMoney()
            val monthly = s.monthlyContribution.parseMoney()

            val projection =
                savingsProjector(
                    SavingsGoalInput(
                        targetAmount = target,
                        startingCapital = capital,
                        monthlyContribution = monthly,
                    ),
                    LocalDate.now(),
                )

            val delta = s.currentBalance?.let { capitalVsBalanceDelta(it, capital) }

            val termMonths =
                s.termYears
                    .trim()
                    .toIntOrNull()
                    ?.times(12)
            val loanProjection =
                if (
                    s.variant == GoalVariant.CREDIT &&
                    termMonths != null &&
                    termMonths >= 1
                ) {
                    loanCalculator(
                        LoanGoalInput(
                            targetAmount = target,
                            startingCapital = capital,
                            downPayment = s.downPayment.parseMoney(),
                            annualRatePercent = s.annualRatePercent.parseMoney(),
                            termMonths = termMonths,
                            monthlyContribution = monthly,
                        ),
                    )
                } else {
                    null
                }

            val canSave =
                s.variant != GoalVariant.CREDIT ||
                    (termMonths != null && termMonths >= 1)

            _state.value =
                s.copy(
                    savingsProjection = projection,
                    capitalDelta = delta,
                    capitalDeltaAmountFormatted = s.currency?.let { currency -> delta?.let { formatMoney(it.abs(), currency) } },
                    loanProjection = loanProjection,
                    loanProjectionTotalInterestFormatted =
                        loanProjection?.let { projection ->
                            s.currency?.let { currency -> formatMoney(projection.totalInterest, currency) }
                        },
                    loanProjectionTotalPaidFormatted =
                        loanProjection?.let { projection ->
                            s.currency?.let { currency -> formatMoney(projection.totalPaid, currency) }
                        },
                    loanProjectionMonthlyPaymentFormatted =
                        loanProjection?.let { projection ->
                            s.currency?.let { currency -> formatMoney(projection.baseMonthlyPayment, currency) }
                        },
                    loanProjectionAccumulationMonths = loanProjection?.accumulationMonths,
                    loanProjectionTotalMonths = loanProjection?.totalMonthsToPayoff,
                    canSave = canSave,
                )
        }

        private fun save() {
            if (_state.value.isSaving) return
            val s = _state.value
            if (!s.canSave) return
            val isCredit = s.variant == GoalVariant.CREDIT

            val targetAmount = s.targetAmount.parseMoneyField(s.currency)
            val startingCapital = s.startingCapital.parseMoneyField(s.currency)
            val monthlyContribution = s.monthlyContribution.parseMoneyField(s.currency)
            val annualRatePercent = if (isCredit) s.annualRatePercent.parseMoneyField() else MoneyField.Valid(BigDecimal.ZERO)
            val downPayment = if (isCredit) s.downPayment.parseMoneyField(s.currency) else MoneyField.Valid(BigDecimal.ZERO)
            if (targetAmount is MoneyField.Invalid ||
                startingCapital is MoneyField.Invalid ||
                monthlyContribution is MoneyField.Invalid ||
                annualRatePercent is MoneyField.Invalid ||
                downPayment is MoneyField.Invalid
            ) {
                _state.value = s.copy(errorMessage = "amount_format")
                return
            }
            val targetAmountValue = (targetAmount as MoneyField.Valid).value
            val startingCapitalValue = (startingCapital as MoneyField.Valid).value
            val monthlyContributionValue = (monthlyContribution as MoneyField.Valid).value
            val annualRatePercentValue = (annualRatePercent as MoneyField.Valid).value
            val downPaymentValue = (downPayment as MoneyField.Valid).value

            _state.value = s.copy(isSaving = true)
            viewModelScope.launch {
                try {
                    val now = Instant.now()
                    goalRepository.upsert(
                        Goal(
                            id = if (s.id == -1L) 0L else s.id,
                            name = s.name,
                            iconKey = s.iconKey,
                            colorHex = s.colorHex,
                            accountId = s.accountId,
                            variant = s.variant,
                            targetAmount = targetAmountValue,
                            startingCapital = startingCapitalValue,
                            monthlyContribution = monthlyContributionValue,
                            annualRatePercent = if (isCredit) annualRatePercentValue else null,
                            downPayment = if (isCredit) downPaymentValue else null,
                            termMonths =
                                if (isCredit) {
                                    s.termYears
                                        .trim()
                                        .toIntOrNull()
                                        ?.times(12)
                                } else {
                                    null
                                },
                            createdAt = s.createdAt ?: now,
                            updatedAt = now,
                            isArchived = false,
                            contributionBreakdown = s.toBreakdown(s.currency),
                        ),
                    )
                    _actions.emit(GoalEditAction.NavigateBack)
                } finally {
                    _state.value = _state.value.copy(isSaving = false)
                }
            }
        }
    }

private fun String.parseMoneyOrNull(): BigDecimal? =
    trim().replace(',', '.').toBigDecimalOrNull()

private fun String.parseMoney(): BigDecimal =
    parseMoneyOrNull() ?: BigDecimal.ZERO

private sealed interface MoneyField {
    data class Valid(
        val value: BigDecimal,
    ) : MoneyField

    data object Invalid : MoneyField
}

private fun String.parseMoneyField(currency: Currency? = null): MoneyField {
    val normalized = trim()
    if (normalized.isBlank()) return MoneyField.Valid(currency?.let { BigDecimal.ZERO.toMoneyScale(it) } ?: BigDecimal.ZERO)
    val parsed = normalized.replace(',', '.').toBigDecimalOrNull()
    return if (parsed != null) MoneyField.Valid(currency?.let { parsed.toMoneyScale(it) } ?: parsed) else MoneyField.Invalid
}

private fun ContributionItem.toRowUi(): ContributionRowUi =
    ContributionRowUi(name = name, amount = formatInput(amount))

private fun List<ContributionRowUi>.toItems(currency: Currency?): List<ContributionItem> =
    map {
        val amount = it.amount.parseMoney()
        ContributionItem(it.name, currency?.let { selectedCurrency -> amount.toMoneyScale(selectedCurrency) } ?: amount)
    }

private fun GoalEditState.toBreakdown(currency: Currency? = null): ContributionBreakdown =
    ContributionBreakdown(
        enabled = advancedContribution,
        incomes = incomeRows.toItems(currency),
        expenses = expenseRows.toItems(currency),
    )

private fun <T> List<T>.updateAt(
    index: Int,
    transform: (T) -> T,
): List<T> =
    if (index in indices) toMutableList().also { it[index] = transform(it[index]) } else this

private fun <T> List<T>.removeAt(index: Int): List<T> =
    if (index in indices) toMutableList().also { it.removeAt(index) } else this

private fun formatMoney(
    amount: BigDecimal,
    currency: Currency,
): String =
    MoneyFormatter.format(
        amount = amount,
        currencySymbol = currency.symbol,
        decimalDigits = minOf(currency.decimalDigits, 2),
        locale = Locale.getDefault(),
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )

private fun formatInput(amount: BigDecimal): String =
    MoneyFormatter.formatInput(amount, Locale.getDefault())

data class GoalEditState(
    val id: Long = -1L,
    val isCreateMode: Boolean = true,
    val name: String = "",
    val iconKey: String = "ic_goal_other",
    val variant: GoalVariant = GoalVariant.SAVINGS,
    val accountId: Long = -1L,
    val accounts: List<Account> = emptyList(),
    val currentBalance: BigDecimal? = null,
    val currentBalanceFormatted: String = "",
    val currency: Currency? = null,
    val currencySymbol: String = "",
    val colorHex: String = "#9C5BB8",
    val startingCapital: String = "",
    val monthlyContribution: String = "",
    val targetAmount: String = "",
    val annualRatePercent: String = "",
    val downPayment: String = "",
    val termYears: String = "",
    val savingsProjection: SavingsProjection? = null,
    val capitalDelta: BigDecimal? = null,
    val capitalDeltaAmountFormatted: String? = null,
    val loanProjection: LoanProjection? = null,
    val loanProjectionTotalInterestFormatted: String? = null,
    val loanProjectionTotalPaidFormatted: String? = null,
    val loanProjectionMonthlyPaymentFormatted: String? = null,
    val loanProjectionAccumulationMonths: Int? = null,
    val loanProjectionTotalMonths: Int? = null,
    val canSave: Boolean = true,
    val advancedContribution: Boolean = false,
    val incomeRows: List<ContributionRowUi> = emptyList(),
    val expenseRows: List<ContributionRowUi> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val createdAt: Instant? = null,
)

data class ContributionRowUi(
    val name: String = "",
    val amount: String = "",
)

sealed interface GoalEditEvent {
    data class NameChanged(
        val value: String,
    ) : GoalEditEvent

    data class IconSelected(
        val iconKey: String,
    ) : GoalEditEvent

    data class VariantChanged(
        val variant: GoalVariant,
    ) : GoalEditEvent

    data class AccountSelected(
        val id: Long,
    ) : GoalEditEvent

    data class StartingCapitalChanged(
        val value: String,
    ) : GoalEditEvent

    data class MonthlyChanged(
        val value: String,
    ) : GoalEditEvent

    data class TargetChanged(
        val value: String,
    ) : GoalEditEvent

    data class RateChanged(
        val value: String,
    ) : GoalEditEvent

    data class DownPaymentChanged(
        val value: String,
    ) : GoalEditEvent

    data class TermYearsChanged(
        val value: String,
    ) : GoalEditEvent

    data class AdvancedToggled(
        val enabled: Boolean,
    ) : GoalEditEvent

    data object IncomeAdded : GoalEditEvent

    data object ExpenseAdded : GoalEditEvent

    data class IncomeRemoved(
        val index: Int,
    ) : GoalEditEvent

    data class ExpenseRemoved(
        val index: Int,
    ) : GoalEditEvent

    data class IncomeNameChanged(
        val index: Int,
        val value: String,
    ) : GoalEditEvent

    data class IncomeAmountChanged(
        val index: Int,
        val value: String,
    ) : GoalEditEvent

    data class ExpenseNameChanged(
        val index: Int,
        val value: String,
    ) : GoalEditEvent

    data class ExpenseAmountChanged(
        val index: Int,
        val value: String,
    ) : GoalEditEvent

    data object SaveClicked : GoalEditEvent

    data object BackClicked : GoalEditEvent
}

sealed interface GoalEditAction {
    data object NavigateBack : GoalEditAction
}
