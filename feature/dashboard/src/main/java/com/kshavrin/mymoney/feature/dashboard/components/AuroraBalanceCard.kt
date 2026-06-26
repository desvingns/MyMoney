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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.chartHiddenHint
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccentForSign
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValue
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
    // SecAurora uses the multi-hue accent (#37E1C0) for the positive card, not NeonMint.
    // dashboardAuroraAccentForSign still owns the per-currency cards' NeonMint positive, so the
    // positive accent is resolved locally here to match the mockup without changing that token.
    val accent =
        if (netPositive) {
            MaterialTheme.colorScheme.dashboardAuroraAccent
        } else {
            MaterialTheme.colorScheme.dashboardAuroraAccentForSign(false)
        }
    val balanceGlowBlur = with(LocalDensity.current) { 8.dp.toPx() }
    AuroraCardSurface(
        cardTestTag = DASHBOARD_AURORA_CARD_TAG,
        modifier = modifier,
        accent = accent,
    ) {
        Text(
            text = stringResource(R.string.dashboard_aurora_free_balance_label).uppercase(),
            style = MaterialTheme.typography.dashboardAuroraBalanceLabel,
            color = Color.White.copy(alpha = 0.55f),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraValueBottomMargin))
        Text(
            text = balance,
            style =
                MaterialTheme.typography.dashboardAuroraBalanceValue.copy(
                    shadow = Shadow(color = accent.copy(alpha = 0.5f), blurRadius = balanceGlowBlur),
                ),
            color = Color.White,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(DASHBOARD_AURORA_BALANCE_TAG),
        )
        Spacer(modifier = Modifier.height(Spacing.dashboardAuroraPillBottomMargin))
        IncomeExpensePills(
            income = income,
            expense = expense,
            incomePillTestTag = DASHBOARD_AURORA_INCOME_PILL_TAG,
            expensePillTestTag = DASHBOARD_AURORA_EXPENSE_PILL_TAG,
        )
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
                    labels = labels,
                    // SecAurora's wave has no gridlines; suppress them for the hero card regardless
                    // of the persisted AppSettings.chartShowGridlines (scoped to this card only).
                    showGridlines = false,
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

const val DASHBOARD_AURORA_CARD_TAG = "dashboard_aurora_card"
const val DASHBOARD_AURORA_BALANCE_TAG = "dashboard_aurora_balance"
const val DASHBOARD_AURORA_INCOME_PILL_TAG = "dashboard_aurora_income_pill"
const val DASHBOARD_AURORA_EXPENSE_PILL_TAG = "dashboard_aurora_expense_pill"
