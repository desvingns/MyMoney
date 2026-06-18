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
    fun `layout reserves glow allowance outside the ring and keeps a smaller inner slot`() {
        val layout = calculateNeonRingChartLayout(fraction = 0.25f)

        assertEquals(316.dp, layout.containerSize)
        assertEquals(200.dp, layout.innerDiameter)
        assertTrue(layout.containerSize > Spacing.neonRingDiameter)
        assertTrue(layout.innerDiameter < Spacing.neonRingDiameter)
    }
}
