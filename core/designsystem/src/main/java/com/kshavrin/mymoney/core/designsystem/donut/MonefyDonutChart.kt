package com.kshavrin.mymoney.core.designsystem.donut

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.dashboardCalloutLabel
import com.kshavrin.mymoney.core.ui.theme.dashboardCalloutPercentage
import com.kshavrin.mymoney.core.ui.theme.dashboardDonutCenterDivider
import com.kshavrin.mymoney.core.ui.theme.dashboardDonutCenterTotal
import com.kshavrin.mymoney.core.ui.theme.dashboardDonutLeaderLine
import java.math.BigDecimal
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val DEFAULT_LABEL_MIN_FRACTION = 0.03f

enum class DonutStyle { Flat, Extrude }

@Composable
fun MonefyDonutChart(
    income: BigDecimal,
    expense: BigDecimal,
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    currencySymbol: String = "",
    decimalDigits: Int = 2,
    emptyStateIcons: List<CategorySlice> = emptyList(),
    onSliceClick: ((CategorySlice) -> Unit)? = null,
    onEmptyCategoryClick: ((CategorySlice) -> Unit)? = null,
    animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.7f, stiffness = 300f),
    outerRadiusFraction: Float = 0.75f,
    ringThicknessFraction: Float = 0.39f,
    sliceGapDegrees: Float = 5f,
    iconScale: Float = 1.7f,
    centerDecimalDigits: Int = decimalDigits,
    style: DonutStyle = DonutStyle.Extrude,
    explodedOffset: Dp = Spacing.none,
    centerTextStyle: TextStyle = MaterialTheme.typography.dashboardDonutCenterTotal,
    centerDividerColor: Color = MaterialTheme.colorScheme.dashboardDonutCenterDivider,
    centerDividerWidth: Dp = Spacing.dashboardDonutCenterDividerWidth,
    centerDividerThickness: Dp = Spacing.dashboardDonutCenterDividerThickness,
    calloutIconSize: Dp = Spacing.dashboardDonutCalloutIconSize,
    calloutLabelStyle: TextStyle = MaterialTheme.typography.dashboardCalloutLabel,
    calloutPercentageStyle: TextStyle = MaterialTheme.typography.dashboardCalloutPercentage,
    calloutLabelColor: Color = MaterialTheme.colorScheme.dashboardCalloutLabel,
    leaderLineColor: Color = MaterialTheme.colorScheme.dashboardDonutLeaderLine,
    leaderLineThickness: Dp = Spacing.dashboardDonutLeaderLineThickness,
    labelMinFraction: Float = DEFAULT_LABEL_MIN_FRACTION,
    showCategoryLabels: Boolean = false,
) {
    val arcs = remember(slices) { DonutGeometry.computeSliceArcs(slices) }
    val animationKey = slices.map { it.categoryId to it.fraction }
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val iconPainters = (slices + emptyStateIcons)
        .distinctBy { it.iconKey }
        .associate { it.iconKey to rememberVectorPainter(categoryIcon(it.iconKey)) }

    LaunchedEffect(animationKey) {
        progress.snapTo(0f)
        progress.animateTo(targetValue = 1f, animationSpec = animationSpec)
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val budgetAlertColor = MaterialTheme.colorScheme.error
    val badgeBorderColor = MaterialTheme.colorScheme.surface
    val incomeColor = MaterialTheme.colorScheme.secondary
    val expenseColor = MaterialTheme.colorScheme.tertiary
    val discColor = MaterialTheme.colorScheme.background

    val locale = LocalConfiguration.current.locales[0]
    val incomeText = MoneyFormatter.format(
        amount = income,
        currencySymbol = currencySymbol,
        decimalDigits = centerDecimalDigits,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )
    val expenseText = MoneyFormatter.format(
        amount = expense,
        currencySymbol = currencySymbol,
        decimalDigits = centerDecimalDigits,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )

    val chartHeader = stringResource(
        R.string.donut_chart_cd,
        income.toPlainString(),
        expense.toPlainString(),
    )
    val sliceTemplate = stringResource(R.string.donut_chart_slice)
    val budgetAlertLabel = stringResource(R.string.donut_chart_budget_alert)
    val chartDescription = remember(chartHeader, sliceTemplate, budgetAlertLabel, slices) {
        val sliceText = slices.joinToString(separator = " ") { slice ->
            val description = String.format(sliceTemplate, slice.label, (slice.fraction * 100f).toInt())
            if (slice.hasBudgetAlert) "$description, $budgetAlertLabel" else description
        }
        if (sliceText.isEmpty()) chartHeader else "$chartHeader $sliceText"
    }

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = chartDescription
        },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(
                    arcs,
                    emptyStateIcons,
                    outerRadiusFraction,
                    ringThicknessFraction,
                    sliceGapDegrees,
                    iconScale,
                    explodedOffset,
                    onSliceClick,
                    onEmptyCategoryClick,
                ) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = min(size.width, size.height) / 2f * outerRadiusFraction
                        if (arcs.isEmpty()) {
                            val iconSize = (26f * iconScale).roundToInt().dp.toPx()
                            val discRadius = (iconSize + 12.dp.toPx()) / 2f
                            val hit = emptyStateIcons.firstOrNullIndexed { index, _ ->
                                val slot = emptyIconSlot(
                                    center = center,
                                    outerRadius = outerRadius,
                                    index = index,
                                    count = emptyStateIcons.size,
                                )
                                hypot(offset.x - slot.x, offset.y - slot.y) <= discRadius
                            }
                            if (hit != null) onEmptyCategoryClick?.invoke(hit)
                            return@detectTapGestures
                        }
                        val strokeWidth = outerRadius * ringThicknessFraction
                        val innerRadius = outerRadius - strokeWidth
                        val explodedOffsetPx = explodedOffset.toPx()
                        val hit = DonutGeometry.hitTest(
                            offsetX = offset.x,
                            offsetY = offset.y,
                            centerX = center.x,
                            centerY = center.y,
                            innerRadius = innerRadius,
                            outerRadius = outerRadius,
                            arcs = arcs,
                            sliceGapDegrees = sliceGapDegrees,
                            explodedOffset = explodedOffsetPx,
                        )
                        if (hit != null) onSliceClick?.invoke(hit)
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = min(size.width, size.height) / 2f * outerRadiusFraction
            val th = outerRadius * ringThicknessFraction
            val r = outerRadius - th / 2f
            val explodedOffsetPx = explodedOffset.toPx()

            val iconSize = calloutIconSize.toPx() * (iconScale / 1.7f)
            val discDiameter = iconSize + Spacing.m.toPx()
            val labelHeight = discDiameter + Spacing.xl.toPx() + Spacing.m.toPx()
            val frame = computeIconFrame(
                width = size.width,
                height = size.height,
                discDiameter = discDiameter,
                labelHeight = labelHeight,
                density = this,
            )

            if (slices.isEmpty()) {
                drawCircle(
                    color = outlineColor,
                    radius = r,
                    center = center,
                    style = Stroke(width = th),
                )
                emptyStateIcons.forEachIndexed { index, slice ->
                    val slot = emptyIconSlot(
                        center = center,
                        outerRadius = outerRadius,
                        index = index,
                        count = emptyStateIcons.size,
                    )
                    drawIconDisc(
                        slotCenter = slot,
                        discDiameter = discDiameter,
                        iconSize = iconSize,
                        discColor = discColor,
                        tintColor = slice.color,
                        iconPainter = iconPainters[slice.iconKey],
                    )
                }
                drawCenterTotals(
                    center = center,
                    incomeText = incomeText,
                    expenseText = expenseText,
                    incomeColor = incomeColor,
                    expenseColor = expenseColor,
                    textMeasurer = textMeasurer,
                    innerRadius = outerRadius - th,
                    textStyle = centerTextStyle,
                    dividerColor = centerDividerColor,
                    dividerWidth = centerDividerWidth.toPx(),
                    dividerThickness = centerDividerThickness.toPx(),
                )
                return@Canvas
            }

            val placed = layoutSlices(arcs, frame)

            placed.forEach { p ->
                if (p.slice.fraction <= 0f) return@forEach
                val offset = p.explodedOffset(explodedOffsetPx)
                drawLine(
                    color = leaderLineColor,
                    start = Offset(
                        center.x + offset.x + (outerRadius + leaderLineThickness.toPx()) * cos(p.midRadians),
                        center.y + offset.y + (outerRadius + leaderLineThickness.toPx()) * sin(p.midRadians),
                    ),
                    end = Offset(center.x + p.frameX, center.y + p.frameY),
                    strokeWidth = leaderLineThickness.toPx(),
                )
            }

            val gappedArcs = placed.map { p ->
                val animatedSweep = p.sweepDegrees * progress.value
                val gap = DonutGeometry.gapForSweep(p.sweepDegrees, sliceGapDegrees)
                val offset = p.explodedOffset(explodedOffsetPx)
                GappedArc(
                    color = p.slice.color,
                    startAngle = p.startAngleDegrees + gap / 2f,
                    sweepAngle = (animatedSweep - gap).coerceAtLeast(0f),
                    offset = offset,
                )
            }

            if (style == DonutStyle.Extrude) {
                drawExtrudedRing(center = center, radius = r, th = th, arcs = gappedArcs)
            } else {
                drawFlatRing(center = center, radius = r, th = th, arcs = gappedArcs)
            }

            placed.forEach { p ->
                if (progress.value < 1f || p.slice.fraction <= 0f) return@forEach
                val slot = Offset(center.x + p.frameX, center.y + p.frameY)
                val iconCenter = drawIconDisc(
                    slotCenter = slot,
                    discDiameter = discDiameter,
                    iconSize = iconSize,
                    discColor = discColor,
                    tintColor = p.slice.color,
                    iconPainter = iconPainters[p.slice.iconKey],
                )
                if (p.slice.hasBudgetAlert) {
                    val badgeCenter = Offset(
                        x = iconCenter.x + iconSize * 0.35f,
                        y = iconCenter.y - iconSize * 0.35f,
                    )
                    drawCircle(color = badgeBorderColor, radius = 5.dp.toPx(), center = badgeCenter)
                    drawCircle(color = budgetAlertColor, radius = 3.5.dp.toPx(), center = badgeCenter)
                }
                if (p.slice.fraction >= labelMinFraction) {
                    val labelText = "${(p.slice.fraction * 100f).roundToInt()}%"
                    drawCalloutText(
                        slot = slot,
                        discDiameter = discDiameter,
                        label = p.slice.label.takeIf { showCategoryLabels },
                        percentage = labelText,
                        sliceColor = p.slice.color,
                        labelColor = calloutLabelColor,
                        labelStyle = calloutLabelStyle,
                        percentageStyle = calloutPercentageStyle,
                        textMeasurer = textMeasurer,
                    )
                }
            }

            drawCenterTotals(
                center = center,
                incomeText = incomeText,
                expenseText = expenseText,
                incomeColor = incomeColor,
                expenseColor = expenseColor,
                textMeasurer = textMeasurer,
                innerRadius = outerRadius - th,
                textStyle = centerTextStyle,
                dividerColor = centerDividerColor,
                dividerWidth = centerDividerWidth.toPx(),
                dividerThickness = centerDividerThickness.toPx(),
            )
        }
    }
}

