package com.kshavrin.mymoney.core.designsystem.donut

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private const val CALLOUT_LABEL_MIN_SP = 10f

enum class DonutStyle { Flat, Extrude }

/**
 * Stable, value-equal animation key derived from the visual identity of the slice set.
 *
 * The donut entry animation must restart only when the rendered set of slices actually
 * changes — i.e. a different category id or a different fraction. A balance recompute that
 * yields the same `(categoryId, fraction)` set (a new `List` instance with identical content)
 * must NOT restart the animation, otherwise the donut visibly re-grows on every unrelated
 * table write. Deriving the key here (rather than keying `LaunchedEffect` on the raw list)
 * makes that invariant explicit and unit-testable.
 */
internal data class DonutAnimationKey(
    private val ids: List<Long>,
    private val fractions: List<Float>,
)

internal fun donutAnimationKey(slices: List<CategorySlice>): DonutAnimationKey =
    DonutAnimationKey(
        ids = slices.map { it.categoryId },
        fractions = slices.map { it.fraction },
    )

/** Reusable, allocation-free draw objects for the extruded ring's cast shadow. */
private class ExtrudedRingPaints(
    blurRadiusPx: Float,
) {
    val shadowPaint: Paint =
        Paint().apply {
            style = PaintingStyle.Stroke
            asFrameworkPaint().maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
}

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
    leaderLineColor: Color = MaterialTheme.colorScheme.dashboardDonutLeaderLine,
    leaderLineThickness: Dp = Spacing.dashboardDonutLeaderLineThickness,
    labelMinFraction: Float = DEFAULT_LABEL_MIN_FRACTION,
    showCategoryLabels: Boolean = false,
) {
    val arcs = remember(slices) { DonutGeometry.computeSliceArcs(slices) }
    val animationKey = remember(slices) { donutAnimationKey(slices) }
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val extrudedRingPaints =
        remember(density) {
            ExtrudedRingPaints(blurRadiusPx = with(density) { 7.dp.toPx() })
        }
    val iconPainters =
        (slices + emptyStateIcons)
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

    val locale = LocalConfiguration.current.locales[0]
    val incomeText =
        MoneyFormatter.format(
            amount = income,
            currencySymbol = currencySymbol,
            decimalDigits = centerDecimalDigits,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )
    val expenseText =
        MoneyFormatter.format(
            amount = expense,
            currencySymbol = currencySymbol,
            decimalDigits = centerDecimalDigits,
            locale = locale,
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )

    val chartHeader =
        stringResource(
            R.string.donut_chart_cd,
            income.toPlainString(),
            expense.toPlainString(),
        )
    val sliceTemplate = stringResource(R.string.donut_chart_slice)
    val budgetAlertLabel = stringResource(R.string.donut_chart_budget_alert)
    val chartDescription =
        remember(chartHeader, sliceTemplate, budgetAlertLabel, labelMinFraction, slices) {
            val sliceText =
                slices
                    .asSequence()
                    .filter { it.fraction > 0f && it.fraction >= labelMinFraction }
                    .joinToString(separator = " ") { slice ->
                        val description =
                            String.format(sliceTemplate, slice.label, (slice.fraction * 100f).roundToInt())
                        if (slice.hasBudgetAlert) "$description, $budgetAlertLabel" else description
                    }
            if (sliceText.isEmpty()) chartHeader else "$chartHeader $sliceText"
        }

    val openCategoryActionLabel = stringResource(R.string.donut_chart_open_category_action)

    Box(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        contentDescription = chartDescription
                    },
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewConfiguration = LocalViewConfiguration.current
                val iconSizeDp = calloutIconSize * (iconScale / 1.7f)
                val iconTouchRadiusDp = (iconSizeDp + Spacing.m) / 2f
                val chartHalf = minOf(maxWidth, maxHeight) / 2f
                val iconCenterRadius =
                    chartHalf * outerRadiusFraction + Spacing.s + iconSizeDp / 2f + explodedOffset
                val iconTouchOverflow = maxOf(Spacing.none, iconCenterRadius + iconTouchRadiusDp - chartHalf)
                val minimumTouchTargetSize =
                    DpSize(
                        width =
                            maxOf(
                                viewConfiguration.minimumTouchTargetSize.width,
                                maxWidth + iconTouchOverflow * 2f,
                            ),
                        height =
                            maxOf(
                                viewConfiguration.minimumTouchTargetSize.height,
                                maxHeight + iconTouchOverflow * 2f,
                            ),
                    )
                val donutViewConfiguration =
                    remember(viewConfiguration, minimumTouchTargetSize) {
                        DonutViewConfiguration(
                            base = viewConfiguration,
                            minimumTouchTargetSize = minimumTouchTargetSize,
                        )
                    }
                CompositionLocalProvider(LocalViewConfiguration provides donutViewConfiguration) {
                    Canvas(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .pointerInput(
                                    arcs,
                                    emptyStateIcons,
                                    outerRadiusFraction,
                                    ringThicknessFraction,
                                    sliceGapDegrees,
                                    iconScale,
                                    calloutIconSize,
                                    explodedOffset,
                                    onSliceClick,
                                    onEmptyCategoryClick,
                                ) {
                                    detectTapGestures { offset ->
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val outerRadius = min(size.width, size.height) / 2f * outerRadiusFraction
                                        if (arcs.isEmpty()) {
                                            val iconSize = calloutIconSize.toPx() * (iconScale / 1.7f)
                                            val iconTouchRadius = (iconSize + Spacing.m.toPx()) / 2f
                                            val inset = iconSize / 2f + Spacing.s.toPx()
                                            val insetHalfWidth = (size.width / 2f - inset).coerceAtLeast(0f)
                                            val insetHalfHeightTop = (center.y - inset).coerceAtLeast(0f)
                                            val insetHalfHeightBottom =
                                                (size.height - center.y - inset).coerceAtLeast(0f)
                                            val angles = DonutGeometry.evenAngles(emptyStateIcons.size)
                                            val hit =
                                                emptyStateIcons.firstOrNullIndexed { index, _ ->
                                                    val slot =
                                                        emptyIconFrameSlot(
                                                            center = center,
                                                            angleDegrees = angles[index],
                                                            insetHalfWidth = insetHalfWidth,
                                                            insetHalfHeightTop = insetHalfHeightTop,
                                                            insetHalfHeightBottom = insetHalfHeightBottom,
                                                        )
                                                    hypot(offset.x - slot.x, offset.y - slot.y) <= iconTouchRadius
                                                }
                                            if (hit != null) onEmptyCategoryClick?.invoke(hit)
                                            return@detectTapGestures
                                        }
                                        val strokeWidth = outerRadius * ringThicknessFraction
                                        val innerRadius = outerRadius - strokeWidth
                                        val explodedOffsetPx = explodedOffset.toPx()
                                        val iconSize = calloutIconSize.toPx() * (iconScale / 1.7f)
                                        val iconMargin = Spacing.s.toPx()
                                        val iconTouchRadius = (iconSize + Spacing.m.toPx()) / 2f
                                        val inset = iconSize / 2f + iconMargin
                                        val insetHalfWidth = (size.width / 2f - inset).coerceAtLeast(0f)
                                        val insetHalfHeightTop = (center.y - inset).coerceAtLeast(0f)
                                        val insetHalfHeightBottom = (size.height - center.y - inset).coerceAtLeast(0f)
                                        val iconHit =
                                            layoutSlices(arcs)
                                                .firstOrNull { placed ->
                                                    if (
                                                        progress.value < 1f ||
                                                        placed.slice.fraction <= 0f
                                                    ) {
                                                        return@firstOrNull false
                                                    }
                                                    val slot =
                                                        placed.frameIconCenter(
                                                            center = center,
                                                            insetHalfWidth = insetHalfWidth,
                                                            insetHalfHeightTop = insetHalfHeightTop,
                                                            insetHalfHeightBottom = insetHalfHeightBottom,
                                                            explodedOffset = placed.explodedOffset(explodedOffsetPx),
                                                        )
                                                    hypot(offset.x - slot.x, offset.y - slot.y) <= iconTouchRadius
                                                }?.slice
                                        if (iconHit != null) {
                                            onSliceClick?.invoke(iconHit)
                                            return@detectTapGestures
                                        }
                                        val hit =
                                            DonutGeometry.hitTest(
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
                                }.drawWithCache {
                                    val cache =
                                        buildDonutChartDrawCache(
                                            incomeText = incomeText,
                                            expenseText = expenseText,
                                            slices = slices,
                                            arcs = arcs,
                                            emptyStateIcons = emptyStateIcons,
                                            iconPainters = iconPainters,
                                            textMeasurer = textMeasurer,
                                            outlineColor = outlineColor,
                                            outerRadiusFraction = outerRadiusFraction,
                                            ringThicknessFraction = ringThicknessFraction,
                                            sliceGapDegrees = sliceGapDegrees,
                                            iconScale = iconScale,
                                            explodedOffset = explodedOffset,
                                            style = style,
                                            calloutIconSize = calloutIconSize,
                                            labelMinFraction = labelMinFraction,
                                            showCategoryLabels = showCategoryLabels,
                                            centerTextStyle = centerTextStyle,
                                            centerDividerWidth = centerDividerWidth,
                                            centerDividerThickness = centerDividerThickness,
                                            calloutLabelStyle = calloutLabelStyle,
                                            calloutPercentageStyle = calloutPercentageStyle,
                                            leaderLineThickness = leaderLineThickness,
                                        )
                                    onDrawBehind {
                                        drawDonutChart(
                                            cache = cache,
                                            extrudedRingPaints = extrudedRingPaints,
                                            progress = progress.value,
                                            budgetAlertColor = budgetAlertColor,
                                            badgeBorderColor = badgeBorderColor,
                                            incomeColor = incomeColor,
                                            expenseColor = expenseColor,
                                            centerDividerColor = centerDividerColor,
                                        )
                                    }
                                },
                    ) {}
                }
            }
        }
        slices.forEach { slice ->
            if (slice.fraction <= 0f || slice.fraction < labelMinFraction) return@forEach
            val sliceDescription =
                String.format(sliceTemplate, slice.label, (slice.fraction * 100f).roundToInt())
            Box(
                modifier =
                    Modifier
                        .semantics(mergeDescendants = true) {
                            contentDescription = sliceDescription
                            if (onSliceClick != null) {
                                onClick(label = openCategoryActionLabel) {
                                    onSliceClick(slice)
                                    true
                                }
                            }
                        },
            )
        }
    }
}

