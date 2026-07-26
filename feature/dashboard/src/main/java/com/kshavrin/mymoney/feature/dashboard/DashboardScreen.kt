package com.kshavrin.mymoney.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.confetti.Confetti
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.flow.CollectActions
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardNeonBackground
import com.kshavrin.mymoney.feature.dashboard.components.AllAccountsConversionDialog
import com.kshavrin.mymoney.feature.dashboard.components.AllAccountsConversionDialogHost
import com.kshavrin.mymoney.feature.dashboard.components.AuroraBalanceCard
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTilesList
import com.kshavrin.mymoney.feature.dashboard.components.ChartSettingsSheet
import com.kshavrin.mymoney.feature.dashboard.components.CurrencyBalanceCardList
import com.kshavrin.mymoney.feature.dashboard.components.DashboardDrawerOverlay
import com.kshavrin.mymoney.feature.dashboard.components.DrawerSide
import com.kshavrin.mymoney.feature.dashboard.components.LeftDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.OperationsSummarySheet
import com.kshavrin.mymoney.feature.dashboard.components.PeriodSwitcher
import com.kshavrin.mymoney.feature.dashboard.components.RightDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.ThreeFabLayout
import com.kshavrin.mymoney.feature.dashboard.components.localDateToMaterialPickerUtcMillis
import com.kshavrin.mymoney.feature.dashboard.components.materialPickerUtcMillisToLocalDate
import com.kshavrin.mymoney.feature.dashboard.components.trendPointLabels
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardRoute(
    onAction: (DashboardAction) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var conversionDialog by remember { mutableStateOf<AllAccountsConversionDialog?>(null) }

    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            is DashboardAction.ShowAllAccountsModeDialog -> conversionDialog = AllAccountsConversionDialog.Mode
            is DashboardAction.ShowTargetCurrencyPicker ->
                conversionDialog = AllAccountsConversionDialog.TargetPicker(action.currencies)
            is DashboardAction.ShowAllAccountsRateConfirm ->
                conversionDialog = AllAccountsConversionDialog.RateConfirm(action.rows, action.sourceCurrencyIds)
            else -> onAction(action)
        }
    }

    DashboardContent(state = state, onEvent = viewModel::onEvent)

    AllAccountsConversionDialogHost(
        dialog = conversionDialog,
        onDismiss = {
            conversionDialog = null
            viewModel.onEvent(DashboardEvent.AllAccountsConversionDismissed)
        },
        onConvertChosen = {
            conversionDialog = null
            viewModel.onEvent(DashboardEvent.AllAccountsConvertChosen)
        },
        onSeparateChosen = {
            conversionDialog = null
            viewModel.onEvent(DashboardEvent.AllAccountsSeparateChosen)
        },
        onTargetChosen = { currencyId ->
            conversionDialog = null
            viewModel.onEvent(DashboardEvent.AllAccountsTargetCurrencyChosen(currencyId))
        },
        onRatesConfirmed = { overrides ->
            conversionDialog = null
            viewModel.onEvent(DashboardEvent.AllAccountsRatesConfirmed(overrides))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardState,
    onEvent: (DashboardEvent) -> Unit,
) {
    val soundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current
    val configuration = LocalConfiguration.current
    val resourceLocale = configuration.locales[0]
    val drawerOpen = state.leftDrawerOpen || state.rightDrawerOpen
    var showPickDateRangePicker by remember { mutableStateOf(false) }

    // Monefy-style swipe pager (SPEC 02): the body becomes a 3-up [prev | current | next] pager.
    // Page 1 is always the committed period; settling onto a neighbour commits the period change and
    // re-centres without animation (infinite paging). Period.All has no neighbours, so it collapses
    // to a single page (paging disabled, G13).
    val pagingEnabled = state.period !is Period.All
    // Keyed on pagingEnabled so toggling Period.All on/off rebuilds the pager centred on the right
    // page (single page 0 for All, centre page otherwise).
    val pagerState =
        key(pagingEnabled) {
            rememberPagerState(
                initialPage = if (pagingEnabled) DASHBOARD_PAGER_CENTER_PAGE else 0,
                pageCount = { if (pagingEnabled) DASHBOARD_PAGER_PAGE_COUNT else 1 },
            )
        }

    BackHandler(enabled = drawerOpen) {
        onEvent(DashboardEvent.DrawerDismissed)
    }

    LaunchedEffect(state.showConfetti) {
        if (state.showConfetti) {
            soundPlayer.play(SoundKey.MILESTONE)
            hapticPlayer.fire(HapticKind.SUCCESS_SHIMMER)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.dashboardNeonBackground,
            topBar = {
                DashboardTopBar(
                    period = state.period,
                    pagerState = pagerState,
                    pagingEnabled = pagingEnabled,
                    drawerOpen = drawerOpen,
                    onNavigationClick = {
                        hapticPlayer.fire(HapticKind.MEDIUM)
                        onEvent(
                            if (drawerOpen) {
                                DashboardEvent.DrawerDismissed
                            } else {
                                DashboardEvent.LeftDrawerToggled
                            },
                        )
                    },
                    onPreviousPeriodClick = {
                        hapticPlayer.fire(HapticKind.SOFT)
                        onEvent(DashboardEvent.PreviousPeriod)
                    },
                    onNextPeriodClick = {
                        hapticPlayer.fire(HapticKind.SOFT)
                        onEvent(DashboardEvent.NextPeriod)
                    },
                    onMoreClick = {
                        hapticPlayer.fire(HapticKind.MEDIUM)
                        onEvent(DashboardEvent.RightDrawerToggled)
                    },
                    onEvent = onEvent,
                )
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = { onEvent(DashboardEvent.RefreshRequested) },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                    ) {
                        DashboardBodyPager(
                            state = state,
                            pagerState = pagerState,
                            pagingEnabled = pagingEnabled,
                            onEvent = onEvent,
                            locale = resourceLocale,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    ThreeFabLayout(
                        onMinusClick = {
                            hapticPlayer.fire(HapticKind.MEDIUM)
                            onEvent(DashboardEvent.MinusFabClicked)
                        },
                        onTransferClick = {
                            hapticPlayer.fire(HapticKind.MEDIUM)
                            onEvent(DashboardEvent.TransferClicked)
                        },
                        onPlusClick = {
                            hapticPlayer.fire(HapticKind.MEDIUM)
                            onEvent(DashboardEvent.PlusFabClicked)
                        },
                    )
                }

                Confetti(
                    show = state.showConfetti,
                    onFinished = { onEvent(DashboardEvent.ConfettiAcknowledged) },
                )
            }
        }
        DashboardDrawerOverlay(
            open = state.leftDrawerOpen,
            side = DrawerSide.Left,
            onDismiss = { onEvent(DashboardEvent.DrawerDismissed) },
        ) {
            LeftDrawerContent(
                state = state,
                onEvent = onEvent,
                onPickDateRangeClick = {
                    showPickDateRangePicker = true
                    onEvent(DashboardEvent.DrawerDismissed)
                },
            )
        }
        DashboardDrawerOverlay(
            open = state.rightDrawerOpen,
            side = DrawerSide.Right,
            onDismiss = { onEvent(DashboardEvent.DrawerDismissed) },
        ) {
            RightDrawerContent(onEvent = onEvent)
        }
    }

    if (showPickDateRangePicker) {
        val selectedRange = state.period as? Period.CustomRange
        val pickerState =
            rememberDateRangePickerState(
                initialSelectedStartDateMillis =
                    selectedRange?.start?.let(::localDateToMaterialPickerUtcMillis),
                initialSelectedEndDateMillis =
                    selectedRange?.end?.let(::localDateToMaterialPickerUtcMillis),
            )
        val startMillis = pickerState.selectedStartDateMillis
        val endMillis = pickerState.selectedEndDateMillis
        val validRange = startMillis != null && endMillis != null && startMillis <= endMillis

        DatePickerDialog(
            onDismissRequest = { showPickDateRangePicker = false },
            confirmButton = {
                TextButton(
                    enabled = validRange,
                    onClick = {
                        if (startMillis != null && endMillis != null && startMillis <= endMillis) {
                            soundPlayer.play(SoundKey.SWIPE)
                            hapticPlayer.fire(HapticKind.SOFT)
                            onEvent(
                                DashboardEvent.PeriodChanged(
                                    Period.CustomRange(
                                        start = materialPickerUtcMillisToLocalDate(startMillis),
                                        end = materialPickerUtcMillisToLocalDate(endMillis),
                                    ),
                                ),
                            )
                            showPickDateRangePicker = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.period_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPickDateRangePicker = false }) {
                    Text(stringResource(R.string.period_cancel))
                }
            },
        ) {
            DateRangePicker(state = pickerState)
        }
    }

    if (state.chartSettingsSheetOpen) {
        ChartSettingsSheet(
            config = state.chartConfig,
            onEvent = onEvent,
            onDismiss = { onEvent(DashboardEvent.ChartSettingsDismissed) },
        )
    }

    state.operationsSummary?.let { summary ->
        OperationsSummarySheet(
            records = summary.records,
            loading = summary.loading,
            title =
                summary.categoryName
                    ?: stringResource(R.string.operations_summary_title_all_operations),
            onRowClick = { transactionId -> onEvent(DashboardEvent.RecordRowClicked(transactionId)) },
            onOpenTransactionsList = { onEvent(DashboardEvent.OpenTransactionsListClicked) },
            onDismiss = { onEvent(DashboardEvent.OperationsSummaryDismissed) },
            canOpenTransactionsList = summary.canOpenTransactionsList,
            currencies = state.currencies,
            categoryDisplays = state.categoryDisplays,
        )
    }
}

@Composable
private fun DashboardBodyPager(
    state: DashboardState,
    pagerState: PagerState,
    pagingEnabled: Boolean,
    onEvent: (DashboardEvent) -> Unit,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val soundPlayer = LocalSoundPlayer.current
    val hapticPlayer = LocalHapticPlayer.current

    // When the pager settles onto a neighbour page, commit the period change and snap back to the
    // centre page without animation so the next swipe again has a page on either side (infinite
    // paging, D4). Settling onto the centre page (a snap-back below the threshold) changes nothing.
    if (pagingEnabled) {
        LaunchedEffect(pagerState, state.period) {
            snapshotFlow { pagerState.isScrollInProgress to pagerState.settledPage }
                .collect { (scrolling, settledPage) ->
                    if (!scrolling && settledPage != DASHBOARD_PAGER_CENTER_PAGE) {
                        soundPlayer.play(SoundKey.SWIPE)
                        hapticPlayer.fire(HapticKind.SOFT)
                        onEvent(
                            if (settledPage > DASHBOARD_PAGER_CENTER_PAGE) {
                                DashboardEvent.NextPeriod
                            } else {
                                DashboardEvent.PreviousPeriod
                            },
                        )
                        pagerState.scrollToPage(DASHBOARD_PAGER_CENTER_PAGE)
                    }
                }
        }
    }

    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = if (pagingEnabled) 1 else 0,
        modifier = modifier,
    ) { page ->
        val pageState =
            when {
                !pagingEnabled || page == DASHBOARD_PAGER_CENTER_PAGE -> state.toCurrentPageState()
                page < DASHBOARD_PAGER_CENTER_PAGE -> state.previousPeriodPage
                else -> state.nextPeriodPage
            }
        DashboardPage(
            pageState = pageState,
            interactive = !pagingEnabled || page == DASHBOARD_PAGER_CENTER_PAGE,
            chartConfig = state.chartConfig,
            overBudgetAmount = state.overBudgetAmount,
            onEvent = onEvent,
            locale = locale,
        )
    }
}

@Composable
private fun DashboardPage(
    pageState: PeriodPageState?,
    interactive: Boolean,
    chartConfig: ChartConfig,
    overBudgetAmount: Money?,
    onEvent: (DashboardEvent) -> Unit,
    locale: Locale,
) {
    // A neighbour whose background precompute has not landed yet renders nothing (a blank page),
    // which the pager peeks during the drag and replaces once the real data arrives (H2).
    if (pageState == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(DASHBOARD_SCROLL_CONTENT_TAG)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val chartMetricLabel = stringResource(chartMetricLabelRes(chartConfig.metric))
        if (pageState.isSeparateMode) {
            // "All accounts → show separately" (D6): the donut is dropped — several currencies would
            // overload it — and replaced by a stack of per-currency balance cards. No category tiles
            // here (no single-currency expense total).
            Spacer(modifier = Modifier.height(Spacing.l))
            CurrencyBalanceCardList(
                cards = pageState.currencyCards,
                chartConfig = chartConfig,
                metricLabel = chartMetricLabel,
            )
        } else {
            val snapshot = pageState.balanceSnapshot
            Spacer(modifier = Modifier.height(Spacing.l))
            AuroraBalanceCard(
                balance =
                    if (snapshot == null) {
                        stringResource(R.string.dashboard_balance_unavailable_amount)
                    } else {
                        formatMoney(pageState.periodNet, locale)
                    },
                income =
                    snapshot?.let { formatMoneyPlain(it.income, locale) }
                        ?: stringResource(R.string.dashboard_balance_unavailable_amount),
                expense =
                    snapshot?.let { formatMoneyPlain(it.expense, locale) }
                        ?: stringResource(R.string.dashboard_balance_unavailable_amount),
                points = pageState.trendPoints.map { it.value.amount.toFloat() },
                labels = trendPointLabels(pageState.trendPoints, pageState.trendAxis, locale),
                chartConfig = chartConfig,
                metricLabel = chartMetricLabel,
                onChartClick = { if (interactive) onEvent(DashboardEvent.ChartTapped) },
                netPositive = pageState.periodNet.amount.signum() >= 0,
            )

            val overBudgetText =
                overBudgetAmount?.takeIf { interactive }?.let { overage ->
                    stringResource(R.string.dashboard_over_budget, formatMoney(overage, locale))
                }
            if (overBudgetText != null) {
                Spacer(modifier = Modifier.height(Spacing.s))
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Text(
                        text = overBudgetText,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s),
                    )
                }
            }

            CategoryTilesList(
                expenseTiles = pageState.expenseTiles,
                onTileClick = { categoryId ->
                    if (interactive) onEvent(DashboardEvent.SliceClicked(categoryId))
                },
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
        }
    }
}

