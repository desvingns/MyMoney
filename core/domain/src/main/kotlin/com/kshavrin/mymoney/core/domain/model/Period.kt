package com.kshavrin.mymoney.core.domain.model

import java.time.LocalDate
import java.time.YearMonth

sealed class Period {
    data class Day(val date: LocalDate) : Period()
    data class Week(val weekStart: LocalDate) : Period()
    data class Month(val yearMonth: YearMonth) : Period()
    data class Year(val year: Int) : Period()
    data object All : Period()
    data class CustomRange(val start: LocalDate, val end: LocalDate) : Period()
}