private fun CacheDrawScope.buildDonutChartDrawCache(
    incomeText: String,
    expenseText: String,
    slices: List<CategorySlice>,
    arcs: List<SliceArc>,
    emptyStateIcons: List<CategorySlice>,
    iconPainters: Map<String, VectorPainter>,
    textMeasurer: TextMeasurer,
    outlineColor: Color,
    outerRadiusFraction: Float,
    ringThicknessFraction: Float,
    sliceGapDegrees: Float,
    iconScale: Float,
    explodedOffset: Dp,
    style: DonutStyle,
    calloutIconSize: Dp,
    labelMinFraction: Float,
    showCategoryLabels: Boolean,
    centerTextStyle: TextStyle,
    centerDividerWidth: Dp,
    centerDividerThickness: Dp,
    calloutLabelStyle: TextStyle,
    calloutPercentageStyle: TextStyle,
    leaderLineThickness: Dp,
): DonutChartDrawCache {
    val center = Offset(size.width / 2f, size.height / 2f)
    val outerRadius = min(size.width, size.height) / 2f * outerRadiusFraction
    val thickness = outerRadius * ringThicknessFraction
    val ringRadius = outerRadius - thickness / 2f
    val explodedOffsetPx = explodedOffset.toPx()
    val iconSize = calloutIconSize.toPx() * (iconScale / 1.7f)
    val iconMargin = Spacing.s.toPx()
    val centerTotals =
        buildCenterTotalsCache(
            center = center,
            incomeText = incomeText,
            expenseText = expenseText,
            textMeasurer = textMeasurer,
            innerRadius = outerRadius - thickness,
            textStyle = centerTextStyle,
            dividerWidth = centerDividerWidth.toPx(),
            dividerThickness = centerDividerThickness.toPx(),
        )

    if (slices.isEmpty()) {
        val ringArcs =
            listOf(
                CachedDonutArc(
                    color = outlineColor,
                    wallColor = shade(outlineColor, -0.40f),
                    startAngle = -90f,
                    fullSweep = 360f,
                    gap = 0f,
                ),
            )
        val inset = iconSize / 2f + iconMargin
        val insetHalfWidth = (size.width / 2f - inset).coerceAtLeast(0f)
        val insetHalfHeightTop = (center.y - inset).coerceAtLeast(0f)
        val insetHalfHeightBottom = (size.height - center.y - inset).coerceAtLeast(0f)
        val angles = DonutGeometry.evenAngles(emptyStateIcons.size)
        val emptyCallouts = ArrayList<EmptyDonutCalloutCache>(emptyStateIcons.size)
        for (index in emptyStateIcons.indices) {
            val slice = emptyStateIcons[index]
            val angleDegrees = angles[index]
            val rawSlot =
                emptyIconFrameSlot(
                    center = center,
                    angleDegrees = angleDegrees,
                    insetHalfWidth = insetHalfWidth,
                    insetHalfHeightTop = insetHalfHeightTop,
                    insetHalfHeightBottom = insetHalfHeightBottom,
                )
            val text =
                measureDonutCallout(
                    iconSize = iconSize,
                    label = null,
                    percentage = "",
                    percentageStyle = calloutPercentageStyle,
                    labelStyle = calloutLabelStyle,
                    textMeasurer = textMeasurer,
                )
            val slot =
                clampCalloutAnchor(
                    anchor = rawSlot,
                    iconSize = iconSize,
                    text = text,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                )
            val ringOuterPoint =
                radialPoint(
                    center = center,
                    midRadians = Math.toRadians(angleDegrees.toDouble()).toFloat(),
                    radius = outerRadius,
                    explodedOffset = Offset.Zero,
                )
            emptyCallouts +=
                EmptyDonutCalloutCache(
                    color = slice.color,
                    lineStart = ringOuterPoint,
                    slot = slot,
                    iconPainter = iconPainters[slice.iconKey],
                )
        }
        return DonutChartDrawCache(
            isEmpty = true,
            center = center,
            outerRadius = outerRadius,
            ringRadius = ringRadius,
            thickness = thickness,
            iconSize = iconSize,
            leaderLineThickness = leaderLineThickness.toPx(),
            ringArcs = ringArcs,
            emptyCallouts = emptyCallouts,
            slices = emptyList(),
            centerTotals = centerTotals,
            extrudedDepth = (thickness * 0.62f).roundToInt().coerceIn(7, 22),
            budgetBadgeBorderRadius = 5.dp.toPx(),
            budgetBadgeRadius = 3.5.dp.toPx(),
            style = style,
        )
    }

    val inset = iconSize / 2f + iconMargin
    val insetHalfWidth = (size.width / 2f - inset).coerceAtLeast(0f)
    val insetHalfHeightTop = (center.y - inset).coerceAtLeast(0f)
    val insetHalfHeightBottom = (size.height - center.y - inset).coerceAtLeast(0f)
    val ringArcs = ArrayList<CachedDonutArc>(arcs.size)
    val cachedSlices = ArrayList<DonutSliceDrawCache>(arcs.size)
    val placed = layoutSlices(arcs)
    for (placedSlice in placed) {
        val gap = DonutGeometry.gapForSweep(placedSlice.sweepDegrees, sliceGapDegrees)
        val offset = placedSlice.explodedOffset(explodedOffsetPx)
        ringArcs +=
            CachedDonutArc(
                color = placedSlice.slice.color,
                wallColor = shade(placedSlice.slice.color, -0.40f),
                startAngle = placedSlice.startAngleDegrees + gap / 2f,
                fullSweep = placedSlice.sweepDegrees,
                gap = gap,
                offset = offset,
            )
        if (placedSlice.slice.fraction <= 0f) continue
        val percentage = "${(placedSlice.slice.fraction * 100f).roundToInt()}%"
        val label =
            placedSlice.slice.label.takeIf {
                showCategoryLabels && placedSlice.slice.fraction >= labelMinFraction
            }
        val text =
            measureDonutCallout(
                iconSize = iconSize,
                label = label,
                percentage = percentage,
                percentageStyle = calloutPercentageStyle,
                labelStyle = calloutLabelStyle,
                textMeasurer = textMeasurer,
            )
        val rawSlot =
            placedSlice.frameIconCenter(
                center = center,
                insetHalfWidth = insetHalfWidth,
                insetHalfHeightTop = insetHalfHeightTop,
                insetHalfHeightBottom = insetHalfHeightBottom,
                explodedOffset = offset,
            )
        val slot =
            clampCalloutAnchor(
                anchor = rawSlot,
                iconSize = iconSize,
                text = text,
                canvasWidth = size.width,
                canvasHeight = size.height,
            )
        val lineStart =
            radialPoint(
                center = center,
                midRadians = placedSlice.midRadians,
                radius = outerRadius,
                explodedOffset = offset,
            )
        val callout =
            if (placedSlice.slice.fraction >= labelMinFraction) {
                text.toDrawCache(iconCenter = slot, iconSize = iconSize)
            } else {
                null
            }
        val badgeCenter =
            if (placedSlice.slice.hasBudgetAlert) {
                Offset(
                    x = slot.x + iconSize * 0.35f,
                    y = slot.y - iconSize * 0.35f,
                )
            } else {
                null
            }
        cachedSlices +=
            DonutSliceDrawCache(
                slice = placedSlice.slice,
                lineStart = lineStart,
                iconCenter = slot,
                iconPainter = iconPainters[placedSlice.slice.iconKey],
                callout = callout,
                badgeCenter = badgeCenter,
            )
    }
    return DonutChartDrawCache(
        isEmpty = false,
        center = center,
        outerRadius = outerRadius,
        ringRadius = ringRadius,
        thickness = thickness,
        iconSize = iconSize,
        leaderLineThickness = leaderLineThickness.toPx(),
        ringArcs = ringArcs,
        emptyCallouts = emptyList(),
        slices = cachedSlices,
        centerTotals = centerTotals,
        extrudedDepth = (thickness * 0.62f).roundToInt().coerceIn(7, 22),
        budgetBadgeBorderRadius = 5.dp.toPx(),
        budgetBadgeRadius = 3.5.dp.toPx(),
        style = style,
    )
}

