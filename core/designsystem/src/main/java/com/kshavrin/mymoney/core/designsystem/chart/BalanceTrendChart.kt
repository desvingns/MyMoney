package com.kshavrin.mymoney.core.designsystem.chart

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardAuroraAccent
import com.kshavrin.mymoney.core.ui.theme.expenseAccent
import com.kshavrin.mymoney.core.ui.theme.incomeAccent
import com.kshavrin.mymoney.core.ui.theme.trendChartGridLine
import com.kshavrin.mymoney.core.ui.theme.trendChartMarkerGlow
import com.kshavrin.mymoney.core.ui.theme.trendChartZeroLine
import java.text.NumberFormat
import java.util.Locale

const val BALANCE_TREND_CHART_TAG = "balance_trend_chart"

const val BALANCE_TREND_CHART_GRIDLINE_COUNT = 3

private data class BalanceTrendChartPalette(
    val line: Color,
    val glow: Color,
    val accent: Color,
    val accentLine: Boolean,
)

private data class BalanceTrendChartLabelDrawCache(
    val layout: TextLayoutResult,
    val topLeft: Offset,
)

private data class BalanceTrendChartStyleDrawCache(
    val baseline: Float,
    val lineStroke: Float,
    val pointRadius: Float,
    val markerRadius: Float,
    val barWidth: Float,
    val smoothPath: Path,
    val smoothGlowStroke: Stroke,
    val smoothStroke: Stroke,
    val waveColor: Color,
    val waveGlowColor: Color,
    val waveLastDotColor: Color,
    val barColor: Color,
)

private data class BalanceTrendChartDrawCache(
    val geometry: BalanceTrendChartGeometry,
    val chartWidth: Float,
    val chartHeight: Float,
    val showGridlines: Boolean,
    val gridColor: Color,
    val zeroLineColor: Color,
    val gridStroke: Float,
    val style: ChartStyle,
    val palette: BalanceTrendChartPalette,
    val styleCache: BalanceTrendChartStyleDrawCache,
    val labels: List<BalanceTrendChartLabelDrawCache>,
    val labelColor: Color,
)

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

private fun describeBalanceTrendPeriod(
    labels: List<String>,
    selectedPeriodLabel: String,
    periodRangeTemplate: String,
): String {
    val visibleLabels = labels.filter(String::isNotBlank)
    return when {
        visibleLabels.size >= 2 ->
            String.format(
                Locale.ROOT,
                periodRangeTemplate,
                visibleLabels.first(),
                visibleLabels.last(),
            )
        visibleLabels.size == 1 -> visibleLabels.single()
        else -> selectedPeriodLabel
    }
}

private fun formatBalanceTrendValue(
    value: Float,
    locale: Locale,
): String =
    NumberFormat
        .getNumberInstance(locale)
        .apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 6
        }.format(value.toDouble())

