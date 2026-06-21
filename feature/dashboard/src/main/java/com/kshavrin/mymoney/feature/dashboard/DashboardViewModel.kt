package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.designsystem.dialog.RateRow
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryBalance
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.DomainEvent
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.ObserveBudgetAlertsUseCase
import com.kshavrin.mymoney.core.domain.usecase.ResolveRateUseCase
import com.kshavrin.mymoney.core.domain.usecase.RingGaugeCalculator
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
import java.math.BigDecimal
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
        private val resolveRateUseCase: ResolveRateUseCase,
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

        // One-shot cross-rates the user confirmed for the current "All accounts → convert to one"
        // fold, keyed by source currency id → cross-rate into the target currency. These are NOT
        // persisted (D5); they live only as long as this convert selection is active and are
        // discarded the moment the selection changes.
        private var allAccountsRateOverrides: Map<Long, BigDecimal> = emptyMap()

        // The fold target the user picked in the picker, kept only between the picker and the rate
        // confirmation step. Re-asked every time the convert flow runs (D7).
        private var pendingConvertTarget: Currency? = null

        // The selected period lives only in transient state, so before this guard the cold-started
        // ViewModel snaps to the current month and hides data imported into a past month once the
        // one-shot import focus is cleared. On the first settings emission we restore the persisted
        // period anchor; afterwards in-session navigation owns the period.
        private var restoredPersistedPeriod = false

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
                    val period = resolvePeriod(focusPeriod, current.period, inputs.settings)
                    _state.value =
                        current.copy(
                            accounts = inputs.accounts,
                            currencies = inputs.currencies,
                            period = period,
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

        // Cold start: an import focus wins, then the persisted period anchor, then the current
        // (defaulted-to-now) state. Once the persisted anchor has been restored, in-session
        // navigation owns the period and settings re-emissions must not pull it back.
        private fun resolvePeriod(
            focusPeriod: Period.Month?,
            current: Period,
            settings: AppSettings,
        ): Period {
            if (focusPeriod != null) {
                restoredPersistedPeriod = true
                // Durably record the imported month so it still resolves after the transient
                // import focus is cleared on the first period/account navigation.
                if (settings.dashboardPeriodEpochMs != focusPeriod.anchorEpochMsOrNull()) {
                    persistDashboardPeriod(focusPeriod)
                }
                return focusPeriod
            }
            if (!restoredPersistedPeriod) {
                restoredPersistedPeriod = true
                settings.dashboardPeriod()?.let { return it }
            }
            return current
        }

        private fun AppSettings.dashboardPeriod(): Period.Month? =
            dashboardPeriodEpochMs
                .takeIf { it > 0L }
                ?.let { epochMs ->
                    Period.Month(YearMonth.from(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())))
                }

        private fun persistDashboardPeriod(period: Period) {
            val epochMs = period.anchorEpochMsOrNull() ?: return
            viewModelScope.launch {
                appSettingsRepository.update { it.copy(dashboardPeriodEpochMs = epochMs) }
            }
        }

        private fun Period.anchorEpochMsOrNull(): Long? =
            when (this) {
                is Period.Month ->
                    yearMonth
                        .atDay(1)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                else -> null
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
            // A restored "all accounts" selection cannot remember a fold target — the target is
            // asked every time (D7) — so it comes back in Separate mode (SPEC 08).
            return if (settings.dashboardSelectionMode == DASHBOARD_SELECTION_ALL && accounts.isNotEmpty()) {
                DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate)
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
                    if (accounts.isEmpty()) {
                        null
                    } else {
                        // A ConvertTo target that has been removed degrades to Separate.
                        when (val mode = current.foldMode) {
                            is AllAccountsFoldMode.ConvertTo ->
                                currencies
                                    .firstOrNull { it.id == mode.target.id }
                                    ?.let { DashboardSelection.AllAccounts(AllAccountsFoldMode.ConvertTo(it)) }
                                    ?: DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate)
                            AllAccountsFoldMode.Separate -> current
                        }
                    }
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
            // Import focus surfaces the freshly imported data across every account; the per-currency
            // breakdown (SPEC 08) is the safe default since no fold target was chosen (D7).
            return DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate)
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
                    val snapshot = computeSnapshot(selection, state.accounts, period)
                    val previousExpense =
                        if (snapshot.income.amount.signum() == 0 && snapshot.expense.amount.signum() > 0) {
                            computeSnapshot(selection, state.accounts, period.previous()).expense
                        } else {
                            null
                        }
                    val gauge =
                        RingGaugeCalculator(
                            income = snapshot.income,
                            expense = snapshot.expense,
                            previousExpense = previousExpense,
                        )
                    val periodNet = snapshot.toPeriodNet()
                    val settings = appSettingsRepository.settings.first()
                    val firstPositive = !settings.firstPositiveSeen && snapshot.net.amount.signum() > 0
                    val slices = snapshotToSlices(snapshot, _state.value.budgetAlertCategoryIds)
                    val expenseTiles = snapshotToExpenseTiles(snapshot, _state.value.budgetAlertCategoryIds)
                    val currencyCards = computeCurrencyCards(selection, state.accounts, period)
                    _state.value =
                        _state.value.copy(
                            balanceSnapshot = snapshot,
                            currencyCards = currencyCards,
                            periodNet = periodNet,
                            ringFraction = gauge.fraction,
                            ringIsExpense = gauge.isExpense,
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

        private suspend fun computeSnapshot(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period,
        ): BalanceSnapshot =
            when (selection) {
                is DashboardSelection.SpecificAccount -> balanceCalculator(selection.account.id, period)
                is DashboardSelection.AllAccounts ->
                    when (val mode = selection.foldMode) {
                        is AllAccountsFoldMode.ConvertTo ->
                            convertedAllAccountsSnapshot(accounts, mode.target, period)
                        AllAccountsFoldMode.Separate ->
                            // Per-currency rendering is delivered by SPEC 08; until then fold into the
                            // first account's currency group so the dashboard has a non-crashing snapshot.
                            separateFallbackSnapshot(accounts, period)
                    }
            }

        // Aggregate every account in [target]: balance each same-currency group with
        // BalanceCalculator.forAccounts (G11 requires one currency per call), convert each group's
        // figures into the target using the confirmed one-shot cross-rates, then sum. Conversion
        // happens AFTER each group's balance is computed — never sum across currencies before
        // converting.
        private suspend fun convertedAllAccountsSnapshot(
            accounts: List<Account>,
            target: Currency,
            period: Period,
        ): BalanceSnapshot {
            val groups =
                accounts
                    .filterNot { it.isArchived }
                    .groupBy { it.currencyId }

            var totalIncome = BigDecimal.ZERO
            var totalExpense = BigDecimal.ZERO
            val categoryTotals = mutableListOf<CategoryBalance>()

            for ((currencyId, groupAccounts) in groups) {
                val groupCurrency = _state.value.currencies.firstOrNull { it.id == currencyId } ?: continue
                val groupSnapshot = balanceCalculator.forAccounts(groupAccounts, groupCurrency, period)

                totalIncome = totalIncome.add(convertAmount(groupSnapshot.income.amount, groupCurrency, target))
                totalExpense = totalExpense.add(convertAmount(groupSnapshot.expense.amount, groupCurrency, target))

                groupSnapshot.byCategory.forEach { catBal ->
                    categoryTotals +=
                        catBal.copy(total = Money(convertAmount(catBal.total.amount, groupCurrency, target), target))
                }
            }

            val income = totalIncome.toMoneyScale(target)
            val expense = totalExpense.toMoneyScale(target)
            val net = income.subtract(expense).toMoneyScale(target)
            val combined = income.add(expense)
            return BalanceSnapshot(
                income = Money(income, target),
                expense = Money(expense, target),
                net = Money(net, target),
                byCategory =
                    mergeCategoryBalances(categoryTotals, target).map { catBal ->
                        catBal.copy(
                            fraction =
                                if (combined.signum() == 0) {
                                    0f
                                } else {
                                    catBal.total.amount.toFloat() / combined.toFloat()
                                },
                        )
                    },
            )
        }

        // The same category can appear in more than one currency group; merge them by id/kind in
        // the target currency so the donut and tiles see a single row per category.
        private fun mergeCategoryBalances(
            balances: List<CategoryBalance>,
            target: Currency,
        ): List<CategoryBalance> =
            balances
                .groupBy { it.categoryId to it.isExpense }
                .map { (_, entries) ->
                    val first = entries.first()
                    first.copy(
                        total =
                            Money(
                                entries
                                    .fold(BigDecimal.ZERO) { acc, b -> acc.add(b.total.amount) }
                                    .toMoneyScale(target),
                                target,
                            ),
                    )
                }

        // [allAccountsRateOverrides] maps a source currency id to the one-shot cross-rate the user
        // confirmed (units of [to] per 1 unit of [from], e.g. 1 EUR = 1.2 USD). Multiply the source
        // amount by that cross-rate to land in the target currency. Same-currency groups pass
        // through unchanged; a missing override (cannot happen once the confirm list is honoured)
        // degrades that group to zero rather than crashing.
        private fun convertAmount(
            amount: BigDecimal,
            from: Currency,
            to: Currency,
        ): BigDecimal {
            if (from.id == to.id) return amount.toMoneyScale(to)
            val crossRate = allAccountsRateOverrides[from.id] ?: return BigDecimal.ZERO
            return amount.multiply(crossRate).toMoneyScale(to)
        }

        // "All accounts → show separately" (D6/G12): one card per currency that has active accounts.
        // Each currency group is balanced on its own with BalanceCalculator.forAccounts (G11 requires
        // a single currency per call) — no conversion, no rates. Cards are sorted by largest absolute
        // net first, then by currency code, so the busiest currency leads the stack. Returns empty for
        // every selection/mode other than Separate so the stacked view only appears where it belongs.
        private suspend fun computeCurrencyCards(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period,
        ): List<CurrencyBalanceCard> {
            val allAccounts = selection as? DashboardSelection.AllAccounts ?: return emptyList()
            if (allAccounts.foldMode != AllAccountsFoldMode.Separate) return emptyList()

            return accounts
                .filterNot { it.isArchived }
                .groupBy { it.currencyId }
                .mapNotNull { (currencyId, groupAccounts) ->
                    val currency = _state.value.currencies.firstOrNull { it.id == currencyId } ?: return@mapNotNull null
                    CurrencyBalanceCard(
                        currency = currency,
                        snapshot = balanceCalculator.forAccounts(groupAccounts, currency, period),
                    )
                }.sortedWith(
                    compareByDescending<CurrencyBalanceCard> {
                        it.snapshot.net.amount
                            .abs()
                    }.thenBy { it.currency.code },
                )
        }

        private suspend fun separateFallbackSnapshot(
            accounts: List<Account>,
            period: Period,
        ): BalanceSnapshot {
            val active = accounts.filterNot { it.isArchived }
            val firstCurrency =
                active
                    .firstOrNull()
                    ?.let { account -> _state.value.currencies.firstOrNull { it.id == account.currencyId } }
                    ?: return emptySnapshot(DASHBOARD_FALLBACK_CURRENCY)
            val group = active.filter { it.currencyId == firstCurrency.id }
            return balanceCalculator.forAccounts(group, firstCurrency, period)
        }

        private fun emptySnapshot(currency: Currency): BalanceSnapshot =
            BalanceSnapshot(
                income = Money(BigDecimal.ZERO.toMoneyScale(currency), currency),
                expense = Money(BigDecimal.ZERO.toMoneyScale(currency), currency),
                net = Money(BigDecimal.ZERO.toMoneyScale(currency), currency),
                byCategory = emptyList(),
            )

        private fun BalanceSnapshot.toPeriodNet(): Money =
            Money(
                amount = income.amount.subtract(expense.amount),
                currency = income.currency,
            )

        fun onEvent(event: DashboardEvent) {
            when (event) {
                is DashboardEvent.PeriodChanged -> {
                    _state.value = _state.value.copy(period = event.period)
                    restoredPersistedPeriod = true
                    clearImportFocus()
                    persistDashboardPeriod(event.period)
                    recomputeBalance()
                }
                DashboardEvent.PreviousPeriod -> {
                    val period = _state.value.period.previous()
                    _state.value = _state.value.copy(period = period)
                    restoredPersistedPeriod = true
                    clearImportFocus()
                    persistDashboardPeriod(period)
                    recomputeBalance()
                }
                DashboardEvent.NextPeriod -> {
                    val period = _state.value.period.next()
                    _state.value = _state.value.copy(period = period)
                    restoredPersistedPeriod = true
                    clearImportFocus()
                    persistDashboardPeriod(period)
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
                    _state.value = _state.value.copy(leftDrawerOpen = false)
                    emit(DashboardAction.ShowAllAccountsModeDialog)
                }
                DashboardEvent.AllAccountsConvertChosen -> {
                    // Target currency is asked every time (D7) — nothing is pre-selected here.
                    emit(DashboardAction.ShowTargetCurrencyPicker(_state.value.currencies))
                }
                DashboardEvent.AllAccountsSeparateChosen -> applyAllAccountsSeparate()
                is DashboardEvent.AllAccountsTargetCurrencyChosen -> openRateConfirm(event.currencyId)
                is DashboardEvent.AllAccountsRatesConfirmed -> applyAllAccountsConvert(event.rateOverrides)
                DashboardEvent.AllAccountsConversionDismissed -> Unit
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
                            (selection.foldMode as? AllAccountsFoldMode.ConvertTo)?.let { mode ->
                                emit(
                                    DashboardAction.NavigateTransactionsByCurrency(mode.target.id, range.first, range.last),
                                )
                            }
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
                            (selection.foldMode as? AllAccountsFoldMode.ConvertTo)?.let { mode ->
                                emit(
                                    DashboardAction.NavigateTransactionsByCategory(
                                        null,
                                        mode.target.id,
                                        event.categoryId,
                                        range.first,
                                        range.last,
                                    ),
                                )
                            }
                        }
                        null -> Unit
                    }
                }
                DashboardEvent.ConfettiAcknowledged ->
                    _state.value = _state.value.copy(showConfetti = false)
            }
        }

        // "Show each currency separately" (SPEC 08). No rate work is needed, but the aggregate
        // selection mode is still persisted so a cold start restores an all-accounts view.
        private fun applyAllAccountsSeparate() {
            pendingConvertTarget = null
            allAccountsRateOverrides = emptyMap()
            _state.value =
                _state.value.copy(
                    dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                    leftDrawerOpen = false,
                )
            clearImportFocus()
            selectBudgetAlerts()
            recomputeBalance()
            viewModelScope.launch {
                appSettingsRepository.update { it.copy(dashboardSelectionMode = DASHBOARD_SELECTION_ALL) }
            }
        }

        // Resolve the cross-rate of every currency that has an account into the chosen target and
        // show the confirmation list (SPEC 05 / D9). One row per source currency that differs from
        // the target; EUR-based cross-rates via ResolveRateUseCase (SPEC 04 — lazy refresh, offline
        // fallback, never throws).
        private fun openRateConfirm(targetCurrencyId: Long) {
            val target = _state.value.currencies.firstOrNull { it.id == targetCurrencyId } ?: return
            pendingConvertTarget = target
            viewModelScope.launch {
                val sourceCurrencies =
                    _state.value.accounts
                        .filterNot { it.isArchived }
                        .map { it.currencyId }
                        .distinct()
                        .mapNotNull { id -> _state.value.currencies.firstOrNull { it.id == id } }
                        .filter { it.id != target.id }

                if (sourceCurrencies.isEmpty()) {
                    // Every account is already in the target currency — nothing to confirm.
                    applyAllAccountsConvert(emptyMap())
                    return@launch
                }

                val rows = mutableListOf<RateRow>()
                val sourceIds = mutableListOf<Long>()
                sourceCurrencies.forEach { source ->
                    val info = resolveRateUseCase(source, target)
                    rows +=
                        RateRow(
                            fromCode = source.code,
                            toCode = target.code,
                            lastUpdated = info.lastUpdated,
                            displayRate = info.crossRate,
                            stale = info.stale,
                            missing = info.missing,
                        )
                    sourceIds += source.id
                }
                emit(DashboardAction.ShowAllAccountsRateConfirm(rows, sourceIds))
            }
        }

        // The user confirmed (and possibly edited) the cross-rates. [rateOverrides] maps a source
        // currency id to its one-shot cross-rate into the target; these are kept in memory only and
        // never written to the rate table (D5).
        private fun applyAllAccountsConvert(rateOverrides: Map<Long, BigDecimal>) {
            val target = pendingConvertTarget ?: return
            pendingConvertTarget = null
            allAccountsRateOverrides = rateOverrides
            _state.value =
                _state.value.copy(
                    dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.ConvertTo(target)),
                    leftDrawerOpen = false,
                )
            clearImportFocus()
            selectBudgetAlerts()
            recomputeBalance()
            viewModelScope.launch {
                appSettingsRepository.update { it.copy(dashboardSelectionMode = DASHBOARD_SELECTION_ALL) }
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

private val DASHBOARD_FALLBACK_CURRENCY =
    Currency(
        id = 0L,
        code = "",
        symbol = "",
        name = "",
        decimalDigits = 2,
        isActive = false,
        sortOrder = 0,
    )
internal const val OTHER_GROUP_MAX_FRACTION = 0.02f
const val OTHER_CATEGORY_ID = -1L
const val OTHER_CATEGORY_ICON_KEY = "ic_cat_other"