private fun DrawScope.drawDonutChart(
    cache: DonutChartDrawCache,
    extrudedRingPaints: ExtrudedRingPaints,
    progress: Float,
    budgetAlertColor: Color,
    badgeBorderColor: Color,
    incomeColor: Color,
    expenseColor: Color,
    centerDividerColor: Color,
) {
    if (cache.isEmpty) {
        drawDonutRing(cache, progress = 1f, paints = extrudedRingPaints)
        for (callout in cache.emptyCallouts) {
            drawLine(
                color = callout.color,
                start = callout.lineStart,
                end = callout.slot,
                strokeWidth = cache.leaderLineThickness,
            )
            drawIconDisc(
                slotCenter = callout.slot,
                iconSize = cache.iconSize,
                tintColor = callout.color,
                iconPainter = callout.iconPainter,
            )
        }
    } else {
        drawDonutRing(cache, progress = progress, paints = extrudedRingPaints)
        if (progress >= 1f) {
            for (slice in cache.slices) {
                drawLine(
                    color = slice.slice.color,
                    start = slice.lineStart,
                    end = slice.iconCenter,
                    strokeWidth = cache.leaderLineThickness,
                )
                drawIconDisc(
                    slotCenter = slice.iconCenter,
                    iconSize = cache.iconSize,
                    tintColor = slice.slice.color,
                    iconPainter = slice.iconPainter,
                )
                slice.badgeCenter?.let { badgeCenter ->
                    drawCircle(
                        color = badgeBorderColor,
                        radius = cache.budgetBadgeBorderRadius,
                        center = badgeCenter,
                    )
                    drawCircle(
                        color = budgetAlertColor,
                        radius = cache.budgetBadgeRadius,
                        center = badgeCenter,
                    )
                }
                slice.callout?.let { callout ->
                    drawCalloutText(
                        cache = callout,
                        sliceColor = slice.slice.color,
                        labelColor = slice.slice.labelColor,
                    )
                }
            }
        }
    }
    drawCenterTotals(
        cache = cache.centerTotals,
        incomeColor = incomeColor,
        expenseColor = expenseColor,
        dividerColor = centerDividerColor,
    )
}