private data class IconFrame(val halfWidth: Float, val halfTop: Float, val halfBottom: Float)

private data class PlacedSlice(
    val slice: CategorySlice,
    val startAngleDegrees: Float,
    val sweepDegrees: Float,
    val midRadians: Float,
    val frameX: Float,
    val frameY: Float,
)

private data class GappedArc(
    val color: Color,
    val startAngle: Float,
    val sweepAngle: Float,
    val offset: Offset = Offset.Zero,
)

private inline fun <T> List<T>.firstOrNullIndexed(predicate: (Int, T) -> Boolean): T? {
    forEachIndexed { index, value ->
        if (predicate(index, value)) return value
    }
    return null
}

private fun DrawScope.drawCenterTotals(
    center: Offset,
    incomeText: String,
    expenseText: String,
    incomeColor: Color,
    expenseColor: Color,
    textMeasurer: TextMeasurer,
    innerRadius: Float,
    textStyle: TextStyle,
    dividerColor: Color,
    dividerWidth: Float,
    dividerThickness: Float,
) {
    if (innerRadius <= 0f) return
    val lineGap = Spacing.s.toPx()
    val baseIncome = textMeasurer.measure(text = incomeText, style = textStyle)
    val baseExpense = textMeasurer.measure(text = expenseText, style = textStyle)
    val maxLineWidth = max(baseIncome.size.width, baseExpense.size.width).toFloat()
    val totalBaseHeight = baseIncome.size.height + lineGap * 2f + dividerThickness + baseExpense.size.height
    if (maxLineWidth <= 0f || totalBaseHeight <= 0f) return
    val targetW = innerRadius * 2f * 0.92f
    val targetH = innerRadius * 2f * 0.76f
    val scale = min(min(targetW / maxLineWidth, targetH / totalBaseHeight), 1f)
    val scaledStyle = textStyle.copy(fontSize = textStyle.fontSize * scale)
    val incomeLayout = textMeasurer.measure(text = incomeText, style = scaledStyle)
    val expenseLayout = textMeasurer.measure(text = expenseText, style = scaledStyle)
    val totalHeight = incomeLayout.size.height + lineGap * 2f + dividerThickness + expenseLayout.size.height
    val incomeTop = center.y - totalHeight / 2f
    val dividerY = incomeTop + incomeLayout.size.height + lineGap
    val expenseTop = dividerY + dividerThickness + lineGap
    drawText(
        textLayoutResult = incomeLayout,
        color = incomeColor,
        topLeft = Offset(center.x - incomeLayout.size.width / 2f, incomeTop),
    )
    drawLine(
        color = dividerColor,
        start = Offset(center.x - dividerWidth / 2f, dividerY),
        end = Offset(center.x + dividerWidth / 2f, dividerY),
        strokeWidth = dividerThickness,
    )
    drawText(
        textLayoutResult = expenseLayout,
        color = expenseColor,
        topLeft = Offset(center.x - expenseLayout.size.width / 2f, expenseTop),
    )
}

