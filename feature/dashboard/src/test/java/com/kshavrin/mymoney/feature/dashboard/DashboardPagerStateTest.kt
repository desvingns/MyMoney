package com.kshavrin.mymoney.feature.dashboard

import com.kshavrin.mymoney.core.domain.model.Period
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure unit coverage for the pager-state derivations introduced in SPEC
 * dashboard-swipe-period-paging-02.
 *
 * These tests exercise the [DashboardState.previousPeriodPage] /
 * [DashboardState.nextPeriodPage] fields and the Period-based paging-disabled flag
 * (Period.All → no paging) without any Compose or coroutine infrastructure.
 */
class DashboardPagerStateTest {
    // ── pagingEnabled: Period.All collapses to a single page ──────────────────

    @Test
    fun `Period All keeps previousPeriodPage null — neighbor precompute skipped`() {
        val state = DashboardState(period = Period.All, isLoading = false)
        assertNull(
            "previousPeriodPage must be null for Period.All (no paging)",
            state.previousPeriodPage,
        )
    }

    @Test
    fun `Period All keeps nextPeriodPage null — neighbor precompute skipped`() {
        val state = DashboardState(period = Period.All, isLoading = false)
        assertNull(
            "nextPeriodPage must be null for Period.All (no paging)",
            state.nextPeriodPage,
        )
    }

    @Test
    fun `non-All period state accepts both neighbor pages`() {
        val prev = PeriodPageState(period = Period.Month(YearMonth.of(2026, 3)))
        val next = PeriodPageState(period = Period.Month(YearMonth.of(2026, 5)))
        val state =
            DashboardState(
                period = Period.Month(YearMonth.of(2026, 4)),
                isLoading = false,
                previousPeriodPage = prev,
                nextPeriodPage = next,
            )
        assertTrue("previousPeriodPage must be present for a non-All period", state.previousPeriodPage != null)
        assertTrue("nextPeriodPage must be present for a non-All period", state.nextPeriodPage != null)
    }

    // ── Period.All.next() and .previous() both return All ────────────────────
    // (This validates the single-page invariant: the pager has nowhere to go.)

    @Test
    fun `Period All next returns All`() {
        val shifted = Period.All.next()
        assertTrue(
            "Period.All.next() must be Period.All so the pager has no adjacent page",
            shifted is Period.All,
        )
    }

    @Test
    fun `Period All previous returns All`() {
        val shifted = Period.All.previous()
        assertTrue(
            "Period.All.previous() must be Period.All so the pager has no adjacent page",
            shifted is Period.All,
        )
    }

    // ── PeriodPageState does not recurse ─────────────────────────────────────

    @Test
    fun `PeriodPageState has no previousPeriodPage or nextPeriodPage fields`() {
        val pageState = PeriodPageState(period = Period.Month(YearMonth.of(2026, 4)))
        val fieldNames = pageState.javaClass.declaredFields.map { it.name }
        assertFalse(
            "PeriodPageState must not have a previousPeriodPage field — precompute must not recurse",
            fieldNames.contains("previousPeriodPage"),
        )
        assertFalse(
            "PeriodPageState must not have a nextPeriodPage field — precompute must not recurse",
            fieldNames.contains("nextPeriodPage"),
        )
    }

    // ── PeriodPageState fields match DashboardState.toCurrentPageState fields ─

    @Test
    fun `PeriodPageState isLoading defaults to true before data lands`() {
        val pageState = PeriodPageState(period = Period.Month(YearMonth.of(2026, 4)))
        assertTrue(
            "PeriodPageState.isLoading must default to true — page shows a blank until data arrives",
            pageState.isLoading,
        )
    }

    @Test
    fun `PeriodPageState expenseTiles defaults to empty`() {
        val pageState = PeriodPageState(period = Period.Month(YearMonth.of(2026, 4)))
        assertTrue(
            "PeriodPageState.expenseTiles must default to empty before data lands",
            pageState.expenseTiles.isEmpty(),
        )
    }

    @Test
    fun `PeriodPageState isSeparateMode defaults to false`() {
        val pageState = PeriodPageState(period = Period.Day(LocalDate.of(2026, 6, 1)))
        assertFalse(
            "PeriodPageState.isSeparateMode must default to false",
            pageState.isSeparateMode,
        )
    }

    // ── Direction semantics: left swipe → next period, right swipe → previous ─

    @Test
    fun `Month next advances by one month`() {
        val current = Period.Month(YearMonth.of(2026, 4))
        val next = current.next()
        assertTrue(
            "next() of April 2026 must be May 2026",
            next == Period.Month(YearMonth.of(2026, 5)),
        )
    }

    @Test
    fun `Month previous steps back by one month`() {
        val current = Period.Month(YearMonth.of(2026, 4))
        val prev = current.previous()
        assertTrue(
            "previous() of April 2026 must be March 2026",
            prev == Period.Month(YearMonth.of(2026, 3)),
        )
    }

    @Test
    fun `CustomRange next shifts the window forward by the range length`() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 7)
        val range = Period.CustomRange(start, end)
        val next = range.next() as Period.CustomRange
        assertTrue(
            "custom range next must start one window-length after current start",
            next.start == LocalDate.of(2026, 5, 8),
        )
        assertTrue(
            "custom range next must end one window-length after current end",
            next.end == LocalDate.of(2026, 5, 14),
        )
    }

    @Test
    fun `CustomRange previous shifts the window back by the range length`() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 7)
        val range = Period.CustomRange(start, end)
        val prev = range.previous() as Period.CustomRange
        assertTrue(
            "custom range previous must start one window-length before current start",
            prev.start == LocalDate.of(2026, 4, 24),
        )
        assertTrue(
            "custom range previous must end one window-length before current end",
            prev.end == LocalDate.of(2026, 4, 30),
        )
    }
}