private fun DrawScope.drawDonutRing(
    cache: DonutChartDrawCache,
    progress: Float,
    paints: ExtrudedRingPaints,
) {
    if (cache.style == DonutStyle.Extrude) {
        drawExtrudedRing(
            center = cache.center,
            radius = cache.ringRadius,
            thickness = cache.thickness,
            arcs = cache.ringArcs,
            progress = progress,
            depth = cache.extrudedDepth,
            paints = paints,
        )
    } else {
        drawFlatRing(
            center = cache.center,
            radius = cache.ringRadius,
            thickness = cache.thickness,
            arcs = cache.ringArcs,
            progress = progress,
        )
    }
}

private class DonutViewConfiguration(
    private val base: ViewConfiguration,
    override val minimumTouchTargetSize: DpSize,
) : ViewConfiguration by base

private data class PlacedSlice(
    val slice: CategorySlice,
    val startAngleDegrees: Float,
    val sweepDegrees: Float,
    val midRadians: Float,
)

private data class CachedDonutArc(
    val color: Color,
    val wallColor: Color,
    val startAngle: Float,
    val fullSweep: Float,
    val gap: Float,
    val offset: Offset = Offset.Zero,
)

private data class DonutChartDrawCache(
    val isEmpty: Boolean,
    val center: Offset,
    val outerRadius: Float,
    val ringRadius: Float,
    val thickness: Float,
    val iconSize: Float,
    val leaderLineThickness: Float,
    val ringArcs: List<CachedDonutArc>,
    val emptyCallouts: List<EmptyDonutCalloutCache>,
    val slices: List<DonutSliceDrawCache>,
    val centerTotals: DonutCenterTotalsCache?,
    val extrudedDepth: Int,
    val budgetBadgeBorderRadius: Float,
    val budgetBadgeRadius: Float,
    val style: DonutStyle = DonutStyle.Extrude,
)

