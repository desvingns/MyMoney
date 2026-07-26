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
import androidx.compose.ui.graphics.Brush
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

private class BalanceTrendChartDrawingCache {
    val linePath = Path()
    val areaPath = Path()
}

private data class BalanceTrendChartPalette(
    val line: Color,
    val income: Color,
    val expense: Color,
    val glow: Color,
    // Neon-wave accent (ChartWave). Used by SmoothArea when the colour rule is the default
    // (multi mode): the wave fill + stroke + dots are drawn in this accent instead of a
    // sign-driven income/expense colour.
    val accent: Color,
    // True when no explicit Income/Expense colour was chosen, so the wave should use [accent].
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
    val linePath: Path,
    val areaPath: Path,
    val primaryPathStroke: Stroke?,
    val secondaryPathStroke: Stroke?,
    val tertiaryPathStroke: Stroke?,
    val gradientBrush: Brush?,
    val fillBrush: Brush?,
    val waveColor: Color,
    val waveGlowColor: Color,
    val waveLastDotColor: Color,
    val neonAreaColor: Color,
    val steppedAreaColor: Color,
    val baselineAreaColor: Color,
    val baselineLineColor: Color,
    val barColor: Color,
    val dualGlowOuterColor: Color,
    val dualGlowInnerColor: Color,
    val ribbonColor: Color,
)