@Composable
fun BalanceTrendChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    metricLabel: String? = null,
    showGridlines: Boolean = true,
    showLabels: Boolean = false,
    colorRule: ChartColorRule = ChartColorRule.Default,
    style: ChartStyle = ChartStyle.Default,
    chartHeight: Dp = Spacing.trendChartDefaultHeight,
) {
    val incomeColor = MaterialTheme.colorScheme.incomeAccent
    val expenseColor = MaterialTheme.colorScheme.expenseAccent
    val lineColor =
        when (colorRule) {
            ChartColorRule.Income -> incomeColor
            ChartColorRule.Expense -> expenseColor
            ChartColorRule.BySign ->
                if ((points.lastOrNull() ?: 0f) >= 0f) {
                    incomeColor
                } else {
                    expenseColor
                }
        }
    val gridColor = MaterialTheme.colorScheme.trendChartGridLine
    val zeroLineColor = MaterialTheme.colorScheme.trendChartZeroLine
    val glowColor = MaterialTheme.colorScheme.trendChartMarkerGlow
    val accentColor = MaterialTheme.colorScheme.dashboardAuroraAccent
    val palette =
        BalanceTrendChartPalette(
            line = lineColor,
            glow = glowColor,
            accent = accentColor,
            accentLine = colorRule == ChartColorRule.BySign,
        )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = gridColor)

    val height = chartHeight
    val labelHeight = Spacing.trendChartLabelHeight
    val totalHeight = if (showLabels) height + labelHeight else height

    val density = LocalDensity.current
    val markerGlowRadius = Spacing.trendChartMarkerGlowRadius
    val textMeasurer = rememberTextMeasurer()
    val zeroLineDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) }
    val locale = LocalConfiguration.current.locales[0]
    val metricDescriptionLabel = metricLabel ?: stringResource(R.string.balance_trend_chart_metric)
    val selectedPeriodLabel = stringResource(R.string.balance_trend_chart_period_selected)
    val periodRangeTemplate = stringResource(R.string.balance_trend_chart_period_range)
    val noDataLabel = stringResource(R.string.balance_trend_chart_no_data)
    val startPoint = points.firstOrNull()
    val endPoint = points.lastOrNull()
    val directionResource =
        when {
            startPoint == null || endPoint == null || endPoint == startPoint ->
                R.string.balance_trend_chart_direction_unchanged
            endPoint > startPoint -> R.string.balance_trend_chart_direction_increasing
            else -> R.string.balance_trend_chart_direction_decreasing
        }
    val directionLabel =
        stringResource(directionResource)
    val periodLabel =
        describeBalanceTrendPeriod(
            labels = labels,
            selectedPeriodLabel = selectedPeriodLabel,
            periodRangeTemplate = periodRangeTemplate,
        )
    val startValue = startPoint?.let { formatBalanceTrendValue(it, locale) } ?: noDataLabel
    val endValue = endPoint?.let { formatBalanceTrendValue(it, locale) } ?: noDataLabel
    val chartDescription =
        stringResource(
            R.string.balance_trend_chart_cd,
            metricDescriptionLabel,
            periodLabel,
            startValue,
            endValue,
            directionLabel,
        )

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
                .semantics {
                    contentDescription = chartDescription
                }.testTag(BALANCE_TREND_CHART_TAG)
                .drawWithCache {
                    val cache =
                        buildBalanceTrendChartDrawCache(
                            values = points,
                            labels = labels,
                            chartHeight = height,
                            showGridlines = showGridlines,
                            showLabels = showLabels,
                            style = style,
                            palette = palette,
                            gridColor = gridColor,
                            zeroLineColor = zeroLineColor,
                            labelStyle = labelStyle,
                            textMeasurer = textMeasurer,
                        )
                    onDrawBehind {
                        drawBalanceTrendChart(
                            cache = cache,
                            zeroLineDash = zeroLineDash,
                            glowPaint = glowPaint,
                        )
                    }
                },
    ) {}
}

private fun CacheDrawScope.buildBalanceTrendChartDrawCache(
    values: List<Float>,
    labels: List<String>,
    chartHeight: Dp,
    showGridlines: Boolean,
    showLabels: Boolean,
    style: ChartStyle,
    palette: BalanceTrendChartPalette,
    gridColor: Color,
    zeroLineColor: Color,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
): BalanceTrendChartDrawCache {
    val chartHeightPx = chartHeight.toPx()
    val geometry =
        calculateBalanceTrendChartGeometry(
            values = values,
            width = size.width,
            height = chartHeightPx,
            horizontalPadding = Spacing.trendChartHorizontalPadding.toPx(),
            verticalPadding = Spacing.trendChartVerticalPadding.toPx(),
        )
    val labelCache =
        if (showLabels && labels.isNotEmpty()) {
            val labelCount = minOf(labels.size, geometry.points.size)
            ArrayList<BalanceTrendChartLabelDrawCache>(labelCount).apply {
                for (index in 0 until labelCount) {
                    val layout =
                        textMeasurer.measure(
                            text = labels[index],
                            style = labelStyle.copy(textAlign = TextAlign.Center),
                            maxLines = 1,
                        )
                    add(
                        BalanceTrendChartLabelDrawCache(
                            layout = layout,
                            topLeft = Offset(geometry.points[index].x - layout.size.width / 2f, chartHeightPx),
                        ),
                    )
                }
            }
        } else {
            emptyList()
        }

    return BalanceTrendChartDrawCache(
        geometry = geometry,
        chartWidth = size.width,
        chartHeight = chartHeightPx,
        showGridlines = showGridlines,
        gridColor = gridColor,
        zeroLineColor = zeroLineColor,
        gridStroke = Spacing.trendChartGridLineStrokeWidth.toPx(),
        style = style,
        palette = palette,
        styleCache =
            buildBalanceTrendChartStyleDrawCache(
                values = values,
                geometry = geometry,
                chartHeight = chartHeightPx,
                verticalPadding = Spacing.trendChartVerticalPadding.toPx(),
                chartWidth = size.width,
                palette = palette,
            ),
        labels = labelCache,
        labelColor = labelStyle.color,
    )
}

