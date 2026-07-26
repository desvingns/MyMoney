package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.TrendPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class BalanceTrendCalculatorTest {
    private val calculator = BalanceTrendCalculator()

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

    private val fixedZone: ZoneId = ZoneOffset.UTC
    private val fixedToday: LocalDate = LocalDate.of(2026, 6, 22)

    // ---- helpers -------------------------------------------------------

    private fun money(amount: String) =
        Money(
            BigDecimal.valueOf(amount.toBigDecimal().toLong()).add(
                BigDecimal(amount).subtract(BigDecimal(amount.toBigDecimal().toLong())),
            ),
            usd,
        ).let { Money(BigDecimal(amount), usd) }

    private fun snapshot(
        income: String,
        expense: String,
    ): BalanceSnapshot {
        val inc = BigDecimal(income)
        val exp = BigDecimal(expense)
        val net = inc.subtract(exp)
        return BalanceSnapshot(
            income = Money(inc, usd),
            expense = Money(exp, usd),
            net = Money(net, usd),
            byCategory = emptyList(),
        )
    }

    private fun zeroSnapshot() = snapshot("0", "0")

    // Fake provider backed by a Period→snapshot map; falls back to zero.
    private fun providerFor(map: Map<Period, BalanceSnapshot>): suspend (Period) -> BalanceSnapshot =
        { period -> map[period] ?: zeroSnapshot() }

    // ---- buildWindow ---------------------------------------------------

    @Test
    fun `buildWindow for Month returns exactly count periods oldest first`() {
        val anchor = Period.Month(YearMonth.of(2026, 5))
        val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
        assertEquals(5, window.size)
        // oldest = Jan 2026, newest = May 2026
        assertEquals(Period.Month(YearMonth.of(2026, 1)), window.first())
        assertEquals(Period.Month(YearMonth.of(2026, 5)), window.last())
    }

    @Test
    fun `buildWindow for Day returns exactly count days oldest first`() {
        val anchor = Period.Day(LocalDate.of(2026, 5, 10))
        val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
        assertEquals(5, window.size)
        assertEquals(Period.Day(LocalDate.of(2026, 5, 6)), window.first())
        assertEquals(Period.Day(LocalDate.of(2026, 5, 10)), window.last())
    }

    @Test
    fun `buildWindow for Week returns exactly count weeks oldest first`() {
        val anchor = Period.Week(LocalDate.of(2026, 5, 11))
        val window = calculator.buildWindow(anchor, count = 3, zone = fixedZone)
        assertEquals(3, window.size)
        assertEquals(Period.Week(LocalDate.of(2026, 4, 27)), window.first())
        assertEquals(Period.Week(LocalDate.of(2026, 5, 11)), window.last())
    }

    @Test
    fun `buildWindow for Year returns exactly count years oldest first`() {
        val anchor = Period.Year(2026)
        val window = calculator.buildWindow(anchor, count = 4, zone = fixedZone)
        assertEquals(4, window.size)
        assertEquals(Period.Year(2023), window.first())
        assertEquals(Period.Year(2026), window.last())
    }

    @Test
    fun `buildWindow for CustomRange splits into N equal sub-intervals and last ends at original range end`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 10)
        val anchor = Period.CustomRange(start, end)
        val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
        assertEquals(5, window.size)
        val last = window.last() as Period.CustomRange
        assertEquals(end, last.end)
        // first sub-interval starts at original start
        val first = window.first() as Period.CustomRange
        assertEquals(start, first.start)
    }

    @Test
    fun `buildWindow for one day Interval caps count and never creates inverted buckets`() {
        val date = LocalDate.of(2026, 6, 22)

        val window = calculator.buildWindow(Period.Interval(date, date), count = 5, zone = fixedZone)

        assertEquals(listOf(Period.Interval(date, date)), window)
        assertTrue(window.all { (it as Period.Interval).start <= it.end })
    }

    @Test
    fun `buildAutoWindow for one day Interval returns one daily bucket`() {
        val date = LocalDate.of(2026, 6, 22)

        val window = calculator.buildAutoWindow(Period.Interval(date, date), today = fixedToday, zone = fixedZone)

        assertEquals(listOf(Period.Day(date)), window)
    }

    @Test
    fun `trend for one day Interval keeps the Interval bucket and net value`() =
        runTest {
            val date = LocalDate.of(2026, 6, 22)
            val window = calculator.buildWindow(Period.Interval(date, date), count = 5, zone = fixedZone)

            val result = calculator(window, ChartMetric.PERIOD_NET) { snapshot("5", "0") }

            assertEquals(1, result.size)
            assertEquals(Period.Interval(date, date), result.single().period)
            assertAmount(result.single().value, "5.00")
        }

    @Test
    fun `buildWindow for All returns exactly count sub-intervals`() {
        val window = calculator.buildWindow(Period.All, count = 5, zone = fixedZone)
        assertEquals(5, window.size)
    }

    @Test
    fun `buildWindow requires positive count`() {
        var threw = false
        try {
            calculator.buildWindow(Period.Month(YearMonth.of(2026, 5)), count = 0, zone = fixedZone)
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    // ---- buildAutoWindow -----------------------------------------------

    @Test
    fun `buildAutoWindow for Week returns seven daily buckets`() {
        val anchor = Period.Week(LocalDate.of(2026, 5, 11))

        val window = calculator.buildAutoWindow(anchor, today = fixedToday, zone = fixedZone)

        assertEquals(7, window.size)
        assertEquals(Period.Day(LocalDate.of(2026, 5, 11)), window.first())
        assertEquals(Period.Day(LocalDate.of(2026, 5, 17)), window.last())
    }

    @Test
    fun `buildAutoWindow for non leap-year February returns twenty eight daily buckets`() {
        val anchor = Period.Month(YearMonth.of(2026, 2))

        val window = calculator.buildAutoWindow(anchor, today = fixedToday, zone = fixedZone)

        assertEquals(28, window.size)
        assertEquals(Period.Day(LocalDate.of(2026, 2, 1)), window.first())
        assertEquals(Period.Day(LocalDate.of(2026, 2, 28)), window.last())
    }

    @Test
    fun `buildAutoWindow for leap-year February returns twenty nine daily buckets`() {
        val anchor = Period.Month(YearMonth.of(2024, 2))

        val window = calculator.buildAutoWindow(anchor, today = fixedToday, zone = fixedZone)

        assertEquals(29, window.size)
        assertEquals(Period.Day(LocalDate.of(2024, 2, 1)), window.first())
        assertEquals(Period.Day(LocalDate.of(2024, 2, 29)), window.last())
    }

    @Test
    fun `buildAutoWindow for Year returns twelve monthly buckets`() {
        val anchor = Period.Year(2026)

        val window = calculator.buildAutoWindow(anchor, today = fixedToday, zone = fixedZone)

        assertEquals(12, window.size)
        assertEquals(Period.Month(YearMonth.of(2026, 1)), window.first())
        assertEquals(Period.Month(YearMonth.of(2026, 12)), window.last())
    }

    @Test
    fun `buildAutoWindow for All returns thirty custom buckets from earliest date to today`() {
        val earliestDate = LocalDate.of(2026, 1, 1)

        val window =
            calculator.buildAutoWindow(
                anchor = Period.All,
                earliestDate = earliestDate,
                today = fixedToday,
                zone = fixedZone,
            )

        assertEquals(30, window.size)
        assertTrue(window.all { it is Period.CustomRange })
        assertEquals(earliestDate, (window.first() as Period.CustomRange).start)
        assertEquals(fixedToday, (window.last() as Period.CustomRange).end)
    }

    @Test
    fun `buildAutoWindow for All returns empty when earliest date is null`() {
        val window =
            calculator.buildAutoWindow(
                anchor = Period.All,
                earliestDate = null,
                today = fixedToday,
                zone = fixedZone,
            )

        assertTrue(window.isEmpty())
    }

    @Test
    fun `buildAutoWindow for thirty day CustomRange returns daily buckets`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 30)
        val anchor = Period.CustomRange(start, end)

        val window = calculator.buildAutoWindow(anchor, today = fixedToday, zone = fixedZone)

        assertEquals(30, window.size)
        assertTrue(window.all { it is Period.Day })
        assertEquals(Period.Day(start), window.first())
        assertEquals(Period.Day(end), window.last())
    }

    @Test
    fun `buildAutoWindow for CustomRange longer than thirty days returns thirty buckets ending at range end`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 3, 31)
        val anchor = Period.CustomRange(start, end)

        val window = calculator.buildAutoWindow(anchor, today = fixedToday, zone = fixedZone)

        assertEquals(30, window.size)
        assertTrue(window.all { it is Period.CustomRange })
        assertEquals(start, (window.first() as Period.CustomRange).start)
        assertEquals(end, (window.last() as Period.CustomRange).end)
    }

    @Test
    fun `buildAutoWindow for Day throws IllegalArgumentException`() {
        var threw = false

        try {
            calculator.buildAutoWindow(
                anchor = Period.Day(LocalDate.of(2026, 5, 10)),
                today = fixedToday,
                zone = fixedZone,
            )
        } catch (expected: IllegalArgumentException) {
            threw = true
        }

        assertTrue(threw)
    }

    // ---- buildAutoSeries ----------------------------------------------

    @Test
    fun `buildAutoSeries trims trailing empty month buckets and keeps internal stagnation for cumulative`() =
        runTest {
            val window =
                calculator.buildAutoWindow(
                    anchor = Period.Month(YearMonth.of(2026, 6)),
                    today = fixedToday,
                    zone = fixedZone,
                )
            val snapshotsByPeriod =
                mapOf(
                    window[0] to snapshot("10", "0"),
                    window[1] to snapshot("0", "4"),
                    window[2] to snapshot("6", "0"),
                    window[4] to snapshot("3", "0"),
                    window[6] to snapshot("2", "0"),
                )

            val result =
                calculator.buildAutoSeries(
                    window = window,
                    metric = ChartMetric.CUMULATIVE,
                    snapshotProvider = providerFor(snapshotsByPeriod),
                )

            assertEquals(7, result.size)
            assertEquals(window.take(7), result.map { it.period })
            assertEquals((0..6).toList(), result.map { it.index })
            assertSeriesValues(result, "10.00", "6.00", "12.00", "12.00", "15.00", "15.00", "17.00")
        }

    @Test
    fun `buildAutoSeries keeps internal stagnation and trims week tail for cumulative`() =
        runTest {
            val window =
                calculator.buildAutoWindow(
                    anchor = Period.Week(LocalDate.of(2026, 5, 11)),
                    today = fixedToday,
                    zone = fixedZone,
                )
            val snapshotsByPeriod =
                mapOf(
                    window[0] to snapshot("10", "0"),
                    window[4] to snapshot("0", "4"),
                )

            val result =
                calculator.buildAutoSeries(
                    window = window,
                    metric = ChartMetric.CUMULATIVE,
                    snapshotProvider = providerFor(snapshotsByPeriod),
                )

            assertEquals(5, result.size)
            assertEquals(window.take(5), result.map { it.period })
            assertSeriesValues(result, "10.00", "10.00", "10.00", "10.00", "6.00")
        }

    @Test
    fun `buildAutoSeries returns empty when no window bucket has activity`() =
        runTest {
            val window =
                calculator.buildAutoWindow(
                    anchor = Period.Week(LocalDate.of(2026, 5, 11)),
                    today = fixedToday,
                    zone = fixedZone,
                )

            val result =
                calculator.buildAutoSeries(
                    window = window,
                    metric = ChartMetric.CUMULATIVE,
                    snapshotProvider = { zeroSnapshot() },
                )

            assertTrue(result.isEmpty())
        }

    @Test
    fun `buildAutoSeries returns one point when only the first bucket has activity`() =
        runTest {
            val window =
                calculator.buildAutoWindow(
                    anchor = Period.Week(LocalDate.of(2026, 5, 11)),
                    today = fixedToday,
                    zone = fixedZone,
                )

            val result =
                calculator.buildAutoSeries(
                    window = window,
                    metric = ChartMetric.CUMULATIVE,
                    snapshotProvider = providerFor(mapOf(window[0] to snapshot("10", "0"))),
                )

            assertEquals(1, result.size)
            assertEquals(listOf(window[0]), result.map { it.period })
            assertSeriesValues(result, "10.00")
        }

    @Test
    fun `buildAutoSeries calls snapshot provider once per input bucket`() =
        runTest {
            val window =
                calculator.buildAutoWindow(
                    anchor = Period.Week(LocalDate.of(2026, 5, 11)),
                    today = fixedToday,
                    zone = fixedZone,
                )
            val snapshotsByPeriod =
                mapOf(
                    window[0] to snapshot("10", "0"),
                    window[4] to snapshot("0", "4"),
                )
            val callCounts = mutableMapOf<Period, Int>()

            val result =
                calculator.buildAutoSeries(
                    window = window,
                    metric = ChartMetric.CUMULATIVE,
                    snapshotProvider = { period ->
                        callCounts[period] = (callCounts[period] ?: 0) + 1
                        snapshotsByPeriod[period] ?: zeroSnapshot()
                    },
                )

            assertEquals(5, result.size)
            assertEquals(window.size, callCounts.values.sum())
            window.forEach { period ->
                assertEquals(1, callCounts[period] ?: 0)
            }
        }

    @Test
    fun `buildAutoSeries applies the same trailing trim for period net`() =
        runTest {
            val window =
                calculator.buildAutoWindow(
                    anchor = Period.Week(LocalDate.of(2026, 5, 11)),
                    today = fixedToday,
                    zone = fixedZone,
                )
            val snapshotsByPeriod =
                mapOf(
                    window[0] to snapshot("10", "0"),
                    window[4] to snapshot("0", "4"),
                )

            val result =
                calculator.buildAutoSeries(
                    window = window,
                    metric = ChartMetric.PERIOD_NET,
                    snapshotProvider = providerFor(snapshotsByPeriod),
                )

            assertEquals(5, result.size)
            assertEquals(window.take(5), result.map { it.period })
            assertSeriesValues(result, "10.00", "0.00", "0.00", "0.00", "-4.00")
        }

    // ---- CUMULATIVE: 3 worked SPEC examples ----------------------------

    @Test
    fun `CUMULATIVE example 1 net +10 -4 +6 0 +3 yields running sum 10 6 12 12 15`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            val netValues =
                listOf("+10" to "0", "0" to "4", "+6" to "0", "0" to "0", "+3" to "0")
                    .map { (inc, exp) -> snapshot(inc.replace("+", ""), exp) }
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                netValues[window.indexOf(period)]
            }
            val result = calculator(window, ChartMetric.CUMULATIVE, provider)
            assertEquals(5, result.size)
            assertAmount(result[0].value, "10.00")
            assertAmount(result[1].value, "6.00")
            assertAmount(result[2].value, "12.00")
            assertAmount(result[3].value, "12.00")
            assertAmount(result[4].value, "15.00")
        }

    @Test
    fun `CUMULATIVE example 2 new account only current period +5 four prior empty gives 0 0 0 0 5`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            // only the last (current) period has income; all others return zero snapshot
            val currentPeriod = window.last()
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                if (period == currentPeriod) snapshot("5", "0") else zeroSnapshot()
            }
            val result = calculator(window, ChartMetric.CUMULATIVE, provider)
            assertEquals(5, result.size)
            assertAmount(result[0].value, "0.00")
            assertAmount(result[1].value, "0.00")
            assertAmount(result[2].value, "0.00")
            assertAmount(result[3].value, "0.00")
            assertAmount(result[4].value, "5.00")
        }

    @Test
    fun `CUMULATIVE example 3 net +4 -1 -2 -3 -1 yields 4 3 1 -2 -3 crossing zero`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            val snapshots =
                listOf(
                    snapshot("4", "0"),
                    snapshot("0", "1"),
                    snapshot("0", "2"),
                    snapshot("0", "3"),
                    snapshot("0", "1"),
                )
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                snapshots[window.indexOf(period)]
            }
            val result = calculator(window, ChartMetric.CUMULATIVE, provider)
            assertEquals(5, result.size)
            assertAmount(result[0].value, "4.00")
            assertAmount(result[1].value, "3.00")
            assertAmount(result[2].value, "1.00")
            assertAmount(result[3].value, "-2.00")
            assertAmount(result[4].value, "-3.00")
            // last point is negative — not clamped to zero
            assertTrue(result[4].value.amount.signum() < 0)
        }

    // ---- CUMULATIVE accumulator resets between calls -------------------

    @Test
    fun `CUMULATIVE accumulates from zero for each invoke call independently`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 3, zone = fixedZone)
            val snapshots = listOf(snapshot("10", "0"), snapshot("5", "0"), snapshot("0", "2"))
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                snapshots[window.indexOf(period)]
            }
            val first = calculator(window, ChartMetric.CUMULATIVE, provider)
            val second = calculator(window, ChartMetric.CUMULATIVE, provider)
            assertAmount(first[0].value, "10.00")
            assertAmount(first[1].value, "15.00")
            assertAmount(first[2].value, "13.00")
            // second call must produce the same result (no stale cumulative state)
            assertAmount(second[0].value, "10.00")
            assertAmount(second[1].value, "15.00")
            assertAmount(second[2].value, "13.00")
        }

    // ---- PERIOD_NET metric ---------------------------------------------

    @Test
    fun `PERIOD_NET returns each period net independently without accumulation`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            val snapshots =
                listOf(
                    snapshot("10", "0"),
                    snapshot("0", "4"),
                    snapshot("6", "0"),
                    snapshot("0", "0"),
                    snapshot("3", "0"),
                )
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                snapshots[window.indexOf(period)]
            }
            val result = calculator(window, ChartMetric.PERIOD_NET, provider)
            assertEquals(5, result.size)
            assertAmount(result[0].value, "10.00")
            assertAmount(result[1].value, "-4.00")
            assertAmount(result[2].value, "6.00")
            assertAmount(result[3].value, "0.00")
            assertAmount(result[4].value, "3.00")
        }

    @Test
    fun `PERIOD_NET zero-fills missing periods`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            val result = calculator(window, ChartMetric.PERIOD_NET) { zeroSnapshot() }
            assertEquals(5, result.size)
            result.forEach { assertAmount(it.value, "0.00") }
        }

    // ---- INCOME_EXPENSE metric -----------------------------------------

    @Test
    fun `INCOME_EXPENSE populates income and expense fields on each point`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 3, zone = fixedZone)
            val snapshots =
                listOf(
                    snapshot("100", "40"),
                    snapshot("200", "80"),
                    snapshot("0", "0"),
                )
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                snapshots[window.indexOf(period)]
            }
            val result = calculator(window, ChartMetric.INCOME_EXPENSE, provider)
            assertEquals(3, result.size)
            assertAmount(result[0].income!!, "100.00")
            assertAmount(result[0].expense!!, "40.00")
            assertAmount(result[1].income!!, "200.00")
            assertAmount(result[1].expense!!, "80.00")
            assertAmount(result[2].income!!, "0.00")
            assertAmount(result[2].expense!!, "0.00")
        }

    @Test
    fun `INCOME_EXPENSE value field equals net for each period`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 3, zone = fixedZone)
            val snapshots =
                listOf(
                    snapshot("100", "40"),
                    snapshot("50", "80"),
                    snapshot("30", "10"),
                )
            val provider: suspend (Period) -> BalanceSnapshot = { period ->
                snapshots[window.indexOf(period)]
            }
            val result = calculator(window, ChartMetric.INCOME_EXPENSE, provider)
            assertAmount(result[0].value, "60.00")
            assertAmount(result[1].value, "-30.00")
            assertAmount(result[2].value, "20.00")
        }

    @Test
    fun `INCOME_EXPENSE zero-fills missing periods with zero income and expense`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            val result = calculator(window, ChartMetric.INCOME_EXPENSE) { zeroSnapshot() }
            assertEquals(5, result.size)
            result.forEach {
                assertAmount(it.income!!, "0.00")
                assertAmount(it.expense!!, "0.00")
            }
        }

    // ---- point index and period assignment ----------------------------

    @Test
    fun `result points carry sequential index and matching period`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            val result = calculator(window, ChartMetric.PERIOD_NET) { zeroSnapshot() }
            result.forEachIndexed { i, point ->
                assertEquals(i, point.index)
                assertEquals(window[i], point.period)
            }
        }

    // ---- zero-fill guarantee for all metrics --------------------------

    @Test
    fun `all three metrics return exactly count points even when provider always returns zero`() =
        runTest {
            val anchor = Period.Month(YearMonth.of(2026, 5))
            val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
            ChartMetric.entries.forEach { metric ->
                val result = calculator(window, metric) { zeroSnapshot() }
                assertEquals("Expected 5 points for $metric", 5, result.size)
            }
        }

    // ---- CustomRange split: last sub-interval ends at original end ----

    @Test
    fun `CustomRange buildWindow last sub-interval end equals original anchor end`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 6, 30)
        val anchor = Period.CustomRange(start, end)
        val window = calculator.buildWindow(anchor, count = 5, zone = fixedZone)
        assertEquals(5, window.size)
        val lastSub = window.last() as Period.CustomRange
        assertEquals(end, lastSub.end)
    }

    @Test
    fun `CustomRange buildWindow sub-intervals are contiguous and non-overlapping`() {
        val start = LocalDate.of(2026, 1, 1)
        val end = LocalDate.of(2026, 1, 20)
        val anchor = Period.CustomRange(start, end)
        val window = calculator.buildWindow(anchor, count = 4, zone = fixedZone)
        assertEquals(4, window.size)
        for (i in 0 until window.size - 1) {
            val current = window[i] as Period.CustomRange
            val next = window[i + 1] as Period.CustomRange
            // sub-intervals must not overlap: current.end < next.start
            assertTrue(
                "Sub-interval $i end ${current.end} must be before next start ${next.start}",
                !current.end.isAfter(next.start.minusDays(1)),
            )
        }
    }

    // ---- helper -------------------------------------------------------

    private fun assertAmount(
        money: Money,
        expected: String,
    ) {
        assertEquals(
            "Expected $expected but was ${money.amount}",
            0,
            BigDecimal(expected).compareTo(money.amount),
        )
    }

    private fun assertSeriesValues(
        points: List<TrendPoint>,
        vararg expected: String,
    ) {
        assertEquals(expected.size, points.size)
        points.zip(expected).forEach { (point, amount) ->
            assertAmount(point.value, amount)
        }
    }
}
