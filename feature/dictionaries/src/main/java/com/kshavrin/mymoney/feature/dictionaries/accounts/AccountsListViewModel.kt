package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

@HiltViewModel
class AccountsListViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val balanceCalculator: BalanceCalculator,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AccountsListState())
        val state: StateFlow<AccountsListState> = _state.asStateFlow()

        private val _actions = MutableSharedFlow<AccountsListAction>(extraBufferCapacity = 4)
        val actions: SharedFlow<AccountsListAction> = _actions.asSharedFlow()

        init {
            viewModelScope.launch {
                combine(
                    accountRepository.observeActive(),
                    currencyRepository.observeAll(),
                ) { accounts, currencies -> accounts to currencies }
                    .collect { (accounts, currencies) ->
                        val rows =
                            accounts
                                .filter { !it.isArchived }
                                .sortedBy { it.sortOrder }
                                .map { account ->
                                    val balance =
                                        runCatching {
                                            balanceCalculator(account.id, Period.All).net.amount
                                        }.getOrDefault(BigDecimal.ZERO)
                                    val currency = currencies.firstOrNull { it.id == account.currencyId }
                                    AccountRow(account = account, balance = balance, currency = currency)
                                }
                        _state.value = _state.value.copy(rows = rows)
                    }
            }
        }

        fun onEvent(event: AccountsListEvent) {
            when (event) {
                AccountsListEvent.AddClicked ->
                    viewModelScope.launch { _actions.emit(AccountsListAction.NavigateAdd) }
                is AccountsListEvent.ItemClicked ->
                    viewModelScope.launch { _actions.emit(AccountsListAction.NavigateEdit(event.id)) }
                AccountsListEvent.BackClicked ->
                    viewModelScope.launch { _actions.emit(AccountsListAction.NavigateBack) }
            }
        }
    }

data class AccountsListState(
    val rows: List<AccountRow> = emptyList(),
)

data class AccountRow(
    val account: Account,
    val balance: BigDecimal,
    val currency: Currency?,
)

sealed interface AccountsListEvent {
    data object AddClicked : AccountsListEvent

    data class ItemClicked(
        val id: Long,
    ) : AccountsListEvent

    data object BackClicked : AccountsListEvent
}

sealed interface AccountsListAction {
    data object NavigateAdd : AccountsListAction

    data class NavigateEdit(
        val id: Long,
    ) : AccountsListAction

    data object NavigateBack : AccountsListAction
}
