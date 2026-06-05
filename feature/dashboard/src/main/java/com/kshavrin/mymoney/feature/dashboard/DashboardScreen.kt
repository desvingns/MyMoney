package com.kshavrin.mymoney.feature.dashboard

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.confetti.MonefyConfetti
import com.kshavrin.mymoney.core.designsystem.donut.DonutStyle
import com.kshavrin.mymoney.core.designsystem.donut.MonefyDonutChart
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.feedback.LocalHapticPlayer
import com.kshavrin.mymoney.core.ui.feedback.LocalSoundPlayer
import com.kshavrin.mymoney.core.ui.haptic.HapticKind
import com.kshavrin.mymoney.core.ui.sound.SoundKey
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanel
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContainer
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContent
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelOutline
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelShadow
import com.kshavrin.mymoney.core.ui.theme.dashboardBalanceValue
import com.kshavrin.mymoney.core.ui.theme.dashboardCalloutLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardCalloutPercentage
import com.kshavrin.mymoney.core.ui.theme.dashboardDonutCenterDivider
import com.kshavrin.mymoney.core.ui.theme.dashboardDonutCenterTotal
import com.kshavrin.mymoney.core.ui.theme.dashboardDonutLeaderLine
import com.kshavrin.mymoney.core.ui.theme.dashboardHeroGradientEnd
import com.kshavrin.mymoney.core.ui.theme.dashboardHeroGradientStart
import com.kshavrin.mymoney.core.ui.theme.dashboardTopBarSubtitle
import com.kshavrin.mymoney.core.ui.theme.dashboardTopBarTitle
import com.kshavrin.mymoney.feature.dashboard.components.DashboardDrawerOverlay
import com.kshavrin.mymoney.feature.dashboard.components.DrawerSide
import com.kshavrin.mymoney.feature.dashboard.components.LeftDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.PeriodLabel
import com.kshavrin.mymoney.feature.dashboard.components.RightDrawerContent
import com.kshavrin.mymoney.feature.dashboard.components.TwoFabLayout
import java.math.BigDecimal
import java.util.Locale

@Composable
fun DashboardRoute(
    onAction: (DashboardAction) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action -> onAction(action) }
    }

    DashboardContent(state = state, onEvent = viewModel::onEvent)
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

    LaunchedEffect(state.showConfetti) {
        if (state.showConfetti) {
            soundPlayer.play(SoundKey.MILESTONE)
            hapticPlayer.fire(HapticKind.SUCCESS_SHIMMER)
        }
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                subtitle = state.currentCurrency?.name,
                onMenuClick = {
                    hapticPlayer.fire(HapticKind.MEDIUM)
                    onEvent(DashboardEvent.LeftDrawerToggled)
                },
                onSearchClick = { onEvent(DashboardEvent.SearchClicked) },
                onTransferClick = { onEvent(DashboardEvent.TransferClicked) },
                onMoreClick = {
                    hapticPlayer.fire(HapticKind.MEDIUM)
                    onEvent(DashboardEvent.RightDrawerToggled)
                },
            )
        },
    ) { innerPadding ->
        val swipeThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
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
                        val balanceAmount = formatBalanceAmount(
                            state = state,
                            unavailableText = stringResource(R.string.dashboard_balance_unavailable_amount),
                            locale = resourceLocale,
                        )
                        val overBudgetText = state.overBudgetAmount?.let { overage ->
                            stringResource(R.string.dashboard_over_budget, formatMoney(overage, resourceLocale))
                        }

                        PeriodLabel(
                            period = state.period,
                            onPreviousClick = {
                                hapticPlayer.fire(HapticKind.SOFT)
                                onEvent(DashboardEvent.PreviousPeriod)
                            },
                            onNextClick = {
                                hapticPlayer.fire(HapticKind.SOFT)
                                onEvent(DashboardEvent.NextPeriod)
                            },
                            modifier = Modifier.height(Spacing.dashboardPeriodRowHeight),
                        )

                        DashboardBalancePanel(
                            label = stringResource(R.string.dashboard_balance),
                            amount = balanceAmount,
                            onClick = { onEvent(DashboardEvent.BalanceCardClicked) },
                            modifier = Modifier.testTag("dashboard_balance_bar"),
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .testTag(DASHBOARD_DONUT_TAG),
                        ) {
                            MonefyDonutChart(
                                income = state.balanceSnapshot?.income?.amount ?: BigDecimal.ZERO,
                                expense = state.balanceSnapshot?.expense?.amount ?: BigDecimal.ZERO,
                                slices = state.slices,
                                modifier = Modifier.fillMaxSize(),
                                currencySymbol = state.currentCurrency?.symbol ?: "",
                                decimalDigits = state.currentCurrency?.decimalDigits ?: 2,
                                emptyStateIcons = state.expenseCategoryPlaceholders,
                                outerRadiusFraction = 0.62f,
                                ringThicknessFraction = 0.36f,
                                sliceGapDegrees = 7f,
                                explodedOffset = Spacing.dashboardDonutExplodedOffset,
                                centerDecimalDigits = state.currentCurrency?.decimalDigits ?: 2,
                                centerTextStyle = MaterialTheme.typography.dashboardDonutCenterTotal,
                                centerDividerColor = MaterialTheme.colorScheme.dashboardDonutCenterDivider,
                                centerDividerWidth = Spacing.dashboardDonutCenterDividerWidth,
                                centerDividerThickness = Spacing.dashboardDonutCenterDividerThickness,
                                calloutIconSize = Spacing.dashboardDonutCalloutIconSize,
                                calloutLabelStyle = MaterialTheme.typography.dashboardCalloutLabel,
                                calloutPercentageStyle = MaterialTheme.typography.dashboardCalloutPercentage,
                                calloutLabelColor = MaterialTheme.colorScheme.dashboardCalloutLabel,
                                leaderLineColor = MaterialTheme.colorScheme.dashboardDonutLeaderLine,
                                leaderLineThickness = Spacing.dashboardDonutLeaderLineThickness,
                                labelMinFraction = 0f,
                                showCategoryLabels = true,
                                style = DonutStyle.Extrude,
                                onSliceClick = { slice ->
                                    onEvent(DashboardEvent.SliceClicked(slice.categoryId))
                                },
                            )
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
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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
                            expenseLabel = stringResource(R.string.fab_expense_label),
                            incomeLabel = stringResource(R.string.fab_income_label),
                        )
                    }

                    MonefyConfetti(
                        show = state.showConfetti,
                        onFinished = { onEvent(DashboardEvent.ConfettiAcknowledged) },
                    )
                }
            }
            DashboardDrawerOverlay(
                open = state.leftDrawerOpen,
                side = DrawerSide.Left,
                onDismiss = {},
            ) {
                LeftDrawerContent(state = state, onEvent = onEvent)
            }
            DashboardDrawerOverlay(
                open = state.rightDrawerOpen,
                side = DrawerSide.Right,
                onDismiss = {},
            ) {
                RightDrawerContent(onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun DashboardTopBar(
    subtitle: String?,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTransferClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.dashboardHeroGradientStart,
                        MaterialTheme.colorScheme.dashboardHeroGradientEnd,
                    ),
                ),
            )
            .statusBarsPadding()
            .height(Spacing.dashboardTopBarHeight)
            .padding(horizontal = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = stringResource(R.string.dashboard_menu),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(Spacing.xxl),
            )
        }
        DashboardTopBarTitle(
            title = stringResource(R.string.dashboard_title),
            subtitle = subtitle,
            modifier = Modifier
                .weight(1f)
                .padding(start = Spacing.s),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.dashboard_search),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Spacing.xxl),
                )
            }
            IconButton(onClick = onTransferClick) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(R.string.dashboard_transfer),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Spacing.xxl),
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.dashboard_overflow_menu),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(Spacing.xxl),
                )
            }
        }
    }
}

