package com.kshavrin.mymoney.core.domain.time

import com.kshavrin.mymoney.core.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class PeriodArithmeticTest {

    private val zone = ZoneId.of("UTC")

    private fun startMillis(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endMillis(date: LocalDate): Long =
        date.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `Day spans start of day to end of same day`() {
        val date = LocalDate.of(2026, 5, 18)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Day(date), zone)

        assertEquals(startMillis(date), range.first)
        assertEquals(endMillis(date), range.last)
        assertTrue(range.first <= range.last)
    }

    @Test
    fun `Day end is last millisecond before midnight`() {
        val date = LocalDate.of(2026, 5, 18)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Day(date), zone)

        // LocalTime.MAX is 23:59:59.999999999 -> truncated to .999 in epoch-millis.
        val nextDayStart = startMillis(date.plusDays(1))
        assertEquals(nextDayStart - 1, range.last)
    }

    @Test
    fun `consecutive Days are contiguous and non-overlapping`() {
        val day1 = LocalDate.of(2026, 5, 18)
        val day2 = day1.plusDays(1)
        val range1 = PeriodArithmetic.toEpochMillisRange(Period.Day(day1), zone)
        val range2 = PeriodArithmetic.toEpochMillisRange(Period.Day(day2), zone)

        assertTrue(range1.last < range2.first)
        assertEquals(range1.last + 1, range2.first)
    }

    @Test
    fun `Week spans exactly seven days from week start`() {
        val weekStart = LocalDate.of(2026, 5, 18)
        val weekEnd = weekStart.plusDays(6)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Week(weekStart), zone)

        assertEquals(startMillis(weekStart), range.first)
        assertEquals(endMillis(weekEnd), range.last)
        assertTrue(range.first <= range.last)
    }

    @Test
    fun `Week ends at last millisecond of seventh day`() {
        val weekStart = LocalDate.of(2026, 5, 18)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Week(weekStart), zone)

        val dayAfterWeek = startMillis(weekStart.plusDays(7))
        assertEquals(dayAfterWeek - 1, range.last)
    }

    @Test
    fun `Month spans first to last day of a 31-day month`() {
        val yearMonth = YearMonth.of(2026, 5)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Month(yearMonth), zone)

        assertEquals(startMillis(LocalDate.of(2026, 5, 1)), range.first)
        assertEquals(endMillis(LocalDate.of(2026, 5, 31)), range.last)
        assertTrue(range.first <= range.last)
    }

    @Test
    fun `Month uses leap-year end of February`() {
        val yearMonth = YearMonth.of(2024, 2)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Month(yearMonth), zone)

        // 2024 is a leap year: February ends on the 29th.
        assertEquals(startMillis(LocalDate.of(2024, 2, 1)), range.first)
        assertEquals(endMillis(LocalDate.of(2024, 2, 29)), range.last)
        assertEquals(LocalDate.of(2024, 2, 29), yearMonth.atEndOfMonth())
    }

    @Test
    fun `Month uses non-leap end of February`() {
        val yearMonth = YearMonth.of(2026, 2)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Month(yearMonth), zone)

        // 2026 is not a leap year: February ends on the 28th.
        assertEquals(startMillis(LocalDate.of(2026, 2, 1)), range.first)
        assertEquals(endMillis(LocalDate.of(2026, 2, 28)), range.last)
        assertEquals(LocalDate.of(2026, 2, 28), yearMonth.atEndOfMonth())
    }

    @Test
    fun `Year spans Jan 1 to Dec 31`() {
        val range = PeriodArithmetic.toEpochMillisRange(Period.Year(2026), zone)

        assertEquals(startMillis(LocalDate.of(2026, 1, 1)), range.first)
        assertEquals(endMillis(LocalDate.of(2026, 12, 31)), range.last)
        assertTrue(range.first <= range.last)
    }

    @Test
    fun `Year start is midnight Jan 1`() {
        val range = PeriodArithmetic.toEpochMillisRange(Period.Year(2026), zone)

        // 2026-01-01T00:00:00Z in epoch-millis.
        val expectedStart = LocalDate.of(2026, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(expectedStart, range.first)
    }

    @Test
    fun `All spans full epoch range`() {
        val range = PeriodArithmetic.toEpochMillisRange(Period.All, zone)

        assertEquals(0L, range.first)
        assertEquals(Long.MAX_VALUE, range.last)
    }

    @Test
    fun `CustomRange single day matches a Day range`() {
        val date = LocalDate.of(2026, 5, 18)
        val custom = PeriodArithmetic.toEpochMillisRange(Period.CustomRange(date, date), zone)
        val day = PeriodArithmetic.toEpochMillisRange(Period.Day(date), zone)

        assertEquals(day.first, custom.first)
        assertEquals(day.last, custom.last)
    }

    @Test
    fun `CustomRange spans start of first day to end of last day`() {
        val start = LocalDate.of(2026, 3, 15)
        val end = LocalDate.of(2026, 7, 2)
        val range = PeriodArithmetic.toEpochMillisRange(Period.CustomRange(start, end), zone)

        assertEquals(startMillis(start), range.first)
        assertEquals(endMillis(end), range.last)
        assertTrue(range.first <= range.last)
    }

    @Test
    fun `CustomRange multi-month covers the whole interval inclusively`() {
        val start = LocalDate.of(2026, 3, 15)
        val end = LocalDate.of(2026, 7, 2)
        val range = PeriodArithmetic.toEpochMillisRange(Period.CustomRange(start, end), zone)

        // A timestamp at the very start of the end day is inside the range;
        // the first millisecond of the day after the end is not.
        assertTrue(startMillis(end) in range)
        assertTrue(startMillis(end.plusDays(1)) !in range)
        assertTrue(startMillis(start) in range)
    }
}
