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
    val income: Color,
    val expense: Color,
    val byDirection: Boolean,
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
    val directionSmoothGlowStroke: Stroke,
    val directionSmoothStroke: Stroke,
    val waveColor: Color,
    val waveGlowColor: Color,
    val waveLastDotColor: Color,
    val barColor: Color,
    val directionSegments: List<BalanceTrendChartHorizontalSegment>,
    val directionSmoothPaths: List<Path>,
    val aboveWaveGlowColor: Color,
    val belowWaveGlowColor: Color,
    val aboveLastDotColor: Color,
    val belowLastDotColor: Color,
    val aboveBarColor: Color,
    val belowBarColor: Color,
)

private data class BalanceTrendChartProjectionDrawCache(
    val abovePath: Path,
    val belowPath: Path,
    val aboveColor: Color,
    val belowColor: Color,
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
    val projection: BalanceTrendChartProjectionDrawCache?,
    val labels: List<BalanceTrendChartLabelDrawCache>,
    val labelColor: Color,
)

data class BalanceTrendChartGeometry(
    val points: List<Offset>,
    val gridLineXs: List<Float>,
    val zeroLineY: Float?,
    val marker: Offset?,
)

internal enum class BalanceTrendChartHorizontalZone {
    AboveOrOn,
    Below,
}

internal data class BalanceTrendChartHorizontalSegment(
    val start: Offset,
    val end: Offset,
    val zone: BalanceTrendChartHorizontalZone,
)

internal fun splitBalanceTrendChartSegmentsAtHorizontalLine(
    points: List<Offset>,
    horizontalLineY: Float,
): List<BalanceTrendChartHorizontalSegment> {
    if (points.size < 2) return emptyList()

    val segments = ArrayList<BalanceTrendChartHorizontalSegment>(points.size)
    for (index in 0 until points.lastIndex) {
        val start = points[index]
        val end = points[index + 1]
        val startDistance = start.y - horizontalLineY
        val endDistance = end.y - horizontalLineY
        if (startDistance * endDistance < 0f) {
            val t = (horizontalLineY - start.y) / (end.y - start.y)
            val intersection = Offset(start.x + t * (end.x - start.x), horizontalLineY)
            segments +=
                BalanceTrendChartHorizontalSegment(
                    start = start,
                    end = intersection,
                    zone = horizontalZoneFor(start, horizontalLineY),
                )
            segments +=
                BalanceTrendChartHorizontalSegment(
                    start = intersection,
                    end = end,
                    zone = horizontalZoneFor(end, horizontalLineY),
                )
        } else {
            segments +=
                BalanceTrendChartHorizontalSegment(
                    start = start,
                    end = end,
                    zone = horizontalZoneForSegment(startDistance, endDistance),
                )
        }
    }
    return segments
}

private fun horizontalZoneFor(
    point: Offset,
    horizontalLineY: Float,
): BalanceTrendChartHorizontalZone =
    if (point.y <= horizontalLineY) {
        BalanceTrendChartHorizontalZone.AboveOrOn
    } else {
        BalanceTrendChartHorizontalZone.Below
    }

