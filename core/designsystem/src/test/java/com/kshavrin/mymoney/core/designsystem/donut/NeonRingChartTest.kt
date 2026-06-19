package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.unit.dp
import com.kshavrin.mymoney.core.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeonRingChartTest {
    @Test
    fun `layout uses fraction to derive a proportional sweep`() {
        val layout = calculateNeonRingChartLayout(fraction = 0.5f)

        assertEquals(180f, layout.sweepAngleDegrees, 0.001f)
        assertTrue(layout.showsGradientArc)
    }

    @Test
    fun `layout hides the gradient arc when fraction is zero`() {
        val layout = calculateNeonRingChartLayout(fraction = 0f)

        assertEquals(0f, layout.sweepAngleDegrees, 0.001f)
        assertFalse(layout.showsGradientArc)
    }

    @Test
    fun `layout uses the 200dp ring contract and keeps the stroke-aware glow margin`() {
        val layout = calculateNeonRingChartLayout(fraction = 0.25f)

        assertEquals(200.dp, Spacing.neonRingDiameter)
        assertEquals(16.dp, Spacing.neonRingStrokeWidth)
        assertEquals(264.dp, layout.containerSize)
        assertEquals(160.dp, layout.innerDiameter)
        assertTrue(layout.containerSize > Spacing.neonRingDiameter + Spacing.neonRingGlowRadius * 2)
        assertTrue(layout.innerDiameter < Spacing.neonRingDiameter)
    }
}
