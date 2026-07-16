package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.usecase.GetOperationsSummaryUseCase
import com.kshavrin.mymoney.core.ui.navigation.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class TransactionsListViewModel
    @Inject
    constructor(
        private val getOperationsSummary: GetOperationsSummaryUseCase,
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val categoryRepository: CategoryRepository,
        private val transactionRepository: TransactionRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val route = savedStateHandle.toRoute<Destinations.TransactionsList>()
        private val accountId: Long? = route.accountId.takeIf { it >= 0 }
        private val currencyId: Long? = route.currencyId.takeIf { it >= 0 }
        private val initialCategoryId: Long? = route.categoryId.takeIf { it >= 0 }
        private val fromMillis: Long? = route.from.takeIf { it >= 0 }
        private val toMillis: Long? = route.to.takeIf { it >= 0 }
        private val period: Period = resolvePeriod()

        private val _state =
            MutableStateFlow(
                TransactionsListUiState(
                    accountId = accountId,
                    currencyId = currencyId,
                    categoryId = initialCategoryId,
                    period = period,
                ),
            )
        val state: StateFlow<TransactionsListUiState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<TransactionsListAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<TransactionsListAction> = _actions.asSharedFlow()

        init {
            load()
            observeTransactionChanges()
        }

        fun onEvent(event: TransactionsListEvent) {
            when (event) {
                TransactionsListEvent.CategoryFilterCleared -> clearCategoryFilter()
                is TransactionsListEvent.RowClicked -> emit(TransactionsListAction.OpenDetail(event.id))
            }
        }

        private fun observeTransactionChanges() {
            viewModelScope.launch {
                transactionRepository
                    .observeRecent(limit = 1)
                    .drop(1)
                    .collect {
                        try {
                            applyLoadedState()
                        } catch (t: Throwable) {
                            t.reportToSentry()
                        }
                    }
            }
        }

        private fun load() {
            viewModelScope.launch {
                try {
                    applyLoadedState()
                } catch (t: Throwable) {
                    t.reportToSentry()
                    _state.value = _state.value.copy(isLoading = false)
                }
            }
        }

        private suspend fun applyLoadedState() {
            val categories = categoryRepository.observeAll().first()
            val currencies = currencyRepository.observeAll().first()
            val categoryId = _state.value.categoryId
            val selection = resolveSelection(currencies)
            val records =
                when (selection) {
                    is TransactionsSelection.SpecificAccount ->
                        getOperationsSummary(selection.account.id, period, categoryId)
                    is TransactionsSelection.AllAccounts ->
                        getOperationsSummary.forAccounts(selection.accounts, selection.currency, period, categoryId)
                }

            _state.value =
                _state.value.copy(
                    currencyId = _state.value.currencyId ?: (selection as? TransactionsSelection.AllAccounts)?.currency?.id,
                    categoryId = categoryId,
                    categoryName = categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name },
                    records = records,
                    currencies = currencies.associateBy { it.id },
                    categoryDisplays =
                        categories.associate { category ->
                            category.id to
                                TransactionCategoryDisplay(
                                    name = category.name,
                                    iconKey = category.iconKey,
                                )
                        },
                    isLoading = false,
                )
        }

        private suspend fun resolveSelection(currencies: List<Currency>): TransactionsSelection {
            accountId?.let { id ->
                val account =
                    accountRepository.findById(id)
                        ?: throw IllegalArgumentException("Account $id not found")
                return TransactionsSelection.SpecificAccount(account)
            }
            currencyId?.let { id ->
                val currency =
                    currencies.firstOrNull { it.id == id }
                        ?: throw IllegalArgumentException("Currency $id not found")
                val accounts = accountRepository.observeActive().first().filter { it.currencyId == currency.id }
                return TransactionsSelection.AllAccounts(accounts, currency)
            }
            val account =
                accountRepository.findDefault()
                    ?: accountRepository.observeActive().first().firstOrNull()
                    ?: throw IllegalStateException("No active account")
            return TransactionsSelection.SpecificAccount(account)
        }

        private fun clearCategoryFilter() {
            if (_state.value.categoryId == null) return
            _state.value =
                _state.value.copy(
                    categoryId = null,
                    categoryName = null,
                    isLoading = true,
                )
            load()
        }

        private fun resolvePeriod(): Period {
            if (fromMillis == 0L && toMillis == Long.MAX_VALUE) {
                return Period.All
            }
            if (fromMillis != null && toMillis != null) {
                val zone = ZoneId.systemDefault()
                val start = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
                val end = Instant.ofEpochMilli(toMillis).atZone(zone).toLocalDate()
                return Period.CustomRange(start, end)
            }
            return Period.Month(YearMonth.now())
        }

        private fun emit(action: TransactionsListAction) {
            viewModelScope.launch { _actions.emit(action) }
        }

        companion object {
            const val KEY_ACCOUNT_ID = "accountId"
            const val KEY_CURRENCY_ID = "currencyId"
            const val KEY_CATEGORY_ID = "categoryId"
            const val KEY_FROM = "from"
            const val KEY_TO = "to"
        }
    }

private sealed interface TransactionsSelection {
    data class SpecificAccount(
        val account: Account,
    ) : TransactionsSelection

    data class AllAccounts(
        val accounts: List<Account>,
        val currency: Currency,
    ) : TransactionsSelection
}
