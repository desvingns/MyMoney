package com.kshavrin.mymoney.core.designsystem.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTrendChartGeometryTest {
    // ---- helpers ----

    private fun geometry(
        values: List<Float>,
        width: Float = 400f,
        height: Float = 200f,
        horizontalPadding: Float = 0f,
        verticalPadding: Float = 0f,
        gridLineCount: Int = BALANCE_TREND_CHART_GRIDLINE_COUNT,
    ) = calculateBalanceTrendChartGeometry(
        values = values,
        width = width,
        height = height,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        gridLineCount = gridLineCount,
    )

    // ---- empty series ----

    @Test
    fun `empty series produces no points and null marker`() {
        val g = geometry(emptyList())
        assertTrue(g.points.isEmpty())
        assertNull(g.marker)
    }

    @Test
    fun `empty series still produces gridlines`() {
        val g = geometry(emptyList(), gridLineCount = 3)
        assertEquals(3, g.gridLineXs.size)
    }

    @Test
    fun `empty series zero line is null`() {
        val g = geometry(emptyList())
        assertNull(g.zeroLineY)
    }

    // ---- single point ----

    @Test
    fun `single point is placed at horizontal center`() {
        val g = geometry(listOf(5f), width = 400f)
        assertEquals(1, g.points.size)
        assertEquals(200f, g.points[0].x, 0.5f)
    }

    @Test
    fun `single point is placed at vertical center when all values are equal`() {
        val g = geometry(listOf(5f), height = 200f)
        assertEquals(100f, g.points[0].y, 0.5f)
    }

    @Test
    fun `single-point marker equals the only point`() {
        val g = geometry(listOf(5f))
        assertNotNull(g.marker)
        assertEquals(g.points[0].x, g.marker!!.x, 0.001f)
        assertEquals(g.points[0].y, g.marker!!.y, 0.001f)
    }

    // ---- min–max vertical scaling ----

    @Test
    fun `maximum value maps to top of plot area (y = verticalPadding)`() {
        val vPad = 10f
        val g = geometry(listOf(0f, 10f), height = 200f, verticalPadding = vPad)
        val maxPoint = g.points.maxByOrNull { -it.y }!!
        assertEquals(vPad, maxPoint.y, 0.5f)
    }

    @Test
    fun `minimum value maps to bottom of plot area (y = height - verticalPadding)`() {
        val vPad = 10f
        val g = geometry(listOf(0f, 10f), height = 200f, verticalPadding = vPad)
        val minPoint = g.points.maxByOrNull { it.y }!!
        assertEquals(200f - vPad, minPoint.y, 0.5f)
    }

    @Test
    fun `all-equal values place all points at vertical midpoint`() {
        val g = geometry(listOf(7f, 7f, 7f), height = 200f, verticalPadding = 0f)
        g.points.forEach { assertEquals(100f, it.y, 0.5f) }
    }

    // ---- horizontal point distribution ----

    @Test
    fun `five points span the full plot width from left edge to right edge`() {
        val hPad = 0f
        val width = 400f
        val g = geometry(listOf(1f, 2f, 3f, 4f, 5f), width = width, horizontalPadding = hPad)
        assertEquals(5, g.points.size)
        assertEquals(0f, g.points.first().x, 0.5f)
        assertEquals(width, g.points.last().x, 0.5f)
    }

    @Test
    fun `points are evenly spaced across the plot width`() {
        val width = 400f
        val g = geometry(listOf(1f, 2f, 3f, 4f, 5f), width = width, horizontalPadding = 0f)
        val step = width / 4f
        g.points.forEachIndexed { index, point ->
            assertEquals(step * index, point.x, 0.5f)
        }
    }

    // ---- gridlines ----

    @Test
    fun `three gridlines are produced by default`() {
        val g = geometry(listOf(1f, 2f, 3f))
        assertEquals(BALANCE_TREND_CHART_GRIDLINE_COUNT, g.gridLineXs.size)
    }

    @Test
    fun `gridlines are evenly distributed across the plot width`() {
        val width = 400f
        val g = geometry(listOf(1f, 2f), width = width, horizontalPadding = 0f, gridLineCount = 3)
        val step = width / 4f
        assertEquals(step * 1, g.gridLineXs[0], 0.5f)
        assertEquals(step * 2, g.gridLineXs[1], 0.5f)
        assertEquals(step * 3, g.gridLineXs[2], 0.5f)
    }

    @Test
    fun `zero gridLineCount produces empty gridLineXs list`() {
        val g = geometry(listOf(1f, 2f, 3f), gridLineCount = 0)
        assertTrue(g.gridLineXs.isEmpty())
    }

    @Test
    fun `horizontal padding shifts gridlines inward from the edges`() {
        val hPad = 20f
        val width = 400f
        val g = geometry(listOf(1f, 2f), width = width, horizontalPadding = hPad, gridLineCount = 1)
        val plotWidth = width - 2f * hPad
        val expectedX = hPad + plotWidth / 2f
        assertEquals(expectedX, g.gridLineXs[0], 0.5f)
    }

    // ---- zero line (D8) ----

    @Test
    fun `zero line is null when all values are positive`() {
        val g = geometry(listOf(10f, 6f, 12f, 12f, 15f))
        assertNull(g.zeroLineY)
    }

    @Test
    fun `zero line is null when all values are negative`() {
        val g = geometry(listOf(-5f, -3f, -8f, -1f))
        assertNull(g.zeroLineY)
    }

    @Test
    fun `zero line is null when series touches zero but does not cross it from below`() {
        val g = geometry(listOf(0f, 5f, 3f))
        assertNull(g.zeroLineY)
    }

    @Test
    fun `zero line is present when series crosses zero (mixed signs)`() {
        val g = geometry(listOf(4f, 3f, 1f, -2f, -3f))
        assertNotNull(g.zeroLineY)
    }

    @Test
    fun `zero line y-coordinate is between plot top and plot bottom`() {
        val vPad = 10f
        val height = 200f
        val g = geometry(listOf(4f, 3f, 1f, -2f, -3f), height = height, verticalPadding = vPad)
        val zeroY = g.zeroLineY!!
        assertTrue("zeroLineY=$zeroY must be below plot top ($vPad)", zeroY > vPad)
        assertTrue("zeroLineY=$zeroY must be above plot bottom (${height - vPad})", zeroY < height - vPad)
    }

    @Test
    fun `zero line y is at midpoint when values are exactly +N and -N`() {
        val g = geometry(listOf(-5f, 5f), height = 200f, verticalPadding = 0f)
        assertEquals(100f, g.zeroLineY!!, 0.5f)
    }

    // ---- marker (D9) ----

    @Test
    fun `marker is null for empty series`() {
        val g = geometry(emptyList())
        assertNull(g.marker)
    }

    @Test
    fun `marker equals the last point in a multi-point series`() {
        val g = geometry(listOf(1f, 5f, 3f, 8f, 2f))
        assertNotNull(g.marker)
        val last = g.points.last()
        assertEquals(last.x, g.marker!!.x, 0.001f)
        assertEquals(last.y, g.marker!!.y, 0.001f)
    }

    @Test
    fun `marker is at the rightmost x when series has five points`() {
        val width = 400f
        val g = geometry(listOf(1f, 2f, 3f, 4f, 5f), width = width, horizontalPadding = 0f)
        assertEquals(width, g.marker!!.x, 0.5f)
    }

    // ---- ChartColorRule and renderer geometry ----

    @Test
    fun `ChartColorRule exposes exactly the four canonical modes and their persisted ids`() {
        assertEquals(
            listOf(
                ChartColorRule.Solid,
                ChartColorRule.AlwaysGreen,
                ChartColorRule.AlwaysRed,
                ChartColorRule.ByDirection,
            ),
            ChartColorRule.entries.toList(),
        )
        assertEquals(
            listOf("solid", "always_green", "always_red", "by_direction"),
            ChartColorRule.entries.map(ChartColorRule::id),
        )
    }

    @Test
    fun `Default color rule is ByDirection`() {
        assertEquals(ChartColorRule.ByDirection, ChartColorRule.Default)
    }

    @Test
    fun `canonical color modes resolve to their required line colors`() {
        val solid = Color.Blue
        val income = Color.Green
        val expense = Color.Red

        assertEquals(solid, resolveBalanceTrendChartLineColor(ChartColorRule.Solid, solid, income, expense))
        assertEquals(income, resolveBalanceTrendChartLineColor(ChartColorRule.AlwaysGreen, solid, income, expense))
        assertEquals(expense, resolveBalanceTrendChartLineColor(ChartColorRule.AlwaysRed, solid, income, expense))
        assertEquals(income, resolveBalanceTrendChartLineColor(ChartColorRule.ByDirection, solid, income, expense))
    }

    @Test
    fun `projection colors stay green and red independently of the line color mode`() {
        val income = Color.Green
        val expense = Color.Red
        val colors = balanceTrendChartProjectionColors(income, expense)

        assertEquals(income.copy(alpha = 0.22f), colors.above)
        assertEquals(expense.copy(alpha = 0.22f), colors.below)
    }

    @Test
    fun `linear splitter divides a strict crossing at the requested horizontal line`() {
        val segments =
            splitBalanceTrendChartSegmentsAtHorizontalLine(
                points = listOf(Offset(0f, -5f), Offset(10f, 5f)),
                horizontalLineY = 0f,
            )

        assertEquals(2, segments.size)
        assertEquals(Offset(5f, 0f), segments[0].end)
        assertEquals(Offset(5f, 0f), segments[1].start)
        assertEquals(BalanceTrendChartHorizontalZone.AboveOrOn, segments[0].zone)
        assertEquals(BalanceTrendChartHorizontalZone.Below, segments[1].zone)
    }

    @Test
    fun `linear splitter keeps touch points green and only splits strict sign changes`() {
        val touched =
            splitBalanceTrendChartSegmentsAtHorizontalLine(
                points = listOf(Offset(0f, 0f), Offset(10f, 0f)),
                horizontalLineY = 0f,
            )
        val startTouch =
            splitBalanceTrendChartSegmentsAtHorizontalLine(
                points = listOf(Offset(0f, 0f), Offset(10f, -5f)),
                horizontalLineY = 0f,
            )

        assertEquals(1, touched.size)
        assertEquals(BalanceTrendChartHorizontalZone.AboveOrOn, touched.single().zone)
        assertEquals(1, startTouch.size)
        assertEquals(BalanceTrendChartHorizontalZone.AboveOrOn, startTouch.single().zone)
    }

    @Test
    fun `linear splitter keeps single-sign segments in one deterministic zone`() {
        val above =
            splitBalanceTrendChartSegmentsAtHorizontalLine(
                points = listOf(Offset(0f, -5f), Offset(10f, -1f)),
                horizontalLineY = 0f,
            )
        val below =
            splitBalanceTrendChartSegmentsAtHorizontalLine(
                points = listOf(Offset(0f, 5f), Offset(10f, 1f)),
                horizontalLineY = 0f,
            )

        assertEquals(BalanceTrendChartHorizontalZone.AboveOrOn, above.single().zone)
        assertEquals(BalanceTrendChartHorizontalZone.Below, below.single().zone)
    }

    @Test
    fun `linear splitter handles the by-direction fixture and degenerate series`() {
        val segments =
            splitBalanceTrendChartSegmentsAtHorizontalLine(
                points = listOf(Offset(0f, 1000f), Offset(1f, 1200f), Offset(2f, 800f)),
                horizontalLineY = 1000f,
            )

        assertEquals(3, segments.size)
        assertEquals(Offset(1.5f, 1000f), segments[1].end)
        assertEquals(BalanceTrendChartHorizontalZone.Below, segments[0].zone)
        assertEquals(BalanceTrendChartHorizontalZone.Below, segments[1].zone)
        assertEquals(BalanceTrendChartHorizontalZone.AboveOrOn, segments[2].zone)
        assertTrue(splitBalanceTrendChartSegmentsAtHorizontalLine(emptyList(), 0f).isEmpty())
        assertTrue(splitBalanceTrendChartSegmentsAtHorizontalLine(listOf(Offset.Zero), 0f).isEmpty())
    }

    @Test
    fun `smooth projection uses the same split cubic geometry as the visible smooth line`() {
        val points = listOf(Offset(0f, 0f), Offset(100f, 100f))
        val projection =
            calculateBalanceTrendChartProjectionSegments(
                points = points,
                baseline = 50f,
                style = ChartStyle.Smooth,
            )

        assertEquals(2, projection.size)
        val first = (projection[0] as BalanceTrendChartProjectionSegment.Cubic).segment.cubic
        val second = (projection[1] as BalanceTrendChartProjectionSegment.Cubic).segment.cubic
        assertEquals(50f, first.end.x, 0.001f)
        assertEquals(50f, first.end.y, 0.001f)
        assertEquals(50f, second.start.x, 0.001f)
        assertEquals(50f, second.start.y, 0.001f)
        assertEquals(25f, first.firstControl.x, 0.001f)
        assertEquals(0f, first.firstControl.y, 0.001f)
        assertEquals(37.5f, first.secondControl.x, 0.001f)
        assertEquals(25f, first.secondControl.y, 0.001f)
        assertEquals(62.5f, second.firstControl.x, 0.001f)
        assertEquals(75f, second.firstControl.y, 0.001f)
        assertEquals(75f, second.secondControl.x, 0.001f)
        assertEquals(100f, second.secondControl.y, 0.001f)
    }

    @Test
    fun `projection geometry is absent for bars and linear for line style`() {
        val points = listOf(Offset(0f, -5f), Offset(10f, 5f))

        assertTrue(
            calculateBalanceTrendChartProjectionSegments(
                points = points,
                baseline = 0f,
                style = ChartStyle.Bars,
            ).isEmpty(),
        )
        assertTrue(
            calculateBalanceTrendChartProjectionSegments(
                points = points,
                baseline = 0f,
                style = ChartStyle.Line,
            ).all { it is BalanceTrendChartProjectionSegment.Linear },
        )
    }

    // ---- acceptance scenarios from SPEC ----

    @Test
    fun `crossing series 4 3 1 -2 -3 produces a zero line`() {
        val g = geometry(listOf(4f, 3f, 1f, -2f, -3f))
        assertNotNull("zero line must be present when series crosses zero", g.zeroLineY)
    }

    @Test
    fun `single-sign series 10 6 12 12 15 produces no zero line`() {
        val g = geometry(listOf(10f, 6f, 12f, 12f, 15f))
        assertNull("zero line must be absent for a positive-only series", g.zeroLineY)
    }

    @Test
    fun `five-point series produces exactly five points and a non-null marker`() {
        val g = geometry(listOf(10f, 6f, 12f, 12f, 15f))
        assertEquals(5, g.points.size)
        assertNotNull(g.marker)
    }

    @Test
    fun `default gridLineCount constant equals 3`() {
        assertEquals(3, BALANCE_TREND_CHART_GRIDLINE_COUNT)
    }
}
