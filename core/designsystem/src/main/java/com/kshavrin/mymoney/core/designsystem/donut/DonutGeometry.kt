package com.kshavrin.mymoney.core.designsystem.donut

import kotlin.math.atan2
import kotlin.math.hypot

object DonutGeometry {

    fun framePoint(t: Float, hw: Float, hhTop: Float, hhBot: Float): FrameOffset {
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
    ): CategorySlice? {
        val dx = offsetX - centerX
        val dy = offsetY - centerY
        val distance = hypot(dx, dy)
        if (distance < innerRadius || distance > outerRadius) return null
        val angleDegrees = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 450f) % 360f
        return arcs.firstOrNull { arc ->
            val start = (arc.startAngleDegrees + 360f) % 360f
            val end = (start + arc.sweepDegrees) % 360f
            if (end >= start) angleDegrees in start..end else angleDegrees >= start || angleDegrees <= end
        }?.slice
    }
}

data class SliceArc(
    val slice: CategorySlice,
    val startAngleDegrees: Float,
    val sweepDegrees: Float,
)

data class FrameOffset(val x: Float, val y: Float)