private fun DrawScope.drawCalloutText(
    slot: Offset,
    discDiameter: Float,
    label: String?,
    percentage: String,
    sliceColor: Color,
    labelColor: Color,
    labelStyle: TextStyle,
    percentageStyle: TextStyle,
    textMeasurer: TextMeasurer,
) {
    val maxTextWidth = discDiameter * 2.3f
    val labelLayout = label?.let {
        textMeasurer.measure(
            text = it,
            style = labelStyle.copy(color = labelColor, textAlign = TextAlign.Center),
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            constraints = Constraints(maxWidth = maxTextWidth.roundToInt()),
        )
    }
    val percentageLayout = textMeasurer.measure(
        text = percentage,
        style = percentageStyle.copy(color = sliceColor, textAlign = TextAlign.Center),
        maxLines = 1,
        constraints = Constraints(maxWidth = maxTextWidth.roundToInt()),
    )
    val labelTop = slot.y + discDiameter / 2f + Spacing.xxs.toPx()
    if (labelLayout != null) {
        drawTextCentered(labelLayout, slot.x, labelTop, labelColor)
    }
    val percentageTop = labelTop + (labelLayout?.size?.height ?: 0) + Spacing.xxs.toPx()
    drawTextCentered(percentageLayout, slot.x, percentageTop, sliceColor)
}

