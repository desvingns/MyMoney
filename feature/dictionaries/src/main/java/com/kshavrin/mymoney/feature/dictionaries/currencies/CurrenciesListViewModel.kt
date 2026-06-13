package com.kshavrin.mymoney.feature.dictionaries.currencies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
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
class CurrenciesListViewModel
    @Inject
    constructor(
        private val currencyRepository: CurrencyRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(CurrenciesListState())
        val state: StateFlow<CurrenciesListState> = _state.asStateFlow()

        private val _actions = MutableSharedFlow<CurrenciesListAction>(extraBufferCapacity = 4)
        val actions: SharedFlow<CurrenciesListAction> = _actions.asSharedFlow()

        init {
            viewModelScope.launch {
                currencyRepository.observeAll().collect { all ->
                    _state.value = _state.value.copy(currencies = all.sortedBy { it.sortOrder })
                }
            }
        }

        fun onEvent(event: CurrenciesListEvent) {
            when (event) {
                CurrenciesListEvent.AddClicked ->
                    viewModelScope.launch { _actions.emit(CurrenciesListAction.NavigateAdd) }
                is CurrenciesListEvent.ItemClicked ->
                    viewModelScope.launch { _actions.emit(CurrenciesListAction.NavigateEdit(event.id)) }
                CurrenciesListEvent.BackClicked ->
                    viewModelScope.launch { _actions.emit(CurrenciesListAction.NavigateBack) }
                is CurrenciesListEvent.ActiveToggled ->
                    viewModelScope.launch { currencyRepository.setActive(event.id, event.active) }
            }
        }
    }

data class CurrenciesListState(
    val currencies: List<Currency> = emptyList(),
)

sealed interface CurrenciesListEvent {
    data object AddClicked : CurrenciesListEvent

    data class ItemClicked(
        val id: Long,
    ) : CurrenciesListEvent

    data class ActiveToggled(
        val id: Long,
        val active: Boolean,
    ) : CurrenciesListEvent

    data object BackClicked : CurrenciesListEvent
}

sealed interface CurrenciesListAction {
    data object NavigateAdd : CurrenciesListAction

    data class NavigateEdit(
        val id: Long,
    ) : CurrenciesListAction

    data object NavigateBack : CurrenciesListAction
}
