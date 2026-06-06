package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class DonutGeometryTest {

    private fun slice(id: Long, fraction: Float) = CategorySlice(
        categoryId = id,
        color = Color.Red,
        fraction = fraction,
        label = "Slice-$id",
    )

    @Test
    fun computeSliceArcs_evenSlices() {
        val arcs = DonutGeometry.computeSliceArcs(listOf(slice(1, 0.5f), slice(2, 0.5f)))
        assertEquals(2, arcs.size)
        assertEquals(-90f, arcs[0].startAngleDegrees, 0.001f)
        assertEquals(180f, arcs[0].sweepDegrees, 0.001f)
        assertEquals(90f, arcs[1].startAngleDegrees, 0.001f)
        assertEquals(180f, arcs[1].sweepDegrees, 0.001f)
    }

    @Test
    fun computeSliceArcs_emptyReturnsEmpty() {
        assertEquals(0, DonutGeometry.computeSliceArcs(emptyList()).size)
    }

    @Test
    fun evenAngles_zeroCount_returnsEmpty() {
        assertEquals(emptyList<Float>(), DonutGeometry.evenAngles(0))
    }

    @Test
    fun evenAngles_negativeCount_returnsEmpty() {
        assertEquals(emptyList<Float>(), DonutGeometry.evenAngles(-3))
    }

    @Test
    fun evenAngles_singleCount_startsAtMinusNinety() {
        assertEquals(listOf(-90f), DonutGeometry.evenAngles(1))
    }

    @Test
    fun evenAngles_fourCount_quartersFromMinusNinety() {
        val angles = DonutGeometry.evenAngles(4)
        assertEquals(4, angles.size)
        assertEquals(-90f, angles[0], 0.001f)
        assertEquals(0f, angles[1], 0.001f)
        assertEquals(90f, angles[2], 0.001f)
        assertEquals(180f, angles[3], 0.001f)
    }

    @Test
    fun evenAngles_stepIsThreeSixtyOverCount() {
        val angles = DonutGeometry.evenAngles(5)
        assertEquals(5, angles.size)
        val step = 360f / 5
        angles.forEachIndexed { index, angle ->
            assertEquals(-90f + index * step, angle, 0.001f)
        }
    }

    @Test
    fun hitTest_insideAnnulus_returnsSlice() {
        val arcs = DonutGeometry.computeSliceArcs(listOf(slice(1, 0.5f), slice(2, 0.5f)))
        val hit = DonutGeometry.hitTest(
            offsetX = 100f,
            offsetY = 0f,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
        )
        assertNotNull(hit)
    }

    @Test
    fun hitTest_outsideOuter_returnsNull() {
        val arcs = DonutGeometry.computeSliceArcs(listOf(slice(1, 1f)))
        val hit = DonutGeometry.hitTest(
            offsetX = 200f,
            offsetY = 0f,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
        )
        assertNull(hit)
    }

    @Test
    fun hitTest_insideInner_returnsNull() {
        val arcs = DonutGeometry.computeSliceArcs(listOf(slice(1, 1f)))
        val hit = DonutGeometry.hitTest(
            offsetX = 30f,
            offsetY = 0f,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
        )
        assertNull(hit)
    }

    @Test
    fun `hitTest inside default rendered gap returns null while adjacent visible arc point still hits slice`() {
        val firstSlice = slice(1, 0.5f)
        val secondSlice = slice(2, 0.5f)
        val arcs = DonutGeometry.computeSliceArcs(listOf(firstSlice, secondSlice))
        val radius = 100f

        val gapHit = DonutGeometry.hitTest(
            offsetX = pointAtAngleDegrees(angleDegrees = 90f, radius = radius).first,
            offsetY = pointAtAngleDegrees(angleDegrees = 90f, radius = radius).second,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
            sliceGapDegrees = 5f,
        )
        val visibleArcHit = DonutGeometry.hitTest(
            offsetX = pointAtAngleDegrees(angleDegrees = 87f, radius = radius).first,
            offsetY = pointAtAngleDegrees(angleDegrees = 87f, radius = radius).second,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
            sliceGapDegrees = 5f,
        )

        assertNull(gapHit)
        assertEquals(firstSlice, visibleArcHit)
    }

    @Test
    fun `hitTest uses bounded gap for a narrow slice and still preserves its remaining visible sweep`() {
        val narrowSlice = slice(1, 0.01f)
        val wideSlice = slice(2, 0.99f)
        val arcs = DonutGeometry.computeSliceArcs(listOf(narrowSlice, wideSlice))
        val narrowArc = arcs.first()
        val radius = 100f
        val boundedGap = DonutGeometry.gapForSweep(narrowArc.sweepDegrees, 5f)
        val hiddenGapAngle = narrowArc.startAngleDegrees + boundedGap / 4f
        val visibleAngle =
            narrowArc.startAngleDegrees + boundedGap / 2f + (narrowArc.sweepDegrees - boundedGap) / 2f

        val hiddenGapHit = DonutGeometry.hitTest(
            offsetX = pointAtAngleDegrees(angleDegrees = hiddenGapAngle, radius = radius).first,
            offsetY = pointAtAngleDegrees(angleDegrees = hiddenGapAngle, radius = radius).second,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
            sliceGapDegrees = 5f,
        )
        val visibleArcHit = DonutGeometry.hitTest(
            offsetX = pointAtAngleDegrees(angleDegrees = visibleAngle, radius = radius).first,
            offsetY = pointAtAngleDegrees(angleDegrees = visibleAngle, radius = radius).second,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
            sliceGapDegrees = 5f,
        )

        assertEquals(2.16f, boundedGap, 0.001f)
        assertNull(hiddenGapHit)
        assertEquals(narrowSlice, visibleArcHit)
    }

    @Test
    fun `hitTest applies exploded offset when locating a shifted slice`() {
        val shiftedSlice = slice(1, 1f)
        val arcs = DonutGeometry.computeSliceArcs(listOf(shiftedSlice))

        val hitWithoutOffset = DonutGeometry.hitTest(
            offsetX = 0f,
            offsetY = 180f,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
            explodedOffset = 0f,
        )
        val hitWithOffset = DonutGeometry.hitTest(
            offsetX = 0f,
            offsetY = 180f,
            centerX = 0f,
            centerY = 0f,
            innerRadius = 50f,
            outerRadius = 150f,
            arcs = arcs,
            explodedOffset = 80f,
        )

        assertNull(hitWithoutOffset)
        assertEquals(shiftedSlice, hitWithOffset)
    }

    // ---- framePoint: symmetric rectangle (hhTop == hhBot) ----

    @Test
    fun `framePoint t=0 returns top-left corner of symmetric rectangle`() {
        val hw = 100f
        val hh = 50f
        val p = DonutGeometry.framePoint(t = 0f, hw = hw, hhTop = hh, hhBot = hh)
        assertEquals(0f, p.x, 0.5f)
        assertEquals(-hh, p.y, 0.5f)
    }

    @Test
    fun `framePoint t=0 25 on symmetric rectangle returns right edge midpoint`() {
        val hw = 100f
        val hh = 50f
        // perimeter = 4*100 + 2*100 = 600; t=0.25 → d=150; first top edge=100, then right side 50
        val p = DonutGeometry.framePoint(t = 0.25f, hw = hw, hhTop = hh, hhBot = hh)
        assertEquals(hw, p.x, 0.5f)
        assertEquals(0f, p.y, 0.5f)
    }

    @Test
    fun `framePoint t=0 5 on symmetric rectangle returns bottom-center`() {
        val hw = 100f
        val hh = 50f
        // perimeter=600; t=0.5 → d=300; bottom edge [200,400): x=hw-(d-hw-side)=100-100=0, y=hhBot
        val p = DonutGeometry.framePoint(t = 0.5f, hw = hw, hhTop = hh, hhBot = hh)
        assertEquals(0f, p.x, 1f)
        assertEquals(hh, p.y, 1f)
    }

    @Test
    fun `framePoint t=1 0 wraps to same position as t=0`() {
        val hw = 80f
        val hh = 40f
        val p0 = DonutGeometry.framePoint(t = 0f, hw = hw, hhTop = hh, hhBot = hh)
        val p1 = DonutGeometry.framePoint(t = 1f, hw = hw, hhTop = hh, hhBot = hh)
        assertEquals(p0.x, p1.x, 0.5f)
        assertEquals(p0.y, p1.y, 0.5f)
    }

    @Test
    fun `framePoint negative t wraps modulo to the same position as t=0`() {
        val hw = 80f
        val hh = 40f
        val p0 = DonutGeometry.framePoint(t = 0f, hw = hw, hhTop = hh, hhBot = hh)
        val pNeg = DonutGeometry.framePoint(t = -1f, hw = hw, hhTop = hh, hhBot = hh)
        assertEquals(p0.x, pNeg.x, 0.5f)
        assertEquals(p0.y, pNeg.y, 0.5f)
    }

    // ---- framePoint: asymmetric rectangle (hhTop > hhBot) ----

    @Test
    fun `framePoint asymmetric hhTop greater than hhBot yields negative y on top edge`() {
        val hw = 100f
        val hhTop = 87.4f
        val hhBot = 73.9f
        val p = DonutGeometry.framePoint(t = 0f, hw = hw, hhTop = hhTop, hhBot = hhBot)
        assertTrue("y must be negative (top of rectangle), got ${p.y}", p.y < 0f)
    }

    @Test
    fun `framePoint asymmetric right-edge x equals hw`() {
        val hw = 100f
        val hhTop = 87.4f
        val hhBot = 73.9f
        // right side is at x=hw for any t that lands in side segment
        // perimeter = 4*hw + 2*(hhTop+hhBot) = 400 + 2*161.3 = 722.6
        // top edge ends at hw=100; right side from 100 to 100+161.3=261.3
        // pick t so d ≈ 150 (lands in right side)
        val perimeter = 4f * hw + 2f * (hhTop + hhBot)
        val t = 150f / perimeter
        val p = DonutGeometry.framePoint(t = t, hw = hw, hhTop = hhTop, hhBot = hhBot)
        assertEquals(hw, p.x, 0.5f)
    }

    @Test
    fun `framePoint asymmetric left-edge x equals negative hw`() {
        val hw = 100f
        val hhTop = 87.4f
        val hhBot = 73.9f
        val perimeter = 4f * hw + 2f * (hhTop + hhBot)
        // left edge starts at d = 3*hw + side (NOT 3*hw + 2*side)
        val side = hhTop + hhBot
        val t = (3f * hw + side + 10f) / perimeter
        val p = DonutGeometry.framePoint(t = t % 1f, hw = hw, hhTop = hhTop, hhBot = hhBot)
        assertEquals(-hw, p.x, 0.5f)
    }

    // ---- framePoint: even distribution of N icons ----

    @Test
    fun `framePoint even index distribution for four slices stays on rectangle perimeter`() {
        val hw = 100f
        val hhTop = 87.4f
        val hhBot = 73.9f
        val count = 4
        val points = (0 until count).map { i ->
            DonutGeometry.framePoint(t = i.toFloat() / count, hw = hw, hhTop = hhTop, hhBot = hhBot)
        }
        points.forEach { p ->
            // each point must lie ON the rectangle edge (one coordinate at ±hw or ±hhTop/hhBot)
            val onHorizontal = abs(p.y + hhTop) < 0.5f || abs(p.y - hhBot) < 0.5f
            val onVertical = abs(p.x - hw) < 0.5f || abs(p.x + hw) < 0.5f
            assertTrue(
                "Point (${p.x}, ${p.y}) must be on rectangle perimeter",
                onHorizontal || onVertical,
            )
        }
    }

    @Test
    fun `framePoint even index distribution for eight slices produces distinct points`() {
        val hw = 100f
        val hhTop = 87.4f
        val hhBot = 73.9f
        val count = 8
        val points = (0 until count).map { i ->
            DonutGeometry.framePoint(t = i.toFloat() / count, hw = hw, hhTop = hhTop, hhBot = hhBot)
        }
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                val dx = abs(points[i].x - points[j].x)
                val dy = abs(points[i].y - points[j].y)
                assertTrue(
                    "Points at index $i and $j must be distinct",
                    dx > 0.1f || dy > 0.1f,
                )
            }
        }
    }

    // ---- computeSliceArcs: gap-split geometry contract ----

    @Test
    fun `computeSliceArcs total sweep of single full-circle slice is 360 degrees`() {
        val arcs = DonutGeometry.computeSliceArcs(listOf(slice(1, 1f)))
        assertEquals(360f, arcs[0].sweepDegrees, 0.001f)
    }

    @Test
    fun `computeSliceArcs three equal slices each sweep 120 degrees`() {
        val arcs = DonutGeometry.computeSliceArcs(
            listOf(slice(1, 1f / 3f), slice(2, 1f / 3f), slice(3, 1f / 3f)),
        )
        arcs.forEach { assertEquals(120f, it.sweepDegrees, 0.5f) }
    }

    @Test
    fun `computeSliceArcs slices are contiguous — each start equals previous start plus sweep`() {
        val slices = listOf(slice(1, 0.4f), slice(2, 0.35f), slice(3, 0.25f))
        val arcs = DonutGeometry.computeSliceArcs(slices)
        for (i in 1 until arcs.size) {
            val expected = arcs[i - 1].startAngleDegrees + arcs[i - 1].sweepDegrees
            assertEquals(expected, arcs[i].startAngleDegrees, 0.001f)
        }
    }

    // ---- gap-split contract (applied in MonefyDonutChart, derived from sweep) ----

    @Test
    fun `gap split is bounded by min of sliceGapDegrees and 0 6 times sweep`() {
        val sliceGapDegrees = 5f
        val testSweeps = listOf(3f, 5f, 10f, 60f, 120f, 180f, 360f)
        testSweeps.forEach { sweep ->
            val gap = minOf(sliceGapDegrees, sweep * 0.6f)
            val halfGap = gap / 2f
            val effectiveSweep = (sweep - gap).coerceAtLeast(0f)
            assertTrue(
                "sweep=$sweep: gap half must not exceed sweep/2, got halfGap=$halfGap",
                halfGap <= sweep / 2f + 0.001f,
            )
            assertTrue(
                "sweep=$sweep: effectiveSweep=$effectiveSweep must be >= 0",
                effectiveSweep >= 0f,
            )
        }
    }

    @Test
    fun `gap split for very narrow slice uses 0 6 factor instead of capped 5 degrees`() {
        val sliceGapDegrees = 5f
        val narrowSweep = 4f
        val gap = minOf(sliceGapDegrees, narrowSweep * 0.6f)
        // 4 * 0.6 = 2.4, which is less than 5 → gap is 2.4
        assertEquals(2.4f, gap, 0.001f)
    }

    @Test
    fun `gap split for wide slice is capped at sliceGapDegrees`() {
        val sliceGapDegrees = 5f
        val wideSweep = 120f
        val gap = minOf(sliceGapDegrees, wideSweep * 0.6f)
        assertEquals(sliceGapDegrees, gap, 0.001f)
    }

    @Test
    fun `gap split for 8 33 degree sweep uses 0 6 factor leaving positive effective sweep`() {
        val sliceGapDegrees = 5f
        val sweep = 360f / 8f
        val gap = minOf(sliceGapDegrees, sweep * 0.6f)
        val effectiveSweep = (sweep - gap).coerceAtLeast(0f)
        assertTrue("effectiveSweep=$effectiveSweep must be > 0 for 8 equal slices", effectiveSweep > 0f)
    }

    @Test
    fun `gapForSweep clamps a negative configured max gap to zero`() {
        assertEquals(0f, DonutGeometry.gapForSweep(sweepDegrees = 120f, maxGapDegrees = -5f), 0.001f)
    }

    @Test
    fun `mid angle zero projects contour icon center to the right of donut center`() {
        val arc = SliceArc(
            slice = slice(1, 1f),
            startAngleDegrees = -30f,
            sweepDegrees = 60f,
        )

        val iconCenter = contourPoint(
            arc = arc,
            radius = 100f + 8f + 20f,
            explodedOffset = 12f,
        )

        assertEquals(140f, iconCenter.first, 0.001f)
        assertEquals(0f, iconCenter.second, 0.001f)
        assertTrue(iconCenter.first > 0f)
    }

    @Test
    fun `mid angle minus ninety projects contour icon center above donut center`() {
        val arc = SliceArc(
            slice = slice(1, 1f),
            startAngleDegrees = -120f,
            sweepDegrees = 60f,
        )

        val iconCenter = contourPoint(
            arc = arc,
            radius = 100f + 8f + 20f,
            explodedOffset = 12f,
        )

        assertEquals(0f, iconCenter.first, 0.001f)
        assertEquals(-140f, iconCenter.second, 0.001f)
        assertTrue(iconCenter.second < 0f)
    }

    private fun pointAtAngleDegrees(angleDegrees: Float, radius: Float): Pair<Float, Float> {
        val radians = Math.toRadians(angleDegrees.toDouble())
        return (radius * cos(radians)).toFloat() to (radius * sin(radians)).toFloat()
    }

    private fun contourPoint(
        arc: SliceArc,
        radius: Float,
        explodedOffset: Float,
    ): Pair<Float, Float> {
        val mid = DonutGeometry.midAngleRadians(arc)
        val dx = cos(mid) * (radius + explodedOffset)
        val dy = sin(mid) * (radius + explodedOffset)
        return dx to dy
    }
}
