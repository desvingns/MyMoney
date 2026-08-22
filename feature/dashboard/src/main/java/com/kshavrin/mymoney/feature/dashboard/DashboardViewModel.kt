package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.datastore.usecase.DashboardDataUseCase
import com.kshavrin.mymoney.core.designsystem.dialog.RateRow
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryBalance
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.DomainEvent
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.SummaryRecord
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.core.domain.model.toMoneyScale
import com.kshavrin.mymoney.core.domain.time.PeriodArithmetic
import com.kshavrin.mymoney.core.domain.usecase.BalanceCalculator
import com.kshavrin.mymoney.core.domain.usecase.BalanceTrendCalculator
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetOperationsSummaryUseCase
import com.kshavrin.mymoney.core.domain.usecase.IntradayTrendCalculator
import com.kshavrin.mymoney.core.domain.usecase.ObserveBudgetAlertsUseCase
import com.kshavrin.mymoney.core.domain.usecase.ResolveRateUseCase
import com.kshavrin.mymoney.core.domain.usecase.RingGaugeCalculator
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import com.kshavrin.mymoney.feature.dashboard.components.SummaryRecordCategoryDisplay
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val dashboardDataUseCase: DashboardDataUseCase,
        private val balanceCalculator: BalanceCalculator,
        private val balanceTrendCalculator: BalanceTrendCalculator,
        private val intradayTrendCalculator: IntradayTrendCalculator,
        private val observeBudgetAlertsUseCase: ObserveBudgetAlertsUseCase,
        private val resolveRateUseCase: ResolveRateUseCase,
        private val getCategoryRecords: GetCategoryRecordsUseCase,
        private val getOperationsSummary: GetOperationsSummaryUseCase,
        private val journalSync: JournalSync,
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

        private var categoryRecordsJob: Job? = null

        // Best-effort background jobs that precompute the adjacent periods' render-state for the
        // swipe pager (SPEC 02). Cancelled and relaunched on every committed period/selection change
        // so a stale neighbor never surfaces (G9). Never run for [Period.All] (no neighbors, G13).
        private var neighborJob: Job? = null

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
        private var importFocusCurrencyId: Long? = null

        init {
            observeAccountsAndCurrencies()
            observeTransactionChanges()
            observeBudgetAlerts()
            observeExpenseCategories()
            observeCategoryDisplays()
        }

        // Resolve icon + name for every category (income and expense) so the operations-summary rows
        // (SPEC 03) can render each operation's category leaf without re-querying per row.
        private fun observeCategoryDisplays() {
            viewModelScope.launch {
                dashboardDataUseCase.observeCategories().collect { categories ->
                    _state.value =
                        _state.value.copy(
                            categoryDisplays =
                                categories.associate { category ->
                                    category.id to
                                        SummaryRecordCategoryDisplay(
                                            name = category.name,
                                            iconKey = category.iconKey,
                                        )
                                },
                        )
                }
            }
        }

        private fun observeExpenseCategories() {
            viewModelScope.launch {
                dashboardDataUseCase.observeExpenseCategories().collect { categories ->
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
                labelColor = parseHexColor(category.textColor),
                iconKey = category.iconKey,
            )

        private fun observeAccountsAndCurrencies() {
            viewModelScope.launch {
                dashboardDataUseCase.observeInputs().collect { inputs ->
                    val current = _state.value
                    val focusPeriod = inputs.settings.importFocusPeriod()
                    val focusSelection = inputs.settings.importFocusSelection(inputs.accounts)
                    importFocusCurrencyId =
                        if (focusSelection != null) {
                            inputs.settings.importFocusCurrencyId
                        } else {
                            null
                        }
                    val selection =
                        if (focusPeriod != null) {
                            focusSelection
                                ?: resolveDashboardSelection(
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
                    val chartConfig = inputs.settings.toChartConfig()
                    val contextChanged = current.period != period || current.dashboardSelection != selection
                    val balanceInputsChanged =
                        current.accounts != inputs.accounts ||
                            current.currencies != inputs.currencies ||
                            contextChanged
                    if (contextChanged) categoryRecordsJob?.cancel()
                    _state.value =
                        current.copy(
                            accounts = inputs.accounts,
                            currencies = inputs.currencies,
                            period = period,
                            dashboardSelection = selection,
                            isLoading = selection == null,
                            chartConfig = chartConfig,
                            expandedCategoryId = if (contextChanged) null else current.expandedCategoryId,
                            expandedRecords = if (contextChanged) emptyList() else current.expandedRecords,
                            expandedRecordsLoading = if (contextChanged) false else current.expandedRecordsLoading,
                        )
                    if (balanceInputsChanged) {
                        selectBudgetAlerts()
                        recomputeBalance()
                    }
                }
            }
        }

        private fun clearImportFocus() {
            viewModelScope.launch {
                dashboardDataUseCase.updateSettings {
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
                dashboardDataUseCase.updateSettings { it.copy(dashboardPeriodEpochMs = epochMs) }
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

        private fun AppSettings.importFocusSelection(accounts: List<Account>): DashboardSelection.AllAccounts? {
            if (importFocusEpochMs <= 0L || importFocusCurrencyId <= 0L) return null
            if (accounts.none { it.currencyId == importFocusCurrencyId }) return null
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
                dashboardDataUseCase.observeTransactionChanges().collect {
                    recomputeBalance()
                }
            }
        }

        // Commit a swipe-driven period change WITHOUT a stale frame. The pager snaps back to the
        // centre page synchronously, and the centre page projects the committed render fields; if we
        // only changed [period] here the centre page would read the previous period's figures until
        // the async recomputeBalance() lands (~100-300ms later), flashing the wrong-period chart.
        // So we atomically promote the already-precomputed neighbour page's fields into the committed
        // state in the SAME copy that sets the new period. recomputeBalance() then re-confirms the
        // identical data with no visible change. If the neighbour was not precomputed yet (very fast
        // swipe), fall back to a clean loading placeholder rather than retain the old period's data.
        private fun commitSwipedPeriod(
            period: Period,
            neighbor: PeriodPageState?,
        ) {
            clearExpandedCategory()
            _state.value =
                if (neighbor != null) {
                    _state.value.copy(
                        period = period,
                        balanceSnapshot = neighbor.balanceSnapshot,
                        currencyCards = neighbor.currencyCards,
                        periodNet = neighbor.periodNet,
                        ringFraction = neighbor.ringFraction,
                        ringIsExpense = neighbor.ringIsExpense,
                        trendPoints = neighbor.trendPoints,
                        trendAxis = neighbor.trendAxis,
                        slices = neighbor.slices,
                        expenseTiles = neighbor.expenseTiles,
                        isLoading = neighbor.isLoading,
                    )
                } else {
                    _state.value.copy(
                        period = period,
                        balanceSnapshot = null,
                        currencyCards = emptyList(),
                        periodNet = Money.zero(DASHBOARD_FALLBACK_CURRENCY),
                        ringFraction = 0f,
                        ringIsExpense = false,
                        trendPoints = emptyList(),
                        trendAxis = ChartTrendAxis.None,
                        slices = emptyList(),
                        expenseTiles = emptyList(),
                        isLoading = true,
                    )
                }
        }

        private fun recomputeBalance() {
            val state = _state.value
            recomputeJob?.cancel()
            neighborJob?.cancel()
            val selection = state.dashboardSelection
            if (selection == null) {
                _state.value =
                    state.copy(
                        balanceSnapshot = null,
                        currencyCards = emptyList(),
                        periodNet = Money.zero(DASHBOARD_FALLBACK_CURRENCY),
                        ringFraction = 0f,
                        ringIsExpense = false,
                        trendPoints = emptyList(),
                        trendAxis = ChartTrendAxis.None,
                        slices = emptyList(),
                        expenseTiles = emptyList(),
                        previousPeriodPage = null,
                        nextPeriodPage = null,
                    )
                return
            }
            val period = _state.value.period
            if (_state.value.previousPeriodPage != null || _state.value.nextPeriodPage != null) {
                _state.value = _state.value.copy(previousPeriodPage = null, nextPeriodPage = null)
            }
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
                    val settings = dashboardDataUseCase.currentSettings()
                    val firstPositive = !settings.firstPositiveSeen && snapshot.net.amount.signum() > 0
                    val chartConfig = _state.value.chartConfig
                    val currencyCards = computeCurrencyCards(selection, state.accounts, period, chartConfig)
                    val trend =
                        if (selection.isSeparateMode()) {
                            TrendResult(emptyList(), ChartTrendAxis.None)
                        } else {
                            computeTrend(selection, state.accounts, period, chartConfig)
                        }
                    val slices = snapshotToSlices(snapshot, _state.value.budgetAlertCategoryIds)
                    val expenseTiles = snapshotToExpenseTiles(snapshot, _state.value.budgetAlertCategoryIds)
                    _state.value =
                        _state.value.copy(
                            balanceSnapshot = snapshot,
                            currencyCards = currencyCards,
                            periodNet = periodNet,
                            ringFraction = gauge.fraction,
                            ringIsExpense = gauge.isExpense,
                            trendPoints = trend.points,
                            trendAxis = trend.axis,
                            slices = slices,
                            expenseTiles = expenseTiles,
                            isLoading = false,
                            showConfetti = firstPositive,
                        )
                    if (firstPositive) {
                        dashboardDataUseCase.updateSettings { it.copy(firstPositiveSeen = true) }
                    }
                    recomputeNeighbors(selection, state.accounts, period, chartConfig)
                }
        }

        // Precompute the previous/next period's render-state in the background so the swipe pager
        // (SPEC 02) can render the adjacent page with real data. Each neighbor is computed with the
        // SAME per-period helpers used for the center period (G9) and exposed independently the
        // moment its job lands. [Period.All] has no neighbors (G13) so nothing is computed.
        private fun recomputeNeighbors(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period,
            chartConfig: ChartConfig,
        ) {
            neighborJob?.cancel()
            if (period is Period.All) return
            neighborJob =
                viewModelScope.launch {
                    launch {
                        val page = computePeriodPage(selection, accounts, period.previous(), chartConfig)
                        if (_state.value.period == period) {
                            _state.value = _state.value.copy(previousPeriodPage = page)
                        }
                    }
                    launch {
                        val page = computePeriodPage(selection, accounts, period.next(), chartConfig)
                        if (_state.value.period == period) {
                            _state.value = _state.value.copy(nextPeriodPage = page)
                        }
                    }
                }
        }

        private suspend fun computePeriodPage(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period,
            chartConfig: ChartConfig,
        ): PeriodPageState {
            val snapshot = computeSnapshot(selection, accounts, period)
            val previousExpense =
                if (snapshot.income.amount.signum() == 0 && snapshot.expense.amount.signum() > 0) {
                    computeSnapshot(selection, accounts, period.previous()).expense
                } else {
                    null
                }
            val gauge =
                RingGaugeCalculator(
                    income = snapshot.income,
                    expense = snapshot.expense,
                    previousExpense = previousExpense,
                )
            val currencyCards = computeCurrencyCards(selection, accounts, period, chartConfig)
            val trend =
                if (selection.isSeparateMode()) {
                    TrendResult(emptyList(), ChartTrendAxis.None)
                } else {
                    computeTrend(selection, accounts, period, chartConfig)
                }
            return PeriodPageState(
                period = period,
                balanceSnapshot = snapshot,
                currencyCards = currencyCards,
                periodNet = snapshot.toPeriodNet(),
                ringFraction = gauge.fraction,
                ringIsExpense = gauge.isExpense,
                trendPoints = trend.points,
                trendAxis = trend.axis,
                slices = snapshotToSlices(snapshot, _state.value.budgetAlertCategoryIds),
                expenseTiles = snapshotToExpenseTiles(snapshot, _state.value.budgetAlertCategoryIds),
                isSeparateMode = selection.isSeparateMode(),
                isLoading = false,
            )
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
                        textColorHex = catBal.textColorHex,
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
                            labelColor = parseHexColor(catBal.textColorHex),
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
        // a single currency per call) — no conversion, no rates. Each card also carries a per-currency
        // cumulative-balance trend (G17), computed by the same provider over that group only so every
        // point stays in the card's own currency; it honours the global chart config (period type /
        // point count / metric) and is empty when the chart is hidden in settings. Cards are sorted by
        // largest absolute net first, then by currency code, so the busiest currency leads the stack.
        // Returns empty for every selection/mode other than Separate so the stacked view only appears
        // where it belongs.
        private suspend fun computeCurrencyCards(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period,
            chartConfig: ChartConfig,
        ): List<CurrencyBalanceCard> {
            val allAccounts = selection as? DashboardSelection.AllAccounts ?: return emptyList()
            if (allAccounts.foldMode != AllAccountsFoldMode.Separate) return emptyList()

            val manualWindow =
                if (chartConfig.visible && !chartConfig.autoMode) {
                    balanceTrendCalculator.buildWindow(
                        anchor = trendAnchorPeriod(chartConfig.periodType, period),
                        count = chartConfig.pointCount,
                    )
                } else {
                    emptyList()
                }

            return accounts
                .filterNot { it.isArchived }
                .groupBy { it.currencyId }
                .mapNotNull { (currencyId, groupAccounts) ->
                    val currency = resolveRenderableCurrency(currencyId) ?: return@mapNotNull null
                    val trendPoints =
                        if (!chartConfig.visible) {
                            emptyList()
                        } else {
                            computeGroupTrend(groupAccounts, currency, period, chartConfig, manualWindow).points
                        }
                    CurrencyBalanceCard(
                        currency = currency,
                        snapshot = balanceCalculator.forAccounts(groupAccounts, currency, period),
                        trendPoints = trendPoints,
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
            val focusedAccount =
                importFocusCurrencyId?.let { currencyId ->
                    active.firstOrNull { it.currencyId == currencyId }
                }
            val firstCurrency =
                (focusedAccount ?: active.firstOrNull())
                    ?.let { account -> resolveRenderableCurrency(account.currencyId) }
                    ?: return emptySnapshot(DASHBOARD_FALLBACK_CURRENCY)
            val group = active.filter { it.currencyId == firstCurrency.id }
            return balanceCalculator.forAccounts(group, firstCurrency, period)
        }

        private suspend fun resolveRenderableCurrency(currencyId: Long): Currency? =
            _state.value.currencies.firstOrNull { it.id == currencyId }
                ?: importFocusCurrencyId
                    ?.takeIf { it == currencyId }
                    ?.let { dashboardDataUseCase.findCurrency(it) }

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

        private fun DashboardSelection.isSeparateMode(): Boolean =
            this is DashboardSelection.AllAccounts && foldMode == AllAccountsFoldMode.Separate

        // Follow mirrors the dashboard period; the calendar variants anchor an independent window
        // of that granularity at "now" so the chart stays meaningful regardless of which period
        // the dashboard is currently showing.
        private fun trendAnchorPeriod(
            periodType: ChartPeriodType,
            dashboardPeriod: Period,
        ): Period {
            val today = java.time.LocalDate.now(ZoneId.systemDefault())
            return when (periodType) {
                ChartPeriodType.Follow -> dashboardPeriod
                ChartPeriodType.Day -> Period.Day(today)
                ChartPeriodType.Week -> Period.Week(today.with(java.time.DayOfWeek.MONDAY))
                ChartPeriodType.Month -> Period.Month(YearMonth.from(today))
                ChartPeriodType.Year -> Period.Year(today.year)
            }
        }

        // The aggregate (Aurora-card) trend for the current selection. In auto mode (the default) the
        // window is derived from the dashboard's selected period: Period.Day routes through the
        // intra-day calculator on the raw transactions of the selection (SPEC 03), every other period
        // builds an auto window + auto series anchored on the selected period (SPEC 01/02). Manual
        // mode keeps the legacy buildWindow + independent-anchor + pointCount path byte-for-byte (G14).
        private suspend fun computeTrend(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period,
            chartConfig: ChartConfig,
        ): TrendResult {
            if (!chartConfig.autoMode) {
                val points =
                    balanceTrendCalculator(
                        window =
                            balanceTrendCalculator.buildWindow(
                                anchor = trendAnchorPeriod(chartConfig.periodType, period),
                                count = chartConfig.pointCount,
                            ),
                        metric = chartConfig.metric,
                    ) { trendPeriod ->
                        computeSnapshot(selection, accounts, trendPeriod)
                    }
                return TrendResult(points, ChartTrendAxis.None)
            }
            if (period is Period.Day) {
                val transactions = selectionTransactions(selection, accounts, period)
                return intradayTrendResult(transactions, period.date, computeSnapshot(selection, accounts, period))
            }
            val window =
                balanceTrendCalculator.buildAutoWindow(
                    anchor = period,
                    earliestDate = if (period is Period.All) earliestTransactionDate() else null,
                    today = java.time.LocalDate.now(ZoneId.systemDefault()),
                )
            val points =
                balanceTrendCalculator.buildAutoSeries(window, chartConfig.metric) { trendPeriod ->
                    computeSnapshot(selection, accounts, trendPeriod)
                }
            return TrendResult(points, autoAxisFor(period))
        }

        // Per-currency-group trend for the "show separately" cards (D5/G10). Same auto/manual fork as
        // the aggregate trend, but every snapshot stays inside the group's own currency — no
        // conversion. Day routes through intra-day on the group's transactions only.
        private suspend fun computeGroupTrend(
            groupAccounts: List<Account>,
            currency: Currency,
            period: Period,
            chartConfig: ChartConfig,
            manualWindow: List<Period>,
        ): TrendResult {
            if (!chartConfig.autoMode) {
                if (manualWindow.isEmpty()) return TrendResult(emptyList(), ChartTrendAxis.None)
                val points =
                    balanceTrendCalculator(
                        window = manualWindow,
                        metric = chartConfig.metric,
                    ) { trendPeriod ->
                        balanceCalculator.forAccounts(groupAccounts, currency, trendPeriod)
                    }
                return TrendResult(points, ChartTrendAxis.None)
            }
            if (period is Period.Day) {
                val transactions = groupTransactions(groupAccounts, period)
                return intradayTrendResult(
                    transactions,
                    period.date,
                    balanceCalculator.forAccounts(groupAccounts, currency, period),
                )
            }
            val window =
                balanceTrendCalculator.buildAutoWindow(
                    anchor = period,
                    earliestDate = if (period is Period.All) earliestTransactionDate() else null,
                    today = java.time.LocalDate.now(ZoneId.systemDefault()),
                )
            val points =
                balanceTrendCalculator.buildAutoSeries(window, chartConfig.metric) { trendPeriod ->
                    balanceCalculator.forAccounts(groupAccounts, currency, trendPeriod)
                }
            return TrendResult(points, autoAxisFor(period))
        }

        // Wrap the intra-day cumulative-net series (SPEC 03 — slot 0..n, 2-hour buckets) into
        // TrendPoints in the snapshot's currency so the chart and labels share one shape. Survives a
        // 0- or 1-slot series (G13).
        private fun intradayTrendResult(
            transactions: List<Transaction>,
            day: java.time.LocalDate,
            snapshot: BalanceSnapshot,
        ): TrendResult {
            val currency = snapshot.net.currency
            val series = intradayTrendCalculator.buildIntradaySeries(transactions, day)
            val points =
                series.mapIndexed { index, amount ->
                    TrendPoint(
                        index = index,
                        period = Period.Day(day),
                        value = Money(amount.toMoneyScale(currency), currency),
                    )
                }
            return TrendResult(points, ChartTrendAxis.Hours)
        }

        private fun autoAxisFor(period: Period): ChartTrendAxis =
            when (period) {
                is Period.Year -> ChartTrendAxis.Months
                is Period.All, is Period.Interval, is Period.CustomRange -> ChartTrendAxis.RangeBuckets
                else -> ChartTrendAxis.Days
            }

        // Raw transactions backing the intra-day chart for the whole selection (G6). SpecificAccount
        // reads its own account; an all-accounts selection merges every active account's transactions
        // for the day. Amounts stay in each account's own currency — the intra-day net is a single
        // signed running total, so a multi-currency ConvertTo selection is intentionally summed
        // unconverted (the per-currency cards carry the currency-correct breakdown).
        private suspend fun selectionTransactions(
            selection: DashboardSelection,
            accounts: List<Account>,
            period: Period.Day,
        ): List<Transaction> =
            when (selection) {
                is DashboardSelection.SpecificAccount -> dashboardDataUseCase.findTransactions(selection.account.id, period)
                is DashboardSelection.AllAccounts ->
                    groupTransactions(accounts.filterNot { it.isArchived }, period)
            }

        private suspend fun groupTransactions(
            groupAccounts: List<Account>,
            period: Period.Day,
        ): List<Transaction> =
            groupAccounts.flatMap { account -> dashboardDataUseCase.findTransactions(account.id, period) }

        // Earliest transaction date for the Period.All auto window (O2). Derived from the existing
        // observeAll() flow — no new DAO — and null when the ledger is empty (buildAutoWindow then
        // yields an empty window, which the chart renders as empty, G13).
        private suspend fun earliestTransactionDate(): java.time.LocalDate? =
            dashboardDataUseCase
                .allTransactions()
                .minByOrNull { it.occurredAt }
                ?.occurredAt
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()

        fun onEvent(event: DashboardEvent) {
            when (event) {
                is DashboardEvent.PeriodChanged -> {
                    clearExpandedCategory()
                    _state.value = _state.value.copy(period = event.period)
                    restoredPersistedPeriod = true
                    clearImportFocus()
                    persistDashboardPeriod(event.period)
                    recomputeBalance()
                }
                DashboardEvent.PreviousPeriod -> {
                    val period = _state.value.period.previous()
                    commitSwipedPeriod(period, _state.value.previousPeriodPage)
                    restoredPersistedPeriod = true
                    clearImportFocus()
                    persistDashboardPeriod(period)
                    recomputeBalance()
                }
                DashboardEvent.NextPeriod -> {
                    val period = _state.value.period.next()
                    commitSwipedPeriod(period, _state.value.nextPeriodPage)
                    restoredPersistedPeriod = true
                    clearImportFocus()
                    persistDashboardPeriod(period)
                    recomputeBalance()
                }
                is DashboardEvent.AccountSelected -> {
                    val acc = _state.value.accounts.firstOrNull { it.id == event.accountId } ?: return
                    clearExpandedCategory()
                    _state.value =
                        _state.value.copy(
                            dashboardSelection = DashboardSelection.SpecificAccount(acc),
                            leftDrawerOpen = false,
                        )
                    clearImportFocus()
                    selectBudgetAlerts()
                    recomputeBalance()
                    viewModelScope.launch {
                        dashboardDataUseCase.updateSettings {
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
                DashboardEvent.RefreshRequested -> refreshNow()
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
                DashboardEvent.SupportClicked -> {
                    closeDrawers()
                    emit(DashboardAction.NavigateSupport)
                }
                DashboardEvent.BalanceCardClicked -> openOperationsSummary(categoryId = null)
                is DashboardEvent.SliceClicked -> toggleExpandedCategory(categoryId = event.categoryId)
                is DashboardEvent.RecordRowClicked -> {
                    _state.value = _state.value.copy(operationsSummary = null)
                    emit(DashboardAction.NavigateToTransactionDetail(event.transactionId))
                }
                DashboardEvent.OpenTransactionsListClicked -> openTransactionsList()
                DashboardEvent.OperationsSummaryDismissed ->
                    _state.value = _state.value.copy(operationsSummary = null)
                DashboardEvent.ConfettiAcknowledged ->
                    _state.value = _state.value.copy(showConfetti = false)
                DashboardEvent.ChartTapped -> openOperationsSummary(categoryId = null)
                DashboardEvent.ChartSettingsClicked -> {
                    closeDrawers()
                    _state.value = _state.value.copy(chartSettingsSheetOpen = true)
                }
                DashboardEvent.ChartSettingsDismissed ->
                    _state.value = _state.value.copy(chartSettingsSheetOpen = false)
                is DashboardEvent.ChartStyleChanged ->
                    updateChartSettings { it.copy(chartStyle = event.style.toId()) }
                is DashboardEvent.ChartPeriodTypeChanged ->
                    updateChartSettings(recomputeTrend = true) {
                        it.copy(chartPeriodType = event.periodType.toId())
                    }
                is DashboardEvent.ChartPointCountChanged ->
                    updateChartSettings(recomputeTrend = true) {
                        it.copy(chartPointCount = event.pointCount.coerceIn(CHART_POINT_COUNT_RANGE))
                    }
                is DashboardEvent.ChartMetricChanged ->
                    updateChartSettings(recomputeTrend = true) { it.copy(chartMetric = event.metric.toId()) }
                is DashboardEvent.ChartGridlinesToggled ->
                    updateChartSettings { it.copy(chartShowGridlines = event.enabled) }
                is DashboardEvent.ChartLabelsToggled ->
                    updateChartSettings { it.copy(chartShowLabels = event.enabled) }
                is DashboardEvent.ChartProjectionToggled ->
                    updateChartSettings(recomputeTrend = false) {
                        it.copy(chartShowProjection = event.enabled)
                    }
                is DashboardEvent.ChartColorRuleChanged ->
                    updateChartSettings { it.copy(chartColorRule = event.colorRule.toId()) }
                is DashboardEvent.ChartVisibilityChanged ->
                    updateChartSettings { it.copy(chartVisible = event.visible) }
                is DashboardEvent.ChartAutoModeChanged ->
                    updateChartSettings(recomputeTrend = true) {
                        it.copy(chartAutoMode = event.autoMode)
                    }
            }
        }

        // Persist a chart-config change (G15 — DataStore is the single source of truth, never the
        // transient state). The settings flow re-emission projects the new config back into state;
        // [recomputeTrend] re-derives the trend points immediately for period-type/point-count/metric
        // changes so the chart updates without waiting for the next data emission.
        private fun updateChartSettings(
            recomputeTrend: Boolean = false,
            transform: (AppSettings) -> AppSettings,
        ) {
            viewModelScope.launch {
                dashboardDataUseCase.updateSettings(transform)
                if (recomputeTrend) {
                    _state.value = _state.value.copy(chartConfig = dashboardDataUseCase.currentSettings().toChartConfig())
                    recomputeBalance()
                }
            }
        }

        // "Show each currency separately" (SPEC 08). No rate work is needed, but the aggregate
        // selection mode is still persisted so a cold start restores an all-accounts view.
        private fun applyAllAccountsSeparate() {
            pendingConvertTarget = null
            allAccountsRateOverrides = emptyMap()
            clearExpandedCategory()
            _state.value =
                _state.value.copy(
                    dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                    leftDrawerOpen = false,
                )
            clearImportFocus()
            selectBudgetAlerts()
            recomputeBalance()
            viewModelScope.launch {
                dashboardDataUseCase.updateSettings { it.copy(dashboardSelectionMode = DASHBOARD_SELECTION_ALL) }
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
            clearExpandedCategory()
            _state.value =
                _state.value.copy(
                    dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.ConvertTo(target)),
                    leftDrawerOpen = false,
                )
            clearImportFocus()
            selectBudgetAlerts()
            recomputeBalance()
            viewModelScope.launch {
                dashboardDataUseCase.updateSettings { it.copy(dashboardSelectionMode = DASHBOARD_SELECTION_ALL) }
            }
        }

        private fun closeDrawers() {
            _state.value = _state.value.copy(leftDrawerOpen = false, rightDrawerOpen = false)
        }

        private fun refreshNow() {
            if (_state.value.isRefreshing) return
            viewModelScope.launch {
                _state.value = _state.value.copy(isRefreshing = true)
                try {
                    journalSync.syncNow()
                    recomputeBalance()
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                } finally {
                    _state.value = _state.value.copy(isRefreshing = false)
                }
            }
        }

        // Open the operations-summary drawer (SPEC 03). [categoryId] null = "all operations" (income
        // + expense + transfers); a real category id filters to that one category (transfers
        // excluded, GetOperationsSummaryUseCase). The drawer lives in DashboardState so it survives
        // recomposition. "Прочее" (OTHER_CATEGORY_ID) and the separate multi-currency mode never open.
        private fun openOperationsSummary(categoryId: Long?) {
            if (categoryId == OTHER_CATEGORY_ID) return
            val selection = _state.value.dashboardSelection
            val period = _state.value.period
            val source: (suspend () -> List<SummaryRecord>) =
                when (selection) {
                    is DashboardSelection.SpecificAccount -> {
                        { getOperationsSummary(selection.account.id, period, categoryId) }
                    }
                    is DashboardSelection.AllAccounts ->
                        when (val mode = selection.foldMode) {
                            is AllAccountsFoldMode.ConvertTo -> {
                                {
                                    getOperationsSummary.forAccounts(
                                        _state.value.accounts,
                                        mode.target,
                                        period,
                                        categoryId,
                                    )
                                }
                            }
                            AllAccountsFoldMode.Separate -> return
                        }
                    null -> return
                }
            val categoryName = categoryId?.let { _state.value.categoryDisplays[it]?.name }
            _state.value =
                _state.value.copy(
                    operationsSummary =
                        OperationsSummaryState(
                            categoryFilter = categoryId,
                            categoryName = categoryName,
                            records = emptyList(),
                            loading = true,
                            canOpenTransactionsList = canOpenTransactionsList(selection),
                        ),
                )
            viewModelScope.launch {
                try {
                    val records = source()
                    val open = _state.value.operationsSummary
                    if (open != null && open.categoryFilter == categoryId) {
                        _state.value =
                            _state.value.copy(
                                operationsSummary =
                                    open.copy(
                                        records = records,
                                        loading = false,
                                    ),
                            )
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    t.reportToSentry()
                    val open = _state.value.operationsSummary
                    if (open != null && open.categoryFilter == categoryId) {
                        _state.value =
                            _state.value.copy(
                                operationsSummary = open.copy(loading = false),
                            )
                    }
                }
            }
        }

        private fun openTransactionsList() {
            val summary = _state.value.operationsSummary ?: return
            if (!summary.canOpenTransactionsList) return
            val range = PeriodArithmetic.toEpochMillisRange(_state.value.period)
            val action =
                when (val selection = _state.value.dashboardSelection) {
                    is DashboardSelection.SpecificAccount ->
                        DashboardAction.NavigateToTransactionsList(
                            accountId = selection.account.id,
                            currencyId = null,
                            categoryId = summary.categoryFilter,
                            fromMillis = range.first,
                            toMillis = range.last,
                        )
                    is DashboardSelection.AllAccounts ->
                        when (val mode = selection.foldMode) {
                            is AllAccountsFoldMode.ConvertTo ->
                                DashboardAction.NavigateToTransactionsList(
                                    accountId = null,
                                    currencyId = mode.target.id,
                                    categoryId = summary.categoryFilter,
                                    fromMillis = range.first,
                                    toMillis = range.last,
                                )
                            AllAccountsFoldMode.Separate -> return
                        }
                    null -> return
                }
            _state.value = _state.value.copy(operationsSummary = null)
            emit(action)
        }

        private fun canOpenTransactionsList(selection: DashboardSelection?): Boolean =
            when (selection) {
                is DashboardSelection.SpecificAccount -> true
                is DashboardSelection.AllAccounts ->
                    when (val mode = selection.foldMode) {
                        is AllAccountsFoldMode.ConvertTo ->
                            _state.value.accounts
                                .filterNot { it.isArchived }
                                .all { it.currencyId == mode.target.id }
                        AllAccountsFoldMode.Separate -> false
                    }
                null -> false
            }

        private fun toggleExpandedCategory(categoryId: Long) {
            if (categoryId == OTHER_CATEGORY_ID) return
            if (categoryId == _state.value.expandedCategoryId) {
                clearExpandedCategory()
                return
            }
            val state = _state.value
            val selection = state.dashboardSelection
            val period = state.period
            val source: suspend () -> List<Transaction> =
                when (selection) {
                    is DashboardSelection.SpecificAccount -> {
                        {
                            getCategoryRecords(selection.account.id, period, categoryId)
                                .firstOrNull { it.categoryId == categoryId }
                                ?.transactions
                                .orEmpty()
                        }
                    }
                    is DashboardSelection.AllAccounts ->
                        when (val mode = selection.foldMode) {
                            is AllAccountsFoldMode.ConvertTo -> {
                                val accountsByCurrency =
                                    state.accounts
                                        .filterNot { it.isArchived }
                                        .groupBy { it.currencyId }
                                val currenciesById = state.currencies.associateBy { it.id }
                                val groupedSource: suspend () -> List<Transaction> = {
                                    accountsByCurrency
                                        .flatMap { (currencyId, accounts) ->
                                            val sourceCurrency =
                                                currenciesById[currencyId]
                                                    ?: error("Currency $currencyId is unavailable for active account records")
                                            getCategoryRecords
                                                .forAccounts(accounts, sourceCurrency, period, categoryId)
                                                .firstOrNull { it.categoryId == categoryId }
                                                ?.transactions
                                                .orEmpty()
                                        }.sortedByDescending { it.occurredAt }
                                }
                                groupedSource
                            }
                            AllAccountsFoldMode.Separate -> return
                        }
                    null -> return
                }
            clearExpandedCategory()
            _state.value =
                _state.value.copy(
                    expandedCategoryId = categoryId,
                    expandedRecords = emptyList(),
                    expandedRecordsLoading = true,
                )
            categoryRecordsJob =
                viewModelScope.launch {
                    try {
                        val records = source()
                        if (
                            _state.value.expandedCategoryId == categoryId &&
                            _state.value.period == period &&
                            _state.value.dashboardSelection == selection
                        ) {
                            _state.value =
                                _state.value.copy(
                                    expandedRecords = records,
                                    expandedRecordsLoading = false,
                                )
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        t.reportToSentry()
                        if (
                            _state.value.expandedCategoryId == categoryId &&
                            _state.value.period == period &&
                            _state.value.dashboardSelection == selection
                        ) {
                            _state.value = _state.value.copy(expandedRecordsLoading = false)
                        }
                    }
                }
        }

        private fun clearExpandedCategory() {
            categoryRecordsJob?.cancel()
            categoryRecordsJob = null
            _state.value =
                _state.value.copy(
                    expandedCategoryId = null,
                    expandedRecords = emptyList(),
                    expandedRecordsLoading = false,
                )
        }

        private fun emit(action: DashboardAction) {
            viewModelScope.launch { _actions.emit(action) }
        }
    }

private data class BudgetAlertSelection(
    val accountId: Long,
)

private data class TrendResult(
    val points: List<TrendPoint>,
    val axis: ChartTrendAxis,
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
