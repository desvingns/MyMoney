package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.ChartMetric
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

class BalanceTrendCalculatorPropertyTest {
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
    private val firstDate =
        Instant.parse("2026-01-01T00:00:00Z")
            .atZone(ZoneOffset.UTC)
            .toLocalDate()

    @Test
    fun `buildAutoSeries preserves trimmed length indexes periods and metric consistency`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072606L,
                arb = Arb.list(Arb.int(0..10_000), 1..12),
            ) { values ->
                val window = dailyWindow(values.size)
                val snapshots = values.map(::snapshotFrom)
                val expectedSize =
                    snapshots.indexOfLast { snapshot ->
                        snapshot.income.amount.signum() > 0 || snapshot.expense.amount.signum() > 0
                    }.let { lastActiveIndex -> if (lastActiveIndex < 0) 0 else lastActiveIndex + 1 }
                val seriesByMetric =
                    ChartMetric.entries.associateWith { metric ->
                        calculator.buildAutoSeries(window, metric) { period ->
                            snapshots[window.indexOf(period)]
                        }
                    }

                seriesByMetric.values.forEach { series ->
                    assertEquals(expectedSize, series.size)
                    assertEquals((0 until expectedSize).toList(), series.map { it.index })
                    assertEquals(window.take(expectedSize), series.map { it.period })
                }

                val cumulativeSeries = seriesByMetric.getValue(ChartMetric.CUMULATIVE)
                val periodNetSeries = seriesByMetric.getValue(ChartMetric.PERIOD_NET)
                val incomeExpenseSeries = seriesByMetric.getValue(ChartMetric.INCOME_EXPENSE)
                var expectedCumulative = BigDecimal.ZERO

                snapshots.take(expectedSize).forEachIndexed { index, snapshot ->
                    expectedCumulative = expectedCumulative.add(snapshot.net.amount)
                    assertAmount(
                        expectedCumulative,
                        cumulativeSeries[index].value,
                        "cumulative[$index]",
                    )
                    assertAmount(
                        snapshot.net.amount,
                        periodNetSeries[index].value,
                        "period net[$index]",
                    )
                    assertAmount(
                        periodNetSeries[index].value.amount,
                        incomeExpenseSeries[index].value,
                        "income expense net[$index]",
                    )
                    assertAmount(
                        snapshot.income.amount,
                        incomeExpenseSeries[index].income!!,
                        "income[$index]",
                    )
                    assertAmount(
                        snapshot.expense.amount,
                        incomeExpenseSeries[index].expense!!,
                        "expense[$index]",
                    )
                }
            }
        }

    @Test
    fun `buildAutoSeries preserves internal stagnation before the last active period`() =
        runTest {
            checkPropertyWithSeed(
                seed = 2026072607L,
                arb = Arb.list(Arb.int(0..10_000), 1..10),
            ) { values ->
                val size = maxOf(3, values.size)
                val middleIndex = size / 2
                val normalized = List(size) { index -> values[index % values.size] }.toMutableList()
                normalized[0] = 1
                normalized[middleIndex] = 0
                normalized[size - 1] = 202
                val window = dailyWindow(size)
                val snapshots = normalized.map(::snapshotFrom)

                ChartMetric.entries.forEach { metric ->
                    val result = calculator.buildAutoSeries(window, metric) { period ->
                        snapshots[window.indexOf(period)]
                    }

                    assertEquals(size, result.size)
                    assertEquals(window[middleIndex], result[middleIndex].period)
                    when (metric) {
                        ChartMetric.CUMULATIVE ->
                            assertAmount(
                                result[middleIndex - 1].value.amount,
                                result[middleIndex].value,
                                "cumulative stagnation[$middleIndex]",
                            )
                        ChartMetric.PERIOD_NET,
                        ChartMetric.INCOME_EXPENSE,
                        -> {
                            assertAmount(
                                BigDecimal.ZERO,
                                result[middleIndex].value,
                                "net stagnation[$middleIndex]",
                            )
                            if (metric == ChartMetric.INCOME_EXPENSE) {
                                assertAmount(
                                    BigDecimal.ZERO,
                                    result[middleIndex].income!!,
                                    "income stagnation[$middleIndex]",
                                )
                                assertAmount(
                                    BigDecimal.ZERO,
                                    result[middleIndex].expense!!,
                                    "expense stagnation[$middleIndex]",
                                )
                            }
                        }
                    }
                }
            }
        }

    private fun dailyWindow(size: Int): List<Period> =
        (0 until size).map { index -> Period.Day(firstDate.plusDays(index.toLong())) }

    private fun snapshotFrom(value: Int): BalanceSnapshot {
        val income = BigDecimal(value % 101).movePointLeft(2)
        val expense = BigDecimal((value / 101) % 101).movePointLeft(2)
        return BalanceSnapshot(
            income = Money(income, usd),
            expense = Money(expense, usd),
            net = Money(income.subtract(expense), usd),
            byCategory = emptyList(),
        )
    }

    private fun assertAmount(
        expected: BigDecimal,
        actual: Money,
        label: String,
    ) {
        assertEquals(
            "$label expected=$expected actual=${actual.amount}",
            0,
            expected.compareTo(actual.amount),
        )
        assertTrue(actual.currency == usd)
    }

    private suspend fun <A> checkPropertyWithSeed(
        seed: Long,
        arb: Arb<A>,
        assertion: suspend (A) -> Unit,
    ) {
        try {
            checkAll(
                PropTestConfig(seed = seed, iterations = PROPERTY_ITERATIONS),
                arb,
            ) { value -> assertion(value) }
        } catch (failure: Throwable) {
            val error = AssertionError("Property failed; seed=$seed")
            error.initCause(failure)
            throw error
        }
    }

    private companion object {
        const val PROPERTY_ITERATIONS = 100
    }
}
