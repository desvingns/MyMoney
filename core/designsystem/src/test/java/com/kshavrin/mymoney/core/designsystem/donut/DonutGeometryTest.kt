package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
}
