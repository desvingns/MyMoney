package com.kshavrin.mymoney.feature.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.designsystem.chart.ChartColorRule
import com.kshavrin.mymoney.core.designsystem.confetti.MonefyConfetti
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.flow.CollectActions
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanel
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContainer
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContainerNegative
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContent
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContentNegative
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelOutline
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelShadow
import com.kshavrin.mymoney.core.ui.theme.dashboardBalanceValue
import com.kshavrin.mymoney.core.ui.theme.dashboardNeonBackground
import com.kshavrin.mymoney.feature.dashboard.components.AllAccountsConversionDialog
import com.kshavrin.mymoney.feature.dashboard.components.AllAccountsConversionDialogHost
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTilesList
import com.kshavrin.mymoney.feature.dashboard.components.CurrencyBalanceCardList
import com.kshavrin.mymoney.feature.dashboard.components.DashboardDrawerOverlay
import com.kshavrin.mymoney.feature.dashboard.components.DrawerSide
import com.kshavrin.mymoney.feature.dashboard.components.LeftDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.PeriodSwitcher
import com.kshavrin.mymoney.feature.dashboard.components.RightDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.TwoFabLayout
import com.kshavrin.mymoney.feature.dashboard.components.localizedLabel
import java.time.Year
import java.util.Locale

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
                    onSearchClick = { onEvent(DashboardEvent.SearchClicked) },
                    onTransferClick = { onEvent(DashboardEvent.TransferClicked) },
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
                )
            },
        ) { innerPadding ->
            val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onDragEnd = {
                                        if (totalDrag <= -swipeThresholdPx) {
                                            soundPlayer.play(SoundKey.SWIPE)
                                            hapticPlayer.fire(HapticKind.SOFT)
                                            onEvent(DashboardEvent.NextPeriod)
                                        } else if (totalDrag >= swipeThresholdPx) {
                                            soundPlayer.play(SoundKey.SWIPE)
                                            hapticPlayer.fire(HapticKind.SOFT)
                                            onEvent(DashboardEvent.PreviousPeriod)
                                        }
                                    },
                                ) { _, dragAmount -> totalDrag += dragAmount }
                            },
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val overBudgetText =
                                state.overBudgetAmount?.let { overage ->
                                    stringResource(R.string.dashboard_over_budget, formatMoney(overage, resourceLocale))
                                }

                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag(DASHBOARD_SCROLL_CONTENT_TAG)
                                        .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (state.isSeparateMode) {
                                    // "All accounts → show separately" (D6): the donut is dropped — several
                                    // currencies would overload it — and replaced by a stack of per-currency
                                    // balance cards. No category tiles here (no single-currency expense total).
                                    Spacer(modifier = Modifier.height(Spacing.l))
                                    CurrencyBalanceCardList(
                                        cards = state.currencyCards,
                                        modifier = Modifier.padding(horizontal = Spacing.l),
                                    )
                                } else {
                                    val snapshot = state.balanceSnapshot
                                    val periodLabel =
                                        state.period.localizedLabel(
                                            locale = resourceLocale,
                                            allLabel = stringResource(R.string.period_all),
                                            currentYear = Year.now().value,
                                        )
                                    DashboardTrendBalanceCard(
                                        label = stringResource(R.string.dashboard_balance_for_period, periodLabel),
                                        amount =
                                            formatBalanceAmount(
                                                state = state,
                                                unavailableText = stringResource(R.string.dashboard_balance_unavailable_amount),
                                                locale = resourceLocale,
                                            ),
                                        isNegative = state.periodNet.amount.signum() < 0,
                                        points = state.trendPoints.map { it.value.amount.toFloat() },
                                        onClick = { onEvent(DashboardEvent.BalanceCardClicked) },
                                        modifier = Modifier.padding(horizontal = Spacing.l),
                                    )

                                    if (snapshot != null) {
                                        Spacer(modifier = Modifier.height(Spacing.s))
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = Spacing.l),
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                                        ) {
                                            DashboardBalancePanel(
                                                label = stringResource(R.string.dashboard_currency_card_income),
                                                amount = formatMoney(snapshot.income, resourceLocale),
                                                isNegative = false,
                                                modifier = Modifier.weight(1f),
                                            )
                                            DashboardBalancePanel(
                                                label = stringResource(R.string.dashboard_currency_card_expense),
                                                amount = formatMoney(snapshot.expense, resourceLocale),
                                                isNegative = false,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
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
                                        expenseTiles = state.expenseTiles,
                                        onTileClick = { categoryId ->
                                            onEvent(DashboardEvent.SliceClicked(categoryId))
                                        },
                                        modifier =
                                            Modifier
                                                .padding(horizontal = Spacing.l)
                                                .then(
                                                    if (state.expenseTiles.isEmpty()) {
                                                        Modifier
                                                    } else {
                                                        Modifier.height(
                                                            Spacing.dashboardTileHeight * state.expenseTiles.size +
                                                                Spacing.s * (state.expenseTiles.size + 1),
                                                        )
                                                    },
                                                ),
                                    )
                                }
                            }

                            TwoFabLayout(
                                onMinusClick = {
                                    hapticPlayer.fire(HapticKind.MEDIUM)
                                    onEvent(DashboardEvent.MinusFabClicked)
                                },
                                onPlusClick = {
                                    hapticPlayer.fire(HapticKind.MEDIUM)
                                    onEvent(DashboardEvent.PlusFabClicked)
                                },
                            )
                        }

                        MonefyConfetti(
                            show = state.showConfetti,
                            onFinished = { onEvent(DashboardEvent.ConfettiAcknowledged) },
                        )
                    }
                }
            }
        }
        DashboardDrawerOverlay(
            open = state.leftDrawerOpen,
            side = DrawerSide.Left,
            onDismiss = { onEvent(DashboardEvent.DrawerDismissed) },
        ) {
            LeftDrawerContent(state = state, onEvent = onEvent)
        }
        DashboardDrawerOverlay(
            open = state.rightDrawerOpen,
            side = DrawerSide.Right,
            onDismiss = { onEvent(DashboardEvent.DrawerDismissed) },
        ) {
            RightDrawerContent(onEvent = onEvent)
        }
    }
}