private fun DrawScope.drawTextCentered(
    layout: TextLayoutResult,
    centerX: Float,
    top: Float,
    color: Color,
) {
    drawText(
        textLayoutResult = layout,
        color = color,
        topLeft = Offset(centerX - layout.size.width / 2f, top),
    )
}

private fun emptyIconSlot(
    center: Offset,
    outerRadius: Float,
    index: Int,
    count: Int,
): Offset {
    val angleDegrees = -90f + index * (360f / count)
    val angleRadians = Math.toRadians(angleDegrees.toDouble()).toFloat()
    return Offset(
        center.x + outerRadius * 0.92f * cos(angleRadians),
        center.y + outerRadius * 0.92f * sin(angleRadians),
    )
}

private fun computeIconFrame(
    width: Float,
    height: Float,
    discDiameter: Float,
    labelHeight: Float,
    density: DrawScope,
): IconFrame {
    val halfHeight = height / 2f
    val pad4 = with(density) { 4.dp.toPx() }
    val halfWidth = width / 2f - discDiameter / 2f - pad4
    val halfSide = halfHeight - discDiameter / 2f - labelHeight - pad4
    return IconFrame(
        halfWidth = max(halfWidth, 0f),
        halfTop = max(halfSide, 0f),
        halfBottom = max(halfSide, 0f),
    )
}