private data class EmptyDonutCalloutCache(
    val color: Color,
    val lineStart: Offset,
    val slot: Offset,
    val iconPainter: VectorPainter?,
)

private data class DonutSliceDrawCache(
    val slice: CategorySlice,
    val lineStart: Offset,
    val iconCenter: Offset,
    val iconPainter: VectorPainter?,
    val callout: DonutCalloutTextCache?,
    val badgeCenter: Offset?,
)

private data class DonutCenterTotalsCache(
    val incomeLayout: TextLayoutResult,
    val expenseLayout: TextLayoutResult,
    val incomeTop: Float,
    val dividerY: Float,
    val expenseTop: Float,
    val dividerWidth: Float,
    val dividerThickness: Float,
    val centerX: Float,
)

private inline fun <T> List<T>.firstOrNullIndexed(predicate: (Int, T) -> Boolean): T? {
    forEachIndexed { index, value ->
        if (predicate(index, value)) return value
    }
    return null
}

private fun CacheDrawScope.buildCenterTotalsCache(
    center: Offset,
    incomeText: String,
    expenseText: String,
    textMeasurer: TextMeasurer,
    innerRadius: Float,
    textStyle: TextStyle,
    dividerWidth: Float,
    dividerThickness: Float,
): DonutCenterTotalsCache? {
    if (innerRadius <= 0f) return null
    val lineGap = Spacing.s.toPx()
    val baseIncome = textMeasurer.measure(text = incomeText, style = textStyle)
    val baseExpense = textMeasurer.measure(text = expenseText, style = textStyle)
    val maxLineWidth = max(baseIncome.size.width, baseExpense.size.width).toFloat()
    val totalBaseHeight = baseIncome.size.height + lineGap * 2f + dividerThickness + baseExpense.size.height
    if (maxLineWidth <= 0f || totalBaseHeight <= 0f) return null
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
    return DonutCenterTotalsCache(
        incomeLayout = incomeLayout,
        expenseLayout = expenseLayout,
        incomeTop = incomeTop,
        dividerY = dividerY,
        expenseTop = expenseTop,
        dividerWidth = dividerWidth,
        dividerThickness = dividerThickness,
        centerX = center.x,
    )
}