@Composable
private fun DashboardTopBar(
    period: Period,
    drawerOpen: Boolean,
    onNavigationClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTransferClick: () -> Unit,
    onPreviousPeriodClick: () -> Unit,
    onNextPeriodClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.dashboardNeonBackground)
                .statusBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
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
            IconButton(onClick = onTransferClick) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(R.string.dashboard_transfer),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.dashboard_search),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.dashboard_overflow_menu),
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(Spacing.dashboardTopBarIconGlyphSize),
                )
            }
        }
        PeriodSwitcher(
            period = period,
            onPreviousClick = onPreviousPeriodClick,
            onNextClick = onNextPeriodClick,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(DASHBOARD_TOP_BAR_PERIOD_TAG),
        )
    }
}

@Composable
private fun DashboardTrendBalanceCard(
    label: String,
    amount: String,
    isNegative: Boolean,
    points: List<Float>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.dashboardBalancePanel
    val containerColor =
        if (isNegative) {
            MaterialTheme.colorScheme.dashboardBalancePanelContainerNegative
        } else {
            MaterialTheme.colorScheme.dashboardBalancePanelContainer
        }
    val contentColor =
        if (isNegative) {
            MaterialTheme.colorScheme.dashboardBalancePanelContentNegative
        } else {
            MaterialTheme.colorScheme.dashboardBalancePanelContent
        }
    Column(
        modifier =
            modifier
                .widthIn(max = Spacing.dashboardBalancePanelMaxWidth)
                .fillMaxWidth()
                .shadow(
                    elevation = Spacing.s,
                    shape = shape,
                    ambientColor = MaterialTheme.colorScheme.dashboardBalancePanelShadow,
                    spotColor = MaterialTheme.colorScheme.dashboardBalancePanelShadow,
                ).background(containerColor, shape)
                .border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = MaterialTheme.colorScheme.dashboardBalancePanelOutline,
                    shape = shape,
                ).clickable(onClick = onClick)
                .padding(horizontal = Spacing.m, vertical = Spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.dashboardBalanceLabel,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.dashboardBalanceValue,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.s))
        Box(modifier = Modifier.fillMaxWidth().testTag(DASHBOARD_TREND_CHART_TAG)) {
            BalanceTrendChart(
                points = points,
                colorRule = ChartColorRule.BySign,
            )
        }
    }
}

@Composable
private fun DashboardBalancePanel(
    label: String,
    amount: String,
    isNegative: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.dashboardBalancePanel
    val containerColor =
        if (isNegative) {
            MaterialTheme.colorScheme.dashboardBalancePanelContainerNegative
        } else {
            MaterialTheme.colorScheme.dashboardBalancePanelContainer
        }
    val contentColor =
        if (isNegative) {
            MaterialTheme.colorScheme.dashboardBalancePanelContentNegative
        } else {
            MaterialTheme.colorScheme.dashboardBalancePanelContent
        }
    Column(
        modifier =
            modifier
                .widthIn(max = Spacing.dashboardBalancePanelMaxWidth)
                .fillMaxWidth()
                .height(Spacing.dashboardBalancePanelHeight)
                .padding(horizontal = Spacing.none)
                .shadow(
                    elevation = Spacing.s,
                    shape = shape,
                    ambientColor = MaterialTheme.colorScheme.dashboardBalancePanelShadow,
                    spotColor = MaterialTheme.colorScheme.dashboardBalancePanelShadow,
                ).background(containerColor, shape)
                .border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = MaterialTheme.colorScheme.dashboardBalancePanelOutline,
                    shape = shape,
                ).then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.dashboardBalanceLabel,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.dashboardBalanceValue,
            color = contentColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatBalanceAmount(
    state: DashboardState,
    unavailableText: String,
    locale: Locale,
): String {
    if (state.balanceSnapshot == null) return unavailableText
    return MoneyFormatter.format(
        amount = state.periodNet.amount,
        currencySymbol = state.periodNet.currency.symbol,
        decimalDigits = state.periodNet.currency.decimalDigits,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )
}

private fun formatMoney(
    money: Money,
    locale: Locale,
): String =
    MoneyFormatter.format(
        amount = money.amount,
        currencySymbol = money.currency.symbol,
        decimalDigits = money.currency.decimalDigits,
        locale = locale,
    )

const val DASHBOARD_TOP_BAR_TITLE_TAG = "dashboard_top_bar_title"
const val DASHBOARD_TOP_BAR_SUBTITLE_TAG = "dashboard_top_bar_subtitle"
const val DASHBOARD_TOP_BAR_PERIOD_TAG = "dashboard_top_bar_period"
const val DASHBOARD_DONUT_TAG = "dashboard_donut"
const val DASHBOARD_TREND_CHART_TAG = "dashboard_trend_chart"
const val DASHBOARD_SCROLL_CONTENT_TAG = "dashboard_scroll_content"
