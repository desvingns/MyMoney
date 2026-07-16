package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kshavrin.mymoney.core.common.calculator.CalculatorEngine
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.keypad.toCalculator
import com.kshavrin.mymoney.core.designsystem.keypad.toDesignsystem
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import com.kshavrin.mymoney.feature.transactionslist.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
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
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel
    @Inject
    constructor(
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val categoryRepository: CategoryRepository,
        private val currencyRateRepository: CurrencyRateRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val transactionId: Long =
            savedStateHandle.toRoute<Destinations.TransactionDetail>().transactionId

        private val engine = CalculatorEngine()

        private var original: Transaction? = null

        private val _state = MutableStateFlow(TransactionDetailState(transactionId = transactionId))
        val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<TransactionDetailAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<TransactionDetailAction> = _actions.asSharedFlow()

        private var allCategories: List<Category> = emptyList()

        init {
            load()
            observeCategories()
        }

        private fun observeCategories() {
            viewModelScope.launch {
                categoryRepository.observeAll().collect { all ->
                    allCategories = all
                    _state.value = _state.value.copy(categories = filteredCategories())
                }
            }
        }

        // The category step lists only categories of the transaction's own kind; transfers have none.
        private fun filteredCategories(): List<Category> {
            val target = categoryKindFor(_state.value.kind) ?: return emptyList()
            return allCategories
                .filter { it.kind == target && !it.isArchived }
                .sortedBy { it.sortOrder }
        }

        private fun load() {
            viewModelScope.launch {
                val tx = transactionRepository.findById(transactionId) ?: return@launch
                original = tx
                val accounts = accountRepository.observeActive().first()
                val currencies = currencyRepository.observeActive().first()
                val account = accounts.firstOrNull { it.id == tx.accountId }
                val currency = currencies.firstOrNull { it.id == tx.currencyId }
                val category = tx.categoryId?.let { categoryRepository.findById(it) }
                val targetAccount = tx.toAccountId?.let { id -> accounts.firstOrNull { it.id == id } }
                val targetCurrency =
                    targetAccount?.let { acc ->
                        currencies.firstOrNull { it.id == acc.currencyId }
                    }
                seedEngine(tx.amount)
                _state.value =
                    _state.value.copy(
                        kind = tx.kind,
                        amount = engine.currentValue,
                        amountInput = engine.display,
                        expression = engine.expression,
                        note = tx.note.orEmpty(),
                        occurredAt = tx.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate(),
                        account = account,
                        currency = currency,
                        category = category,
                        targetAccount = targetAccount,
                        targetCurrency = targetCurrency,
                        exchangeRate = tx.exchangeRate,
                        rateInput =
                            tx.exchangeRate
                                ?.let { MoneyFormatter.formatInput(BigDecimal.valueOf(it), Locale.getDefault()) }
                                .orEmpty(),
                        accounts = accounts,
                        currencies = currencies,
                        isLoaded = true,
                        isDirty = false,
                    )
                _state.value = _state.value.copy(categories = filteredCategories())
            }
        }

        fun onEvent(event: TransactionDetailEvent) {
            when (event) {
                is TransactionDetailEvent.KeypadDigit -> {
                    engine.inputDigit(event.d)
                    syncFromEngine()
                }
                is TransactionDetailEvent.KeypadOperator -> {
                    engine.inputOperator(event.op.toCalculator())
                    syncFromEngine()
                }
                TransactionDetailEvent.KeypadDot -> {
                    engine.inputDot()
                    syncFromEngine()
                }
                TransactionDetailEvent.KeypadBackspace -> {
                    engine.backspace()
                    syncFromEngine()
                }
                TransactionDetailEvent.KeypadEquals -> {
                    engine.equals()
                    syncFromEngine()
                }
                is TransactionDetailEvent.NoteChanged -> {
                    _state.value = _state.value.copy(note = event.text, errorBannerRes = null)
                    recomputeDirty()
                }
                is TransactionDetailEvent.DateChanged -> {
                    _state.value = _state.value.copy(occurredAt = event.date)
                    recomputeDirty()
                }
                is TransactionDetailEvent.AccountChanged -> onAccountChanged(event.accountId)
                is TransactionDetailEvent.TargetAccountChanged -> onTargetAccountChanged(event.accountId)
                is TransactionDetailEvent.RateChanged -> onRateChanged(event.text)
                TransactionDetailEvent.SelectCategoryClicked -> onSelectCategoryClicked()
                TransactionDetailEvent.BackToAmount ->
                    _state.value = _state.value.copy(categoryStep = false, errorBannerRes = null)
                TransactionDetailEvent.AddCategoryClicked -> onAddCategoryClicked()
                is TransactionDetailEvent.CategoryPicked -> onCategoryPicked(event.categoryId)
                TransactionDetailEvent.SaveClicked -> save()
                TransactionDetailEvent.DeleteClicked ->
                    _state.value = _state.value.copy(confirmDeleteVisible = true)
                TransactionDetailEvent.DismissDelete ->
                    _state.value = _state.value.copy(confirmDeleteVisible = false)
                TransactionDetailEvent.ConfirmDelete -> delete()
                is TransactionDetailEvent.UndoDeleteClicked -> restore(event.id)
                TransactionDetailEvent.BackClicked -> emit(TransactionDetailAction.NavigateBack)
                TransactionDetailEvent.DismissError ->
                    _state.value = _state.value.copy(errorBannerRes = null)
            }
        }

        private fun syncFromEngine() {
            _state.value =
                _state.value.copy(
                    amount = engine.currentValue,
                    amountInput = engine.display,
                    expression = engine.expression,
                    pendingOperator = engine.pendingOp.toDesignsystem(),
                    errorBannerRes = null,
                )
            recomputeDirty()
        }

        private fun onAccountChanged(accountId: Long) {
            val acc = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
            val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
            _state.value = _state.value.copy(account = acc, currency = cur)
            recomputeDirty()
        }

        private fun onTargetAccountChanged(accountId: Long) {
            val acc = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
            val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
            _state.value = _state.value.copy(targetAccount = acc, targetCurrency = cur)
            recomputeDirty()
        }

        private fun onRateChanged(text: String) {
            _state.value =
                _state.value.copy(
                    rateInput = text,
                    exchangeRate = text.trim().replace(',', '.').toDoubleOrNull(),
                    errorBannerRes = null,
                )
            recomputeDirty()
        }

        private fun onSelectCategoryClicked() {
            val s = _state.value
            if (s.amount <= BigDecimal.ZERO) {
                _state.value = s.copy(errorBannerRes = R.string.detail_error_enter_amount)
                return
            }
            _state.value = s.copy(categoryStep = true, errorBannerRes = null)
        }

        private fun onAddCategoryClicked() {
            val kind = categoryKindFor(_state.value.kind) ?: return
            emit(TransactionDetailAction.NavigateToCreateCategory(kind.name))
        }

        // Editing a category is explicit-save (unlike New, where picking saves immediately):
        // we only set the chosen category and return to the amount step so the user can review
        // and confirm via the Save FAB.
        private fun onCategoryPicked(categoryId: Long) {
            viewModelScope.launch {
                val category = categoryRepository.findById(categoryId) ?: return@launch
                _state.value =
                    _state.value.copy(
                        category = category,
                        categoryStep = false,
                        errorBannerRes = null,
                    )
                recomputeDirty()
            }
        }

        private fun recomputeDirty() {
            val tx = original ?: return
            val s = _state.value
            val originalDate = tx.occurredAt.atZone(ZoneId.systemDefault()).toLocalDate()
            val dirty =
                s.amount.compareTo(tx.amount) != 0 ||
                    s.note != tx.note.orEmpty() ||
                    s.occurredAt != originalDate ||
                    s.account?.id != tx.accountId ||
                    s.category?.id != tx.categoryId ||
                    s.targetAccount?.id != tx.toAccountId ||
                    !ratesEqual(s.exchangeRate, tx.exchangeRate)
            _state.value = _state.value.copy(isDirty = dirty)
        }

        private fun save() {
            if (_state.value.isSaving) return
            val tx = original ?: return
            val s = _state.value
            if (s.amount <= BigDecimal.ZERO) {
                _state.value = s.copy(errorBannerRes = R.string.detail_error_enter_amount)
                return
            }
            val account = s.account ?: return
            val currency = s.currency ?: return
            if (s.isTransfer) {
                saveTransfer(tx, s, account, currency)
                return
            }
            _state.value = s.copy(isSaving = true)
            viewModelScope.launch {
                try {
                    val updated =
                        tx.copy(
                            amount = s.amount,
                            currencyId = currency.id,
                            accountId = account.id,
                            categoryId = s.category?.id ?: tx.categoryId,
                            note = s.note.takeIf { it.isNotBlank() },
                            occurredAt = s.occurredAt.toLocalInstant(),
                            updatedAt = Instant.now(),
                        )
                    transactionRepository.upsert(updated)
                    _state.value = _state.value.copy(isSaving = false)
                    _actions.emit(TransactionDetailAction.NavigateBack)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            errorBannerRes = R.string.detail_error_save_failed,
                        )
                }
            }
        }

        private fun saveTransfer(
            original: Transaction,
            s: TransactionDetailState,
            account: Account,
            currency: Currency,
        ) {
            val target = s.targetAccount
            val targetCurrency = s.targetCurrency
            if (target == null || targetCurrency == null || target.id == account.id) {
                _state.value = s.copy(errorBannerRes = R.string.detail_error_save_failed)
                return
            }
            val crossCurrency = currency.id != targetCurrency.id
            val rate = if (crossCurrency) s.exchangeRate else null
            if (crossCurrency && (rate == null || rate <= 0.0)) {
                _state.value = s.copy(errorBannerRes = R.string.detail_error_enter_rate)
                return
            }
            _state.value = s.copy(isSaving = true)
            viewModelScope.launch {
                try {
                    val now = Instant.now()
                    val toAmount =
                        if (crossCurrency && rate != null) {
                            s.amount.multiply(BigDecimal.valueOf(rate))
                        } else {
                            s.amount
                        }
                    val updated =
                        original.copy(
                            amount = s.amount,
                            currencyId = currency.id,
                            accountId = account.id,
                            note = s.note.takeIf { it.isNotBlank() },
                            occurredAt = s.occurredAt.toLocalInstant(),
                            updatedAt = now,
                            toAccountId = target.id,
                            toAmount = toAmount,
                            exchangeRate = rate,
                        )
                    transactionRepository.upsert(updated)
                    if (crossCurrency && rate != null && !ratesEqual(rate, original.exchangeRate)) {
                        upsertRate(currency.id, targetCurrency.id, rate, now)
                    }
                    _state.value = _state.value.copy(isSaving = false)
                    _actions.emit(TransactionDetailAction.NavigateBack)
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            errorBannerRes = R.string.detail_error_save_failed,
                        )
                }
            }
        }

        private suspend fun upsertRate(
            fromId: Long,
            toId: Long,
            rate: Double,
            now: Instant,
        ) {
            val existing = currencyRateRepository.findRate(fromId, toId)
            currencyRateRepository.upsert(
                CurrencyRate(
                    id = existing?.id ?: 0L,
                    fromCurrencyId = fromId,
                    toCurrencyId = toId,
                    rate = rate,
                    updatedAt = now,
                ),
            )
        }

        private fun delete() {
            if (_state.value.isSaving) return
            val s = _state.value
            _state.value = s.copy(isSaving = true, confirmDeleteVisible = false)
            viewModelScope.launch {
                try {
                    transactionRepository.softDelete(transactionId, Instant.now())
                    _state.value = _state.value.copy(isSaving = false)
                    // The screen runs the 5s UNDO window on its own SnackbarHost, then pops back to
                    // S12; emitting NavigateBack here would dispose the host before it can show.
                    _actions.emit(TransactionDetailAction.ShowUndoSnackbar(transactionId))
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            errorBannerRes = R.string.detail_error_save_failed,
                        )
                }
            }
        }

        private fun restore(id: Long) {
            viewModelScope.launch {
                try {
                    transactionRepository.restore(id, Instant.now())
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                }
            }
        }

        // CalculatorEngine has no public setter; replay the loaded amount through the keypad API
        // so editing continues from the existing value instead of starting at zero.
        private fun seedEngine(amount: BigDecimal) {
            engine.clear()
            val plain = amount.stripTrailingZeros().toPlainString()
            for (ch in plain) {
                when (ch) {
                    '.' -> engine.inputDot()
                    in '0'..'9' -> engine.inputDigit(ch - '0')
                    else -> Unit
                }
            }
        }

        private fun emit(action: TransactionDetailAction) {
            viewModelScope.launch { _actions.emit(action) }
        }

        private fun LocalDate.toLocalInstant(): Instant =
            atStartOfDay(ZoneId.systemDefault()).toInstant()

        private fun categoryKindFor(kind: TransactionKind): CategoryKind? =
            when (kind) {
                TransactionKind.Expense -> CategoryKind.Expense
                TransactionKind.Income -> CategoryKind.Income
                TransactionKind.Transfer -> null
            }

        companion object {
            const val KEY_TRANSACTION_ID = "transactionId"
            const val KEY_CREATED_CATEGORY_ID = "createdCategoryId"

            private fun ratesEqual(
                a: Double?,
                b: Double?,
            ): Boolean = a == b
        }
    }
