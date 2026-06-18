package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.DomainEvent
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.ObserveBudgetAlertsUseCase
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val accountRepository: AccountRepository,
        private val currencyRepository: CurrencyRepository,
        private val balanceCalculator: BalanceCalculator,
        private val appSettingsRepository: AppSettingsRepository,
        private val transactionRepository: TransactionRepository,
        private val observeBudgetAlertsUseCase: ObserveBudgetAlertsUseCase,
        private val categoryRepository: CategoryRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow(DashboardState())
        val state: StateFlow<DashboardState> = _state.asStateFlow()

        private val _actions =
            MutableSharedFlow<DashboardAction>(
                extraBufferCapacity = 4,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        val actions: SharedFlow<DashboardAction> = _actions.asSharedFlow()

        private val budgetAlertSelection = MutableStateFlow<BudgetAlertSelection?>(null)

        private var recomputeJob: Job? = null

        init {
            observeAccountsAndCurrencies()
            observeTransactionChanges()
            observeBudgetAlerts()
            observeExpenseCategories()
        }

        private fun observeExpenseCategories() {
            viewModelScope.launch {
                categoryRepository.observeByKind(CategoryKind.Expense).collect { categories ->
                    _state.value =
                        _state.value.copy(
                            expenseCategoryPlaceholders =
                                categories
                                    .sortedBy { it.sortOrder }
                                    .map(::categoryToPlaceholder),
                        )
                }
            }
        }

        private fun categoryToPlaceholder(category: Category): CategorySlice =
            CategorySlice(
                categoryId = category.id,
                color = parseHexColor(category.colorHex),
                fraction = 0f,
                label = category.name,
                iconKey = category.iconKey,
            )

        private fun observeAccountsAndCurrencies() {
            viewModelScope.launch {
                combine(
                    accountRepository.observeActive(),
                    currencyRepository.observeActive(),
                    appSettingsRepository.settings,
                ) { accounts, currencies, settings ->
                    DashboardInputs(accounts, currencies, settings)
                }.collect { inputs ->
                    val current = _state.value
                    val focusPeriod = inputs.settings.importFocusPeriod()
                    val focusSelection = inputs.settings.importFocusSelection(inputs.accounts, inputs.currencies)
                    val selection =
                        if (focusPeriod != null) {
                            focusSelection ?: resolveDashboardSelection(
                                current.dashboardSelection,
                                inputs.accounts,
                                inputs.currencies,
                                inputs.settings,
                            )
                        } else {
                            resolveDashboardSelection(
                                current.dashboardSelection,
                                inputs.accounts,
                                inputs.currencies,
                                inputs.settings,
                            )
                        }
                    _state.value =
                        current.copy(
                            accounts = inputs.accounts,
                            currencies = inputs.currencies,
                            period = focusPeriod ?: current.period,
                            dashboardSelection = selection,
                            isLoading = selection == null,
                        )
                    selectBudgetAlerts()
                    recomputeBalance()
                }
            }
        }

        private fun clearImportFocus() {
            viewModelScope.launch {
                appSettingsRepository.update {
                    it.copy(importFocusEpochMs = 0L, importFocusCurrencyId = -1L)
                }
            }
        }

        private fun resolveDashboardSelection(
            current: DashboardSelection?,
            accounts: List<Account>,
            currencies: List<Currency>,
            settings: AppSettings,
        ): DashboardSelection? {
            preserveDashboardSelection(current, accounts, currencies)?.let { return it }
            val defaultAccount =
                if (settings.defaultAccountId >= 0) {
                    accounts.firstOrNull { it.id == settings.defaultAccountId }
                } else {
                    null
                }
            val activeAccount = defaultAccount ?: accounts.firstOrNull()
            val activeCurrency =
                activeAccount?.let { account ->
                    currencies.firstOrNull { it.id == account.currencyId }
                }
            return if (settings.dashboardSelectionMode == DASHBOARD_SELECTION_ALL) {
                activeCurrency?.let(DashboardSelection::AllAccounts)
            } else {
                activeAccount?.let(DashboardSelection::SpecificAccount)
            }
        }

        private fun preserveDashboardSelection(
            current: DashboardSelection?,
            accounts: List<Account>,
            currencies: List<Currency>,
        ): DashboardSelection? =
            when (current) {
                is DashboardSelection.SpecificAccount ->
                    accounts.firstOrNull { it.id == current.account.id }?.let(DashboardSelection::SpecificAccount)
                is DashboardSelection.AllAccounts ->
                    currencies
                        .firstOrNull { it.id == current.currency.id }
                        ?.takeIf { currency -> accounts.any { it.currencyId == currency.id } }
                        ?.let(DashboardSelection::AllAccounts)
                null -> null
            }

        private fun AppSettings.importFocusPeriod(): Period.Month? =
            importFocusEpochMs
                .takeIf { it > 0L }
                ?.let { epochMs ->
                    Period.Month(YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())))
                }

        private fun AppSettings.importFocusSelection(
            accounts: List<Account>,
            currencies: List<Currency>,
        ): DashboardSelection.AllAccounts? {
            if (importFocusEpochMs <= 0L || importFocusCurrencyId <= 0L) return null
            val currency = currencies.firstOrNull { it.id == importFocusCurrencyId } ?: return null
            if (accounts.none { it.currencyId == currency.id }) return null
            return DashboardSelection.AllAccounts(currency)
        }

        private fun observeBudgetAlerts() {
            viewModelScope.launch {
                budgetAlertSelection
                    .flatMapLatest { selection ->
                        if (selection == null) {
                            flowOf(emptyList())
                        } else {
                            observeBudgetAlertsUseCase(selection.accountId)
                        }
                    }.collect(::applyBudgetAlerts)
            }
        }

        private fun selectBudgetAlerts() {
            val selection =
                when (val dashboardSelection = _state.value.dashboardSelection) {
                    is DashboardSelection.SpecificAccount -> BudgetAlertSelection(accountId = dashboardSelection.account.id)
                    is DashboardSelection.AllAccounts,
                    null,
                    -> null
                }
            if (selection == budgetAlertSelection.value) return
            _state.value =
                _state.value.copy(
                    budgetAlertCategoryIds = emptySet(),
                    overBudgetAmount = null,
                    slices = _state.value.slices.map { it.copy(hasBudgetAlert = false) },
                    expenseTiles = _state.value.expenseTiles.map { it.copy(hasBudgetAlert = false) },
                )
            budgetAlertSelection.value = selection
        }

        private fun applyBudgetAlerts(alerts: List<DomainEvent.BudgetAlert>) {
            val categoryIds = alerts.mapNotNull { it.categoryId }.toSet()
            _state.value =
                _state.value.copy(
                    budgetAlertCategoryIds = categoryIds,
                    overBudgetAmount = alerts.mapNotNull { it.overage }.maxByOrNull { it.amount },
                    slices =
                        _state.value.slices.map { slice ->
                            slice.copy(hasBudgetAlert = slice.categoryId != OTHER_CATEGORY_ID && slice.categoryId in categoryIds)
                        },
                    expenseTiles =
                        _state.value.expenseTiles.map { tile ->
                            tile.copy(hasBudgetAlert = tile.categoryId in categoryIds)
                        },
                )
        }

        // Room-backed Flow re-emits whenever the transaction table changes (e.g. a form
        // saved a row and popped back to S01). The list payload is ignored — it is only a
        // change signal; authoritative figures come from balanceCalculator. recomputeBalance()
        // no-ops until an active account is set, so the first emission covers the initial load.
        private fun observeTransactionChanges() {
            viewModelScope.launch {
                transactionRepository.observeRecent(limit = 1).collect {
                    recomputeBalance()
                }
            }
        }

        private fun recomputeBalance() {
            val state = _state.value
            val selection = state.dashboardSelection ?: return
            val period = _state.value.period
            recomputeJob?.cancel()
            recomputeJob =
                viewModelScope.launch {
                    val snapshot =
                        when (selection) {
                            is DashboardSelection.SpecificAccount -> balanceCalculator(selection.account.id, period)
                            is DashboardSelection.AllAccounts ->
                                balanceCalculator.forAccounts(
                                    accounts = state.accounts.filter { it.currencyId == selection.currency.id },
                                    currency = selection.currency,
                                    period = period,
                                )
                        }
                    val slices = snapshotToSlices(snapshot, _state.value.budgetAlertCategoryIds)
                    val expenseTiles = snapshotToExpenseTiles(snapshot, _state.value.budgetAlertCategoryIds)
                    val ringFraction = snapshot.toRingFraction()
                    val periodNet = snapshot.toPeriodNet()
                    val settings = appSettingsRepository.settings.first()
                    val firstPositive = !settings.firstPositiveSeen && snapshot.net.amount.signum() > 0
                    _state.value =
                        _state.value.copy(
                            balanceSnapshot = snapshot,
                            periodNet = periodNet,
                            ringFraction = ringFraction,
                            slices = slices,
                            expenseTiles = expenseTiles,
                            isLoading = false,
                            showConfetti = firstPositive,
                        )
                    if (firstPositive) {
                        appSettingsRepository.update { it.copy(firstPositiveSeen = true) }
                    }
                }
        }

        internal fun snapshotToExpenseTiles(
            snapshot: BalanceSnapshot,
            alertCategoryIds: Set<Long>,
        ): List<CategoryTileItem> {
            val totalExpense = snapshot.expense.amount
            return snapshot.byCategory
                .filter { it.isExpense && it.categoryId != OTHER_CATEGORY_ID }
                .sortedByDescending { it.total.amount }
                .map { catBal ->
                    val fraction =
                        if (totalExpense.signum() == 0) {
                            0f
                        } else {
                            (catBal.total.amount.toFloat() / totalExpense.toFloat()).coerceIn(0f, 1f)
                        }
                    CategoryTileItem(
                        categoryId = catBal.categoryId,
                        label = catBal.categoryName,
                        amount = catBal.total,
                        fraction = fraction,
                        colorHex = catBal.colorHex,
                        iconKey = catBal.iconKey,
                        hasBudgetAlert = catBal.categoryId in alertCategoryIds,
                    )
                }
        }

        internal fun snapshotToSlices(
            snapshot: BalanceSnapshot,
            alertCategoryIds: Set<Long>,
        ): List<CategorySlice> {
            val totalExpense = snapshot.expense.amount
            val expenseSlices =
                snapshot.byCategory
                    .filter { it.isExpense }
                    .map { catBal ->
                        val fraction =
                            if (totalExpense.signum() == 0) {
                                0f
                            } else {
                                catBal.total.amount.toFloat() / totalExpense.toFloat()
                            }
                        CategorySlice(
                            categoryId = catBal.categoryId,
                            color = parseHexColor(catBal.colorHex),
                            fraction = fraction,
                            label = catBal.categoryName,
                            iconKey = catBal.iconKey,
                            hasBudgetAlert = catBal.categoryId in alertCategoryIds,
                        )
                    }
            if (totalExpense.signum() == 0) return expenseSlices

            val (minorSlices, majorSlices) = expenseSlices.partition { it.fraction < OTHER_GROUP_MAX_FRACTION }
            if (minorSlices.isEmpty()) return expenseSlices

            val majorFraction = majorSlices.sumOf { it.fraction.toDouble() }.toFloat()
            val otherFraction = (1f - majorFraction).coerceAtLeast(0f)
            return majorSlices +
                CategorySlice(
                    categoryId = OTHER_CATEGORY_ID,
                    color = Color.Unspecified,
                    fraction = otherFraction,
                    label = "",
                    iconKey = OTHER_CATEGORY_ICON_KEY,
                    hasBudgetAlert = false,
                )
        }

        private fun BalanceSnapshot.toRingFraction(): Float {
            if (income.amount.signum() == 0) return 0f
            return (expense.amount.toFloat() / income.amount.toFloat()).coerceIn(0f, 1f)
        }

        private fun BalanceSnapshot.toPeriodNet(): Money =
            Money(
                amount = income.amount.subtract(expense.amount),
                currency = income.currency,
            )

        fun onEvent(event: DashboardEvent) {
            when (event) {
                is DashboardEvent.PeriodChanged -> {
                    _state.value = _state.value.copy(period = event.period)
                    clearImportFocus()
                    recomputeBalance()
                }
                DashboardEvent.PreviousPeriod -> {
                    _state.value = _state.value.copy(period = _state.value.period.previous())
                    clearImportFocus()
                    recomputeBalance()
                }
                DashboardEvent.NextPeriod -> {
                    _state.value = _state.value.copy(period = _state.value.period.next())
                    clearImportFocus()
                    recomputeBalance()
                }
                is DashboardEvent.AccountSelected -> {
                    val acc = _state.value.accounts.firstOrNull { it.id == event.accountId } ?: return
                    _state.value =
                        _state.value.copy(
                            dashboardSelection = DashboardSelection.SpecificAccount(acc),
                            leftDrawerOpen = false,
                        )
                    clearImportFocus()
                    selectBudgetAlerts()
                    recomputeBalance()
                    viewModelScope.launch {
                        appSettingsRepository.update {
                            it.copy(
                                defaultAccountId = event.accountId,
                                dashboardSelectionMode = DASHBOARD_SELECTION_SPECIFIC,
                            )
                        }
                    }
                }
                DashboardEvent.AllAccountsSelected -> {
                    val currency = _state.value.currentCurrency ?: return
                    _state.value =
                        _state.value.copy(
                            dashboardSelection = DashboardSelection.AllAccounts(currency),
                            leftDrawerOpen = false,
                        )
                    clearImportFocus()
                    selectBudgetAlerts()
                    recomputeBalance()
                    viewModelScope.launch {
                        appSettingsRepository.update { it.copy(dashboardSelectionMode = DASHBOARD_SELECTION_ALL) }
                    }
                }
                DashboardEvent.LeftDrawerToggled ->
                    _state.value =
                        _state.value.copy(
                            leftDrawerOpen = !_state.value.leftDrawerOpen,
                            rightDrawerOpen = false,
                        )
                DashboardEvent.RightDrawerToggled ->
                    _state.value =
                        _state.value.copy(
                            rightDrawerOpen = !_state.value.rightDrawerOpen,
                            leftDrawerOpen = false,
                        )
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
                DashboardEvent.FinancialGoalsClicked -> {
                    closeDrawers()
                    emit(DashboardAction.NavigateFinancialGoals)
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
                    val range = PeriodArithmetic.toEpochMillisRange(_state.value.period)
                    when (val selection = _state.value.dashboardSelection) {
                        is DashboardSelection.SpecificAccount ->
                            emit(
                                DashboardAction.NavigateTransactionsByAccount(selection.account.id, range.first, range.last),
                            )
                        is DashboardSelection.AllAccounts ->
                            emit(
                                DashboardAction.NavigateTransactionsByCurrency(selection.currency.id, range.first, range.last),
                            )
                        null -> Unit
                    }
                }
                is DashboardEvent.SliceClicked -> {
                    if (event.categoryId == OTHER_CATEGORY_ID) return
                    val range = PeriodArithmetic.toEpochMillisRange(_state.value.period)
                    when (val selection = _state.value.dashboardSelection) {
                        is DashboardSelection.SpecificAccount -> {
                            val currency = _state.value.currencies.firstOrNull { it.id == selection.account.currencyId } ?: return
                            emit(
                                DashboardAction.NavigateTransactionsByCategory(
                                    selection.account.id,
                                    currency.id,
                                    event.categoryId,
                                    range.first,
                                    range.last,
                                ),
                            )
                        }
                        is DashboardSelection.AllAccounts -> {
                            emit(
                                DashboardAction.NavigateTransactionsByCategory(
                                    null,
                                    selection.currency.id,
                                    event.categoryId,
                                    range.first,
                                    range.last,
                                ),
                            )
                        }
                        null -> Unit
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

private data class BudgetAlertSelection(
    val accountId: Long,
)

private data class DashboardInputs(
    val accounts: List<Account>,
    val currencies: List<Currency>,
    val settings: AppSettings,
)

private const val DASHBOARD_SELECTION_SPECIFIC = "specific_account"
private const val DASHBOARD_SELECTION_ALL = "all_accounts"
internal const val OTHER_GROUP_MAX_FRACTION = 0.02f
const val OTHER_CATEGORY_ID = -1L
const val OTHER_CATEGORY_ICON_KEY = "ic_cat_other"
