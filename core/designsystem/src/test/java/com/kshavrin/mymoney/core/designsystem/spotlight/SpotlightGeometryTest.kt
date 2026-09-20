package com.kshavrin.mymoney.core.designsystem.spotlight

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 0.01f

class SpotlightGeometryTest {

    // ── cutoutRect ────────────────────────────────────────────────────────────

    @Test
    fun `circle cutoutRect is square sized to max dimension with padding`() {
        val bounds = Rect(100f, 200f, 148f, 248f)
        val result = cutoutRect(bounds, SpotlightShape.Circle, padding = 8f)
        val side = maxOf(bounds.width, bounds.height) + 16f
        assertEquals(side, result.width, DELTA)
        assertEquals(side, result.height, DELTA)
    }

    @Test
    fun `circle cutoutRect is centered on original bounds`() {
        val bounds = Rect(100f, 200f, 148f, 248f)
        val result = cutoutRect(bounds, SpotlightShape.Circle, padding = 0f)
        assertEquals(bounds.center.x, result.center.x, DELTA)
        assertEquals(bounds.center.y, result.center.y, DELTA)
    }

    @Test
    fun `circle cutoutRect uses wider dimension as side when bounds is not square`() {
        val bounds = Rect(0f, 0f, 100f, 40f)
        val result = cutoutRect(bounds, SpotlightShape.Circle, padding = 0f)
        assertEquals(100f, result.width, DELTA)
        assertEquals(100f, result.height, DELTA)
    }

    @Test
    fun `rounded rect cutoutRect inflates bounds by padding on all sides`() {
        val bounds = Rect(10f, 20f, 60f, 80f)
        val result = cutoutRect(bounds, SpotlightShape.RoundedRect, padding = 8f)
        assertEquals(bounds.left - 8f, result.left, DELTA)
        assertEquals(bounds.top - 8f, result.top, DELTA)
        assertEquals(bounds.right + 8f, result.right, DELTA)
        assertEquals(bounds.bottom + 8f, result.bottom, DELTA)
    }

    @Test
    fun `zero padding leaves bounds unchanged for rounded rect`() {
        val bounds = Rect(10f, 20f, 60f, 80f)
        val result = cutoutRect(bounds, SpotlightShape.RoundedRect, padding = 0f)
        assertEquals(bounds.left, result.left, DELTA)
        assertEquals(bounds.top, result.top, DELTA)
        assertEquals(bounds.right, result.right, DELTA)
        assertEquals(bounds.bottom, result.bottom, DELTA)
    }

    // ── cardPlacement ─────────────────────────────────────────────────────────

    @Test
    fun `places card below when space below is larger`() {
        val cutout = Rect(0f, 100f, 400f, 300f)
        val placement = cardPlacement(cutout = cutout, containerHeight = 800f, cardHeight = 150f)
        assertEquals(CardPlacement.Below, placement)
    }

    @Test
    fun `places card above when space above is larger`() {
        val cutout = Rect(0f, 500f, 400f, 700f)
        val placement = cardPlacement(cutout = cutout, containerHeight = 800f, cardHeight = 150f)
        assertEquals(CardPlacement.Above, placement)
    }

    @Test
    fun `prefers above when space is equal`() {
        val cutout = Rect(0f, 400f, 400f, 400f)
        val placement = cardPlacement(cutout = cutout, containerHeight = 800f, cardHeight = 0f)
        assertEquals(CardPlacement.Above, placement)
    }

    @Test
    fun `card height does not affect which side has more space`() {
        val cutout = Rect(0f, 600f, 400f, 700f)
        val placement = cardPlacement(cutout = cutout, containerHeight = 800f, cardHeight = 500f)
        assertEquals(CardPlacement.Above, placement)
    }

    @Test
    fun `cutout at top of screen places card below`() {
        val cutout = Rect(0f, 0f, 400f, 50f)
        val placement = cardPlacement(cutout = cutout, containerHeight = 800f, cardHeight = 100f)
        assertEquals(CardPlacement.Below, placement)
    }

    @Test
    fun `cutout at bottom of screen places card above`() {
        val cutout = Rect(0f, 750f, 400f, 800f)
        val placement = cardPlacement(cutout = cutout, containerHeight = 800f, cardHeight = 100f)
        assertEquals(CardPlacement.Above, placement)
    }

    // ── rect invariants ───────────────────────────────────────────────────────

    @Test
    fun `cutout rect width and height are non-negative for any shape`() {
        val bounds = Rect(50f, 50f, 50f, 50f)
        for (shape in SpotlightShape.entries) {
            val result = cutoutRect(bounds, shape, padding = 0f)
            assertTrue("width must be >= 0 for $shape", result.width >= 0f)
            assertTrue("height must be >= 0 for $shape", result.height >= 0f)
        }
    }
}
