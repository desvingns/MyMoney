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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onSliceClick: ((CategorySlice) -> Unit)? = null,
    animationSpec: AnimationSpec<Float> = spring(dampingRatio = 0.7f, stiffness = 300f),
) {
    val arcs = remember(slices) { DonutGeometry.computeSliceArcs(slices) }
    val progress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(targetValue = 1f, animationSpec = animationSpec)
    }

    val outlineColor = MaterialTheme.colorScheme.outline

    Box(modifier = modifier) {
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
                val arcMidRadius = outerRadius - strokeWidth / 2f
                val iconRadius = outerRadius + 24.dp.toPx()
                val arcMidPoint = Offset(
                    center.x + arcMidRadius * cos(midRadians),
                    center.y + arcMidRadius * sin(midRadians),
                )
                val iconCenter = Offset(
                    center.x + iconRadius * cos(midRadians),
                    center.y + iconRadius * sin(midRadians),
                )
                drawLine(
                    color = outlineColor,
                    start = arcMidPoint,
                    end = iconCenter,
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
}
