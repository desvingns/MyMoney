package com.kshavrin.mymoney.core.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

sealed class Period {
    data class Day(
        val date: LocalDate,
    ) : Period()

    data class Week(
        val weekStart: LocalDate,
    ) : Period()

    data class Month(
        val yearMonth: YearMonth,
    ) : Period()

    data class Year(
        val year: Int,
    ) : Period()

    data object All : Period()

    data class CustomRange(
        val start: LocalDate,
        val end: LocalDate,
    ) : Period()

    fun next(): Period = shift(1)

    fun previous(): Period = shift(-1)

    private fun shift(direction: Int): Period =
        when (this) {
            is Day -> Day(date.plusDays(direction.toLong()))
            is Week -> Week(weekStart.plusWeeks(direction.toLong()))
            is Month -> Month(yearMonth.plusMonths(direction.toLong()))
            is Year -> Year(year + direction)
            All -> All
            is CustomRange -> {
                val length = ChronoUnit.DAYS.between(start, end) + 1
                val offset = length * direction
                CustomRange(start.plusDays(offset), end.plusDays(offset))
            }
        }
}
