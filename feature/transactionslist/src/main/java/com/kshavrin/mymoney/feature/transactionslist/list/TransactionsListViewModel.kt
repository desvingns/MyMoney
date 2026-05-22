package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionsListViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: Long = savedStateHandle.get<Long>(KEY_ACCOUNT_ID) ?: -1L
    private val categoryId: Long? = savedStateHandle.get<Long>(KEY_CATEGORY_ID)?.takeIf { it >= 0 }
    private val fromMillis: Long? = savedStateHandle.get<Long>(KEY_FROM)?.takeIf { it >= 0 }
    private val toMillis: Long? = savedStateHandle.get<Long>(KEY_TO)?.takeIf { it >= 0 }

    private val from: Instant
    private val to: Instant

    init {
        if (fromMillis != null && toMillis != null) {
            from = Instant.ofEpochMilli(fromMillis)
            to = Instant.ofEpochMilli(toMillis)
        } else {
            val range = PeriodArithmetic.toEpochMillisRange(Period.Month(YearMonth.now()))
            from = Instant.ofEpochMilli(range.first)
            to = Instant.ofEpochMilli(range.last)
        }
    }

    private val _state = MutableStateFlow(
        TransactionsListUiState(accountId = accountId, categoryId = categoryId),
    )
    val state: StateFlow<TransactionsListUiState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<TransactionsListAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<TransactionsListAction> = _actions.asSharedFlow()

    val pagingData: Flow<PagingData<TransactionListItem>> =
        transactionRepository.paged(accountId, categoryId, from, to)
            .map { data -> data.map { it.toRow() }.withDayHeaders() }
            .cachedIn(viewModelScope)

    init {
        loadFilterContext()
    }

    private fun loadFilterContext() {
        viewModelScope.launch {
            val account = accountRepository.findById(accountId)
            val currency = account?.let { currencyRepository.findById(it.currencyId) }
            val category = categoryId?.let { categoryRepository.findById(it) }
            _state.value = _state.value.copy(currency = currency, categoryFilter = category)
        }
    }

    fun onEvent(event: TransactionsListEvent) {
        when (event) {
            is TransactionsListEvent.RowClicked -> emit(TransactionsListAction.OpenDetail(event.id))
            is TransactionsListEvent.SwipeDeleted -> onSwipeDeleted(event.id)
            is TransactionsListEvent.UndoDeleteClicked -> onUndoDelete(event.id)
        }
    }

    private fun onSwipeDeleted(id: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.softDelete(id, Instant.now())
                _actions.emit(TransactionsListAction.ShowUndoSnackbar(id))
            } catch (t: Throwable) {
                t.reportToSentry()
            }
        }
    }

    private fun onUndoDelete(id: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.restore(id, Instant.now())
            } catch (t: Throwable) {
                t.reportToSentry()
            }
        }
    }

    private fun emit(action: TransactionsListAction) {
        viewModelScope.launch { _actions.emit(action) }
    }

    companion object {
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_CATEGORY_ID = "categoryId"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
    }
}

private fun Transaction.toRow(): TransactionListItem.Row = TransactionListItem.Row(
    id = id,
    kind = kind,
    amount = amount,
    currencyId = currencyId,
    categoryId = categoryId,
    note = note,
    occurredAt = occurredAt,
)

private fun PagingData<TransactionListItem.Row>.withDayHeaders(
    zone: ZoneId = ZoneId.systemDefault(),
): PagingData<TransactionListItem> = insertSeparators { before, after ->
    if (after == null) return@insertSeparators null
    val afterDate = after.occurredAt.atZone(zone).toLocalDate()
    val beforeDate = before?.occurredAt?.atZone(zone)?.toLocalDate()
    if (beforeDate != afterDate) TransactionListItem.DayHeader(afterDate) else null
}
