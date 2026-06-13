package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

class RecurringSchedulerTest {
    private val zone = ZoneOffset.UTC

    private fun template(
        kind: String,
        interval: Int = 1,
        byDay: String? = null,
        nextRunAt: Instant = Instant.parse("2026-05-20T00:00:00Z"),
    ) = RecurringTemplate(
        id = 1L,
        baseKind = TransactionKind.Expense,
        amount = BigDecimal.ONE,
        currencyId = 1L,
        accountId = 1L,
        categoryId = 100L,
        toAccountId = null,
        note = null,
        recurrenceKind = kind,
        interval = interval,
        byDay = byDay,
        startsAt = Instant.parse("2026-05-01T00:00:00Z"),
        endsAt = null,
        nextRunAt = nextRunAt,
        isActive = true,
    )

    @Test
    fun daily_advances_by_interval() {
        val next = RecurringScheduler().computeNextRun(template("daily", interval = 3), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-05-23T00:00:00Z"), next)
    }

    @Test
    fun weekly_without_byday_advances_by_interval_weeks() {
        val next = RecurringScheduler().computeNextRun(template("weekly", interval = 2), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-06-03T00:00:00Z"), next)
    }

    @Test
    fun weekly_with_byday_picks_next_matching_day() {
        // 2026-05-20 is Wednesday. byDay "FR" -> next Friday is 2026-05-22.
        val next = RecurringScheduler().computeNextRun(template("weekly", interval = 1, byDay = "FR"), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-05-22T00:00:00Z"), next)
    }

    @Test
    fun monthly_advances_by_interval_months() {
        val next = RecurringScheduler().computeNextRun(template("monthly", interval = 1), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-06-20T00:00:00Z"), next)
    }

    @Test
    fun yearly_advances_by_interval_years() {
        val next = RecurringScheduler().computeNextRun(template("yearly", interval = 1), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2027-05-20T00:00:00Z"), next)
    }
}
