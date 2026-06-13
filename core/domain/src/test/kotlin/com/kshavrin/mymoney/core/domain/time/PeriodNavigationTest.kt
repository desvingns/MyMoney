package com.kshavrin.mymoney.core.domain.time

import com.kshavrin.mymoney.core.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class PeriodNavigationTest {
    @Test
    fun `Day next advances by one calendar day`() {
        val period = Period.Day(LocalDate.of(2026, 5, 18))
        assertEquals(Period.Day(LocalDate.of(2026, 5, 19)), period.next())
    }

    @Test
    fun `Day previous steps back by one calendar day`() {
        val period = Period.Day(LocalDate.of(2026, 5, 18))
        assertEquals(Period.Day(LocalDate.of(2026, 5, 17)), period.previous())
    }

    @Test
    fun `Day next rolls over a month boundary`() {
        val period = Period.Day(LocalDate.of(2026, 5, 31))
        assertEquals(Period.Day(LocalDate.of(2026, 6, 1)), period.next())
    }

    @Test
    fun `Day previous rolls back over a year boundary`() {
        val period = Period.Day(LocalDate.of(2026, 1, 1))
        assertEquals(Period.Day(LocalDate.of(2025, 12, 31)), period.previous())
    }

    @Test
    fun `Week next advances the week start by seven days`() {
        val period = Period.Week(LocalDate.of(2026, 5, 18))
        assertEquals(Period.Week(LocalDate.of(2026, 5, 25)), period.next())
    }

    @Test
    fun `Week previous steps the week start back by seven days`() {
        val period = Period.Week(LocalDate.of(2026, 5, 18))
        assertEquals(Period.Week(LocalDate.of(2026, 5, 11)), period.previous())
    }

    @Test
    fun `Month next advances by one month`() {
        val period = Period.Month(YearMonth.of(2026, 5))
        assertEquals(Period.Month(YearMonth.of(2026, 6)), period.next())
    }

    @Test
    fun `Month previous steps back by one month`() {
        val period = Period.Month(YearMonth.of(2026, 5))
        assertEquals(Period.Month(YearMonth.of(2026, 4)), period.previous())
    }

    @Test
    fun `Month next rolls over into the next year`() {
        val period = Period.Month(YearMonth.of(2026, 12))
        assertEquals(Period.Month(YearMonth.of(2027, 1)), period.next())
    }

    @Test
    fun `Month previous rolls back into the prior year`() {
        val period = Period.Month(YearMonth.of(2026, 1))
        assertEquals(Period.Month(YearMonth.of(2025, 12)), period.previous())
    }

    @Test
    fun `Year next increments the year`() {
        assertEquals(Period.Year(2027), Period.Year(2026).next())
    }

    @Test
    fun `Year previous decrements the year`() {
        assertEquals(Period.Year(2025), Period.Year(2026).previous())
    }

    @Test
    fun `All next is unchanged`() {
        assertSame(Period.All, Period.All.next())
    }

    @Test
    fun `All previous is unchanged`() {
        assertSame(Period.All, Period.All.previous())
    }

    @Test
    fun `CustomRange next shifts by the inclusive range length preserving the length`() {
        // 10..12 April is a 3-day inclusive range, so next() jumps forward 3 days to 13..15 April.
        val period =
            Period.CustomRange(
                start = LocalDate.of(2026, 4, 10),
                end = LocalDate.of(2026, 4, 12),
            )

        val next = period.next() as Period.CustomRange
        assertEquals(LocalDate.of(2026, 4, 13), next.start)
        assertEquals(LocalDate.of(2026, 4, 15), next.end)
        assertEquals(daysInclusive(period), daysInclusive(next))
    }

    @Test
    fun `CustomRange previous shifts back by the inclusive range length preserving the length`() {
        val period =
            Period.CustomRange(
                start = LocalDate.of(2026, 4, 10),
                end = LocalDate.of(2026, 4, 12),
            )

        val previous = period.previous() as Period.CustomRange
        assertEquals(LocalDate.of(2026, 4, 7), previous.start)
        assertEquals(LocalDate.of(2026, 4, 9), previous.end)
        assertEquals(daysInclusive(period), daysInclusive(previous))
    }

    @Test
    fun `CustomRange single day next behaves like a Day step`() {
        val date = LocalDate.of(2026, 5, 18)
        val period = Period.CustomRange(date, date)

        val next = period.next() as Period.CustomRange
        assertEquals(LocalDate.of(2026, 5, 19), next.start)
        assertEquals(LocalDate.of(2026, 5, 19), next.end)
    }

    @Test
    fun `CustomRange next then previous returns to the original range`() {
        val period =
            Period.CustomRange(
                start = LocalDate.of(2026, 4, 10),
                end = LocalDate.of(2026, 4, 17),
            )

        assertEquals(period, period.next().previous())
    }

    @Test
    fun `CustomRange multi-month next shifts by the full inclusive span`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 31)
        val period = Period.CustomRange(start, end)

        val next = period.next() as Period.CustomRange
        // 31-day inclusive span: shift forward 31 days.
        assertEquals(LocalDate.of(2026, 2, 1), next.start)
        assertEquals(LocalDate.of(2026, 3, 3), next.end)
        assertEquals(daysInclusive(period), daysInclusive(next))
    }

    private fun daysInclusive(range: Period.CustomRange): Long =
        java.time.temporal.ChronoUnit.DAYS
            .between(range.start, range.end) + 1
}
