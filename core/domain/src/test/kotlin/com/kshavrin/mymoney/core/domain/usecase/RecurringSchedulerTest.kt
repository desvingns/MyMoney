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
        // startsAt = 2026-05-01 → anchorDay = 1; current = 2026-05-20
        // candidate = 2026-06-20; withDayOfMonth(min(1, 30)) = 2026-06-01
        val next = RecurringScheduler().computeNextRun(template("monthly", interval = 1), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-06-01T00:00:00Z"), next)
    }

    @Test
    fun yearly_advances_by_interval_years() {
        val next = RecurringScheduler().computeNextRun(template("yearly", interval = 1), Instant.EPOCH, zone)
        assertEquals(Instant.parse("2027-05-20T00:00:00Z"), next)
    }

    // ── monthly anchor-day fixtures ──────────────────────────────────────────

    @Test
    fun `monthly anchor 31 clamps to 28 when next month is February`() {
        // current = 2026-01-31, startsAt = 2025-12-31, interval = 1
        // next = (2026-01 + 1 month) = 2026-02; anchorDay=31 > lengthOfMonth(28) → clamp to 28
        val t =
            RecurringTemplate(
                id = 1L,
                baseKind = TransactionKind.Expense,
                amount = BigDecimal.ONE,
                currencyId = 1L,
                accountId = 1L,
                categoryId = 100L,
                toAccountId = null,
                note = null,
                recurrenceKind = "monthly",
                interval = 1,
                byDay = null,
                startsAt = Instant.parse("2025-12-31T00:00:00Z"),
                endsAt = null,
                nextRunAt = Instant.parse("2026-01-31T00:00:00Z"),
                isActive = true,
            )
        val next = RecurringScheduler().computeNextRun(t, Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-02-28T00:00:00Z"), next)
    }

    @Test
    fun `monthly anchor 31 recovers from clamped February back to 31 in March`() {
        // current = 2026-02-28 (was clamped from 31), startsAt = 2025-12-31, interval = 1
        // next = (2026-02 + 1 month) = 2026-03; anchorDay=31, lengthOfMonth(31) → returns to 31
        val t =
            RecurringTemplate(
                id = 1L,
                baseKind = TransactionKind.Expense,
                amount = BigDecimal.ONE,
                currencyId = 1L,
                accountId = 1L,
                categoryId = 100L,
                toAccountId = null,
                note = null,
                recurrenceKind = "monthly",
                interval = 1,
                byDay = null,
                startsAt = Instant.parse("2025-12-31T00:00:00Z"),
                endsAt = null,
                nextRunAt = Instant.parse("2026-02-28T00:00:00Z"),
                isActive = true,
            )
        val next = RecurringScheduler().computeNextRun(t, Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-03-31T00:00:00Z"), next)
    }

    // ── weekly + byDay with interval > 1 fixtures ────────────────────────────

    @Test
    fun `weekly byDay MON interval 2 fires every two weeks`() {
        // current = 2026-06-08 (Mon), base = current + 1 week = 2026-06-15 (Mon)
        // first Mon strictly after base: 2026-06-22
        val t = template("weekly", interval = 2, byDay = "MO", nextRunAt = Instant.parse("2026-06-08T00:00:00Z"))
        val next = RecurringScheduler().computeNextRun(t, Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-06-22T00:00:00Z"), next)
    }

    @Test
    fun `weekly byDay MON interval 1 fires next week`() {
        // current = 2026-06-08 (Mon), base = current + 0 weeks = 2026-06-08 (Mon)
        // first Mon strictly after base: 2026-06-15
        val t = template("weekly", interval = 1, byDay = "MO", nextRunAt = Instant.parse("2026-06-08T00:00:00Z"))
        val next = RecurringScheduler().computeNextRun(t, Instant.EPOCH, zone)
        assertEquals(Instant.parse("2026-06-15T00:00:00Z"), next)
    }
}
