package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.calculator.CalculatorEngine
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.designsystem.dialog.RateRow
import com.kshavrin.mymoney.core.designsystem.keypad.toCalculator
import com.kshavrin.mymoney.core.designsystem.keypad.toDesignsystem
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.ResolveRateUseCase
import com.kshavrin.mymoney.core.domain.usecase.TransferExecutor
import com.kshavrin.mymoney.core.domain.usecase.TransferResult
import com.kshavrin.mymoney.feature.transaction.R
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
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransferViewModel
    @Inject
    constructor(
        @Suppress("unused", "UnusedPrivateProperty")
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val currencyRateRepository: CurrencyRateRepository,
        private val transferExecutor: TransferExecutor,
        private val resolveRate: ResolveRateUseCase,
        private val appSettingsRepository: AppSettingsRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val engine = CalculatorEngine()

        private val _state = MutableStateFlow(TransferState())
        val state: StateFlow<TransferState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<TransferAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<TransferAction> = _actions.asSharedFlow()

        init {
            loadInitialContext()
        }

        private fun loadInitialContext() {
            viewModelScope.launch {
                val settings = appSettingsRepository.settings.first()
                val accounts = accountRepository.observeActive().first()
                val currencies = currencyRepository.observeActive().first()
                val defaultAccount =
                    if (settings.defaultAccountId >= 0) {
                        accounts.firstOrNull { it.id == settings.defaultAccountId }
                    } else {
                        null
                    }
                val sourceAccount = defaultAccount ?: accounts.firstOrNull()
                val sourceCurrency =
                    sourceAccount?.let { acc ->
                        currencies.firstOrNull { it.id == acc.currencyId }
                    }
                _state.value =
                    _state.value.copy(
                        accounts = accounts,
                        currencies = currencies,
                        sourceAccount = sourceAccount,
                        sourceCurrency = sourceCurrency,
                    )
            }
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
                TransferEvent.PendingRateResolved -> refreshCurrentRate()
                is TransferEvent.RateDialogConfirmed -> onRateDialogConfirmed(event.rate)
                TransferEvent.RateDialogDismissed -> onRateDialogDismissed()
                TransferEvent.SaveClicked -> save()
                TransferEvent.BackClicked -> emit(TransferAction.NavigateBack)
                TransferEvent.DismissError ->
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
                )
        }

        private fun onSourceAccountChanged(accountId: Long) {
            val acc = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
            val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
            _state.value =
                _state.value.copy(
                    sourceAccount = acc,
                    sourceCurrency = cur,
                    sameAccountsError = isSameAccount(acc, _state.value.targetAccount),
                )
            evaluateRate()
        }

        private fun onTargetAccountChanged(accountId: Long) {
            val acc = _state.value.accounts.firstOrNull { it.id == accountId } ?: return
            val cur = _state.value.currencies.firstOrNull { it.id == acc.currencyId }
            _state.value =
                _state.value.copy(
                    targetAccount = acc,
                    targetCurrency = cur,
                    sameAccountsError = isSameAccount(_state.value.sourceAccount, acc),
                )
            evaluateRate()
        }

        private fun isSameAccount(
            a: Account?,
            b: Account?,
        ): Boolean =
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
                    _state.value =
                        _state.value.copy(
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
                    _state.value =
                        _state.value.copy(
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
            if (_state.value.isSaving) return
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
            val sourceCurrency = s.sourceCurrency
            val targetCurrency = s.targetCurrency
            // Cross-currency transfer (D5): show the every-time rate dialog before executing.
            // Same-currency transfer keeps the direct path (no dialog) — guards against regression.
            if (sourceCurrency != null && targetCurrency != null && sourceCurrency.id != targetCurrency.id) {
                _state.value = s.copy(isSaving = true, sameAccountsError = false)
                viewModelScope.launch { showRateDialog(sourceCurrency, targetCurrency) }
                return
            }
            _state.value = s.copy(isSaving = true, sameAccountsError = false)
            viewModelScope.launch {
                executeTransfer(source, target, s)
            }
        }

        private suspend fun showRateDialog(
            sourceCurrency: Currency,
            targetCurrency: Currency,
        ) {
            try {
                val info = resolveRate(sourceCurrency, targetCurrency)
                val displayRate = info.crossRate?.setScale(RATE_DISPLAY_SCALE, RoundingMode.HALF_UP)
                _state.value =
                    _state.value.copy(
                        rateDialogRow =
                            RateRow(
                                fromCode = sourceCurrency.code,
                                toCode = targetCurrency.code,
                                lastUpdated = info.lastUpdated,
                                displayRate = displayRate,
                                stale = info.stale,
                                missing = info.missing,
                            ),
                        rateDialogFullRate = info.crossRate,
                    )
                _actions.emit(TransferAction.ShowRateDialog)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.reportToSentry()
                _state.value =
                    _state.value.copy(
                        isSaving = false,
                        rateDialogRow = null,
                        rateDialogFullRate = null,
                        errorBannerRes = R.string.error_save_failed,
                    )
            }
        }

        private fun onRateDialogConfirmed(confirmedRate: BigDecimal) {
            val s = _state.value
            if (!s.isSaving || s.rateDialogRow == null) return
            val source = s.sourceAccount ?: return
            val target = s.targetAccount ?: return
            val targetCurrency = s.targetCurrency ?: return
            // The dialog already resolves the value to use: the manually edited rate if the user
            // typed one, otherwise the (rounded-for-display) stored rate. When the user did not edit
            // we instead use the full unrounded cross-rate so toAmount is computed at full precision
            // and only the final toAmount is rounded (G18). A one-shot edit is never persisted (D5).
            val displayRounded = s.rateDialogRow.displayRate
            val effectiveRate =
                if (displayRounded != null && confirmedRate.compareTo(displayRounded) == 0 && s.rateDialogFullRate != null) {
                    s.rateDialogFullRate
                } else {
                    confirmedRate
                }
            val toAmount = s.amount.multiply(effectiveRate).toMoneyScale(targetCurrency)
            _state.value = s.copy(rateDialogRow = null, rateDialogFullRate = null)
            viewModelScope.launch {
                executeTransfer(
                    source = source,
                    target = target,
                    s = s,
                    overrideToAmount = toAmount,
                    overrideRate = effectiveRate,
                )
            }
        }

        private fun onRateDialogDismissed() {
            // Dismiss cancels execution of THIS transfer: release the save guard, drop dialog state.
            _state.value =
                _state.value.copy(
                    isSaving = false,
                    rateDialogRow = null,
                    rateDialogFullRate = null,
                )
        }

        private suspend fun executeTransfer(
            source: Account,
            target: Account,
            s: TransferState,
            overrideToAmount: BigDecimal? = null,
            overrideRate: BigDecimal? = null,
        ) {
            try {
                val now = Instant.now()
                val occurred = s.occurredAt.atStartOfDay(ZoneId.systemDefault()).toInstant()
                val result =
                    transferExecutor.execute(
                        sourceAccountId = source.id,
                        targetAccountId = target.id,
                        amount = s.amount,
                        note = s.note.takeIf { it.isNotBlank() },
                        occurredAt = occurred,
                        now = now,
                        overrideToAmount = overrideToAmount,
                        overrideRate = overrideRate,
                    )
                when (result) {
                    is TransferResult.Success -> {
                        _state.value =
                            _state.value.copy(
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
                    is TransferResult.Failure.TargetMissing,
                    -> {
                        _state.value =
                            _state.value.copy(
                                isSaving = false,
                                errorBannerRes = R.string.error_save_failed,
                            )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.reportToSentry()
                _state.value =
                    _state.value.copy(
                        isSaving = false,
                        errorBannerRes = R.string.error_save_failed,
                    )
            }
        }

        private fun emit(action: TransferAction) {
            viewModelScope.launch { _actions.emit(action) }
        }

        private fun formatRatePreview(
            from: Currency,
            to: Currency,
            rate: Double,
        ): String =
            "1 ${from.code} = $rate ${to.code}"

        companion object {
            const val KEY_PENDING_RATE = "pendingRate"
            const val NO_PENDING_RATE = -1.0
            private const val RATE_DISPLAY_SCALE = 2
        }
    }
