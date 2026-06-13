package com.kshavrin.mymoney.core.designsystem.donut

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

object DonutGeometry {
    private const val EPSILON = 1e-6f

    fun gapForSweep(
        sweepDegrees: Float,
        maxGapDegrees: Float,
    ): Float =
        minOf(maxGapDegrees.coerceAtLeast(0f), sweepDegrees * 0.6f)

    fun framePoint(
        t: Float,
        hw: Float,
        hhTop: Float,
        hhBot: Float,
    ): FrameOffset {
        val side = hhTop + hhBot
        val perimeter = 4f * hw + 2f * side
        var d = ((t % 1f) + 1f) % 1f * perimeter
        if (d < hw) return FrameOffset(d, -hhTop)
        d -= hw
        if (d < side) return FrameOffset(hw, -hhTop + d)
        d -= side
        if (d < 2f * hw) return FrameOffset(hw - d, hhBot)
        d -= 2f * hw
        if (d < side) return FrameOffset(-hw, hhBot - d)
        d -= side
        return FrameOffset(-hw + d, -hhTop)
    }

    fun projectAngleToFrame(
        angleRadians: Float,
        halfWidth: Float,
        halfHeightTop: Float,
        halfHeightBottom: Float,
    ): FrameOffset {
        val dx = cos(angleRadians)
        val dy = sin(angleRadians)
        val halfHeight = if (dy >= 0f) halfHeightBottom else halfHeightTop
        // Guard near-vertical/near-horizontal rays so the unreached edge contributes no scale.
        val tx = if (abs(dx) < EPSILON) Float.POSITIVE_INFINITY else halfWidth / abs(dx)
        val ty = if (abs(dy) < EPSILON) Float.POSITIVE_INFINITY else halfHeight / abs(dy)
        val scale = minOf(tx, ty)
        return FrameOffset(dx * scale, dy * scale)
    }

    fun computeSliceArcs(slices: List<CategorySlice>): List<SliceArc> {
        if (slices.isEmpty()) return emptyList()
        var cumulative = 0f
        return slices.map { slice ->
            val startAngle = -90f + cumulative * 360f
            val sweep = slice.fraction * 360f
            cumulative += slice.fraction
            SliceArc(slice = slice, startAngleDegrees = startAngle, sweepDegrees = sweep)
        }
    }

    fun evenAngles(count: Int): List<Float> {
        if (count <= 0) return emptyList()
        val step = 360f / count
        return (0 until count).map { -90f + it * step }
    }

    fun midAngleRadians(arc: SliceArc): Float {
        val midDegrees = arc.startAngleDegrees + arc.sweepDegrees / 2f
        return Math.toRadians(midDegrees.toDouble()).toFloat()
    }

    fun hitTest(
        offsetX: Float,
        offsetY: Float,
        centerX: Float,
        centerY: Float,
        innerRadius: Float,
        outerRadius: Float,
        arcs: List<SliceArc>,
        sliceGapDegrees: Float = 0f,
        explodedOffset: Float = 0f,
    ): CategorySlice? {
        return arcs
            .firstOrNull { arc ->
                val midAngle = midAngleRadians(arc)
                val arcCenterX = centerX + explodedOffset * cos(midAngle)
                val arcCenterY = centerY + explodedOffset * sin(midAngle)
                val dx = offsetX - arcCenterX
                val dy = offsetY - arcCenterY
                val distance = hypot(dx, dy)
                if (distance < innerRadius || distance > outerRadius) return@firstOrNull false
                val angleDegrees =
                    normalizeDegrees(
                        Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat(),
                    )
                val gap = gapForSweep(arc.sweepDegrees, sliceGapDegrees)
                containsAngle(
                    angleDegrees = angleDegrees,
                    startAngleDegrees = arc.startAngleDegrees + gap / 2f,
                    sweepDegrees = (arc.sweepDegrees - gap).coerceAtLeast(0f),
                )
            }?.slice
    }

    private fun containsAngle(
        angleDegrees: Float,
        startAngleDegrees: Float,
        sweepDegrees: Float,
    ): Boolean {
        if (sweepDegrees <= 0f) return false
        if (sweepDegrees >= 360f) return true
        return normalizeDegrees(angleDegrees - startAngleDegrees) <= sweepDegrees
    }

    private fun normalizeDegrees(angleDegrees: Float): Float =
        ((angleDegrees % 360f) + 360f) % 360f
}

data class SliceArc(
    val slice: CategorySlice,
    val startAngleDegrees: Float,
    val sweepDegrees: Float,
)

data class FrameOffset(
    val x: Float,
    val y: Float,
)
