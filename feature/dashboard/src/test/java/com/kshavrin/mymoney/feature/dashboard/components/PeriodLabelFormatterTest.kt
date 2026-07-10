package com.kshavrin.mymoney.feature.dashboard.components

import com.kshavrin.mymoney.core.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class PeriodLabelFormatterTest {
    private val locale = Locale.US
    private val allLabel = "All"
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)

    // ── Day ──────────────────────────────────────────────────────────────────

    @Test
    fun `day period formats as a localized single-line date`() {
        val date = LocalDate.of(2026, 6, 25)
        val label = Period.Day(date).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals(date.format(dateFormatter), label)
        assertFalse("day label must not contain a newline", label.contains('\n'))
    }

    @Test
    fun `day period formats correctly for first day of year`() {
        val date = LocalDate.of(2025, 1, 1)
        val label = Period.Day(date).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals(date.format(dateFormatter), label)
    }

    @Test
    fun `day period formats correctly for last day of year`() {
        val date = LocalDate.of(2024, 12, 31)
        val label = Period.Day(date).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals(date.format(dateFormatter), label)
    }

    // ── Week ─────────────────────────────────────────────────────────────────

    @Test
    fun `week period formats as two lines weekStart and weekStart plus 6 days`() {
        val weekStart = LocalDate.of(2026, 6, 15)
        val label = Period.Week(weekStart).localizedLabel(locale, allLabel, currentYear = 2026)
        val expected = "${weekStart.format(dateFormatter)}\n${weekStart.plusDays(6).format(dateFormatter)}"
        assertEquals(expected, label)
    }

    @Test
    fun `week label contains exactly one newline`() {
        val weekStart = LocalDate.of(2026, 1, 5)
        val label = Period.Week(weekStart).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals(1, label.count { it == '\n' })
    }

    @Test
    fun `week period spanning two months formats both dates correctly`() {
        val weekStart = LocalDate.of(2026, 5, 28)
        val label = Period.Week(weekStart).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("${weekStart.format(dateFormatter)}\n${weekStart.plusDays(6).format(dateFormatter)}", label)
    }

    @Test
    fun `week period spanning two years formats both dates correctly`() {
        val weekStart = LocalDate.of(2025, 12, 29)
        val label = Period.Week(weekStart).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("${weekStart.format(dateFormatter)}\n${weekStart.plusDays(6).format(dateFormatter)}", label)
    }

    // ── CustomRange ───────────────────────────────────────────────────────────

    @Test
    fun `custom range formats as two lines start and end`() {
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 3, 31)
        val label = Period.CustomRange(start, end).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("${start.format(dateFormatter)}\n${end.format(dateFormatter)}", label)
    }

    @Test
    fun `custom range label contains exactly one newline`() {
        val start = LocalDate.of(2025, 11, 10)
        val end = LocalDate.of(2025, 12, 5)
        val label = Period.CustomRange(start, end).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals(1, label.count { it == '\n' })
    }

    @Test
    fun `custom range spanning two years formats correctly`() {
        val start = LocalDate.of(2025, 12, 15)
        val end = LocalDate.of(2026, 1, 14)
        val label = Period.CustomRange(start, end).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("${start.format(dateFormatter)}\n${end.format(dateFormatter)}", label)
    }

    // ── Month — current year ──────────────────────────────────────────────────

    @Test
    fun `month in current year formats as single-line month name only`() {
        val period = Period.Month(YearMonth.of(2026, 6))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        val expectedMonthName =
            YearMonth
                .of(2026, 6)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", locale))
        assertEquals(expectedMonthName, label)
        assertFalse("current-year month label must not contain a newline", label.contains('\n'))
    }

    @Test
    fun `month in current year january formats as single-line month name only`() {
        val period = Period.Month(YearMonth.of(2026, 1))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        val expectedMonthName =
            YearMonth
                .of(2026, 1)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", locale))
        assertEquals(expectedMonthName, label)
        assertFalse(label.contains('\n'))
    }

    @Test
    fun `month in current year december formats as single-line month name only`() {
        val period = Period.Month(YearMonth.of(2026, 12))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        val expectedMonthName =
            YearMonth
                .of(2026, 12)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", locale))
        assertEquals(expectedMonthName, label)
        assertFalse(label.contains('\n'))
    }

    // ── Month — past year ─────────────────────────────────────────────────────

    @Test
    fun `month in past year formats as single line month name space year`() {
        val period = Period.Month(YearMonth.of(2025, 4))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        val expectedMonthName =
            YearMonth
                .of(2025, 4)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", locale))
        assertEquals("$expectedMonthName 2025", label)
        assertFalse("off-year month label must not contain a newline", label.contains('\n'))
    }

    @Test
    fun `month in past year contains the year number separated by a space`() {
        val period = Period.Month(YearMonth.of(2024, 11))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        assertFalse("off-year month label must not contain a newline", label.contains('\n'))
        assertTrue("off-year month label must end with the year", label.endsWith("2024"))
    }

    @Test
    fun `month in far past year contains the correct year separated by a space`() {
        val period = Period.Month(YearMonth.of(2020, 3))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        assertFalse("off-year month label must not contain a newline", label.contains('\n'))
        assertTrue("off-year month label must end with the year", label.endsWith("2020"))
    }

    // ── Month — future year ───────────────────────────────────────────────────

    @Test
    fun `month in future year formats as single line month name space year`() {
        val period = Period.Month(YearMonth.of(2027, 2))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        val expectedMonthName =
            YearMonth
                .of(2027, 2)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", locale))
        assertEquals("$expectedMonthName 2027", label)
        assertFalse("off-year month label must not contain a newline", label.contains('\n'))
    }

    @Test
    fun `month in future year contains the year number separated by a space`() {
        val period = Period.Month(YearMonth.of(2030, 8))
        val label = period.localizedLabel(locale, allLabel, currentYear = 2026)
        assertFalse("off-year month label must not contain a newline", label.contains('\n'))
        assertTrue("off-year month label must end with the year", label.endsWith("2030"))
    }

    // ── Year ──────────────────────────────────────────────────────────────────

    @Test
    fun `year period formats as single-line four-digit year`() {
        val label = Period.Year(2026).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("2026", label)
        assertFalse("year label must not contain a newline", label.contains('\n'))
    }

    @Test
    fun `year period past year formats as four-digit year`() {
        val label = Period.Year(2021).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("2021", label)
    }

    @Test
    fun `year period future year formats as four-digit year`() {
        val label = Period.Year(2030).localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals("2030", label)
    }

    // ── All ───────────────────────────────────────────────────────────────────

    @Test
    fun `all period returns the allLabel string unchanged`() {
        val label = Period.All.localizedLabel(locale, "Всё", currentYear = 2026)
        assertEquals("Всё", label)
    }

    @Test
    fun `all period with English allLabel returns the allLabel string unchanged`() {
        val label = Period.All.localizedLabel(locale, allLabel, currentYear = 2026)
        assertEquals(allLabel, label)
    }

    // ── Locale sensitivity ────────────────────────────────────────────────────

    @Test
    fun `month name in Russian locale uses Russian word form`() {
        val ruLocale = Locale("ru")
        val period = Period.Month(YearMonth.of(2026, 1))
        val label = period.localizedLabel(ruLocale, "Всё", currentYear = 2026)
        val expectedMonthName =
            YearMonth
                .of(2026, 1)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", ruLocale))
        assertEquals(expectedMonthName, label)
        assertTrue("Russian January must contain Cyrillic", label.any { it in 'а'..'я' || it in 'А'..'Я' })
    }

    @Test
    fun `date formatter uses locale-specific short dates`() {
        val ruLocale = Locale.forLanguageTag("ru-RU")
        val date = LocalDate.of(2026, 6, 25)
        val russianLabel = Period.Day(date).localizedLabel(ruLocale, "Всё", currentYear = 2026)
        val usLabel = Period.Day(date).localizedLabel(Locale.US, "All", currentYear = 2026)

        assertEquals(
            date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(ruLocale)),
            russianLabel,
        )
        assertFalse(russianLabel == usLabel)
    }
}
