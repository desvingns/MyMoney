package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

class RecurringScheduler
    @Inject
    constructor() {
        fun computeNextRun(
            template: RecurringTemplate,
            now: Instant,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Instant {
            val current = template.nextRunAt.atZone(zone)
            val next =
                when (template.recurrenceKind.lowercase()) {
                    "daily" -> current.plusDays(template.interval.toLong())
                    "weekly" -> nextWeekly(current, template)
                    "monthly" -> nextMonthly(current, template, zone)
                    "yearly" -> current.plusYears(template.interval.toLong())
                    else -> throw IllegalArgumentException("Unknown recurrenceKind: ${template.recurrenceKind}")
                }
            return next.toInstant()
        }

        private fun nextWeekly(
            current: ZonedDateTime,
            template: RecurringTemplate,
        ): ZonedDateTime {
            val byDayMask =
                template.byDay
                    ?.split(",")
                    ?.mapNotNull { token ->
                        DAY_MAP[token.trim().uppercase()]
                    }?.toSet() ?: return current.plusWeeks(template.interval.toLong())

            if (byDayMask.isEmpty()) return current.plusWeeks(template.interval.toLong())

            val base = current.plusWeeks((template.interval - 1).toLong())
            var candidate = base.plusDays(1)
            repeat(7) {
                if (candidate.dayOfWeek in byDayMask) return candidate
                candidate = candidate.plusDays(1)
            }
            return current.plusWeeks(template.interval.toLong())
        }

        private fun nextMonthly(
            current: ZonedDateTime,
            template: RecurringTemplate,
            zone: ZoneId,
        ): ZonedDateTime {
            val anchorDay = template.startsAt.atZone(zone).dayOfMonth
            val candidate = current.plusMonths(template.interval.toLong())
            return candidate.withDayOfMonth(minOf(anchorDay, candidate.toLocalDate().lengthOfMonth()))
        }

        private companion object {
            val DAY_MAP =
                mapOf(
                    "MO" to DayOfWeek.MONDAY,
                    "TU" to DayOfWeek.TUESDAY,
                    "WE" to DayOfWeek.WEDNESDAY,
                    "TH" to DayOfWeek.THURSDAY,
                    "FR" to DayOfWeek.FRIDAY,
                    "SA" to DayOfWeek.SATURDAY,
                    "SU" to DayOfWeek.SUNDAY,
                )
        }
    }
