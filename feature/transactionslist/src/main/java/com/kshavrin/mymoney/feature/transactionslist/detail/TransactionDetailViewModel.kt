package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.calculator.CalculatorEngine
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.designsystem.keypad.toCalculator
import com.kshavrin.mymoney.core.designsystem.keypad.toDesignsystem
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.R
import dagger.hilt.android.lifecycle.HiltViewModel
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
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRateRepository: CurrencyRateRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val transactionId: Long = savedStateHandle.get<Long>(KEY_TRANSACTION_ID) ?: -1L

    private val engine = CalculatorEngine()

    private var original: Transaction? = null

    private val _state = MutableStateFlow(TransactionDetailState(transactionId = transactionId))
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<TransactionDetailAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<TransactionDetailAction> = _actions.asSharedFlow()

    init {
        load()
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
            val targetCurrency = targetAccount?.let { acc ->
                currencies.firstOrNull { it.id == acc.currencyId }
            }
            seedEngine(tx.amount)
            _state.value = _state.value.copy(
                kind = tx.kind,
                amount = engine.currentValue,
                amountInput = engine.display,
                expression = engine.expression,
                note = tx.note.orEmpty(),
                occurredAt = tx.occurredAt.atZone(ZoneOffset.UTC).toLocalDate(),
                account = account,
                currency = currency,
                category = category,
                targetAccount = targetAccount,
                targetCurrency = targetCurrency,
                exchangeRate = tx.exchangeRate,
                rateInput = tx.exchangeRate?.toString().orEmpty(),
                accounts = accounts,
                currencies = currencies,
                isLoaded = true,
                isDirty = false,
            )
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
        _state.value = _state.value.copy(
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
        _state.value = _state.value.copy(
            rateInput = text,
            exchangeRate = text.toDoubleOrNull(),
            errorBannerRes = null,
        )
        recomputeDirty()
    }

    private fun recomputeDirty() {
        val tx = original ?: return
        val s = _state.value
        val originalDate = tx.occurredAt.atZone(ZoneOffset.UTC).toLocalDate()
        val dirty = s.amount.compareTo(tx.amount) != 0 ||
            s.note != tx.note.orEmpty() ||
            s.occurredAt != originalDate ||
            s.account?.id != tx.accountId ||
            s.targetAccount?.id != tx.toAccountId ||
            !ratesEqual(s.exchangeRate, tx.exchangeRate)
        _state.value = _state.value.copy(isDirty = dirty)
    }

    private fun save() {
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                val updated = tx.copy(
                    amount = s.amount,
                    currencyId = currency.id,
                    accountId = account.id,
                    note = s.note.takeIf { it.isNotBlank() },
                    occurredAt = s.occurredAt.toUtcInstant(),
                    updatedAt = Instant.now(),
                )
                transactionRepository.upsert(updated)
                _state.value = _state.value.copy(isSaving = false)
                _actions.emit(TransactionDetailAction.NavigateBack)
            } catch (t: Throwable) {
                t.reportToSentry()
                _state.value = _state.value.copy(
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
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                val now = Instant.now()
                val toAmount = if (crossCurrency && rate != null) {
                    s.amount.multiply(BigDecimal.valueOf(rate))
                } else {
                    s.amount
                }
                val updated = original.copy(
                    amount = s.amount,
                    currencyId = currency.id,
                    accountId = account.id,
                    note = s.note.takeIf { it.isNotBlank() },
                    occurredAt = s.occurredAt.toUtcInstant(),
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
                t.reportToSentry()
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorBannerRes = R.string.detail_error_save_failed,
                )
            }
        }
    }

    private suspend fun upsertRate(fromId: Long, toId: Long, rate: Double, now: Instant) {
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
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(confirmDeleteVisible = false)
                transactionRepository.softDelete(transactionId, Instant.now())
                // The screen runs the 5s UNDO window on its own SnackbarHost, then pops back to
                // S12; emitting NavigateBack here would dispose the host before it can show.
                _actions.emit(TransactionDetailAction.ShowUndoSnackbar(transactionId))
            } catch (t: Throwable) {
                t.reportToSentry()
                _state.value = _state.value.copy(
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

    private fun LocalDate.toUtcInstant(): Instant =
        atStartOfDay(ZoneOffset.UTC).toInstant()

    companion object {
        const val KEY_TRANSACTION_ID = "transactionId"

        private fun ratesEqual(a: Double?, b: Double?): Boolean = a == b
    }
}
