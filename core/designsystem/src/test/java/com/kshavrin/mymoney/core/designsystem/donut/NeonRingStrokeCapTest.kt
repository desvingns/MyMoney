package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.graphics.StrokeCap
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM unit tests for [neonRingStrokeCap].
 *
 * StrokeCap is a @JvmInline value class — no Android runtime needed.
 * Placed in src/test/ alongside NeonRingGradientStopsTest (same package, same layer).
 */
class NeonRingStrokeCapTest {
    @Test
    fun `zero fraction returns Round`() {
        assertEquals(StrokeCap.Round, neonRingStrokeCap(0f))
    }

    @Test
    fun `half fraction returns Round`() {
        assertEquals(StrokeCap.Round, neonRingStrokeCap(0.5f))
    }

    @Test
    fun `fraction just below one returns Round`() {
        assertEquals(StrokeCap.Round, neonRingStrokeCap(0.999f))
    }

    @Test
    fun `fraction exactly one returns Butt`() {
        assertEquals(StrokeCap.Butt, neonRingStrokeCap(1f))
    }

    @Test
    fun `fraction above one returns Butt`() {
        assertEquals(StrokeCap.Butt, neonRingStrokeCap(1.5f))
    }
}
