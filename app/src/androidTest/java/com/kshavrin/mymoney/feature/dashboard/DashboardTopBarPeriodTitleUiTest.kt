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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Verifies that the top-bar period title never overflows or truncates.
 *
 * The DASHBOARD_TOP_BAR_PERIOD_TAG lives on the PeriodSwitcher Row, which merges its
 * children's semantics.  GetTextLayoutResult is only populated on Text nodes, so layout
 * assertions are performed via onNodeWithText(label, useUnmergedTree = true), which
 * targets the actual AutoShrinkPeriodTitle Text node.  The period-tag assertions confirm
 * the containing switcher is visible; the text-node assertions confirm the rendering contract.
 */
@RunWith(AndroidJUnit4::class)
class DashboardTopBarPeriodTitleUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── single-token labels (must be 1 line, no overflow) ──────────────────────

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

    // ── multi-line labels (must be exactly 2 lines, no overflow) ──────────────

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

    // ── toolbar controls remain accessible ────────────────────────────────────

    @Test
    fun `all six toolbar icon controls remain visible when period title spans two lines`() {
        setDashboard(Period.Week(LocalDate.of(2026, 6, 15)))

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_menu))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_transfer))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_previous))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.period_next))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_search))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(ctx.getString(R.string.dashboard_overflow_menu))
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
