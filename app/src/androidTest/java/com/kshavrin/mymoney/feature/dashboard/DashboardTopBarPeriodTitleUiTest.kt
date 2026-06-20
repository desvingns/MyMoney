package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Verifies that the two-row top bar renders the period label without overflow or truncation.
 *
 * Layout contract (two-row DashboardTopBar):
 *   Row 1 — exactly 4 icon buttons: menu, transfer, search, more-vert.
 *   Row 2 — full-width PeriodSwitcher: ‹ <AutoShrinkPeriodTitle> ›
 *
 * DASHBOARD_TOP_BAR_PERIOD_TAG is on the PeriodSwitcher Row (row 2).
 * GetTextLayoutResult is only populated on Text nodes, so layout assertions use
 * onNodeWithText(..., useUnmergedTree = true) to reach the AutoShrinkPeriodTitle Text.
 *
 * Single-token labels (Day, current-year Month, Year, All) must render on exactly 1 line.
 * Range/multi-part labels (Week, off-year Month, CustomRange) must render on exactly 2 lines.
 * Neither variant may overflow its width.
 */
@RunWith(AndroidJUnit4::class)
class DashboardTopBarPeriodTitleUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── two-row layout structural assertions ──────────────────────────────────

    @Test
    fun `period switcher sits below the icon row and is not in the same row as the icons`() {
        setDashboard(Period.Month(YearMonth.of(2026, 9)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val menuBounds =
            composeTestRule
                .onNodeWithContentDescription(ctx.getString(R.string.dashboard_menu))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val periodBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "PeriodSwitcher row must start at or below the bottom of the icon row " +
                "(menuBottom=${menuBounds.bottom}, periodTop=${periodBounds.top})",
            periodBounds.top >= menuBounds.bottom,
        )
    }

    @Test
    fun `icon row contains exactly four icon buttons menu transfer search more`() {
        setDashboard(Period.Month(YearMonth.of(2026, 9)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_menu))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_transfer))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_search))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_overflow_menu))
            .assertIsDisplayed()
    }

    @Test
    fun `period switcher fills the full width of the top bar second row`() {
        setDashboard(Period.Month(YearMonth.of(2026, 9)))

        val rootBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        val screenWidth =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.displayMetrics.widthPixels
                .toFloat()

        assertTrue(
            "PeriodSwitcher must span the full screen width " +
                "(switcher width=${rootBounds.width}, screen=$screenWidth)",
            rootBounds.width >= screenWidth * 0.99f,
        )
    }

    // ── single-token labels (must be 1 line, no overflow) ────────────────────

    @Test
    fun `current-year month renders the full month name on exactly one line without overflow`() {
        val period = Period.Month(YearMonth.of(2026, 9))
        val expectedLabel =
            YearMonth
                .of(2026, 9)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", deviceLocale()))

        setDashboard(period)

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
        assertEquals("single-token month must render on one line", 1, layout.lineCount)
        assertFalse("month title must not overflow its width", layout.didOverflowWidth)
        assertFalse("month title must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `another current-year month renders on exactly one line without overflow`() {
        val period = Period.Month(YearMonth.of(2026, 1))
        val expectedLabel =
            YearMonth
                .of(2026, 1)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", deviceLocale()))

        setDashboard(period)

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
        assertEquals("January label must stay on one line after auto-shrink", 1, layout.lineCount)
        assertFalse("January label must not overflow horizontally", layout.didOverflowWidth)
        assertFalse("January label must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `year period renders on exactly one line without overflow`() {
        setDashboard(Period.Year(2026))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("2026", useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText("2026", useUnmergedTree = true).textLayout()
        assertEquals("year label must render on exactly one line", 1, layout.lineCount)
        assertFalse("year label must not overflow its width", layout.didOverflowWidth)
        assertFalse("year label must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `day period renders on exactly one line without overflow`() {
        setDashboard(Period.Day(LocalDate.of(2026, 6, 25)))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("25.06.26", useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText("25.06.26", useUnmergedTree = true).textLayout()
        assertEquals("day label must render on exactly one line", 1, layout.lineCount)
        assertFalse("day label must not overflow its width", layout.didOverflowWidth)
        assertFalse("day label must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `all period renders on exactly one line without overflow`() {
        setDashboard(Period.All)

        val allLabel =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .getString(R.string.period_all)

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(allLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(allLabel, useUnmergedTree = true).textLayout()
        assertEquals("all period label must render on one line", 1, layout.lineCount)
        assertFalse("all period label must not overflow its width", layout.didOverflowWidth)
        assertFalse("all period label must not overflow its height", layout.didOverflowHeight)
    }

    // ── multi-line labels (must be 2 lines, both parts visible, no overflow) ─

    @Test
    fun `week range renders both dates on two lines without overflow`() {
        val weekStart = LocalDate.of(2026, 6, 15)
        val expectedLabel = "15.06.26\n21.06.26"

        setDashboard(Period.Week(weekStart))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
        assertEquals("week range must span exactly two lines", 2, layout.lineCount)
        assertFalse("each line of the week range must fit the width", layout.didOverflowWidth)
        assertFalse("week range must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `week range shows both dates in the rendered text`() {
        val weekStart = LocalDate.of(2026, 6, 15)

        setDashboard(Period.Week(weekStart))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("15.06.26\n21.06.26", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `off-year month renders month name and year on two lines without overflow`() {
        val period = Period.Month(YearMonth.of(2025, 4))
        val monthName =
            YearMonth
                .of(2025, 4)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", deviceLocale()))
        val expectedLabel = "$monthName\n2025"

        setDashboard(period)

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
        assertEquals("off-year month must span exactly two lines", 2, layout.lineCount)
        assertFalse("off-year month must not overflow its width", layout.didOverflowWidth)
        assertFalse("off-year month must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `custom range renders both dates on two lines without overflow`() {
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 5, 31)
        val expectedLabel = "01.03.26\n31.05.26"

        setDashboard(Period.CustomRange(start, end))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
        assertEquals("custom range must span exactly two lines", 2, layout.lineCount)
        assertFalse("custom range must not overflow its width", layout.didOverflowWidth)
        assertFalse("custom range must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `custom range shows both boundary dates in rendered text`() {
        val start = LocalDate.of(2026, 1, 10)
        val end = LocalDate.of(2026, 12, 31)
        val expectedLabel = "10.01.26\n31.12.26"

        setDashboard(Period.CustomRange(start, end))

        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()
    }

    // ── toolbar controls remain accessible ────────────────────────────────────

    @Test
    fun `four icon row controls and period chevrons remain visible when period title spans two lines`() {
        setDashboard(Period.Week(LocalDate.of(2026, 6, 15)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_menu))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_transfer))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_search))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_overflow_menu))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_previous))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_next))
            .assertIsDisplayed()
    }

    @Test
    fun `period switcher chevron controls are present for Day period`() {
        assertChevronControls(Period.Day(LocalDate.of(2026, 6, 20)))
    }

    @Test
    fun `period switcher chevron controls are present for Week period`() {
        assertChevronControls(Period.Week(LocalDate.of(2026, 6, 15)))
    }

    @Test
    fun `period switcher chevron controls are present for current-year Month period`() {
        assertChevronControls(Period.Month(YearMonth.of(2026, 6)))
    }

    @Test
    fun `period switcher chevron controls are present for off-year Month period`() {
        assertChevronControls(Period.Month(YearMonth.of(2025, 4)))
    }

    @Test
    fun `period switcher chevron controls are present for Year period`() {
        assertChevronControls(Period.Year(2026))
    }

    @Test
    fun `period switcher chevron controls are present for All period`() {
        assertChevronControls(Period.All)
    }

    @Test
    fun `period switcher chevron controls are present for CustomRange period`() {
        assertChevronControls(Period.CustomRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 5, 31)))
    }

    // ── period switcher row does not contain icon-row controls ────────────────

    @Test
    fun `period switcher row is distinct from icon row - chevrons sit inside period tag bounds`() {
        setDashboard(Period.Month(YearMonth.of(2026, 9)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val periodBounds =
            composeTestRule
                .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        val prevBounds =
            composeTestRule
                .onNodeWithContentDescription(ctx.getString(R.string.period_previous))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        val nextBounds =
            composeTestRule
                .onNodeWithContentDescription(ctx.getString(R.string.period_next))
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "Previous chevron vertical centre must be within PeriodSwitcher row bounds",
            prevBounds.center.y >= periodBounds.top && prevBounds.center.y <= periodBounds.bottom,
        )
        assertTrue(
            "Next chevron vertical centre must be within PeriodSwitcher row bounds",
            nextBounds.center.y >= periodBounds.top && nextBounds.center.y <= periodBounds.bottom,
        )
    }

    private fun assertChevronControls(period: Period) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        setDashboard(period)
        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_previous))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_next))
            .assertIsDisplayed()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun setDashboard(period: Period) {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = period, isLoading = false),
                    onEvent = {},
                )
            }
        }
    }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        return results.first()
    }

    private fun deviceLocale() =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.configuration.locales[0]
}