private fun DrawScope.drawCenterTotals(
    cache: DonutCenterTotalsCache?,
    incomeColor: Color,
    expenseColor: Color,
    dividerColor: Color,
) {
    cache ?: return
    drawText(
        textLayoutResult = cache.incomeLayout,
        color = incomeColor,
        topLeft = Offset(cache.centerX - cache.incomeLayout.size.width / 2f, cache.incomeTop),
    )
    drawLine(
        color = dividerColor,
        start = Offset(cache.centerX - cache.dividerWidth / 2f, cache.dividerY),
        end = Offset(cache.centerX + cache.dividerWidth / 2f, cache.dividerY),
        strokeWidth = cache.dividerThickness,
    )
    drawText(
        textLayoutResult = cache.expenseLayout,
        color = expenseColor,
        topLeft = Offset(cache.centerX - cache.expenseLayout.size.width / 2f, cache.expenseTop),
    )
}

private data class DonutCalloutMeasurement(
    val percentageLayout: TextLayoutResult,
    val labelLayout: TextLayoutResult?,
    val inlineGap: Float,
    val iconSize: Float,
) {
    val clampBlockWidth: Float get() = iconSize + percentageLayout.size.width
    val blockWidth: Float get() = iconSize + inlineGap + percentageLayout.size.width

    fun toDrawCache(
        iconCenter: Offset,
        iconSize: Float,
    ): DonutCalloutTextCache {
        val iconLeft = iconCenter.x - iconSize / 2f
        val blockCenterX = iconLeft + blockWidth / 2f
        return DonutCalloutTextCache(
            percentageLayout = percentageLayout,
            percentageTopLeft =
                Offset(
                    x = iconLeft + iconSize + inlineGap,
                    y = iconCenter.y - percentageLayout.size.height / 2f,
                ),
            labelLayout = labelLayout,
            labelCenterX = blockCenterX,
            labelTop = iconCenter.y + iconSize / 2f + inlineGap,
        )
    }
}

