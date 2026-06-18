package com.kshavrin.mymoney.core.designsystem.donut

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.neonRingGradientEnd
import com.kshavrin.mymoney.core.ui.theme.neonRingGradientStart
import com.kshavrin.mymoney.core.ui.theme.neonRingTrack

const val NEON_RING_CHART_TAG = "neon_ring_chart"

private const val FULL_SWEEP_DEGREES = 360f
private const val TOP_START_ROTATION_DEGREES = -90f

@Composable
fun NeonRingChart(
    fraction: Float,
    modifier: Modifier = Modifier,
    centerContent: @Composable BoxScope.() -> Unit,
) {
    val diameter = Spacing.neonRingDiameter
    val strokeWidth = Spacing.neonRingStrokeWidth
    val glowRadius = Spacing.neonRingGlowRadius
    val glowSpread = Spacing.neonRingGlowSpread
    val glowStrokeWidth = strokeWidth + glowSpread * 2
    val glowMargin = glowRadius + glowSpread + glowStrokeWidth / 2
    val containerSize = diameter + glowMargin * 2
    val innerDiameter = diameter - (strokeWidth + glowSpread) * 2
    val gradientStart = MaterialTheme.colorScheme.neonRingGradientStart
    val gradientEnd = MaterialTheme.colorScheme.neonRingGradientEnd
    val trackColor = MaterialTheme.colorScheme.neonRingTrack
    val density = LocalDensity.current
    val glowPaint =
        remember(density, gradientStart, glowRadius, glowStrokeWidth) {
            Paint().apply {
                color = gradientStart
                style = PaintingStyle.Stroke
                strokeCap = StrokeCap.Round
                this.strokeWidth = with(density) { glowStrokeWidth.toPx() }
                asFrameworkPaint().maskFilter =
                    BlurMaskFilter(
                        with(density) { glowRadius.toPx() },
                        BlurMaskFilter.Blur.NORMAL,
                    )
            }
        }
    val sweepAngle = fraction * FULL_SWEEP_DEGREES

    Box(
        modifier =
            modifier
                .size(containerSize)
                .testTag(NEON_RING_CHART_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameterPx = diameter.toPx()
            val strokeWidthPx = strokeWidth.toPx()
            val center = center
            val radius = (diameterPx - strokeWidthPx) / 2f
            val arcTopLeft = Offset(center.x - radius, center.y - radius)
            val arcSize = Size(radius * 2f, radius * 2f)

            drawArc(
                color = trackColor,
                startAngle = TOP_START_ROTATION_DEGREES,
                sweepAngle = FULL_SWEEP_DEGREES,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )

            if (sweepAngle > 0f) {
                rotate(degrees = TOP_START_ROTATION_DEGREES, pivot = center) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawArc(
                            arcTopLeft.x,
                            arcTopLeft.y,
                            arcTopLeft.x + arcSize.width,
                            arcTopLeft.y + arcSize.height,
                            0f,
                            sweepAngle,
                            false,
                            glowPaint.asFrameworkPaint(),
                        )
                    }
                    drawArc(
                        brush =
                            Brush.sweepGradient(
                                colorStops =
                                    arrayOf(
                                        0f to gradientStart,
                                        fraction to gradientEnd,
                                        1f to gradientEnd,
                                    ),
                                center = center,
                            ),
                        startAngle = 0f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                    )
                }
            }
        }

        Box(
            modifier = Modifier.size(innerDiameter),
            content = centerContent,
        )
    }
}
