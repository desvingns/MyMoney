package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.chartHiddenHint
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccentForSign
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValue
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValueMinSp
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
    metricLabel: String? = null,
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
        AutoShrinkAuroraBalance(
            text = balance,
            baseStyle =
                MaterialTheme.typography.dashboardAuroraBalanceValue.copy(
                    shadow = Shadow(color = accent.copy(alpha = 0.5f), blurRadius = balanceGlowBlur),
                ),
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
            val trendChartLabel = stringResource(R.string.dashboard_trend_chart)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChartClick)
                        .semantics {
                            contentDescription = trendChartLabel
                            role = Role.Button
                        }
                        .testTag(DASHBOARD_TREND_CHART_TAG),
            ) {
                BalanceTrendChart(
                    points = points,
                    labels = labels,
                    metricLabel = metricLabel,
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
            val hiddenHintLabel = stringResource(R.string.chart_hidden_hint)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(Spacing.chartHiddenHintHeight)
                        .clickable(onClick = onChartClick)
                        .semantics {
                            contentDescription = hiddenHintLabel
                            role = Role.Button
                        }
                        .testTag(DASHBOARD_CHART_HIDDEN_HINT_TAG),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = hiddenHintLabel,
                    style = MaterialTheme.typography.chartHiddenHint,
                    color = MaterialTheme.colorScheme.dashboardBalancePanelContent,
                    maxLines = 1,
                )
            }
        }
    }
}

// Manual auto-shrink for the Aurora hero balance, mirroring AutoShrinkPeriodTitle in
// PeriodLabel.kt: Compose BoM 2024.11 (Foundation 1.7.5) has no BasicText(autoSize=...),
// so we measure-and-shrink.  Start at dashboardAuroraBalanceValue.fontSize (36sp) and step
// down 1sp while the laid-out value overflows the available width (didOverflowWidth), never
// below dashboardAuroraBalanceValueMinSp.  This keeps the approved 36sp rendering at
// fontScale 1.0 (no overflow → no shrink) yet a long value stays single-line and unclipped
// under a large accessibility fontScale.
@Composable
private fun AutoShrinkAuroraBalance(
    text: String,
    baseStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val maxFontSize = baseStyle.fontSize
    val minFontSize = dashboardAuroraBalanceValueMinSp
    var fontSize by remember(text) { mutableStateOf(maxFontSize) }
    Text(
        text = text,
        style = baseStyle.copy(fontSize = fontSize),
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
        modifier = modifier,
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > minFontSize) {
                val next = (fontSize.value - 1f).sp
                fontSize = if (next.value < minFontSize.value) minFontSize else next
            }
        },
    )
}

const val DASHBOARD_AURORA_CARD_TAG = "dashboard_aurora_card"
const val DASHBOARD_AURORA_BALANCE_TAG = "dashboard_aurora_balance"
const val DASHBOARD_AURORA_INCOME_PILL_TAG = "dashboard_aurora_income_pill"
const val DASHBOARD_AURORA_EXPENSE_PILL_TAG = "dashboard_aurora_expense_pill"
