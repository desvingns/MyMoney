package com.kshavrin.mymoney.core.designsystem.donut

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import java.math.BigDecimal
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val LABEL_THRESHOLD = 0.03f

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
    val incomeColor = MaterialTheme.colorScheme.primary
    val expenseColor = MaterialTheme.colorScheme.tertiary

    val locale = LocalConfiguration.current.locales[0]
    val incomeText = MoneyFormatter.format(
        amount = income,
        currencySymbol = currencySymbol,
        decimalDigits = decimalDigits,
        locale = locale,
        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
    )
    val expenseText = MoneyFormatter.format(
        amount = expense,
        currencySymbol = currencySymbol,
        decimalDigits = decimalDigits,
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
                .pointerInput(arcs) {
                    detectTapGestures { offset ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerRadius = min(size.width, size.height) / 2f * 0.75f
                        val strokeWidth = outerRadius * 0.3f
                        val innerRadius = outerRadius - strokeWidth
                        val hit = DonutGeometry.hitTest(
                            offsetX = offset.x,
                            offsetY = offset.y,
                            centerX = center.x,
                            centerY = center.y,
                            innerRadius = innerRadius,
                            outerRadius = outerRadius,
                            arcs = arcs,
                        )
                        if (hit != null) onSliceClick?.invoke(hit)
                    }
                },
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = min(size.width, size.height) / 2f * 0.75f
            val strokeWidth = outerRadius * 0.3f

            if (slices.isEmpty()) {
                drawArc(
                    color = outlineColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - outerRadius + strokeWidth / 2f,
                        center.y - outerRadius + strokeWidth / 2f,
                    ),
                    size = Size(
                        (outerRadius - strokeWidth / 2f) * 2f,
                        (outerRadius - strokeWidth / 2f) * 2f,
                    ),
                    style = Stroke(width = strokeWidth),
                )
                val angles = DonutGeometry.evenAngles(emptyStateIcons.size)
                emptyStateIcons.forEachIndexed { index, slice ->
                    val angleRadians = Math.toRadians(angles[index].toDouble()).toFloat()
                    drawCategoryIcon(
                        center = center,
                        outerRadius = outerRadius,
                        strokeWidth = strokeWidth,
                        angleRadians = angleRadians,
                        leaderColor = outlineColor,
                        tintColor = slice.color,
                        iconPainter = iconPainters[slice.iconKey],
                    )
                }
            }

            arcs.forEach { arc ->
                val animatedSweep = arc.sweepDegrees * progress.value
                drawArc(
                    color = arc.slice.color,
                    startAngle = arc.startAngleDegrees,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = Offset(
                        center.x - outerRadius + strokeWidth / 2f,
                        center.y - outerRadius + strokeWidth / 2f,
                    ),
                    size = Size(
                        (outerRadius - strokeWidth / 2f) * 2f,
                        (outerRadius - strokeWidth / 2f) * 2f,
                    ),
                    style = Stroke(width = strokeWidth),
                )
                if (arc.slice.fraction >= LABEL_THRESHOLD && progress.value >= 1f) {
                    val midRadians = DonutGeometry.midAngleRadians(arc)
                    val labelRadius = outerRadius - strokeWidth / 2f
                    val labelX = center.x + labelRadius * cos(midRadians)
                    val labelY = center.y + labelRadius * sin(midRadians)
                    val labelText = "${(arc.slice.fraction * 100f).toInt()}%"
                    val layout = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(fontSize = 10.sp, color = Color.White),
                    )
                    drawText(
                        textMeasurer = textMeasurer,
                        text = labelText,
                        topLeft = Offset(
                            labelX - layout.size.width / 2f,
                            labelY - layout.size.height / 2f,
                        ),
                        style = TextStyle(
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }

            arcs.forEach { arc ->
                if (progress.value < 1f) return@forEach
                val midRadians = DonutGeometry.midAngleRadians(arc)
                val iconCenter = drawCategoryIcon(
                    center = center,
                    outerRadius = outerRadius,
                    strokeWidth = strokeWidth,
                    angleRadians = midRadians,
                    leaderColor = outlineColor,
                    tintColor = arc.slice.color,
                    iconPainter = iconPainters[arc.slice.iconKey],
                )
                val iconSize = 18.dp.toPx()
                if (arc.slice.hasBudgetAlert) {
                    val badgeCenter = Offset(
                        x = iconCenter.x + iconSize * 0.35f,
                        y = iconCenter.y - iconSize * 0.35f,
                    )
                    drawCircle(
                        color = badgeBorderColor,
                        radius = 5.dp.toPx(),
                        center = badgeCenter,
                    )
                    drawCircle(
                        color = budgetAlertColor,
                        radius = 3.5.dp.toPx(),
                        center = badgeCenter,
                    )
                }
            }

            val centerTextStyle = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val incomeLayout = textMeasurer.measure(text = incomeText, style = centerTextStyle)
            val expenseLayout = textMeasurer.measure(text = expenseText, style = centerTextStyle)
            val lineGap = 4.dp.toPx()
            val totalHeight = incomeLayout.size.height + lineGap + expenseLayout.size.height
            val incomeTop = center.y - totalHeight / 2f
            val expenseTop = incomeTop + incomeLayout.size.height + lineGap
            drawText(
                textLayoutResult = incomeLayout,
                color = incomeColor,
                topLeft = Offset(center.x - incomeLayout.size.width / 2f, incomeTop),
            )
            drawText(
                textLayoutResult = expenseLayout,
                color = expenseColor,
                topLeft = Offset(center.x - expenseLayout.size.width / 2f, expenseTop),
            )
        }
    }
}

private fun DrawScope.drawCategoryIcon(
    center: Offset,
    outerRadius: Float,
    strokeWidth: Float,
    angleRadians: Float,
    leaderColor: Color,
    tintColor: Color,
    iconPainter: VectorPainter?,
): Offset {
    val arcMidRadius = outerRadius - strokeWidth / 2f
    val iconRadius = outerRadius + 24.dp.toPx()
    val iconSize = 18.dp.toPx()
    val arcMidPoint = Offset(
        center.x + arcMidRadius * cos(angleRadians),
        center.y + arcMidRadius * sin(angleRadians),
    )
    val iconCenter = Offset(
        center.x + iconRadius * cos(angleRadians),
        center.y + iconRadius * sin(angleRadians),
    )
    drawLine(
        color = leaderColor,
        start = arcMidPoint,
        end = iconCenter,
        strokeWidth = 1.dp.toPx(),
    )
    if (iconPainter != null) {
        translate(
            left = iconCenter.x - iconSize / 2f,
            top = iconCenter.y - iconSize / 2f,
        ) {
            with(iconPainter) {
                draw(
                    size = Size(iconSize, iconSize),
                    colorFilter = ColorFilter.tint(tintColor),
                )
            }
        }
    }
    return iconCenter
}
