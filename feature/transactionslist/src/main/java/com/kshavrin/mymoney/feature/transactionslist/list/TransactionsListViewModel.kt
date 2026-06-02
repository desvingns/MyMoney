package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
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
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionsListViewModel @Inject constructor(
    private val getCategoryRecords: GetCategoryRecordsUseCase,
    private val balanceCalculator: BalanceCalculator,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: Long? = savedStateHandle.get<Long>(KEY_ACCOUNT_ID)?.takeIf { it >= 0 }
    private val currencyId: Long? = savedStateHandle.get<Long>(KEY_CURRENCY_ID)?.takeIf { it >= 0 }
    private val categoryId: Long? = savedStateHandle.get<Long>(KEY_CATEGORY_ID)?.takeIf { it >= 0 }
    private val fromMillis: Long? = savedStateHandle.get<Long>(KEY_FROM)?.takeIf { it >= 0 }
    private val toMillis: Long? = savedStateHandle.get<Long>(KEY_TO)?.takeIf { it >= 0 }

    private val period: Period = resolvePeriod()

    private val _state = MutableStateFlow(
        TransactionsListUiState(
            accountId = accountId,
            currencyId = currencyId,
            categoryId = categoryId,
            period = period,
            expandedCategoryIds = categoryId?.let { setOf(it) } ?: emptySet(),
        ),
    )
    val state: StateFlow<TransactionsListUiState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<TransactionsListAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<TransactionsListAction> = _actions.asSharedFlow()

    init {
        load()
    }

    fun onEvent(event: TransactionsListEvent) {
        when (event) {
            is TransactionsListEvent.CategoryClicked -> toggleCategory(event.categoryId)
            is TransactionsListEvent.RowClicked -> emit(TransactionsListAction.OpenDetail(event.id))
            is TransactionsListEvent.SwipeDeleted -> onSwipeDeleted(event.id)
            is TransactionsListEvent.UndoDeleteClicked -> onUndoDelete(event.id)
            TransactionsListEvent.SortClicked -> toggleSort()
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val selection = resolveSelection()
                val groups = when (selection) {
                    is RecordsSelection.SpecificAccount -> getCategoryRecords(selection.account.id, period)
                    is RecordsSelection.AllAccounts -> getCategoryRecords.forAccounts(selection.accounts, selection.currency, period)
                }
                val snapshot = when (selection) {
                    is RecordsSelection.SpecificAccount -> balanceCalculator(selection.account.id, period)
                    is RecordsSelection.AllAccounts -> balanceCalculator.forAccounts(selection.accounts, selection.currency, period)
                }
                _state.value = _state.value.copy(
                    groups = groups,
                    net = snapshot.net,
                    currency = snapshot.net.currency,
                    isLoading = false,
                )
            } catch (t: Throwable) {
                t.reportToSentry()
                _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private fun toggleCategory(id: Long) {
        val expanded = _state.value.expandedCategoryIds
        _state.value = _state.value.copy(
            expandedCategoryIds = if (id in expanded) expanded - id else expanded + id,
        )
    }

    private fun toggleSort() {
        val next = when (_state.value.sort) {
            RecordSort.TotalDesc -> RecordSort.TotalAsc
            RecordSort.TotalAsc -> RecordSort.TotalDesc
        }
        _state.value = _state.value.copy(sort = next)
    }

    private fun onSwipeDeleted(id: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.softDelete(id, Instant.now())
                _actions.emit(TransactionsListAction.ShowUndoSnackbar(id))
                reload()
            } catch (t: Throwable) {
                t.reportToSentry()
            }
        }
    }

    private fun onUndoDelete(id: Long) {
        viewModelScope.launch {
            try {
                transactionRepository.restore(id, Instant.now())
                reload()
            } catch (t: Throwable) {
                t.reportToSentry()
            }
        }
    }

    private suspend fun reload() {
        val selection = resolveSelection()
        val groups = when (selection) {
            is RecordsSelection.SpecificAccount -> getCategoryRecords(selection.account.id, period)
            is RecordsSelection.AllAccounts -> getCategoryRecords.forAccounts(selection.accounts, selection.currency, period)
        }
        val snapshot = when (selection) {
            is RecordsSelection.SpecificAccount -> balanceCalculator(selection.account.id, period)
            is RecordsSelection.AllAccounts -> balanceCalculator.forAccounts(selection.accounts, selection.currency, period)
        }
        _state.value = _state.value.copy(
            groups = groups,
            net = snapshot.net,
            currency = snapshot.net.currency,
        )
    }

    private fun emit(action: TransactionsListAction) {
        viewModelScope.launch { _actions.emit(action) }
    }

    private fun resolvePeriod(): Period {
        if (fromMillis != null && toMillis != null) {
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
            val end = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
            return Period.CustomRange(start, end)
        }
        return Period.Month(YearMonth.now())
    }

    private suspend fun resolveSelection(): RecordsSelection {
        accountId?.let { id ->
            val account = accountRepository.findById(id)
                ?: throw IllegalArgumentException("Account $id not found")
            return RecordsSelection.SpecificAccount(account)
        }
        val currency = currencyId?.let { id -> currencyRepository.findById(id) }
            ?: throw IllegalArgumentException("Currency filter is required for all-account records")
        val accounts = accountRepository.observeActive().first().filter { it.currencyId == currency.id }
        return RecordsSelection.AllAccounts(accounts, currency)
    }

    companion object {
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_CURRENCY_ID = "currencyId"
        const val KEY_CATEGORY_ID = "categoryId"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
    }
}

private sealed interface RecordsSelection {
    data class SpecificAccount(val account: Account) : RecordsSelection
    data class AllAccounts(val accounts: List<Account>, val currency: Currency) : RecordsSelection
}