// Project the committed full state onto the lightweight page shape so the centre page and the
// neighbour pages render through the same DashboardPage composable.
private fun DashboardState.toCurrentPageState(): PeriodPageState =
    PeriodPageState(
        period = period,
        balanceSnapshot = balanceSnapshot,
        currencyCards = currencyCards,
        periodNet = periodNet,
        ringFraction = ringFraction,
        ringIsExpense = ringIsExpense,
        trendPoints = trendPoints,
        trendAxis = trendAxis,
        slices = slices,
        expenseTiles = expenseTiles,
        isSeparateMode = isSeparateMode,
        isLoading = isLoading,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    period: Period,
    pagerState: PagerState,
    pagingEnabled: Boolean,
    drawerOpen: Boolean,
    onNavigationClick: () -> Unit,
    onPreviousPeriodClick: () -> Unit,
    onNextPeriodClick: () -> Unit,
    onMoreClick: () -> Unit,
    onEvent: (DashboardEvent) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    // Drag offset of the body pager relative to the centred page, in [-1, 1]: 0 at rest, +1 as the
    // user drags toward the next period, -1 toward the previous. Feeds the 3-up label so it tracks
    // the finger. Stays 0 when paging is disabled (Period.All).
    val pageDragOffset =
        if (pagingEnabled) {
            (pagerState.currentPage + pagerState.currentPageOffsetFraction) - DASHBOARD_PAGER_CENTER_PAGE
        } else {
            0f
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.dashboardNeonBackground)
                .statusBarsPadding()
                .heightIn(min = Spacing.dashboardTopBarMinHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNavigationClick) {
            Icon(
                imageVector = if (drawerOpen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Menu,
                contentDescription =
                    stringResource(
                        if (drawerOpen) R.string.dashboard_back else R.string.dashboard_menu,
                    ),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
            )
        }
        PeriodSwitcher(
            period = period,
            dragOffset = pageDragOffset,
            onPreviousClick = onPreviousPeriodClick,
            onNextClick = onNextPeriodClick,
            onPeriodLabelClick = { showDatePicker = true },
            modifier =
                Modifier
                    .weight(1f)
                    .testTag(DASHBOARD_TOP_BAR_PERIOD_TAG),
        )
        IconButton(onClick = onMoreClick) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.dashboard_overflow_menu),
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
            )
        }
    }

    if (showDatePicker) {
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = localDateToMaterialPickerUtcMillis(LocalDate.now()),
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { selectedMillis ->
                        onEvent(DashboardEvent.PeriodChanged(Period.Day(materialPickerUtcMillisToLocalDate(selectedMillis))))
                    }
                    showDatePicker = false
                }) {
                    Text(stringResource(R.string.period_apply))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.period_cancel))
                }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private fun formatMoney(
    money: Money,
    locale: Locale,
): String =
    MoneyFormatter.format(
        amount = truncateDashboardAmount(money.amount),
        currencySymbol = money.currency.symbol,
        decimalDigits = 0,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )

