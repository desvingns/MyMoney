package com.kshavrin.mymoney.core.domain.time

import com.kshavrin.mymoney.core.domain.model.Period
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object PeriodArithmetic {

    fun toEpochMillisRange(period: Period, zone: ZoneId = ZoneId.systemDefault()): LongRange = when (period) {
        is Period.Day -> period.date.toRange(zone)
        is Period.Week -> period.weekStart.let { start -> start to start.plusDays(6) }.toRange(zone)
        is Period.Month -> period.yearMonth.let { ym -> ym.atDay(1) to ym.atEndOfMonth() }.toRange(zone)
        is Period.Year -> LocalDate.of(period.year, 1, 1).let { start -> start to LocalDate.of(period.year, 12, 31) }.toRange(zone)
        is Period.All -> 0L..Long.MAX_VALUE
        is Period.CustomRange -> (period.start to period.end).toRange(zone)
    }

    private fun LocalDate.toRange(zone: ZoneId): LongRange = (this to this).toRange(zone)

    private fun Pair<LocalDate, LocalDate>.toRange(zone: ZoneId): LongRange {
        val startMillis = first.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = second.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
        return startMillis..endMillis
    }
}
