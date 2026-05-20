package com.kshavrin.mymoney.core.designsystem.donut

import kotlin.math.atan2
import kotlin.math.hypot

object DonutGeometry {

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
