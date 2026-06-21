package com.kshavrin.mymoney.core.designsystem.chart

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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

private class BalanceTrendChartDrawingCache {
    val linePath = Path()
    val areaPath = Path()
}

private data class BalanceTrendChartPalette(
    val line: Color,
    val income: Color,
    val expense: Color,
    val glow: Color,
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

@Composable
fun BalanceTrendChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    showGridlines: Boolean = true,
    showLabels: Boolean = false,
    colorRule: ChartColorRule = ChartColorRule.Default,
    style: ChartStyle = ChartStyle.Default,
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
    val palette =
        BalanceTrendChartPalette(
            line = lineColor,
            income = incomeColor,
            expense = expenseColor,
            glow = glowColor,
        )
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = gridColor)

    val height = Spacing.trendChartDefaultHeight
    val labelHeight = Spacing.trendChartLabelHeight
    val totalHeight = if (showLabels) height + labelHeight else height

    val density = LocalDensity.current
    val markerGlowRadius = Spacing.trendChartMarkerGlowRadius
    val textMeasurer = rememberTextMeasurer()
    val drawingCache = remember { BalanceTrendChartDrawingCache() }
    val zeroLineDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) }
    val styleDash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 9f)) }

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
                pathEffect = zeroLineDash,
            )
        }

        drawBalanceTrendChartStyle(
            style = style,
            values = points,
            geometry = geometry,
            chartHeight = chartHeightPx,
            verticalPadding = Spacing.trendChartVerticalPadding.toPx(),
            palette = palette,
            cache = drawingCache,
            glowPaint = glowPaint,
            styleDash = styleDash,
        )

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
        ChartStyle.NeonLine,
        ChartStyle.Line,
        -> {
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
            drawArea(chartPoints, baseline, palette.line, cache.areaPath, smooth = true, alpha = 0.16f)
            drawPathLine(chartPoints, palette.line, lineStroke, cache.linePath, smooth = true)
            drawDots(chartPoints, palette.line, pointRadius)
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