private data class DonutCalloutTextCache(
    val percentageLayout: TextLayoutResult,
    val percentageTopLeft: Offset,
    val labelLayout: TextLayoutResult?,
    val labelCenterX: Float,
    val labelTop: Float,
)

private fun CacheDrawScope.measureDonutCallout(
    iconSize: Float,
    label: String?,
    percentage: String,
    percentageStyle: TextStyle,
    labelStyle: TextStyle,
    textMeasurer: TextMeasurer,
): DonutCalloutMeasurement {
    val percentageLayout =
        textMeasurer.measure(
            text = percentage,
            style = percentageStyle,
            maxLines = 1,
        )
    val inlineGap = Spacing.xxs.toPx()
    val blockWidth = iconSize + inlineGap + percentageLayout.size.width
    val labelLayout =
        label?.let {
            measureSingleLineLabel(
                text = it,
                maxWidth = blockWidth.roundToInt(),
                style = labelStyle.copy(textAlign = TextAlign.Center),
                textMeasurer = textMeasurer,
            )
        }
    return DonutCalloutMeasurement(
        percentageLayout = percentageLayout,
        labelLayout = labelLayout,
        inlineGap = inlineGap,
        iconSize = iconSize,
    )
}

private fun clampCalloutAnchor(
    anchor: Offset,
    iconSize: Float,
    text: DonutCalloutMeasurement,
    canvasWidth: Float,
    canvasHeight: Float,
): Offset {
    val leftFromAnchor = iconSize / 2f
    val topFromAnchor = iconSize / 2f
    val bottomFromAnchor =
        iconSize / 2f +
            (
                text.labelLayout
                    ?.size
                    ?.height
                    ?.toFloat() ?: 0f
            )

    val minX = leftFromAnchor
    val maxX = canvasWidth - (text.clampBlockWidth - leftFromAnchor)
    val minY = topFromAnchor
    val maxY = canvasHeight - bottomFromAnchor

    val clampedX = if (maxX < minX) anchor.x else anchor.x.coerceIn(minX, maxX)
    val clampedY = if (maxY < minY) anchor.y else anchor.y.coerceIn(minY, maxY)
    return Offset(clampedX, clampedY)
}

private fun DrawScope.drawCalloutText(
    cache: DonutCalloutTextCache,
    sliceColor: Color,
    labelColor: Color,
) {
    drawText(
        textLayoutResult = cache.percentageLayout,
        color = sliceColor,
        topLeft = cache.percentageTopLeft,
    )
    cache.labelLayout?.let { labelLayout ->
        drawTextCentered(labelLayout, cache.labelCenterX, cache.labelTop, labelColor)
    }
}

private fun measureSingleLineLabel(
    text: String,
    maxWidth: Int,
    style: TextStyle,
    textMeasurer: TextMeasurer,
): TextLayoutResult {
    val baseSizeSp = style.fontSize.value
    var currentSizeSp = baseSizeSp
    var layout =
        textMeasurer.measure(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = maxWidth),
        )
    while (layout.hasVisualOverflow && currentSizeSp > CALLOUT_LABEL_MIN_SP) {
        currentSizeSp = (currentSizeSp - 1f).coerceAtLeast(CALLOUT_LABEL_MIN_SP)
        layout =
            textMeasurer.measure(
                text = text,
                style = style.copy(fontSize = currentSizeSp.sp),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = maxWidth),
            )
    }
    return layout
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

private fun emptyIconFrameSlot(
    center: Offset,
    angleDegrees: Float,
    insetHalfWidth: Float,
    insetHalfHeightTop: Float,
    insetHalfHeightBottom: Float,
): Offset {
    val projected =
        DonutGeometry.projectAngleToFrame(
            angleRadians = Math.toRadians(angleDegrees.toDouble()).toFloat(),
            halfWidth = insetHalfWidth,
            halfHeightTop = insetHalfHeightTop,
            halfHeightBottom = insetHalfHeightBottom,
        )
    return Offset(center.x + projected.x, center.y + projected.y)
}

private fun layoutSlices(arcs: List<SliceArc>): List<PlacedSlice> =
    arcs.map { arc ->
        val mid = DonutGeometry.midAngleRadians(arc)
        PlacedSlice(
            slice = arc.slice,
            startAngleDegrees = arc.startAngleDegrees,
            sweepDegrees = arc.sweepDegrees,
            midRadians = mid,
        )
    }

