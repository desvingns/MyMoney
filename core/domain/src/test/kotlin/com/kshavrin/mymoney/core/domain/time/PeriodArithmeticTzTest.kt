package com.kshavrin.mymoney.core.domain.time

import com.kshavrin.mymoney.core.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class PeriodArithmeticTzTest {

    private val newYork = ZoneId.of("America/New_York")
    private val moscow = ZoneId.of("Europe/Moscow")

    private fun startMillis(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun endMillis(date: LocalDate, zone: ZoneId): Long =
        date.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `Day range in New York starts and ends at local midnight boundaries`() {
        val date = LocalDate.of(2026, 6, 10)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Day(date), newYork)

        assertEquals(startMillis(date, newYork), range.first)
        assertEquals(endMillis(date, newYork), range.last)
        assertTrue(startMillis(date, newYork) in range)
        assertFalse(startMillis(date.minusDays(1), newYork) in range)
    }

    @Test
    fun `Week range in New York spans seven local dates inclusively`() {
        val weekStart = LocalDate.of(2026, 6, 8)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Week(weekStart), newYork)

        assertEquals(startMillis(weekStart, newYork), range.first)
        assertEquals(endMillis(weekStart.plusDays(6), newYork), range.last)
        assertTrue(startMillis(weekStart.plusDays(3), newYork) in range)
        assertFalse(startMillis(weekStart.plusDays(7), newYork) in range)
    }

    @Test
    fun `Month range in Moscow keeps June 1 inside June even though its utc instant is in May`() {
        val yearMonth = YearMonth.of(2026, 6)
        val range = PeriodArithmetic.toEpochMillisRange(Period.Month(yearMonth), moscow)
        val juneFirstLocalMidnight = startMillis(LocalDate.of(2026, 6, 1), moscow)

        assertEquals(juneFirstLocalMidnight, range.first)
        assertEquals(endMillis(LocalDate.of(2026, 6, 30), moscow), range.last)
        assertTrue(juneFirstLocalMidnight in range)
    }

    @Test
    fun `CustomRange in Moscow uses inclusive local start and end boundaries`() {
        val start = LocalDate.of(2026, 6, 1)
        val end = LocalDate.of(2026, 6, 3)
        val range = PeriodArithmetic.toEpochMillisRange(Period.CustomRange(start, end), moscow)

        assertEquals(startMillis(start, moscow), range.first)
        assertEquals(endMillis(end, moscow), range.last)
        assertTrue(startMillis(end, moscow) in range)
        assertFalse(startMillis(end.plusDays(1), moscow) in range)
    }
}
