package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.accessibility.tryPerformAccessibilityChecks
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.test.assertTouchWidthIsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Verifies that the single-row top bar renders the period label without overflow or truncation.
 *
 * Layout contract (single-row DashboardTopBar):
 *   One row — [menu/back icon] · [PeriodSwitcher centred: ‹ title+mint-underline ›] · [more icon].
 *   Transfer and Search icons were removed from the toolbar (transfer → middle FAB,
 *   search → right drawer). The toolbar therefore exposes exactly three interactive controls:
 *   navigation icon, two period chevrons (inside PeriodSwitcher), and the overflow-menu icon.
 *
 * DASHBOARD_TOP_BAR_PERIOD_TAG is on the PeriodSwitcher Row inside the single toolbar row.
 * GetTextLayoutResult is only populated on Text nodes, so layout assertions use
 * onNodeWithText(..., useUnmergedTree = true) to reach the AutoShrinkPeriodTitle Text.
 *
 * Single-token labels (Day, current-year Month, Year, All) must render on exactly 1 line.
 * Range/multi-part labels (Week, CustomRange) must render on exactly 2 lines.
 * Neither variant may overflow its width.
 */
@RunWith(AndroidJUnit4::class)
class DashboardTopBarPeriodTitleUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `dashboard top bar passes accessibility framework checks`() {
        setDashboard(Period.Week(LocalDate.of(2026, 6, 15)))

        composeTestRule.enableAccessibilityChecks()
        composeTestRule.onRoot().tryPerformAccessibilityChecks()
    }

    // ── single-row layout structural assertions ───────────────────────────────

    @Test
    fun `period switcher sits in the same single row as the menu and more icons`() {
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

        // In a single-row top bar the period switcher vertically overlaps the menu icon.
        val periodCentreY = (periodBounds.top + periodBounds.bottom) / 2f
        assertTrue(
            "PeriodSwitcher vertical centre ($periodCentreY) must be within menu icon bounds " +
                "(top=${menuBounds.top}, bottom=${menuBounds.bottom})",
            periodCentreY >= menuBounds.top && periodCentreY <= menuBounds.bottom,
        )
    }

    @Test
    fun `toolbar contains exactly three interactive areas menu period more and no transfer or search icons`() {
        setDashboard(Period.Month(YearMonth.of(2026, 9)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // Menu and More must exist in the single-row toolbar.
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_menu))
            .assertAccessibleTouchTarget()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_overflow_menu))
            .assertAccessibleTouchTarget()

        // Transfer moved from the toolbar to the middle FAB.  The FAB is the single node
        // that carries this content description — assert count == 1 (FAB only, not toolbar).
        composeTestRule
            .onAllNodesWithContentDescription(ctx.getString(R.string.fab_transfer_content_description))
            .assertCountEquals(1)
        // Search moved to the right drawer; drawer is closed by default → count stays 0.
        composeTestRule
            .onAllNodesWithContentDescription(ctx.getString(R.string.dashboard_search))
            .assertCountEquals(0)
    }

    @Test
    fun `period switcher is visible inside the single toolbar row`() {
        setDashboard(Period.Month(YearMonth.of(2026, 9)))

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
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
        val expectedLabel = formatDate(LocalDate.of(2026, 6, 25))

        setDashboard(Period.Day(LocalDate.of(2026, 6, 25)))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
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
        val expectedLabel = formatRange(weekStart, weekStart.plusDays(6))

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
        val expectedLabel = formatRange(weekStart, weekStart.plusDays(6))

        setDashboard(Period.Week(weekStart))

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `off-year month renders month name and year on a single line without overflow`() {
        val period = Period.Month(YearMonth.of(2025, 4))
        val monthName =
            YearMonth
                .of(2025, 4)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", deviceLocale()))
        val expectedLabel = "$monthName 2025"

        setDashboard(period)

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()

        val layout = composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).textLayout()
        assertEquals("off-year month must render on exactly one line", 1, layout.lineCount)
        assertFalse("off-year month must not overflow its width", layout.didOverflowWidth)
        assertFalse("off-year month must not overflow its height", layout.didOverflowHeight)
    }

    @Test
    fun `custom range renders both dates on two lines without overflow`() {
        val start = LocalDate.of(2026, 3, 1)
        val end = LocalDate.of(2026, 5, 31)
        val expectedLabel = formatRange(start, end)

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
        val expectedLabel = formatRange(start, end)

        setDashboard(Period.CustomRange(start, end))

        composeTestRule.onNodeWithText(expectedLabel, useUnmergedTree = true).assertIsDisplayed()
    }

    // ── toolbar controls remain accessible ────────────────────────────────────

    @Test
    fun `menu more and period chevrons remain visible when period title spans two lines`() {
        setDashboard(Period.Week(LocalDate.of(2026, 6, 15)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // Menu and more remain in the single-row toolbar even with a two-line period title.
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_menu))
            .assertAccessibleTouchTarget()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_overflow_menu))
            .assertAccessibleTouchTarget()

        // Search moved to the right drawer; drawer is closed by default → still 0.
        composeTestRule
            .onAllNodesWithContentDescription(ctx.getString(R.string.dashboard_search))
            .assertCountEquals(0)

        // Chevrons inside PeriodSwitcher must still be present.
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_previous))
            .assertAccessibleTouchTarget()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_next))
            .assertAccessibleTouchTarget()
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

    // ── period switcher row chevrons sit within the period tag bounds ─────────

    @Test
    fun `period switcher chevrons sit inside the period tag bounds`() {
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
            .assertAccessibleTouchTarget()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_next))
            .assertAccessibleTouchTarget()
    }

    private fun SemanticsNodeInteraction.assertAccessibleTouchTarget(): SemanticsNodeInteraction =
        assertIsDisplayed()
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)

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

    private fun formatDate(date: LocalDate) =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(deviceLocale()))

    private fun formatRange(start: LocalDate, end: LocalDate) =
        "${formatDate(start)}\n${formatDate(end)}"
}
