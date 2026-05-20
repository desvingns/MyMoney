package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val balanceCalculator: BalanceCalculator,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<DashboardAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<DashboardAction> = _actions.asSharedFlow()

    init {
        observeAccountsAndCurrencies()
    }

    private fun observeAccountsAndCurrencies() {
        viewModelScope.launch {
            val settings = appSettingsRepository.settings.first()
            val accounts = accountRepository.observeActive().first()
            val currencies = currencyRepository.observeActive().first()
            val defaultAccount = if (settings.defaultAccountId >= 0) {
                accounts.firstOrNull { it.id == settings.defaultAccountId }
            } else null
            val activeAccount = defaultAccount ?: accounts.firstOrNull()
            val activeCurrency = activeAccount?.let { acc ->
                currencies.firstOrNull { it.id == acc.currencyId }
            }
            _state.value = _state.value.copy(
                accounts = accounts,
                currencies = currencies,
                currentAccount = activeAccount,
                currentCurrency = activeCurrency,
                isLoading = activeAccount == null,
            )
            if (activeAccount != null) {
                recomputeBalance()
            }
        }
    }

    private fun recomputeBalance() {
        val account = _state.value.currentAccount ?: return
        val period = _state.value.period
        viewModelScope.launch {
            val snapshot = balanceCalculator(account.id, period)
            val slices = snapshotToSlices(snapshot)
            val settings = appSettingsRepository.settings.first()
            val firstPositive = !settings.firstPositiveSeen && snapshot.net.amount.signum() > 0
            _state.value = _state.value.copy(
                balanceSnapshot = snapshot,
                slices = slices,
                isLoading = false,
                showConfetti = firstPositive,
            )
            if (firstPositive) {
                appSettingsRepository.update { it.copy(firstPositiveSeen = true) }
            }
        }
    }

    private fun snapshotToSlices(snapshot: BalanceSnapshot): List<CategorySlice> {
        return snapshot.byCategory.map { catBal ->
            CategorySlice(
                categoryId = catBal.categoryId,
                color = parseHexColor(catBal.colorHex),
                fraction = catBal.fraction,
                label = catBal.categoryName,
            )
        }
    }

    private fun parseHexColor(hex: String): Color = try {
        val cleaned = hex.removePrefix("#")
        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
        Color(argb.toLong(16))
    } catch (_: Exception) {
        Color.Gray
    }

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.PeriodChanged -> {
                _state.value = _state.value.copy(period = event.period)
                recomputeBalance()
            }
            is DashboardEvent.AccountChanged -> {
                val acc = _state.value.accounts.firstOrNull { it.id == event.accountId }
                _state.value = _state.value.copy(
                    currentAccount = acc,
                    currentCurrency = acc?.let { a ->
                        _state.value.currencies.firstOrNull { it.id == a.currencyId }
                    },
                    leftDrawerOpen = false,
                )
                recomputeBalance()
                viewModelScope.launch {
                    appSettingsRepository.update { it.copy(defaultAccountId = event.accountId) }
                }
            }
            DashboardEvent.LeftDrawerToggled ->
                _state.value = _state.value.copy(leftDrawerOpen = !_state.value.leftDrawerOpen)
            DashboardEvent.RightDrawerToggled ->
                _state.value = _state.value.copy(rightDrawerOpen = !_state.value.rightDrawerOpen)
            DashboardEvent.DrawerDismissed ->
                _state.value = _state.value.copy(leftDrawerOpen = false, rightDrawerOpen = false)
            DashboardEvent.MinusFabClicked -> emit(DashboardAction.NavigateAddExpense)
            DashboardEvent.PlusFabClicked -> emit(DashboardAction.NavigateAddIncome)
            DashboardEvent.TransferClicked -> emit(DashboardAction.NavigateTransfer)
            DashboardEvent.SearchClicked -> emit(DashboardAction.NavigateSearch)
            DashboardEvent.SettingsClicked -> {
                closeDrawers()
                emit(DashboardAction.NavigateSettings)
            }
            DashboardEvent.CategoriesClicked -> {
                closeDrawers()
                emit(DashboardAction.NavigateCategories)
            }
            DashboardEvent.AccountsClicked -> {
                closeDrawers()
                emit(DashboardAction.NavigateAccounts)
            }
            DashboardEvent.CurrenciesClicked -> {
                closeDrawers()
                emit(DashboardAction.NavigateCurrencies)
            }
            DashboardEvent.AboutClicked -> {
                closeDrawers()
                emit(DashboardAction.NavigateAbout)
            }
            DashboardEvent.BalanceCardClicked -> {
                _state.value.currentAccount?.let { emit(DashboardAction.NavigateTransactionsByAccount(it.id)) }
            }
            is DashboardEvent.SliceClicked -> {
                _state.value.currentAccount?.let { acc ->
                    emit(DashboardAction.NavigateTransactionsByCategory(acc.id, event.categoryId))
                }
            }
            DashboardEvent.ConfettiAcknowledged ->
                _state.value = _state.value.copy(showConfetti = false)
        }
    }

    private fun closeDrawers() {
        _state.value = _state.value.copy(leftDrawerOpen = false, rightDrawerOpen = false)
    }

    private fun emit(action: DashboardAction) {
        viewModelScope.launch { _actions.emit(action) }
    }
}