private fun layoutSlices(arcs: List<SliceArc>, frame: IconFrame): List<PlacedSlice> {
    val count = arcs.size
    return arcs.mapIndexed { index, arc ->
        val mid = DonutGeometry.midAngleRadians(arc)
        val point = DonutGeometry.framePoint(
            t = index.toFloat() / count,
            hw = frame.halfWidth,
            hhTop = frame.halfTop,
            hhBot = frame.halfBottom,
        )
        PlacedSlice(
            slice = arc.slice,
            startAngleDegrees = arc.startAngleDegrees,
            sweepDegrees = arc.sweepDegrees,
            midRadians = mid,
            frameX = point.x,
            frameY = point.y,
        )
    }
}

private fun shade(color: Color, factor: Float): Color {
    val target = if (factor < 0f) 0f else 1f
    val p = kotlin.math.abs(factor)
    fun mix(channel: Float) = (target - channel) * p + channel
    return Color(
        red = mix(color.red),
        green = mix(color.green),
        blue = mix(color.blue),
        alpha = color.alpha,
    )
}

private fun DrawScope.drawArcBand(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    arc: GappedArc,
    dy: Float = 0f,
) {
    if (arc.sweepAngle <= 0f) return
    val arcCenter = center + arc.offset
    drawArc(
        color = color,
        startAngle = arc.startAngle,
        sweepAngle = arc.sweepAngle,
        useCenter = false,
        topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius + dy),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = StrokeCap.Butt),
    )
}

private fun DrawScope.drawFlatRing(
    center: Offset,
    radius: Float,
    th: Float,
    arcs: List<GappedArc>,
) {
    arcs.forEach { drawArcBand(center, radius, th, it.color, it) }
}

private fun DrawScope.drawExtrudedRing(
    center: Offset,
    radius: Float,
    th: Float,
    arcs: List<GappedArc>,
) {
    val castColor = Color(red = 35f / 255f, green = 60f / 255f, blue = 48f / 255f, alpha = 0.28f)
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = castColor
            style = PaintingStyle.Stroke
            strokeWidth = th * 0.92f
            asFrameworkPaint().maskFilter =
                BlurMaskFilter(7.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawCircle(Offset(center.x, center.y + th * 0.95f), radius, paint)
    }

    val depth = (th * 0.62f).roundToInt().coerceIn(7, 22)
    val wallColor = arcs.map { shade(it.color, -0.40f) }
    for (k in depth downTo 1) {
        arcs.forEachIndexed { i, arc ->
            drawArcBand(center, radius, th, wallColor[i], arc, dy = k.toFloat())
        }
    }

    arcs.forEach { drawArcBand(center, radius, th, it.color, it) }

    clipRect(
        left = center.x - radius - th,
        top = center.y - radius - th,
        right = center.x + radius + th,
        bottom = center.y - th * 0.75f,
        clipOp = ClipOp.Intersect,
    ) {
        arcs.forEach {
            drawArcBand(
                center,
                radius + th * 0.5f - 1.2f,
                2.2f,
                Color.White.copy(alpha = 0.40f),
                it,
            )
        }
    }
    arcs.forEach {
        drawArcBand(
            center,
            radius - th * 0.5f + 1f,
            1.6f,
            Color.Black.copy(alpha = 0.18f),
            it,
        )
    }
}

private fun DrawScope.drawIconDisc(
    slotCenter: Offset,
    discDiameter: Float,
    iconSize: Float,
    discColor: Color,
    tintColor: Color,
    iconPainter: VectorPainter?,
): Offset {
    drawCircle(color = discColor, radius = discDiameter / 2f, center = slotCenter)
    if (iconPainter != null) {
        translate(
            left = slotCenter.x - iconSize / 2f,
            top = slotCenter.y - iconSize / 2f,
        ) {
            with(iconPainter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(tintColor),
                )
            }
        }
    }
    return slotCenter
}

private fun PlacedSlice.explodedOffset(distance: Float): Offset = Offset(
    x = distance * cos(midRadians),
    y = distance * sin(midRadians),
)
