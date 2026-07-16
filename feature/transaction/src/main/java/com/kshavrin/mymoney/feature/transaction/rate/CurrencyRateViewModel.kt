package com.kshavrin.mymoney.feature.transaction.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import com.kshavrin.mymoney.feature.transaction.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CurrencyRateViewModel
    @Inject
    constructor(
        private val currencyRepository: CurrencyRepository,
        private val currencyRateRepository: CurrencyRateRepository,
        private val savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destinations.CurrencyRate>()
        private val fromId: Long = route.fromId
        private val toId: Long = route.toId

        private val _state = MutableStateFlow(CurrencyRateState())
        val state: StateFlow<CurrencyRateState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<CurrencyRateAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<CurrencyRateAction> = _actions.asSharedFlow()

        init {
            loadInitialContext()
        }

        private fun loadInitialContext() {
            viewModelScope.launch {
                val from = currencyRepository.findById(fromId)
                val to = currencyRepository.findById(toId)
                _state.value = _state.value.copy(fromCurrency = from, toCurrency = to)
                if (fromId > 0 && toId > 0) {
                    val existing = currencyRateRepository.findRate(fromId, toId)
                    if (existing != null) {
                        _state.value =
                            _state.value.copy(
                                rate = existing.rate,
                                rateInput = MoneyFormatter.formatInput(BigDecimal.valueOf(existing.rate), Locale.getDefault()),
                                isValid = true,
                            )
                    }
                }
            }
        }

        fun onEvent(event: CurrencyRateEvent) {
            when (event) {
                is CurrencyRateEvent.RateInputChanged -> onRateInputChanged(event.text)
                CurrencyRateEvent.SaveClicked -> save()
                CurrencyRateEvent.BackClicked -> emit(CurrencyRateAction.NavigateBack)
                CurrencyRateEvent.DismissError ->
                    _state.value = _state.value.copy(errorBannerRes = null)
            }
        }

        private fun onRateInputChanged(text: String) {
            val parsed =
                text
                    .trim()
                    .replace(',', '.')
                    .toBigDecimalOrNull()
                    ?.setScale(2, RoundingMode.HALF_UP)
                    ?.toDouble()
            val valid = parsed != null && parsed > 0.0
            _state.value =
                _state.value.copy(
                    rateInput = text,
                    rate = parsed,
                    isValid = valid,
                    errorBannerRes = null,
                )
        }

        private fun save() {
            val s = _state.value
            val rateValue = s.rate
            if (!s.isValid || rateValue == null) {
                _state.value = s.copy(errorBannerRes = R.string.currency_rate_invalid)
                return
            }
            viewModelScope.launch {
                _state.value = _state.value.copy(isSaving = true)
                try {
                    val now = Instant.now()
                    val existing = currencyRateRepository.findRate(fromId, toId)
                    val rate =
                        CurrencyRate(
                            id = existing?.id ?: 0L,
                            fromCurrencyId = fromId,
                            toCurrencyId = toId,
                            rate = rateValue,
                            updatedAt = now,
                        )
                    currencyRateRepository.upsert(rate)
                    _state.value = _state.value.copy(isSaving = false)
                    _actions.emit(CurrencyRateAction.NavigateBackWithRate(rate.rate))
                } catch (t: Throwable) {
                    t.reportToSentry()
                    _state.value =
                        _state.value.copy(
                            isSaving = false,
                            errorBannerRes = R.string.error_save_failed,
                        )
                }
            }
        }

        private fun emit(action: CurrencyRateAction) {
            viewModelScope.launch { _actions.emit(action) }
        }

        companion object {
            const val KEY_FROM_ID = "fromId"
            const val KEY_TO_ID = "toId"
        }
    }
