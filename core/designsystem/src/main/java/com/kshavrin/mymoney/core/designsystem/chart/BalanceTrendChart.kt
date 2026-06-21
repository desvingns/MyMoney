package com.kshavrin.mymoney.core.designsystem.chart

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.expenseAccent
import com.kshavrin.mymoney.core.ui.theme.incomeAccent
import com.kshavrin.mymoney.core.ui.theme.trendChartGridLine
import com.kshavrin.mymoney.core.ui.theme.trendChartMarkerGlow
import com.kshavrin.mymoney.core.ui.theme.trendChartZeroLine

const val BALANCE_TREND_CHART_TAG = "balance_trend_chart"

const val BALANCE_TREND_CHART_GRIDLINE_COUNT = 3

data class BalanceTrendChartGeometry(
    val points: List<Offset>,
    val gridLineXs: List<Float>,
    val zeroLineY: Float?,
    val marker: Offset?,
)

internal fun calculateBalanceTrendChartGeometry(
    values: List<Float>,
    width: Float,
    height: Float,
    horizontalPadding: Float,
    verticalPadding: Float,
    gridLineCount: Int = BALANCE_TREND_CHART_GRIDLINE_COUNT,
): BalanceTrendChartGeometry {
    val plotLeft = horizontalPadding
    val plotRight = width - horizontalPadding
    val plotTop = verticalPadding
    val plotBottom = height - verticalPadding
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(0f)
    val plotHeight = (plotBottom - plotTop).coerceAtLeast(0f)

    val gridLineXs =
        if (gridLineCount <= 0 || plotWidth <= 0f) {
            emptyList()
        } else {
            (1..gridLineCount).map { index ->
                plotLeft + plotWidth * index / (gridLineCount + 1)
            }
        }

    if (values.isEmpty()) {
        return BalanceTrendChartGeometry(
            points = emptyList(),
            gridLineXs = gridLineXs,
            zeroLineY = null,
            marker = null,
        )
    }

    val minValue = values.min()
    val maxValue = values.max()
    val range = maxValue - minValue

    fun yFor(value: Float): Float =
        if (range == 0f) {
            plotTop + plotHeight / 2f
        } else {
            plotBottom - (value - minValue) / range * plotHeight
        }

    val xStep = if (values.size > 1) plotWidth / (values.size - 1) else 0f
    val points =
        values.mapIndexed { index, value ->
            val x = if (values.size > 1) plotLeft + xStep * index else plotLeft + plotWidth / 2f
            Offset(x, yFor(value))
        }

    val zeroLineY = if (minValue < 0f && maxValue > 0f) yFor(0f) else null

    return BalanceTrendChartGeometry(
        points = points,
        gridLineXs = gridLineXs,
        zeroLineY = zeroLineY,
        marker = points.lastOrNull(),
    )
}

@Composable
fun BalanceTrendChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    showGridlines: Boolean = true,
    showLabels: Boolean = false,
    colorRule: ChartColorRule = ChartColorRule.Default,
    @Suppress("UNUSED_PARAMETER") style: ChartStyle = ChartStyle.Default,
) {
    val lineColor =
        when (colorRule) {
            ChartColorRule.Income -> MaterialTheme.colorScheme.incomeAccent
            ChartColorRule.Expense -> MaterialTheme.colorScheme.expenseAccent
            ChartColorRule.BySign ->
                if ((points.lastOrNull() ?: 0f) >= 0f) {
                    MaterialTheme.colorScheme.incomeAccent
                } else {
                    MaterialTheme.colorScheme.expenseAccent
                }
        }
    val gridColor = MaterialTheme.colorScheme.trendChartGridLine
    val zeroLineColor = MaterialTheme.colorScheme.trendChartZeroLine
    val glowColor = MaterialTheme.colorScheme.trendChartMarkerGlow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = gridColor)

    val height = Spacing.trendChartDefaultHeight
    val labelHeight = Spacing.trendChartLabelHeight
    val totalHeight = if (showLabels) height + labelHeight else height

    val density = LocalDensity.current
    val markerGlowRadius = Spacing.trendChartMarkerGlowRadius
    val markerRadius = Spacing.trendChartMarkerRadius
    val textMeasurer = rememberTextMeasurer()

    val glowPaint =
        remember(density, glowColor, markerGlowRadius) {
            Paint().apply {
                color = glowColor
                this.style = PaintingStyle.Fill
                asFrameworkPaint().maskFilter =
                    BlurMaskFilter(
                        with(density) { markerGlowRadius.toPx() },
                        BlurMaskFilter.Blur.NORMAL,
                    )
            }
        }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(totalHeight)
                .testTag(BALANCE_TREND_CHART_TAG),
    ) {
        val chartHeightPx = height.toPx()
        val geometry =
            calculateBalanceTrendChartGeometry(
                values = points,
                width = size.width,
                height = chartHeightPx,
                horizontalPadding = Spacing.trendChartHorizontalPadding.toPx(),
                verticalPadding = Spacing.trendChartVerticalPadding.toPx(),
            )

        if (showGridlines) {
            val gridStroke = Spacing.trendChartGridLineStrokeWidth.toPx()
            geometry.gridLineXs.forEach { x ->
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, chartHeightPx),
                    strokeWidth = gridStroke,
                )
            }
        }

        geometry.zeroLineY?.let { y ->
            drawLine(
                color = zeroLineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = Spacing.trendChartGridLineStrokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
            )
        }

        if (geometry.points.size >= 2) {
            val lineStroke = Spacing.trendChartLineStrokeWidth.toPx()
            for (index in 0 until geometry.points.size - 1) {
                drawLine(
                    color = lineColor,
                    start = geometry.points[index],
                    end = geometry.points[index + 1],
                    strokeWidth = lineStroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        val pointRadius = Spacing.trendChartPointRadius.toPx()
        geometry.points.forEach { point ->
            drawCircle(color = lineColor, radius = pointRadius, center = point)
        }

        geometry.marker?.let { marker ->
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawCircle(
                    marker.x,
                    marker.y,
                    markerRadius.toPx(),
                    glowPaint.asFrameworkPaint(),
                )
            }
            drawCircle(color = glowColor, radius = markerRadius.toPx(), center = marker)
        }

        if (showLabels && labels.isNotEmpty()) {
            drawLabels(
                labels = labels,
                points = geometry.points,
                top = chartHeightPx,
                textMeasurer = textMeasurer,
                style = labelStyle,
            )
        }
    }
}

private fun DrawScope.drawLabels(
    labels: List<String>,
    points: List<Offset>,
    top: Float,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    val count = minOf(labels.size, points.size)
    for (index in 0 until count) {
        val layout: TextLayoutResult =
            textMeasurer.measure(
                text = labels[index],
                style = style.copy(textAlign = TextAlign.Center),
                maxLines = 1,
            )
        drawText(
            textLayoutResult = layout,
            color = style.color,
            topLeft = Offset(points[index].x - layout.size.width / 2f, top),
        )
    }
}
