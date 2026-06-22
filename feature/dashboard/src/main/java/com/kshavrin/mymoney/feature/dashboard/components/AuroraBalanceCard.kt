package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.chartHiddenHint
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValue
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraCard
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraPill
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContent
import com.kshavrin.mymoney.core.ui.theme.dashboardExpensePill
import com.kshavrin.mymoney.core.ui.theme.dashboardIncomePill
import com.kshavrin.mymoney.feature.dashboard.ChartConfig
import com.kshavrin.mymoney.feature.dashboard.DASHBOARD_CHART_HIDDEN_HINT_TAG
import com.kshavrin.mymoney.feature.dashboard.DASHBOARD_TREND_CHART_TAG
import com.kshavrin.mymoney.feature.dashboard.R

// Centered "Aurora" hero card (SecAurora — 03_balance-variants.jsx). Replaces the standalone trend
// card + the two income/expense panels in the non-separate dashboard body. Top-to-bottom: uppercase
// balance label, big balance value, a centered row of income/expense pills, then the configurable
// neon-wave trend chart. The chart stays tappable (opens ChartSettingsSheet) and honours the same
// hidden-state hint as before, so the chart's tag contract is preserved.
@Composable
fun AuroraBalanceCard(
    label: String,
    balance: String,
    income: String,
    expense: String,
    points: List<Float>,
    chartConfig: ChartConfig,
    onChartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.dashboardAuroraAccent
    val shape = MaterialTheme.shapes.dashboardAuroraCard
    // SecAurora: radial-gradient(120% 90% at 50% 0%, accent@0.20, white@0.02 70%). Anchored at the
    // top-center; the gradient brush is sized to the card by drawBehind so it scales with width.
    val gradientTop = accent.copy(alpha = 0.20f)
    val gradientBottom = Color.White.copy(alpha = 0.02f)

    Column(
        modifier =
            modifier
                .widthIn(max = Spacing.dashboardBalancePanelMaxWidth)
                .fillMaxWidth()
                .testTag(DASHBOARD_AURORA_CARD_TAG)
                // Soft neon glow around the card (SecAurora boxShadow neonGlow(accent)).
                .shadow(
                    elevation = Spacing.s,
                    shape = shape,
                    ambientColor = accent,
                    spotColor = accent,
                ).clip(shape)
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colorStops =
                                    arrayOf(
                                        0f to gradientTop,
                                        0.70f to gradientBottom,
                                    ),
                                center = Offset(x = size.width / 2f, y = 0f),
                                radius = size.height * 1.4f,
                            ),
                    )
                }.border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = accent.copy(alpha = 0.28f),
                    shape = shape,
                ).padding(
                    start = Spacing.dashboardAuroraCardPaddingHorizontal,
                    end = Spacing.dashboardAuroraCardPaddingHorizontal,
                    top = Spacing.dashboardAuroraCardPaddingTop,
                    bottom = Spacing.dashboardAuroraCardPaddingBottom,
                ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.dashboardAuroraBalanceLabel,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(DASHBOARD_AURORA_LABEL_TAG),
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraValueBottomMargin))
        Text(
            text = balance,
            style = MaterialTheme.typography.dashboardAuroraBalanceValue,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(DASHBOARD_AURORA_BALANCE_TAG),
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMargin))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.dashboardAuroraPillGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuroraStatPill(
                text = "↑ $income",
                color = MaterialTheme.colorScheme.dashboardIncomePill,
                modifier = Modifier.testTag(DASHBOARD_AURORA_INCOME_PILL_TAG),
            )
            AuroraStatPill(
                text = "↓ $expense",
                color = MaterialTheme.colorScheme.dashboardExpensePill,
                modifier = Modifier.testTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMargin))
        if (chartConfig.visible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChartClick)
                        .testTag(DASHBOARD_TREND_CHART_TAG),
            ) {
                BalanceTrendChart(
                    points = points,
                    showGridlines = chartConfig.showGridlines,
                    showLabels = chartConfig.showLabels,
                    colorRule = chartConfig.colorRule,
                    style = chartConfig.style,
                    chartHeight = Spacing.dashboardAuroraChartHeight,
                )
            }
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.chartHiddenHintHeight)
                        .clickable(onClick = onChartClick)
                        .testTag(DASHBOARD_CHART_HIDDEN_HINT_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.chart_hidden_hint),
                    style = MaterialTheme.typography.chartHiddenHint,
                    color = MaterialTheme.colorScheme.dashboardBalancePanelContent,
                    maxLines = 1,
                )
            }
        }
    }
}

// Income/expense pill (SecAurora StatPill-as-pill): rounded 20dp, text + border in the pill colour,
// translucent fill (@0.12) and a 1dp inset ring (@0.3).
@Composable
private fun AuroraStatPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.dashboardAuroraPill
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(color.copy(alpha = 0.12f), shape)
                .border(
                    width = Spacing.dashboardBalancePanelBorderWidth,
                    color = color.copy(alpha = 0.3f),
                    shape = shape,
                ).padding(
                    horizontal = Spacing.m,
                    vertical = Spacing.dashboardAuroraPillPaddingVertical,
                ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.dashboardAuroraBalanceLabel,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

const val DASHBOARD_AURORA_CARD_TAG = "dashboard_aurora_card"
const val DASHBOARD_AURORA_LABEL_TAG = "dashboard_aurora_label"
const val DASHBOARD_AURORA_BALANCE_TAG = "dashboard_aurora_balance"
const val DASHBOARD_AURORA_INCOME_PILL_TAG = "dashboard_aurora_income_pill"
const val DASHBOARD_AURORA_EXPENSE_PILL_TAG = "dashboard_aurora_expense_pill"
