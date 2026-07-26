package com.kshavrin.mymoney.feature.dashboard.components

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import com.kshavrin.mymoney.feature.dashboard.ChartTrendAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class ChartLabelsTest {
    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun money(amount: String = "0") = Money(BigDecimal(amount), usd)

    private fun dayPoint(
        index: Int,
        date: LocalDate,
    ) = TrendPoint(index = index, period = Period.Day(date), value = money())

    private fun monthPoint(
        index: Int,
        yearMonth: YearMonth,
    ) = TrendPoint(index = index, period = Period.Month(yearMonth), value = money())

    private fun rangePoint(
        index: Int,
        start: LocalDate,
        end: LocalDate,
    ) = TrendPoint(index = index, period = Period.CustomRange(start, end), value = money())

    // -------------------------------------------------------------------------
    // axis == None → always empty
    // -------------------------------------------------------------------------

    @Test
    fun `returns empty list when axis is None regardless of points`() {
        val points = listOf(dayPoint(0, LocalDate.of(2026, 6, 1)), dayPoint(1, LocalDate.of(2026, 6, 2)))
        val result = trendPointLabels(points, ChartTrendAxis.None, Locale.ENGLISH)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns empty list when axis is None and points list is empty`() {
        val result = trendPointLabels(emptyList(), ChartTrendAxis.None, Locale.ENGLISH)
        assertTrue(result.isEmpty())
    }

    // -------------------------------------------------------------------------
    // axis == Days → day-of-month number
    // -------------------------------------------------------------------------

    @Test
    fun `Days axis returns day-of-month numbers for Day periods`() {
        val points =
            listOf(
                dayPoint(0, LocalDate.of(2026, 6, 1)),
                dayPoint(1, LocalDate.of(2026, 6, 15)),
                dayPoint(2, LocalDate.of(2026, 6, 30)),
            )
        val result = trendPointLabels(points, ChartTrendAxis.Days, Locale.ENGLISH)
        assertEquals(listOf("1", "15", "30"), result)
    }

    @Test
    fun `Days axis returns empty string for non-Day period types`() {
        val point = TrendPoint(index = 0, period = Period.Month(YearMonth.of(2026, 6)), value = money())
        val result = trendPointLabels(listOf(point), ChartTrendAxis.Days, Locale.ENGLISH)
        assertEquals(listOf(""), result)
    }

    @Test
    fun `Days axis returns empty list when points list is empty`() {
        val result = trendPointLabels(emptyList(), ChartTrendAxis.Days, Locale.ENGLISH)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `Days axis handles single-day series without crashing`() {
        val result = trendPointLabels(listOf(dayPoint(0, LocalDate.of(2026, 6, 5))), ChartTrendAxis.Days, Locale.ENGLISH)
        assertEquals(listOf("5"), result)
    }

    @Test
    fun `Days axis with zero points does not crash — zero-point edge case`() {
        val result = trendPointLabels(emptyList(), ChartTrendAxis.Days, Locale.ENGLISH)
        assertEquals(0, result.size)
    }

    @Test
    fun `Days axis with one point does not crash — one-point edge case`() {
        val result = trendPointLabels(listOf(dayPoint(0, LocalDate.of(2026, 6, 10))), ChartTrendAxis.Days, Locale.ENGLISH)
        assertEquals(1, result.size)
        assertEquals("10", result[0])
    }

    // -------------------------------------------------------------------------
    // axis == Months → locale-driven short month abbreviation
    // -------------------------------------------------------------------------

    @Test
    fun `Months axis returns short month names in English locale`() {
        val points =
            listOf(
                monthPoint(0, YearMonth.of(2026, 1)),
                monthPoint(1, YearMonth.of(2026, 6)),
                monthPoint(2, YearMonth.of(2026, 12)),
            )
        val result = trendPointLabels(points, ChartTrendAxis.Months, Locale.ENGLISH)
        assertEquals(3, result.size)
        assertEquals("Jan", result[0])
        assertEquals("Jun", result[1])
        assertEquals("Dec", result[2])
    }

    @Test
    fun `Months axis returns locale-driven abbreviation in Russian locale`() {
        val points =
            listOf(
                monthPoint(0, YearMonth.of(2026, 1)),
                monthPoint(1, YearMonth.of(2026, 3)),
            )
        val result = trendPointLabels(points, ChartTrendAxis.Months, Locale("ru"))
        assertEquals(2, result.size)
        assertTrue("January short name in ru locale must not be empty", result[0].isNotEmpty())
        assertTrue("March short name in ru locale must not be empty", result[1].isNotEmpty())
    }

    @Test
    fun `Months axis labels are locale-specific — English and Russian produce different strings for the same month`() {
        val points = listOf(monthPoint(0, YearMonth.of(2026, 1)))
        val en = trendPointLabels(points, ChartTrendAxis.Months, Locale.ENGLISH)
        val ru = trendPointLabels(points, ChartTrendAxis.Months, Locale("ru"))
        assertTrue("English and Russian month names for January must differ", en[0] != ru[0])
    }

    @Test
    fun `Months axis returns empty string for non-Month period type`() {
        val point = dayPoint(0, LocalDate.of(2026, 6, 1))
        val result = trendPointLabels(listOf(point), ChartTrendAxis.Months, Locale.ENGLISH)
        assertEquals(listOf(""), result)
    }

    @Test
    fun `Months axis with zero points does not crash — zero-point edge case`() {
        val result = trendPointLabels(emptyList(), ChartTrendAxis.Months, Locale.ENGLISH)
        assertEquals(0, result.size)
    }

    @Test
    fun `Months axis with one point does not crash — one-point edge case`() {
        val result = trendPointLabels(listOf(monthPoint(0, YearMonth.of(2026, 6))), ChartTrendAxis.Months, Locale.ENGLISH)
        assertEquals(1, result.size)
        assertEquals("Jun", result[0])
    }

    // -------------------------------------------------------------------------
    // axis == Hours → slot index × 2
    // -------------------------------------------------------------------------

    @Test
    fun `Hours axis returns slot start hours as slot-index times two`() {
        val day = LocalDate.of(2026, 6, 1)
        val points = (0..5).map { slot -> TrendPoint(index = slot, period = Period.Day(day), value = money()) }
        val result = trendPointLabels(points, ChartTrendAxis.Hours, Locale.ENGLISH)
        assertEquals(listOf("0", "2", "4", "6", "8", "10"), result)
    }

    @Test
    fun `Hours axis with zero points does not crash — zero-point edge case`() {
        val result = trendPointLabels(emptyList(), ChartTrendAxis.Hours, Locale.ENGLISH)
        assertEquals(0, result.size)
    }

    @Test
    fun `Hours axis with one point does not crash — one-point edge case`() {
        val day = LocalDate.of(2026, 6, 1)
        val result = trendPointLabels(listOf(TrendPoint(index = 0, period = Period.Day(day), value = money())), ChartTrendAxis.Hours, Locale.ENGLISH)
        assertEquals(listOf("0"), result)
    }

    @Test
    fun `Hours axis last slot index 11 maps to hour 22`() {
        val day = LocalDate.of(2026, 6, 1)
        val point = TrendPoint(index = 11, period = Period.Day(day), value = money())
        val result = trendPointLabels(listOf(point), ChartTrendAxis.Hours, Locale.ENGLISH)
        assertEquals(listOf("22"), result)
    }

    // -------------------------------------------------------------------------
    // axis == RangeBuckets → start day-of-month for CustomRange / Day periods
    // -------------------------------------------------------------------------

    @Test
    fun `RangeBuckets axis returns start day for CustomRange periods`() {
        val points =
            listOf(
                rangePoint(0, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 10)),
                rangePoint(1, LocalDate.of(2026, 5, 11), LocalDate.of(2026, 5, 20)),
                rangePoint(2, LocalDate.of(2026, 5, 21), LocalDate.of(2026, 5, 31)),
            )
        val result = trendPointLabels(points, ChartTrendAxis.RangeBuckets, Locale.ENGLISH)
        assertEquals(listOf("1", "11", "21"), result)
    }

    @Test
    fun `RangeBuckets axis returns day-of-month for Day periods`() {
        val points =
            listOf(
                dayPoint(0, LocalDate.of(2026, 6, 7)),
                dayPoint(1, LocalDate.of(2026, 6, 14)),
            )
        val result = trendPointLabels(points, ChartTrendAxis.RangeBuckets, Locale.ENGLISH)
        assertEquals(listOf("7", "14"), result)
    }

    @Test
    fun `RangeBuckets axis returns empty string for Month periods`() {
        val point = monthPoint(0, YearMonth.of(2026, 6))
        val result = trendPointLabels(listOf(point), ChartTrendAxis.RangeBuckets, Locale.ENGLISH)
        assertEquals(listOf(""), result)
    }

    @Test
    fun `RangeBuckets axis with zero points does not crash — zero-point edge case`() {
        val result = trendPointLabels(emptyList(), ChartTrendAxis.RangeBuckets, Locale.ENGLISH)
        assertEquals(0, result.size)
    }

    @Test
    fun `RangeBuckets axis with one point does not crash — one-point edge case`() {
        val result =
            trendPointLabels(
                listOf(rangePoint(0, LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 20))),
                ChartTrendAxis.RangeBuckets,
                Locale.ENGLISH,
            )
        assertEquals(listOf("15"), result)
    }

    // -------------------------------------------------------------------------
    // label count always matches point count
    // -------------------------------------------------------------------------

    @Test
    fun `label count always equals point count for Days axis`() {
        val points = (1..28).map { day -> dayPoint(day - 1, LocalDate.of(2026, 2, day)) }
        val result = trendPointLabels(points, ChartTrendAxis.Days, Locale.ENGLISH)
        assertEquals(points.size, result.size)
    }

    @Test
    fun `label count always equals point count for Months axis`() {
        val points = (1..12).map { month -> monthPoint(month - 1, YearMonth.of(2026, month)) }
        val result = trendPointLabels(points, ChartTrendAxis.Months, Locale.ENGLISH)
        assertEquals(12, result.size)
    }

    @Test
    fun `label count always equals point count for Hours axis`() {
        val day = LocalDate.of(2026, 6, 1)
        val points = (0..11).map { slot -> TrendPoint(index = slot, period = Period.Day(day), value = money()) }
        val result = trendPointLabels(points, ChartTrendAxis.Hours, Locale.ENGLISH)
        assertEquals(12, result.size)
    }
}
