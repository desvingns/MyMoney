package com.kshavrin.mymoney.core.designsystem.donut

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM unit tests for [neonRingGradientStops].
 *
 * Color is a @JvmInline value class (Long) — no Android runtime needed.
 * Placed in src/test/ alongside NeonRingChartTest which already uses internal APIs
 * from this package.
 */
class NeonRingGradientStopsTest {

    // ── green-like pair ──────────────────────────────────────────────────────

    private val greenStart = Color(0xFF00FF88)
    private val greenEnd = Color(0xFF00AA44)

    // ── red-like pair ────────────────────────────────────────────────────────

    private val redStart = Color(0xFFFF4444)
    private val redEnd = Color(0xFFCC0000)

    // ── partial fill: fraction < 1f ──────────────────────────────────────────

    @Test
    fun `partial fill fraction 0f produces three stops at positions 0, 0, 1 with end color for last two`() {
        val stops = neonRingGradientStops(fraction = 0f, gradientStart = greenStart, gradientEnd = greenEnd)

        assertEquals(3, stops.size)
        assertEquals(0f, stops[0].first, 0f)
        assertEquals(greenStart, stops[0].second)
        assertEquals(0f, stops[1].first, 0f)
        assertEquals(greenEnd, stops[1].second)
        assertEquals(1f, stops[2].first, 0f)
        assertEquals(greenEnd, stops[2].second)
    }

    @Test
    fun `partial fill fraction 0_25f middle stop position equals fraction`() {
        val stops = neonRingGradientStops(fraction = 0.25f, gradientStart = greenStart, gradientEnd = greenEnd)

        assertEquals(3, stops.size)
        assertEquals(0f, stops[0].first, 0f)
        assertEquals(greenStart, stops[0].second)
        assertEquals(0.25f, stops[1].first, 0f)
        assertEquals(greenEnd, stops[1].second)
        assertEquals(1f, stops[2].first, 0f)
        assertEquals(greenEnd, stops[2].second)
    }

    @Test
    fun `partial fill fraction 0_5f middle stop position equals fraction`() {
        val stops = neonRingGradientStops(fraction = 0.5f, gradientStart = redStart, gradientEnd = redEnd)

        assertEquals(3, stops.size)
        assertEquals(0f, stops[0].first, 0f)
        assertEquals(redStart, stops[0].second)
        assertEquals(0.5f, stops[1].first, 0f)
        assertEquals(redEnd, stops[1].second)
        assertEquals(1f, stops[2].first, 0f)
        assertEquals(redEnd, stops[2].second)
    }

    @Test
    fun `partial fill fraction 0_9f middle stop position equals fraction`() {
        val stops = neonRingGradientStops(fraction = 0.9f, gradientStart = redStart, gradientEnd = redEnd)

        assertEquals(3, stops.size)
        assertEquals(0f, stops[0].first, 0f)
        assertEquals(redStart, stops[0].second)
        assertEquals(0.9f, stops[1].first, 0.0001f)
        assertEquals(redEnd, stops[1].second)
        assertEquals(1f, stops[2].first, 0f)
        assertEquals(redEnd, stops[2].second)
    }

    // ── full ring: fraction >= 1f ────────────────────────────────────────────

    @Test
    fun `full ring fraction 1f produces seamless stops with start color at both ends`() {
        val stops = neonRingGradientStops(fraction = 1f, gradientStart = greenStart, gradientEnd = greenEnd)

        assertEquals(3, stops.size)
        assertEquals(0f, stops[0].first, 0f)
        assertEquals(greenStart, stops[0].second)
        assertEquals(0.5f, stops[1].first, 0f)
        assertEquals(greenEnd, stops[1].second)
        assertEquals(1f, stops[2].first, 0f)
        assertEquals(greenStart, stops[2].second)
    }

    @Test
    fun `full ring fraction 1f seamlessness invariant first and last stop colors are equal`() {
        val stops = neonRingGradientStops(fraction = 1f, gradientStart = redStart, gradientEnd = redEnd)

        assertEquals(
            "seamlessness violated: first stop color must equal last stop color",
            stops[0].second,
            stops[2].second,
        )
        assertEquals(redStart, stops[0].second)
        assertEquals(redStart, stops[2].second)
    }

    @Test
    fun `over-full fraction 1_5f is treated as full ring and produces symmetric seamless stops`() {
        val stops = neonRingGradientStops(fraction = 1.5f, gradientStart = greenStart, gradientEnd = greenEnd)

        assertEquals(3, stops.size)
        assertEquals(0f, stops[0].first, 0f)
        assertEquals(greenStart, stops[0].second)
        assertEquals(0.5f, stops[1].first, 0f)
        assertEquals(greenEnd, stops[1].second)
        assertEquals(1f, stops[2].first, 0f)
        assertEquals(greenStart, stops[2].second)
    }

    @Test
    fun `over-full fraction 1_5f seamlessness invariant first and last stop colors are equal`() {
        val stops = neonRingGradientStops(fraction = 1.5f, gradientStart = redStart, gradientEnd = redEnd)

        assertEquals(stops[0].second, stops[2].second)
    }

    // ── color agnosticism: partial vs full with swapped color pair ──────────

    @Test
    fun `partial fill works identically with red pair as with green pair`() {
        val greenStops = neonRingGradientStops(0.6f, greenStart, greenEnd)
        val redStops = neonRingGradientStops(0.6f, redStart, redEnd)

        // positions must be identical regardless of color
        assertEquals(greenStops[0].first, redStops[0].first, 0f)
        assertEquals(greenStops[1].first, redStops[1].first, 0f)
        assertEquals(greenStops[2].first, redStops[2].first, 0f)

        // but colors must differ between the two pairs
        assertEquals(redStart, redStops[0].second)
        assertEquals(greenStart, greenStops[0].second)
    }

    @Test
    fun `full ring works identically with red pair as with green pair`() {
        val greenStops = neonRingGradientStops(1f, greenStart, greenEnd)
        val redStops = neonRingGradientStops(1f, redStart, redEnd)

        // positions must be identical
        assertEquals(greenStops[0].first, redStops[0].first, 0f)
        assertEquals(greenStops[1].first, redStops[1].first, 0f)
        assertEquals(greenStops[2].first, redStops[2].first, 0f)

        // seamlessness holds for both
        assertEquals(greenStops[0].second, greenStops[2].second)
        assertEquals(redStops[0].second, redStops[2].second)
    }
}