private fun shade(
    color: Color,
    factor: Float,
): Color {
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
    arc: CachedDonutArc,
    progress: Float,
    dy: Float = 0f,
) {
    val sweepAngle = (arc.fullSweep * progress - arc.gap).coerceAtLeast(0f)
    if (sweepAngle <= 0f) return
    val arcCenter = center + arc.offset
    drawArc(
        color = color,
        startAngle = arc.startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(arcCenter.x - radius, arcCenter.y - radius + dy),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = width, cap = StrokeCap.Butt),
    )
}

private fun DrawScope.drawFlatRing(
    center: Offset,
    radius: Float,
    thickness: Float,
    arcs: List<CachedDonutArc>,
    progress: Float,
) {
    for (arc in arcs) {
        drawArcBand(center, radius, thickness, arc.color, arc, progress)
    }
}

private val EXTRUDED_RING_CAST_COLOR =
    Color(red = 35f / 255f, green = 60f / 255f, blue = 48f / 255f, alpha = 0.28f)
private val DONUT_RING_HIGHLIGHT_COLOR = Color.White.copy(alpha = 0.40f)
private val DONUT_RING_INNER_SHADOW_COLOR = Color.Black.copy(alpha = 0.18f)

private fun DrawScope.drawExtrudedRing(
    center: Offset,
    radius: Float,
    thickness: Float,
    arcs: List<CachedDonutArc>,
    progress: Float,
    depth: Int,
    paints: ExtrudedRingPaints,
) {
    // Visual parity: reuse a remembered Paint/BlurMaskFilter (blur radius is density-derived
    // and thus constant for the composition) and mutate only color/strokeWidth — cheap setters
    // that produce byte-identical output to the per-frame `Paint()`/`BlurMaskFilter()` it replaces.
    val shadowPaint =
        paints.shadowPaint.apply {
            color = EXTRUDED_RING_CAST_COLOR
            strokeWidth = thickness * 0.92f
        }
    drawIntoCanvas { canvas ->
        canvas.drawCircle(Offset(center.x, center.y + thickness * 0.95f), radius, shadowPaint)
    }

    for (k in depth downTo 1) {
        for (arc in arcs) {
            drawArcBand(
                center = center,
                radius = radius,
                width = thickness,
                color = arc.wallColor,
                arc = arc,
                progress = progress,
                dy = k.toFloat(),
            )
        }
    }

    for (arc in arcs) {
        drawArcBand(center, radius, thickness, arc.color, arc, progress)
    }

    clipRect(
        left = center.x - radius - thickness,
        top = center.y - radius - thickness,
        right = center.x + radius + thickness,
        bottom = center.y - thickness * 0.75f,
        clipOp = ClipOp.Intersect,
    ) {
        for (arc in arcs) {
            drawArcBand(
                center = center,
                radius = radius + thickness * 0.5f - 1.2f,
                width = 2.2f,
                color = DONUT_RING_HIGHLIGHT_COLOR,
                arc = arc,
                progress = progress,
            )
        }
    }
    for (arc in arcs) {
        drawArcBand(
            center = center,
            radius = radius - thickness * 0.5f + 1f,
            width = 1.6f,
            color = DONUT_RING_INNER_SHADOW_COLOR,
            arc = arc,
            progress = progress,
        )
    }
}

private fun DrawScope.drawIconDisc(
    slotCenter: Offset,
    iconSize: Float,
    tintColor: Color,
    iconPainter: VectorPainter?,
): Offset {
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

private fun PlacedSlice.explodedOffset(distance: Float): Offset =
    Offset(
        x = distance * cos(midRadians),
        y = distance * sin(midRadians),
    )

private fun PlacedSlice.frameIconCenter(
    center: Offset,
    insetHalfWidth: Float,
    insetHalfHeightTop: Float,
    insetHalfHeightBottom: Float,
    explodedOffset: Offset,
): Offset {
    val projected =
        DonutGeometry.projectAngleToFrame(
            angleRadians = midRadians,
            halfWidth = insetHalfWidth,
            halfHeightTop = insetHalfHeightTop,
            halfHeightBottom = insetHalfHeightBottom,
        )
    return Offset(
        x = center.x + explodedOffset.x + projected.x,
        y = center.y + explodedOffset.y + projected.y,
    )
}

private fun radialPoint(
    center: Offset,
    midRadians: Float,
    radius: Float,
    explodedOffset: Offset,
): Offset =
    Offset(
        x = center.x + explodedOffset.x + radius * cos(midRadians),
        y = center.y + explodedOffset.y + radius * sin(midRadians),
    )