@Composable
private fun DashboardTopBarTitle(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.dashboardTopBarTitle,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.testTag(DASHBOARD_TOP_BAR_TITLE_TAG),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.dashboardTopBarSubtitle,
                maxLines = 1,
                modifier = Modifier.testTag(DASHBOARD_TOP_BAR_SUBTITLE_TAG),
            )
        }
    }
}

@Composable
private fun DashboardBalancePanel(
    label: String,
    amount: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.dashboardBalancePanel
    Column(
        modifier = modifier
            .widthIn(max = Spacing.dashboardBalancePanelMaxWidth)
            .fillMaxWidth()
            .height(Spacing.dashboardBalancePanelHeight)
            .padding(horizontal = Spacing.none)
            .shadow(
                elevation = Spacing.s,
                shape = shape,
                ambientColor = MaterialTheme.colorScheme.dashboardBalancePanelShadow,
                spotColor = MaterialTheme.colorScheme.dashboardBalancePanelShadow,
            )
            .background(MaterialTheme.colorScheme.dashboardBalancePanelContainer, shape)
            .border(
                width = Spacing.dashboardBalancePanelBorderWidth,
                color = MaterialTheme.colorScheme.dashboardBalancePanelOutline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.m, vertical = Spacing.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.dashboardBalanceLabel,
            color = MaterialTheme.colorScheme.dashboardBalancePanelContent,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = amount,
            style = MaterialTheme.typography.dashboardBalanceValue,
            color = MaterialTheme.colorScheme.dashboardBalancePanelContent,
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
    val net = state.balanceSnapshot?.net ?: return unavailableText
    return MoneyFormatter.format(
        amount = net.amount,
        currencySymbol = net.currency.symbol,
        decimalDigits = net.currency.decimalDigits,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )
}

private fun formatMoney(money: Money, locale: Locale): String = MoneyFormatter.format(
    amount = money.amount,
    currencySymbol = money.currency.symbol,
    decimalDigits = money.currency.decimalDigits,
    locale = locale,
)

const val DASHBOARD_TOP_BAR_TITLE_TAG = "dashboard_top_bar_title"
const val DASHBOARD_TOP_BAR_SUBTITLE_TAG = "dashboard_top_bar_subtitle"
const val DASHBOARD_DONUT_TAG = "dashboard_donut"
