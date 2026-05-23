package com.kshavrin.mymoney.feature.transactionslist.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.SearchHistoryRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<SearchAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<SearchAction> = _actions.asSharedFlow()

    private val _query = MutableStateFlow("")

    init {
        observeHistory()
        observeCurrencies()
        observeQuery()
    }

    private fun observeHistory() {
        searchHistoryRepository.observe()
            .onEach { history -> _state.value = _state.value.copy(history = history) }
            .launchIn(viewModelScope)
    }

    private fun observeCurrencies() {
        currencyRepository.observeActive()
            .onEach { currencies -> _state.value = _state.value.copy(currencies = currencies) }
            .launchIn(viewModelScope)
    }

    private fun observeQuery() {
        _query
            .debounce(DEBOUNCE_MILLIS)
            .mapLatest { query -> query to runSearch(query) }
            .onEach { (query, outcome) -> applyOutcome(query, outcome) }
            .launchIn(viewModelScope)
    }

    private suspend fun runSearch(query: String): SearchOutcome {
        if (query.isBlank()) return SearchOutcome.Blank
        return try {
            SearchOutcome.Hits(transactionRepository.searchByNote(query, SEARCH_LIMIT))
        } catch (t: Throwable) {
            t.reportToSentry()
            SearchOutcome.Failure
        }
    }

    private fun applyOutcome(query: String, outcome: SearchOutcome) {
        when (outcome) {
            SearchOutcome.Blank ->
                _state.value = _state.value.copy(results = emptyList(), phase = SearchPhase.Empty)
            SearchOutcome.Failure ->
                _state.value = _state.value.copy(phase = SearchPhase.Error)
            is SearchOutcome.Hits -> {
                val rows = outcome.transactions.map { it.toRow() }
                _state.value = _state.value.copy(
                    results = rows,
                    phase = if (rows.isEmpty()) SearchPhase.EmptyResults else SearchPhase.Results,
                )
            }
        }
    }

    fun onEvent(event: SearchEvent) {
        when (event) {
            is SearchEvent.QueryChanged -> onQueryChanged(event.text)
            is SearchEvent.SuggestionClicked -> onCommittedQuery(event.query)
            is SearchEvent.VoiceResult -> onCommittedQuery(event.text)
            is SearchEvent.ResultClicked -> onResultClicked(event.transactionId)
            SearchEvent.QuerySubmitted -> onSubmitted()
            SearchEvent.QueryCleared -> onQueryChanged("")
            SearchEvent.VoiceUnavailable -> emit(SearchAction.ShowVoiceUnavailable)
            SearchEvent.BackClicked -> emit(SearchAction.NavigateBack)
        }
    }

    private fun onQueryChanged(text: String) {
        _state.value = _state.value.copy(
            query = text,
            phase = if (text.isBlank()) SearchPhase.Empty else SearchPhase.Loading,
        )
        _query.value = text
    }

    private fun onCommittedQuery(query: String) {
        onQueryChanged(query)
        recordHistory(query)
    }

    private fun onSubmitted() {
        recordHistory(_state.value.query)
    }

    private fun onResultClicked(transactionId: Long) {
        recordHistory(_state.value.query)
        emit(SearchAction.OpenDetail(transactionId))
    }

    private fun recordHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                searchHistoryRepository.add(trimmed, Instant.now())
                searchHistoryRepository.pruneToLimit()
            } catch (t: Throwable) {
                t.reportToSentry()
            }
        }
    }

    private fun emit(action: SearchAction) {
        viewModelScope.launch { _actions.emit(action) }
    }

    companion object {
        const val DEBOUNCE_MILLIS = 200L
        private const val SEARCH_LIMIT = 200
    }
}

private sealed interface SearchOutcome {
    data object Blank : SearchOutcome
    data object Failure : SearchOutcome
    data class Hits(val transactions: List<Transaction>) : SearchOutcome
}

private fun Transaction.toRow(): SearchRow = SearchRow(
    id = id,
    kind = kind,
    amount = amount,
    currencyId = currencyId,
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt,
)
