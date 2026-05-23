package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.calculator.CalculatorEngine
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.designsystem.keypad.toCalculator
import com.kshavrin.mymoney.core.designsystem.keypad.toDesignsystem
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.TransferExecutor
import com.kshavrin.mymoney.core.domain.usecase.TransferResult
import com.kshavrin.mymoney.feature.transaction.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject

@HiltViewModel
class TransferViewModel @Inject constructor(
    @Suppress("unused", "UnusedPrivateProperty")
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val currencyRateRepository: CurrencyRateRepository,
    private val transferExecutor: TransferExecutor,
    private val appSettingsRepository: AppSettingsRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val engine = CalculatorEngine()

    private val _state = MutableStateFlow(TransferState())
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<TransferAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<TransferAction> = _actions.asSharedFlow()

    init {
        loadInitialContext()
        observePendingRate()
    }

    private fun loadInitialContext() {
        viewModelScope.launch {
            val settings = appSettingsRepository.settings.first()
            val accounts = accountRepository.observeActive().first()
            val currencies = currencyRepository.observeActive().first()
            val defaultAccount = if (settings.defaultAccountId >= 0) {
                accounts.firstOrNull { it.id == settings.defaultAccountId }
            } else null
            val sourceAccount = defaultAccount ?: accounts.firstOrNull()
            val sourceCurrency = sourceAccount?.let { acc ->
                currencies.firstOrNull { it.id == acc.currencyId }
            }
            _state.value = _state.value.copy(
                accounts = accounts,
                currencies = currencies,
                sourceAccount = sourceAccount,
                sourceCurrency = sourceCurrency,
            )
        }
    }

    private fun observePendingRate() {
        savedStateHandle.getStateFlow(KEY_PENDING_RATE, NO_PENDING_RATE)
            .onEach { rate ->
                if (rate > 0.0) {
                    refreshCurrentRate()
                    savedStateHandle[KEY_PENDING_RATE] = NO_PENDING_RATE
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: TransferEvent) {
        when (event) {
            is TransferEvent.KeypadDigit -> {
                engine.inputDigit(event.d)
                syncFromEngine()
            }
            is TransferEvent.KeypadOperator -> {
                engine.inputOperator(event.op.toCalculator())
                syncFromEngine()
            }
            TransferEvent.KeypadDot -> {
                engine.inputDot()
                syncFromEngine()
            }
            TransferEvent.KeypadBackspace -> {
                engine.backspace()
                syncFromEngine()
            }
            TransferEvent.KeypadEquals -> {
                engine.equals()
                syncFromEngine()
            }
            is TransferEvent.NoteChanged ->
                _state.value = _state.value.copy(note = event.text, errorBannerRes = null)
            is TransferEvent.DateChanged ->
                _state.value = _state.value.copy(occurredAt = event.date)
            is TransferEvent.SourceAccountChanged -> onSourceAccountChanged(event.accountId)
            is TransferEvent.TargetAccountChanged -> onTargetAccountChanged(event.accountId)
            TransferEvent.ChangeRateClicked -> onChangeRateClicked()
            TransferEvent.SaveClicked -> save()
            TransferEvent.BackClicked -> emit(TransferAction.NavigateBack)
            TransferEvent.DismissError ->
                _state.value = _state.value.copy(errorBannerRes = null)
        }
    }

    private fun syncFromEngine() {
        _state.value = _state.value.copy(
            amount = engine.currentValue,
            amountInput = engine.display,
            expression = engine.expression,
            pendingOperator = engine.pendingOp.toDesignsystem(),
        )
    }

    private fun onSourceAccountChanged(accountId: Long) {
        val acc = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
        val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
        _state.value = _state.value.copy(
            sourceAccount = acc,
            sourceCurrency = cur,
            sameAccountsError = isSameAccount(acc, _state.value.targetAccount),
        )
        evaluateRate()
    }

    private fun onTargetAccountChanged(accountId: Long) {
        val acc = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
        val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
        _state.value = _state.value.copy(
            targetAccount = acc,
            targetCurrency = cur,
            sameAccountsError = isSameAccount(_state.value.sourceAccount, acc),
        )
        evaluateRate()
    }

    private fun isSameAccount(a: Account?, b: Account?): Boolean =
        a != null && b != null && a.id == b.id

    // AS-6: when currencies differ and no rate exists, auto-navigate to S27 to capture one.
    private fun evaluateRate() {
        val s = _state.value
        val source = s.sourceAccount ?: return
        val target = s.targetAccount ?: return
        if (source.id == target.id) return
        val sourceCurrency = s.sourceCurrency ?: return
        val targetCurrency = s.targetCurrency ?: return
        if (sourceCurrency.id == targetCurrency.id) {
            _state.value = s.copy(currentRate = null, ratePreviewText = "")
            return
        }
        viewModelScope.launch {
            val rate = currencyRateRepository.findRate(sourceCurrency.id, targetCurrency.id)
            if (rate != null) {
                _state.value = _state.value.copy(
                    currentRate = rate,
                    ratePreviewText = formatRatePreview(sourceCurrency, targetCurrency, rate.rate),
                )
            } else {
                _state.value = _state.value.copy(currentRate = null, ratePreviewText = "")
                _actions.emit(TransferAction.NavigateToRateSetup(sourceCurrency.id, targetCurrency.id))
            }
        }
    }

    private fun refreshCurrentRate() {
        val s = _state.value
        val sourceCurrency = s.sourceCurrency ?: return
        val targetCurrency = s.targetCurrency ?: return
        if (sourceCurrency.id == targetCurrency.id) return
        viewModelScope.launch {
            val rate = currencyRateRepository.findRate(sourceCurrency.id, targetCurrency.id)
            if (rate != null) {
                _state.value = _state.value.copy(
                    currentRate = rate,
                    ratePreviewText = formatRatePreview(sourceCurrency, targetCurrency, rate.rate),
                )
            }
        }
    }

    private fun onChangeRateClicked() {
        val source = _state.value.sourceCurrency ?: return
        val target = _state.value.targetCurrency ?: return
        if (source.id == target.id) return
        emit(TransferAction.NavigateToRateSetup(source.id, target.id))
    }

    private fun save() {
        val s = _state.value
        if (s.amount <= BigDecimal.ZERO) {
            _state.value = s.copy(errorBannerRes = R.string.error_enter_amount_first)
            return
        }
        val source = s.sourceAccount
        val target = s.targetAccount
        if (source == null || target == null) {
            _state.value = s.copy(errorBannerRes = R.string.error_save_failed)
            return
        }
        if (source.id == target.id) {
            _state.value = s.copy(sameAccountsError = true)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, sameAccountsError = false)
            try {
                val now = Instant.now()
                val occurred = s.occurredAt.atStartOfDay(ZoneOffset.UTC).toInstant()
                val result = transferExecutor.execute(
                    sourceAccountId = source.id,
                    targetAccountId = target.id,
                    amount = s.amount,
                    note = s.note.takeIf { it.isNotBlank() },
                    occurredAt = occurred,
                    now = now,
                )
                when (result) {
                    is TransferResult.Success -> {
                        _state.value = _state.value.copy(
                            isSaving = false,
                            savedSignal = _state.value.savedSignal + 1,
                        )
                        _actions.emit(TransferAction.NavigateBack)
                    }
                    is TransferResult.Failure.RateMissing -> {
                        _state.value = _state.value.copy(isSaving = false)
                        _actions.emit(
                            TransferAction.NavigateToRateSetup(result.fromCurrencyId, result.toCurrencyId),
                        )
                    }
                    is TransferResult.Failure.SourceMissing,
                    is TransferResult.Failure.TargetMissing -> {
                        _state.value = _state.value.copy(
                            isSaving = false,
                            errorBannerRes = R.string.error_save_failed,
                        )
                    }
                }
            } catch (t: Throwable) {
                t.reportToSentry()
                _state.value = _state.value.copy(
                    isSaving = false,
                    errorBannerRes = R.string.error_save_failed,
                )
            }
        }
    }

    private fun emit(action: TransferAction) {
        viewModelScope.launch { _actions.emit(action) }
    }

    private fun formatRatePreview(from: Currency, to: Currency, rate: Double): String =
        "1 ${from.code} = $rate ${to.code}"

    companion object {
        const val KEY_PENDING_RATE = "pendingRate"
        const val NO_PENDING_RATE = -1.0
    }
}