private fun CacheDrawScope.buildBalanceTrendChartStyleDrawCache(
    values: List<Float>,
    geometry: BalanceTrendChartGeometry,
    chartHeight: Float,
    verticalPadding: Float,
    chartWidth: Float,
    palette: BalanceTrendChartPalette,
): BalanceTrendChartStyleDrawCache {
    val chartPoints = geometry.points
    val baseline = baselineY(values, geometry, chartHeight, verticalPadding)
    val lineStroke = Spacing.trendChartLineStrokeWidth.toPx()
    val pointRadius = Spacing.trendChartPointRadius.toPx()
    val markerRadius = Spacing.trendChartMarkerRadius.toPx()
    val waveColor = if (palette.accentLine) palette.accent else palette.line
    val smoothPath = Path().apply { buildSmooth(chartPoints) }

    return BalanceTrendChartStyleDrawCache(
        baseline = baseline,
        lineStroke = lineStroke,
        pointRadius = pointRadius,
        markerRadius = markerRadius,
        barWidth = calculateBalanceTrendChartBarWidth(chartPoints, chartWidth),
        smoothPath = smoothPath,
        smoothGlowStroke = Stroke(width = lineStroke * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        smoothStroke = Stroke(width = lineStroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        waveColor = waveColor,
        waveGlowColor = waveColor.copy(alpha = 0.22f),
        waveLastDotColor = lightenColor(waveColor, 1.4f),
        barColor = palette.line.copy(alpha = 0.86f),
    )
}

private fun DrawScope.drawBalanceTrendChart(
    cache: BalanceTrendChartDrawCache,
    zeroLineDash: PathEffect,
    glowPaint: Paint,
) {
    val geometry = cache.geometry
    if (cache.showGridlines) {
        for (index in geometry.gridLineXs.indices) {
            val x = geometry.gridLineXs[index]
            drawLine(
                color = cache.gridColor,
                start = Offset(x, 0f),
                end = Offset(x, cache.chartHeight),
                strokeWidth = cache.gridStroke,
            )
        }
    }
    geometry.zeroLineY?.let { y ->
        drawLine(
            color = cache.zeroLineColor,
            start = Offset(0f, y),
            end = Offset(cache.chartWidth, y),
            strokeWidth = cache.gridStroke,
            pathEffect = zeroLineDash,
        )
    }
    drawCachedBalanceTrendChartStyle(cache, glowPaint)
    for (index in cache.labels.indices) {
        val label = cache.labels[index]
        drawText(textLayoutResult = label.layout, color = cache.labelColor, topLeft = label.topLeft)
    }
}

private fun DrawScope.drawCachedBalanceTrendChartStyle(
    cache: BalanceTrendChartDrawCache,
    glowPaint: Paint,
) {
    val chartPoints = cache.geometry.points
    if (chartPoints.isEmpty()) return

    val styleCache = cache.styleCache
    val palette = cache.palette
    val marker = cache.geometry.marker
    when (cache.style) {
        ChartStyle.Bars -> {
            drawCachedBars(
                points = chartPoints,
                baseline = styleCache.baseline,
                color = styleCache.barColor,
                rounded = false,
                barWidth = styleCache.barWidth,
            )
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.Line -> {
            drawSegmentLine(chartPoints, palette.line, styleCache.lineStroke)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.Smooth -> {
            drawCachedPath(styleCache.smoothPath, styleCache.waveGlowColor, styleCache.smoothGlowStroke)
            drawCachedPath(styleCache.smoothPath, styleCache.waveColor, styleCache.smoothStroke)
            drawCachedWaveDots(chartPoints, styleCache.waveColor, styleCache.waveLastDotColor, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }
    }
}

private fun DrawScope.drawCachedPath(
    path: Path,
    color: Color,
    stroke: Stroke,
) {
    drawPath(path = path, color = color, style = stroke)
}

private fun DrawScope.drawCachedDots(
    points: List<Offset>,
    color: Color,
    radius: Float,
) {
    for (index in points.indices) {
        drawCircle(color = color, radius = radius, center = points[index])
    }
}

private fun DrawScope.drawCachedWaveDots(
    points: List<Offset>,
    color: Color,
    lastDotColor: Color,
    pointRadius: Float,
) {
    for (index in points.indices) {
        val isLast = index == points.lastIndex
        drawCircle(
            color = if (isLast) lastDotColor else color,
            radius = if (isLast) pointRadius * 1.33f else pointRadius * 0.87f,
            center = points[index],
        )
    }
}

private fun DrawScope.drawCachedBars(
    points: List<Offset>,
    baseline: Float,
    color: Color,
    rounded: Boolean,
    barWidth: Float,
) {
    val radius = if (rounded) barWidth / 2f else 0f
    for (index in points.indices) {
        val point = points[index]
        val left = (point.x - barWidth / 2f).coerceIn(0f, (size.width - barWidth).coerceAtLeast(0f))
        val top = minOf(point.y, baseline)
        val height = maxOf(kotlin.math.abs(point.y - baseline), 1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(barWidth, height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private fun DrawScope.drawCachedMarker(
    marker: Offset?,
    color: Color,
    glowPaint: Paint,
    radius: Float,
) {
    marker ?: return
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawCircle(
            marker.x,
            marker.y,
            radius,
            glowPaint.asFrameworkPaint(),
        )
    }
    drawCircle(color = color, radius = radius, center = marker)
}

private fun calculateBalanceTrendChartBarWidth(
    points: List<Offset>,
    chartWidth: Float,
): Float {
    if (points.isEmpty()) return 0f
    if (points.size == 1) return chartWidth * 0.18f
    var minimumStep = Float.MAX_VALUE
    for (index in 0 until points.lastIndex) {
        minimumStep = minOf(minimumStep, kotlin.math.abs(points[index + 1].x - points[index].x))
    }
    return (minimumStep * 0.52f).coerceAtLeast(1f)
}

private fun baselineY(
    values: List<Float>,
    geometry: BalanceTrendChartGeometry,
    chartHeight: Float,
    verticalPadding: Float,
): Float {
    if (values.isEmpty()) return chartHeight / 2f
    val minValue = values.min()
    val maxValue = values.max()
    return when {
        minValue < 0f && maxValue > 0f -> geometry.zeroLineY ?: chartHeight / 2f
        minValue == 0f && maxValue == 0f -> chartHeight / 2f
        maxValue <= 0f -> verticalPadding
        else -> chartHeight - verticalPadding
    }
}

private fun DrawScope.drawSegmentLine(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    cap: StrokeCap = StrokeCap.Round,
) {
    for (index in 0 until points.lastIndex) {
        drawLine(
            color = color,
            start = points[index],
            end = points[index + 1],
            strokeWidth = strokeWidth,
            cap = cap,
        )
    }
}

private fun lightenColor(
    color: Color,
    factor: Float,
): Color {
    val t = (factor - 1f).coerceIn(0f, 1f)
    return Color(
        red = color.red + (1f - color.red) * t,
        green = color.green + (1f - color.green) * t,
        blue = color.blue + (1f - color.blue) * t,
        alpha = color.alpha,
    )
}

private fun Path.buildSmooth(points: List<Offset>) {
    reset()
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    for (index in 0 until points.lastIndex) {
        val current = points[index]
        val next = points[index + 1]
        val midpointX = (current.x + next.x) / 2f
        cubicTo(midpointX, current.y, midpointX, next.y, next.x, next.y)
    }
}
