package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.chartHiddenHint
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccentForSign
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValueCompact
import com.kshavrin.mymoney.core.ui.theme.dashboardBalancePanelContent
import com.kshavrin.mymoney.feature.dashboard.ChartConfig
import com.kshavrin.mymoney.feature.dashboard.DASHBOARD_CHART_HIDDEN_HINT_TAG
import com.kshavrin.mymoney.feature.dashboard.DASHBOARD_TREND_CHART_TAG
import com.kshavrin.mymoney.feature.dashboard.R

@Composable
fun AuroraBalanceCard(
    balance: String,
    income: String,
    expense: String,
    points: List<Float>,
    chartConfig: ChartConfig,
    onChartClick: () -> Unit,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    netPositive: Boolean = true,
) {
    AuroraCardSurface(
        cardTestTag = DASHBOARD_AURORA_CARD_TAG,
        modifier = modifier,
        accent = MaterialTheme.colorScheme.dashboardAuroraAccentForSign(netPositive),
    ) {
        Text(
            text = balance,
            style = MaterialTheme.typography.dashboardAuroraBalanceValueCompact,
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(DASHBOARD_AURORA_BALANCE_TAG),
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMarginCompact))
        IncomeExpensePills(
            income = income,
            expense = expense,
            incomePillTestTag = DASHBOARD_AURORA_INCOME_PILL_TAG,
            expensePillTestTag = DASHBOARD_AURORA_EXPENSE_PILL_TAG,
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMarginCompact))
        if (chartConfig.visible) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fullBleedHorizontal(Spacing.dashboardAuroraCardPaddingHorizontal)
                        .clickable(onClick = onChartClick)
                        .testTag(DASHBOARD_TREND_CHART_TAG),
            ) {
                BalanceTrendChart(
                    points = points,
                    labels = labels,
                    showGridlines = chartConfig.showGridlines,
                    showLabels = chartConfig.showLabels,
                    colorRule = chartConfig.colorRule,
                    style = chartConfig.style,
                    chartHeight = Spacing.dashboardAuroraChartHeightCompact,
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

// Cancels the card's horizontal inner padding for this child only, so the wave chart spans the full
// (clipped) card width while the balance value and pills keep their padding. Compose's
// Modifier.padding rejects negative dp, so we widen the measured constraints by 2×[inset] and shift
// the placement left by [inset]. The parent Column already clips to the 24dp shape, so the bled
// chart stays within the rounded corners.
private fun Modifier.fullBleedHorizontal(inset: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = inset.roundToPx() * 2
        val widened =
            constraints.copy(
                minWidth = (constraints.minWidth + extra).coerceAtLeast(0),
                maxWidth =
                    if (constraints.hasBoundedWidth) {
                        constraints.maxWidth + extra
                    } else {
                        constraints.maxWidth
                    },
            )
        val placeable = measurable.measure(widened)
        layout(placeable.width, placeable.height) {
            placeable.place(-inset.roundToPx(), 0)
        }
    }

const val DASHBOARD_AURORA_CARD_TAG = "dashboard_aurora_card"
const val DASHBOARD_AURORA_BALANCE_TAG = "dashboard_aurora_balance"
const val DASHBOARD_AURORA_INCOME_PILL_TAG = "dashboard_aurora_income_pill"
const val DASHBOARD_AURORA_EXPENSE_PILL_TAG = "dashboard_aurora_expense_pill"
