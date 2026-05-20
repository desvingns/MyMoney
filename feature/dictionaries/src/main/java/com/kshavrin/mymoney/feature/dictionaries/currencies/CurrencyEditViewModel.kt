package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CurrencyEditViewModel @Inject constructor(
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val currencyId: Long = savedStateHandle.get<Long>("id") ?: -1L

    private val _state = MutableStateFlow(CurrencyEditState(isCreateMode = currencyId == -1L))
    val state: StateFlow<CurrencyEditState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<CurrencyEditAction>(extraBufferCapacity = 4)
    val actions: SharedFlow<CurrencyEditAction> = _actions.asSharedFlow()

    init {
        if (currencyId != -1L) {
            viewModelScope.launch {
                currencyRepository.findById(currencyId)?.let { existing ->
                    _state.value = _state.value.copy(
                        code = existing.code,
                        symbol = existing.symbol,
                        name = existing.name,
                        decimalDigitsText = existing.decimalDigits.toString(),
                        isActive = existing.isActive,
                        sortOrder = existing.sortOrder,
                        isCreateMode = false,
                    )
                }
            }
        }
    }

    fun onEvent(event: CurrencyEditEvent) {
        when (event) {
            is CurrencyEditEvent.CodeChanged ->
                _state.value = _state.value.copy(code = event.value.uppercase(), errorMessage = null)
            is CurrencyEditEvent.SymbolChanged ->
                _state.value = _state.value.copy(symbol = event.value, errorMessage = null)
            is CurrencyEditEvent.NameChanged ->
                _state.value = _state.value.copy(name = event.value)
            is CurrencyEditEvent.DecimalDigitsChanged ->
                _state.value = _state.value.copy(decimalDigitsText = event.value, errorMessage = null)
            is CurrencyEditEvent.IsActiveChanged ->
                _state.value = _state.value.copy(isActive = event.value)
            CurrencyEditEvent.SaveClicked -> save()
            CurrencyEditEvent.DeleteClicked -> attemptDelete()
            CurrencyEditEvent.BlockedDeleteDismissed ->
                _state.value = _state.value.copy(blockedDeleteCount = null)
        }
    }

    private fun save() {
        val s = _state.value
        if (!CODE_REGEX.matches(s.code)) {
            _state.value = s.copy(errorMessage = "code_format")
            return
        }
        if (s.symbol.isBlank() || s.symbol.length > 4) {
            _state.value = s.copy(errorMessage = "symbol_required")
            return
        }
        val digits = s.decimalDigitsText.toIntOrNull()
        if (digits == null || digits !in 0..8) {
            _state.value = s.copy(errorMessage = "decimal_digits")
            return
        }
        viewModelScope.launch {
            try {
                currencyRepository.upsert(
                    Currency(
                        id = if (currencyId == -1L) 0L else currencyId,
                        code = s.code,
                        symbol = s.symbol,
                        name = s.name.ifBlank { s.code },
                        decimalDigits = digits,
                        isActive = s.isActive,
                        sortOrder = s.sortOrder,
                    ),
                )
                _actions.emit(CurrencyEditAction.NavigateBack)
            } catch (e: IllegalArgumentException) {
                _state.value = s.copy(errorMessage = e.message ?: "save_failed")
            }
        }
    }

    private fun attemptDelete() {
        if (currencyId == -1L) return
        viewModelScope.launch {
            val count = transactionRepository.countByCurrency(currencyId)
            if (count > 0) {
                _state.value = _state.value.copy(blockedDeleteCount = count)
            } else {
                currencyRepository.setActive(currencyId, false)
                _actions.emit(CurrencyEditAction.NavigateBack)
            }
        }
    }

    private companion object {
        val CODE_REGEX = Regex("^[A-Z]{3}$")
    }
}

data class CurrencyEditState(
    val isCreateMode: Boolean = true,
    val code: String = "",
    val symbol: String = "",
    val name: String = "",
    val decimalDigitsText: String = "2",
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val blockedDeleteCount: Int? = null,
    val errorMessage: String? = null,
)

sealed interface CurrencyEditEvent {
    data class CodeChanged(val value: String) : CurrencyEditEvent
    data class SymbolChanged(val value: String) : CurrencyEditEvent
    data class NameChanged(val value: String) : CurrencyEditEvent
    data class DecimalDigitsChanged(val value: String) : CurrencyEditEvent
    data class IsActiveChanged(val value: Boolean) : CurrencyEditEvent
    data object SaveClicked : CurrencyEditEvent
    data object DeleteClicked : CurrencyEditEvent
    data object BlockedDeleteDismissed : CurrencyEditEvent
}

sealed interface CurrencyEditAction {
    data object NavigateBack : CurrencyEditAction
}