private fun horizontalZoneForSegment(
    startDistance: Float,
    endDistance: Float,
): BalanceTrendChartHorizontalZone =
    when {
        startDistance == 0f && endDistance == 0f -> BalanceTrendChartHorizontalZone.AboveOrOn
        startDistance == 0f -> if (endDistance < 0f) BalanceTrendChartHorizontalZone.AboveOrOn else BalanceTrendChartHorizontalZone.Below
        else -> if (startDistance < 0f) BalanceTrendChartHorizontalZone.AboveOrOn else BalanceTrendChartHorizontalZone.Below
    }

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
    showProjection: Boolean = false,
    chartHeight: Dp = Spacing.trendChartDefaultHeight,
) {
    val incomeColor = MaterialTheme.colorScheme.incomeAccent
    val expenseColor = MaterialTheme.colorScheme.expenseAccent
    val lineColor =
        when (colorRule) {
            ChartColorRule.Solid -> MaterialTheme.colorScheme.dashboardAuroraAccent
            ChartColorRule.AlwaysGreen -> incomeColor
            ChartColorRule.AlwaysRed -> expenseColor
            ChartColorRule.ByDirection -> incomeColor
        }
    val gridColor = MaterialTheme.colorScheme.trendChartGridLine
    val zeroLineColor = MaterialTheme.colorScheme.trendChartZeroLine
    val glowColor = MaterialTheme.colorScheme.trendChartMarkerGlow
    val palette =
        BalanceTrendChartPalette(
            line = lineColor,
            glow = glowColor,
            income = incomeColor,
            expense = expenseColor,
            byDirection = colorRule == ChartColorRule.ByDirection,
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
                            showProjection = showProjection,
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
    showProjection: Boolean,
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

    val styleCache =
        buildBalanceTrendChartStyleDrawCache(
            values = values,
            geometry = geometry,
            chartHeight = chartHeightPx,
            verticalPadding = Spacing.trendChartVerticalPadding.toPx(),
            chartWidth = size.width,
            palette = palette,
        )

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
        styleCache = styleCache,
        projection =
            buildBalanceTrendChartProjectionDrawCache(
                geometry = geometry,
                baseline = styleCache.baseline,
                showProjection = showProjection,
                style = style,
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
    val waveColor = palette.line
    val directionSegments =
        if (palette.byDirection && chartPoints.isNotEmpty()) {
            splitBalanceTrendChartSegmentsAtHorizontalLine(chartPoints, chartPoints.first().y)
        } else {
            emptyList()
        }
    val directionSmoothPaths =
        if (palette.byDirection) {
            ArrayList<Path>(directionSegments.size).apply {
                for (segment in directionSegments) {
                    add(Path().apply { buildSmoothSegment(segment.start, segment.end) })
                }
            }
        } else {
            emptyList()
        }
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
        directionSmoothGlowStroke = Stroke(width = lineStroke * 3.2f, cap = StrokeCap.Butt, join = StrokeJoin.Round),
        directionSmoothStroke = Stroke(width = lineStroke * 1.2f, cap = StrokeCap.Butt, join = StrokeJoin.Round),
        waveColor = waveColor,
        waveGlowColor = waveColor.copy(alpha = 0.22f),
        waveLastDotColor = lightenColor(waveColor, 1.4f),
        barColor = palette.line.copy(alpha = 0.86f),
        directionSegments = directionSegments,
        directionSmoothPaths = directionSmoothPaths,
        aboveWaveGlowColor = palette.income.copy(alpha = 0.22f),
        belowWaveGlowColor = palette.expense.copy(alpha = 0.22f),
        aboveLastDotColor = lightenColor(palette.income, 1.4f),
        belowLastDotColor = lightenColor(palette.expense, 1.4f),
        aboveBarColor = palette.income.copy(alpha = 0.86f),
        belowBarColor = palette.expense.copy(alpha = 0.86f),
    )
}

private fun CacheDrawScope.buildBalanceTrendChartProjectionDrawCache(
    geometry: BalanceTrendChartGeometry,
    baseline: Float,
    showProjection: Boolean,
    style: ChartStyle,
    palette: BalanceTrendChartPalette,
): BalanceTrendChartProjectionDrawCache? {
    if (!showProjection || style == ChartStyle.Bars || geometry.points.size < 2) return null

    val abovePath = Path()
    val belowPath = Path()
    val segments = splitBalanceTrendChartSegmentsAtHorizontalLine(geometry.points, baseline)
    for (segment in segments) {
        val path =
            if (segment.zone == BalanceTrendChartHorizontalZone.AboveOrOn) {
                abovePath
            } else {
                belowPath
            }
        path.moveTo(segment.start.x, segment.start.y)
        path.lineTo(segment.end.x, segment.end.y)
        path.lineTo(segment.end.x, baseline)
        path.lineTo(segment.start.x, baseline)
        path.close()
    }
    return BalanceTrendChartProjectionDrawCache(
        abovePath = abovePath,
        belowPath = belowPath,
        aboveColor = palette.income.copy(alpha = 0.22f),
        belowColor = palette.expense.copy(alpha = 0.22f),
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
    cache.projection?.let { projection ->
        drawPath(path = projection.abovePath, color = projection.aboveColor)
        drawPath(path = projection.belowPath, color = projection.belowColor)
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
            if (palette.byDirection) {
                drawCachedDirectionalBars(
                    points = chartPoints,
                    baseline = styleCache.baseline,
                    directionReferenceY = chartPoints.first().y,
                    aboveColor = styleCache.aboveBarColor,
                    belowColor = styleCache.belowBarColor,
                    rounded = false,
                    barWidth = styleCache.barWidth,
                )
            } else {
                drawCachedBars(
                    points = chartPoints,
                    baseline = styleCache.baseline,
                    color = styleCache.barColor,
                    rounded = false,
                    barWidth = styleCache.barWidth,
                )
            }
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.Line -> {
            if (palette.byDirection) {
                drawDirectionalSegments(
                    segments = styleCache.directionSegments,
                    aboveColor = palette.income,
                    belowColor = palette.expense,
                    strokeWidth = styleCache.lineStroke,
                )
                drawCachedDirectionalDots(
                    points = chartPoints,
                    directionReferenceY = chartPoints.first().y,
                    aboveColor = palette.income,
                    belowColor = palette.expense,
                    radius = styleCache.pointRadius,
                )
            } else {
                drawSegmentLine(chartPoints, palette.line, styleCache.lineStroke)
                drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            }
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.Smooth -> {
            if (palette.byDirection) {
                drawCachedDirectionalSmoothPaths(
                    paths = styleCache.directionSmoothPaths,
                    segments = styleCache.directionSegments,
                    aboveColor = palette.income,
                    belowColor = palette.expense,
                    aboveGlowColor = styleCache.aboveWaveGlowColor,
                    belowGlowColor = styleCache.belowWaveGlowColor,
                    glowStroke = styleCache.directionSmoothGlowStroke,
                    stroke = styleCache.directionSmoothStroke,
                )
                drawCachedDirectionalWaveDots(
                    points = chartPoints,
                    directionReferenceY = chartPoints.first().y,
                    aboveColor = palette.income,
                    belowColor = palette.expense,
                    aboveLastDotColor = styleCache.aboveLastDotColor,
                    belowLastDotColor = styleCache.belowLastDotColor,
                    pointRadius = styleCache.pointRadius,
                )
            } else {
                drawCachedPath(styleCache.smoothPath, styleCache.waveGlowColor, styleCache.smoothGlowStroke)
                drawCachedPath(styleCache.smoothPath, styleCache.waveColor, styleCache.smoothStroke)
                drawCachedWaveDots(chartPoints, styleCache.waveColor, styleCache.waveLastDotColor, styleCache.pointRadius)
            }
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

private fun DrawScope.drawCachedDirectionalBars(
    points: List<Offset>,
    baseline: Float,
    directionReferenceY: Float,
    aboveColor: Color,
    belowColor: Color,
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
            color = if (point.y <= directionReferenceY) aboveColor else belowColor,
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

private fun DrawScope.drawDirectionalSegments(
    segments: List<BalanceTrendChartHorizontalSegment>,
    aboveColor: Color,
    belowColor: Color,
    strokeWidth: Float,
) {
    for (segment in segments) {
        drawLine(
            color = if (segment.zone == BalanceTrendChartHorizontalZone.AboveOrOn) aboveColor else belowColor,
            start = segment.start,
            end = segment.end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Butt,
        )
    }
}

private fun DrawScope.drawCachedDirectionalDots(
    points: List<Offset>,
    directionReferenceY: Float,
    aboveColor: Color,
    belowColor: Color,
    radius: Float,
) {
    for (index in points.indices) {
        val point = points[index]
        drawCircle(
            color = if (point.y <= directionReferenceY) aboveColor else belowColor,
            radius = radius,
            center = point,
        )
    }
}

private fun DrawScope.drawCachedDirectionalSmoothPaths(
    paths: List<Path>,
    segments: List<BalanceTrendChartHorizontalSegment>,
    aboveColor: Color,
    belowColor: Color,
    aboveGlowColor: Color,
    belowGlowColor: Color,
    glowStroke: Stroke,
    stroke: Stroke,
) {
    for (index in paths.indices) {
        val isAbove = segments[index].zone == BalanceTrendChartHorizontalZone.AboveOrOn
        drawCachedPath(paths[index], if (isAbove) aboveGlowColor else belowGlowColor, glowStroke)
        drawCachedPath(paths[index], if (isAbove) aboveColor else belowColor, stroke)
    }
}

private fun DrawScope.drawCachedDirectionalWaveDots(
    points: List<Offset>,
    directionReferenceY: Float,
    aboveColor: Color,
    belowColor: Color,
    aboveLastDotColor: Color,
    belowLastDotColor: Color,
    pointRadius: Float,
) {
    for (index in points.indices) {
        val point = points[index]
        val isAbove = point.y <= directionReferenceY
        val isLast = index == points.lastIndex
        drawCircle(
            color =
                if (isLast) {
                    if (isAbove) aboveLastDotColor else belowLastDotColor
                } else {
                    if (isAbove) aboveColor else belowColor
                },
            radius = if (isLast) pointRadius * 1.33f else pointRadius * 0.87f,
            center = point,
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

private fun Path.buildSmoothSegment(
    start: Offset,
    end: Offset,
) {
    moveTo(start.x, start.y)
    val midpointX = (start.x + end.x) / 2f
    cubicTo(midpointX, start.y, midpointX, end.y, end.x, end.y)
}
