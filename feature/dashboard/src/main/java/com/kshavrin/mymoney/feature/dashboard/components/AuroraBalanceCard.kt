package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.kshavrin.mymoney.core.designsystem.chart.BalanceTrendChart
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.chartHiddenHint
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccentForSign
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraBalanceValueCompact
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraChartBackdrop
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraPill
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
    val backdropColor = MaterialTheme.colorScheme.dashboardAuroraChartBackdrop
    val backdropShape = MaterialTheme.shapes.dashboardAuroraPill as CornerBasedShape
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
                        .drawFeatheredBackdrop(backdropColor, backdropShape)
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

// Paints a dark, rounded backdrop panel behind the wave chart whose ENTIRE perimeter (all four
// edges + corners) feathers to transparent over Spacing.dashboardAuroraChartBackdropFade, so the
// panel melts into the card surface with no hard rectangular boundary. drawBehind renders before the
// chart content, so BalanceTrendChart draws on top. The feather is produced by drawing the dark
// rounded-rect fill into an offscreen layer, then multiplying its alpha (BlendMode.DstIn) with four
// edge gradients (left, right, top, bottom) — overlapping the gradients also softens the corners.
private fun Modifier.drawFeatheredBackdrop(
    color: Color,
    shape: CornerBasedShape,
): Modifier =
    drawBehind {
        val fade = Spacing.dashboardAuroraChartBackdropFade.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        if (fade <= 0f) return@drawBehind
        val cornerRadius = shape.topStart.toPx(size, this)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(
                bounds = Rect(0f, 0f, size.width, size.height),
                paint = Paint(),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            )
            featherEdge(
                start = Offset(0f, 0f),
                end = Offset(fade, 0f),
            )
            featherEdge(
                start = Offset(size.width, 0f),
                end = Offset(size.width - fade, 0f),
            )
            featherEdge(
                start = Offset(0f, 0f),
                end = Offset(0f, fade),
            )
            featherEdge(
                start = Offset(0f, size.height),
                end = Offset(0f, size.height - fade),
            )
            canvas.restore()
        }
    }

// One perimeter feather pass: a transparent→opaque gradient from [start] (the outer edge) to [end]
// (fade width inward). With BlendMode.DstIn this multiplies the destination alpha, so each edge of
// the dark panel ramps from invisible at the border to fully visible past the fade width.
private fun DrawScope.featherEdge(
    start: Offset,
    end: Offset,
) {
    drawRect(
        brush =
            Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.Black),
                start = start,
                end = end,
            ),
        blendMode = BlendMode.DstIn,
    )
}

const val DASHBOARD_AURORA_CARD_TAG = "dashboard_aurora_card"
const val DASHBOARD_AURORA_BALANCE_TAG = "dashboard_aurora_balance"
const val DASHBOARD_AURORA_INCOME_PILL_TAG = "dashboard_aurora_income_pill"
const val DASHBOARD_AURORA_EXPENSE_PILL_TAG = "dashboard_aurora_expense_pill"