// Grouped integer with no currency symbol — SecAurora's income/expense pills (ccFmtPlain).
private fun formatMoneyPlain(
    money: Money,
    locale: Locale,
): String =
    MoneyFormatter
        .format(
            amount = truncateDashboardAmount(money.amount),
            currencySymbol = "",
            decimalDigits = 0,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        ).trim()

private fun truncateDashboardAmount(amount: BigDecimal): BigDecimal = amount.setScale(0, RoundingMode.DOWN)

const val DASHBOARD_TOP_BAR_TITLE_TAG = "dashboard_top_bar_title"
const val DASHBOARD_TOP_BAR_SUBTITLE_TAG = "dashboard_top_bar_subtitle"
const val DASHBOARD_TOP_BAR_PERIOD_TAG = "dashboard_top_bar_period"
const val DASHBOARD_DONUT_TAG = "dashboard_donut"
const val DASHBOARD_TREND_CHART_TAG = "dashboard_trend_chart"
const val DASHBOARD_CHART_HIDDEN_HINT_TAG = "dashboard_chart_hidden_hint"
const val DASHBOARD_SCROLL_CONTENT_TAG = "dashboard_scroll_content"

// The body pager carries the previous / current / next period; the committed period always sits on
// the centre page and the pager re-centres there after every settle (infinite paging, D4).
private const val DASHBOARD_PAGER_PAGE_COUNT = 3
private const val DASHBOARD_PAGER_CENTER_PAGE = 1