private data class BalanceTrendChartDrawCache(
    val values: List<Float>,
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
            income = incomeColor,
            expense = expenseColor,
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
    val styleDash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 9f)) }
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
                            styleDash = styleDash,
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
    styleDash: PathEffect,
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
        values = values,
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
                style = style,
                palette = palette,
                styleDash = styleDash,
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
    style: ChartStyle,
    palette: BalanceTrendChartPalette,
    styleDash: PathEffect,
): BalanceTrendChartStyleDrawCache {
    val chartPoints = geometry.points
    val baseline = baselineY(values, geometry, chartHeight, verticalPadding)
    val lineStroke = Spacing.trendChartLineStrokeWidth.toPx()
    val pointRadius = Spacing.trendChartPointRadius.toPx()
    val markerRadius = Spacing.trendChartMarkerRadius.toPx()
    val waveColor = if (palette.accentLine) palette.accent else palette.line
    val baselineColor = if ((values.lastOrNull() ?: 0f) >= 0f) palette.income else palette.expense
    val linePath = Path()
    val areaPath = Path()

    when (style) {
        ChartStyle.NeonArea,
        ChartStyle.BaselineFill,
        ChartStyle.VerticalGradientArea ->
            areaPath.buildArea(chartPoints, baseline, smooth = false, stepped = false)

        ChartStyle.SmoothLine,
        ChartStyle.Ribbon -> linePath.buildSmooth(chartPoints)

        ChartStyle.SmoothArea -> {
            areaPath.buildArea(chartPoints, baseline, smooth = true, stepped = false)
            linePath.buildSmooth(chartPoints)
        }

        ChartStyle.SteppedLine -> linePath.buildStepped(chartPoints)

        ChartStyle.SteppedArea -> {
            areaPath.buildArea(chartPoints, baseline, smooth = false, stepped = true)
            linePath.buildStepped(chartPoints)
        }

        ChartStyle.GradientStroke,
        ChartStyle.DualGlow,
        ChartStyle.DashedLine,
        ChartStyle.ThinMinimal,
        ChartStyle.ThickBold -> linePath.buildPolyline(chartPoints)

        ChartStyle.Mountain -> {
            areaPath.buildArea(chartPoints, baseline, smooth = true, stepped = false)
            linePath.buildSmooth(chartPoints)
        }

        ChartStyle.NeonLine,
        ChartStyle.Bars,
        ChartStyle.RoundedBars,
        ChartStyle.DotsLine,
        ChartStyle.DotsOnly,
        ChartStyle.CandySegments -> Unit
    }

    val roundedPathStroke =
        Stroke(width = lineStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val primaryPathStroke =
        when (style) {
            ChartStyle.SmoothLine,
            ChartStyle.SteppedLine,
            ChartStyle.SteppedArea,
            ChartStyle.VerticalGradientArea -> roundedPathStroke

            ChartStyle.SmoothArea ->
                Stroke(width = lineStroke * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.GradientStroke ->
                Stroke(width = lineStroke * 1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.DualGlow ->
                Stroke(width = lineStroke * 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.DashedLine ->
                Stroke(
                    width = lineStroke,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = styleDash,
                )

            ChartStyle.ThinMinimal ->
                Stroke(width = lineStroke * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.ThickBold ->
                Stroke(width = lineStroke * 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.Mountain ->
                Stroke(width = lineStroke * 1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.Ribbon ->
                Stroke(width = lineStroke * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            else -> null
        }
    val secondaryPathStroke =
        when (style) {
            ChartStyle.SmoothArea ->
                Stroke(width = lineStroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.DualGlow ->
                Stroke(width = lineStroke * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

            ChartStyle.Ribbon -> roundedPathStroke
            else -> null
        }
    val tertiaryPathStroke = if (style == ChartStyle.DualGlow) roundedPathStroke else null
    val gradientBrush =
        if (style == ChartStyle.GradientStroke) {
            Brush.horizontalGradient(
                colors = listOf(palette.income, palette.line, palette.expense),
                startX = 0f,
                endX = chartWidth,
            )
        } else {
            null
        }
    val fillBrush =
        when (style) {
            ChartStyle.SmoothArea ->
                Brush.verticalGradient(
                    colors = listOf(waveColor.copy(alpha = 0.4f), waveColor.copy(alpha = 0f)),
                    startY = 0f,
                    endY = baseline,
                )

            ChartStyle.VerticalGradientArea,
            ChartStyle.Mountain ->
                Brush.verticalGradient(
                    colors = listOf(palette.line.copy(alpha = 0.36f), palette.line.copy(alpha = 0.04f)),
                    startY = 0f,
                    endY = baseline,
                )

            else -> null
        }

    return BalanceTrendChartStyleDrawCache(
        baseline = baseline,
        lineStroke = lineStroke,
        pointRadius = pointRadius,
        markerRadius = markerRadius,
        barWidth = calculateBalanceTrendChartBarWidth(chartPoints, chartWidth),
        linePath = linePath,
        areaPath = areaPath,
        primaryPathStroke = primaryPathStroke,
        secondaryPathStroke = secondaryPathStroke,
        tertiaryPathStroke = tertiaryPathStroke,
        gradientBrush = gradientBrush,
        fillBrush = fillBrush,
        waveColor = waveColor,
        waveGlowColor = waveColor.copy(alpha = 0.22f),
        waveLastDotColor = lightenColor(waveColor, 1.4f),
        neonAreaColor = palette.line.copy(alpha = 0.16f),
        steppedAreaColor = palette.line.copy(alpha = 0.15f),
        baselineAreaColor = baselineColor.copy(alpha = 0.18f),
        baselineLineColor = baselineColor.copy(alpha = 0.42f),
        barColor = palette.line.copy(alpha = 0.86f),
        dualGlowOuterColor = palette.line.copy(alpha = 0.12f),
        dualGlowInnerColor = palette.glow.copy(alpha = 0.26f),
        ribbonColor = palette.line.copy(alpha = 0.2f),
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
        ChartStyle.NeonLine -> {
            drawSegmentLine(chartPoints, palette.line, styleCache.lineStroke)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.NeonArea -> {
            drawPath(path = styleCache.areaPath, color = styleCache.neonAreaColor)
            drawSegmentLine(chartPoints, palette.line, styleCache.lineStroke)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.SmoothLine -> {
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.SmoothArea -> {
            drawCachedFill(styleCache.areaPath, styleCache.fillBrush!!)
            drawCachedPath(styleCache.linePath, styleCache.waveGlowColor, styleCache.primaryPathStroke!!)
            drawCachedPath(styleCache.linePath, styleCache.waveColor, styleCache.secondaryPathStroke!!)
            drawCachedWaveDots(
                chartPoints,
                styleCache.waveColor,
                styleCache.waveLastDotColor,
                styleCache.pointRadius,
            )
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.SteppedLine -> {
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.SteppedArea -> {
            drawPath(path = styleCache.areaPath, color = styleCache.steppedAreaColor)
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

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

        ChartStyle.RoundedBars -> {
            drawCachedBars(
                points = chartPoints,
                baseline = styleCache.baseline,
                color = styleCache.barColor,
                rounded = true,
                barWidth = styleCache.barWidth,
            )
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.DotsLine -> {
            drawSegmentLine(chartPoints, palette.line, styleCache.lineStroke)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius * 1.35f)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.DotsOnly -> {
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius * 1.55f)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.GradientStroke -> {
            drawCachedPath(styleCache.linePath, styleCache.gradientBrush!!, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.DualGlow -> {
            drawCachedPath(styleCache.linePath, styleCache.dualGlowOuterColor, styleCache.primaryPathStroke!!)
            drawCachedPath(styleCache.linePath, styleCache.dualGlowInnerColor, styleCache.secondaryPathStroke!!)
            drawCachedPath(styleCache.linePath, palette.line, styleCache.tertiaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.DashedLine -> {
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.ThinMinimal -> {
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.ThickBold -> {
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius * 1.25f)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.BaselineFill -> {
            drawPath(path = styleCache.areaPath, color = styleCache.baselineAreaColor)
            drawLine(
                color = styleCache.baselineLineColor,
                start = Offset(0f, styleCache.baseline),
                end = Offset(cache.chartWidth, styleCache.baseline),
                strokeWidth = cache.gridStroke,
            )
            drawSegmentLine(chartPoints, palette.line, styleCache.lineStroke, cap = StrokeCap.Butt)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.VerticalGradientArea -> {
            drawCachedFill(styleCache.areaPath, styleCache.fillBrush!!)
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.CandySegments -> {
            drawCachedCandySegments(cache.values, chartPoints, palette, styleCache.lineStroke * 1.25f)
            drawCachedSignDots(cache.values, chartPoints, palette, styleCache.pointRadius * 1.15f)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.Mountain -> {
            drawCachedFill(styleCache.areaPath, styleCache.fillBrush!!)
            drawCachedPath(styleCache.linePath, palette.line, styleCache.primaryPathStroke!!)
            drawCachedMarker(marker, palette.glow, glowPaint, styleCache.markerRadius)
        }

        ChartStyle.Ribbon -> {
            drawCachedPath(styleCache.linePath, styleCache.ribbonColor, styleCache.primaryPathStroke!!)
            drawCachedPath(styleCache.linePath, palette.line, styleCache.secondaryPathStroke!!)
            drawCachedDots(chartPoints, palette.line, styleCache.pointRadius)
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

private fun DrawScope.drawCachedFill(
    path: Path,
    brush: Brush,
) {
    drawPath(path = path, brush = brush)
}

private fun DrawScope.drawCachedPath(
    path: Path,
    brush: Brush,
    stroke: Stroke,
) {
    drawPath(path = path, brush = brush, style = stroke)
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

private fun DrawScope.drawCachedCandySegments(
    values: List<Float>,
    points: List<Offset>,
    palette: BalanceTrendChartPalette,
    strokeWidth: Float,
) {
    for (index in 0 until points.lastIndex) {
        val value = values.getOrNull(index + 1) ?: values.getOrElse(index) { 0f }
        drawLine(
            color = if (value >= 0f) palette.income else palette.expense,
            start = points[index],
            end = points[index + 1],
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCachedSignDots(
    values: List<Float>,
    points: List<Offset>,
    palette: BalanceTrendChartPalette,
    radius: Float,
) {
    for (index in points.indices) {
        val color = if ((values.getOrNull(index) ?: 0f) >= 0f) palette.income else palette.expense
        drawCircle(color = color, radius = radius, center = points[index])
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

private fun DrawScope.drawBalanceTrendChartStyle(
    style: ChartStyle,
    values: List<Float>,
    geometry: BalanceTrendChartGeometry,
    chartHeight: Float,
    verticalPadding: Float,
    palette: BalanceTrendChartPalette,
    cache: BalanceTrendChartDrawingCache,
    glowPaint: Paint,
    styleDash: PathEffect,
) {
    val chartPoints = geometry.points
    if (chartPoints.isEmpty()) return

    val baseline = baselineY(values, geometry, chartHeight, verticalPadding)
    val lineStroke = Spacing.trendChartLineStrokeWidth.toPx()
    val pointRadius = Spacing.trendChartPointRadius.toPx()

    when (style) {
        ChartStyle.NeonLine -> {
            drawSegmentLine(chartPoints, palette.line, lineStroke)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.NeonArea -> {
            drawArea(chartPoints, baseline, palette.line, cache.areaPath, alpha = 0.16f)
            drawSegmentLine(chartPoints, palette.line, lineStroke)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.SmoothLine -> {
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath, smooth = true)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.SmoothArea -> {
            // Neon-wave look (ChartWave, 02_neon-core-charts.jsx): smooth area filled with an
            // accent gradient (accent@0.4 → 0), a glowing 2.4dp smooth line, small accent dots, and
            // a larger/lighter last dot.
            val waveColor = if (palette.accentLine) palette.accent else palette.line
            drawWave(
                points = chartPoints,
                baseline = baseline,
                color = waveColor,
                areaPath = cache.areaPath,
                linePath = cache.linePath,
                lineStroke = lineStroke,
                pointRadius = pointRadius,
            )
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.SteppedLine -> {
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath, stepped = true)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.SteppedArea -> {
            drawArea(chartPoints, baseline, palette.line, cache.areaPath, stepped = true, alpha = 0.15f)
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath, stepped = true)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.Bars -> {
            drawBars(chartPoints, baseline, palette.line, rounded = false)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.RoundedBars -> {
            drawBars(chartPoints, baseline, palette.line, rounded = true)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.DotsLine -> {
            drawSegmentLine(chartPoints, palette.line, lineStroke)
            drawDots(chartPoints, palette.line, pointRadius * 1.35f)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.DotsOnly -> {
            drawDots(chartPoints, palette.line, pointRadius * 1.55f)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.GradientStroke -> {
            drawGradientPathLine(chartPoints, cache.linePath, palette, lineStroke * 1.4f)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.DualGlow -> {
            cache.linePath.buildPolyline(chartPoints)
            drawPath(
                path = cache.linePath,
                color = palette.line.copy(alpha = 0.12f),
                style = Stroke(width = lineStroke * 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path = cache.linePath,
                color = palette.glow.copy(alpha = 0.26f),
                style = Stroke(width = lineStroke * 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.DashedLine -> {
            drawPathLine(
                points = chartPoints,
                color = palette.line,
                strokeWidth = lineStroke,
                path = cache.linePath,
                pathEffect = styleDash,
            )
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.ThinMinimal -> {
            drawPathLine(chartPoints, palette.line, lineStroke * 0.6f, cache.linePath)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.ThickBold -> {
            drawPathLine(chartPoints, palette.line, lineStroke * 2.2f, cache.linePath)
            drawDots(chartPoints, palette.line, pointRadius * 1.25f)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.BaselineFill -> {
            drawSplitBaselineArea(values, chartPoints, baseline, palette, cache)
            drawSegmentLine(chartPoints, palette.line, lineStroke, cap = StrokeCap.Butt)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.VerticalGradientArea -> {
            drawVerticalGradientArea(chartPoints, baseline, palette.line, cache.areaPath)
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.CandySegments -> {
            drawCandySegments(values, chartPoints, palette, lineStroke * 1.25f)
            drawSignDots(values, chartPoints, palette, pointRadius * 1.15f)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.Mountain -> {
            drawVerticalGradientArea(chartPoints, baseline, palette.line, cache.areaPath, smooth = true)
            drawPathLine(chartPoints, palette.line, lineStroke * 1.6f, cache.linePath, smooth = true)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }

        ChartStyle.Ribbon -> {
            cache.linePath.buildSmooth(chartPoints)
            drawPath(
                path = cache.linePath,
                color = palette.line.copy(alpha = 0.2f),
                style = Stroke(width = lineStroke * 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath, smooth = true)
            drawDots(chartPoints, palette.line, pointRadius)
            drawMarker(geometry.marker, palette.glow, glowPaint)
        }
    }
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

private fun DrawScope.drawPathLine(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    path: Path,
    smooth: Boolean = false,
    stepped: Boolean = false,
    pathEffect: PathEffect? = null,
) {
    if (points.size < 2) return
    when {
        smooth -> path.buildSmooth(points)
        stepped -> path.buildStepped(points)
        else -> path.buildPolyline(points)
    }
    drawPath(
        path = path,
        color = color,
        style =
            Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = pathEffect,
            ),
    )
}

private fun DrawScope.drawGradientPathLine(
    points: List<Offset>,
    path: Path,
    palette: BalanceTrendChartPalette,
    strokeWidth: Float,
) {
    if (points.size < 2) return
    path.buildPolyline(points)
    drawPath(
        path = path,
        brush =
            Brush.horizontalGradient(
                colors = listOf(palette.income, palette.line, palette.expense),
                startX = 0f,
                endX = size.width,
            ),
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawArea(
    points: List<Offset>,
    baseline: Float,
    color: Color,
    path: Path,
    smooth: Boolean = false,
    stepped: Boolean = false,
    alpha: Float,
) {
    path.buildArea(points, baseline, smooth, stepped)
    drawPath(path = path, color = color.copy(alpha = alpha))
}

private fun DrawScope.drawVerticalGradientArea(
    points: List<Offset>,
    baseline: Float,
    color: Color,
    path: Path,
    smooth: Boolean = false,
) {
    path.buildArea(points, baseline, smooth = smooth, stepped = false)
    drawPath(
        path = path,
        brush =
            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.36f), color.copy(alpha = 0.04f)),
                startY = 0f,
                endY = baseline,
            ),
    )
}

// Neon-wave renderer (ChartWave). Order matters: gradient area first, then a soft wide glow stroke
// under the crisp line, then the line, then dots. The last dot is drawn larger and lighter to echo
// the highlighted "today" point in the mockup.
private fun DrawScope.drawWave(
    points: List<Offset>,
    baseline: Float,
    color: Color,
    areaPath: Path,
    linePath: Path,
    lineStroke: Float,
    pointRadius: Float,
) {
    if (points.isEmpty()) return

    areaPath.buildArea(points, baseline, smooth = true, stepped = false)
    drawPath(
        path = areaPath,
        brush =
            Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0f)),
                startY = 0f,
                endY = baseline,
            ),
    )

    if (points.size >= 2) {
        // Soft neon halo: a wide, very translucent copy of the same smooth line.
        linePath.buildSmooth(points)
        drawPath(
            path = linePath,
            color = color.copy(alpha = 0.22f),
            style = Stroke(width = lineStroke * 3.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // Crisp accent line — ~2.4dp (2dp base × 1.2).
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = lineStroke * 1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }

    points.forEachIndexed { index, point ->
        if (index == points.lastIndex) {
            // Larger, lighter "today" dot.
            drawCircle(color = lightenColor(color, 1.4f), radius = pointRadius * 1.33f, center = point)
        } else {
            drawCircle(color = color, radius = pointRadius * 0.87f, center = point)
        }
    }
}

// Mockup lighten(c, f) for f>1: move each channel toward white by (f-1).
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

private fun DrawScope.drawSplitBaselineArea(
    values: List<Float>,
    points: List<Offset>,
    baseline: Float,
    palette: BalanceTrendChartPalette,
    cache: BalanceTrendChartDrawingCache,
) {
    val fillColor =
        if ((values.lastOrNull() ?: 0f) >= 0f) {
            palette.income
        } else {
            palette.expense
        }
    drawArea(points, baseline, fillColor, cache.areaPath, alpha = 0.18f)
    drawLine(
        color = fillColor.copy(alpha = 0.42f),
        start = Offset(0f, baseline),
        end = Offset(size.width, baseline),
        strokeWidth = Spacing.trendChartGridLineStrokeWidth.toPx(),
    )
}

private fun DrawScope.drawBars(
    points: List<Offset>,
    baseline: Float,
    color: Color,
    rounded: Boolean,
) {
    if (points.isEmpty()) return
    val width = barWidth(points)
    val radius = if (rounded) width / 2f else 0f
    points.forEach { point ->
        val left = (point.x - width / 2f).coerceIn(0f, (size.width - width).coerceAtLeast(0f))
        val top = minOf(point.y, baseline)
        val height = maxOf(kotlin.math.abs(point.y - baseline), 1f)
        drawRoundRect(
            color = color.copy(alpha = 0.86f),
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

private fun DrawScope.barWidth(points: List<Offset>): Float {
    if (points.size == 1) return size.width * 0.18f
    var minimumStep = Float.MAX_VALUE
    for (index in 0 until points.lastIndex) {
        minimumStep = minOf(minimumStep, kotlin.math.abs(points[index + 1].x - points[index].x))
    }
    return (minimumStep * 0.52f).coerceAtLeast(1f)
}

private fun DrawScope.drawDots(
    points: List<Offset>,
    color: Color,
    radius: Float,
) {
    points.forEach { point ->
        drawCircle(color = color, radius = radius, center = point)
    }
}

private fun DrawScope.drawSignDots(
    values: List<Float>,
    points: List<Offset>,
    palette: BalanceTrendChartPalette,
    radius: Float,
) {
    points.forEachIndexed { index, point ->
        val color = if ((values.getOrNull(index) ?: 0f) >= 0f) palette.income else palette.expense
        drawCircle(color = color, radius = radius, center = point)
    }
}

private fun DrawScope.drawCandySegments(
    values: List<Float>,
    points: List<Offset>,
    palette: BalanceTrendChartPalette,
    strokeWidth: Float,
) {
    for (index in 0 until points.lastIndex) {
        val value = values.getOrNull(index + 1) ?: values.getOrElse(index) { 0f }
        drawLine(
            color = if (value >= 0f) palette.income else palette.expense,
            start = points[index],
            end = points[index + 1],
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMarker(
    marker: Offset?,
    color: Color,
    glowPaint: Paint,
) {
    marker ?: return
    val markerRadius = Spacing.trendChartMarkerRadius.toPx()
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawCircle(
            marker.x,
            marker.y,
            markerRadius,
            glowPaint.asFrameworkPaint(),
        )
    }
    drawCircle(color = color, radius = markerRadius, center = marker)
}

private fun Path.buildPolyline(points: List<Offset>) {
    reset()
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    for (index in 1 until points.size) {
        lineTo(points[index].x, points[index].y)
    }
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

private fun Path.buildStepped(points: List<Offset>) {
    reset()
    if (points.isEmpty()) return
    moveTo(points.first().x, points.first().y)
    for (index in 0 until points.lastIndex) {
        val current = points[index]
        val next = points[index + 1]
        lineTo(next.x, current.y)
        lineTo(next.x, next.y)
    }
}

private fun Path.buildArea(
    points: List<Offset>,
    baseline: Float,
    smooth: Boolean,
    stepped: Boolean,
) {
    reset()
    if (points.isEmpty()) return
    moveTo(points.first().x, baseline)
    lineTo(points.first().x, points.first().y)
    when {
        smooth -> {
            for (index in 0 until points.lastIndex) {
                val current = points[index]
                val next = points[index + 1]
                val midpointX = (current.x + next.x) / 2f
                cubicTo(midpointX, current.y, midpointX, next.y, next.x, next.y)
            }
        }

        stepped -> {
            for (index in 0 until points.lastIndex) {
                val current = points[index]
                val next = points[index + 1]
                lineTo(next.x, current.y)
                lineTo(next.x, next.y)
            }
        }

        else -> {
            for (index in 1 until points.size) {
                lineTo(points[index].x, points[index].y)
            }
        }
    }
    lineTo(points.last().x, baseline)
    close()
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
