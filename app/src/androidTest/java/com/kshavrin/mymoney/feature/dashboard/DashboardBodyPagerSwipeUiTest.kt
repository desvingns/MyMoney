package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/**
 * Acceptance tests for the 3-page HorizontalPager body added in SPEC
 * dashboard-swipe-period-paging-02.
 *
 * The pager's centre page always hosts the committed period. Settling onto a neighbour page
 * fires DashboardEvent.NextPeriod / PreviousPeriod via a LaunchedEffect, then re-centres.
 * Period.All collapses to a single page (paging disabled, G13) so swiping has no effect.
 *
 * All assertions are via observable state (captured events, period-label semantics,
 * drawer flags) — no internal pager state is accessed.
 */
@RunWith(AndroidJUnit4::class)
class DashboardBodyPagerSwipeUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    /**
     * Sets stateful content that mirrors the ViewModel's event handling for period changes
     * so the state evolves and the pager can be re-observed after each settle.
     */
    private fun setStatefulDashboard(
        initialPeriod: Period = Period.Month(YearMonth.of(2026, 4)),
        onCapturedEvent: (DashboardEvent) -> Unit = {},
    ): () -> DashboardState {
        var state by mutableStateOf(DashboardState(period = initialPeriod, isLoading = false))
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = state,
                    onEvent = { event ->
                        onCapturedEvent(event)
                        state =
                            when (event) {
                                DashboardEvent.NextPeriod ->
                                    state.copy(period = state.period.next())
                                DashboardEvent.PreviousPeriod ->
                                    state.copy(period = state.period.previous())
                                DashboardEvent.LeftDrawerToggled ->
                                    state.copy(leftDrawerOpen = !state.leftDrawerOpen, rightDrawerOpen = false)
                                DashboardEvent.RightDrawerToggled ->
                                    state.copy(rightDrawerOpen = !state.rightDrawerOpen, leftDrawerOpen = false)
                                DashboardEvent.DrawerDismissed ->
                                    state.copy(leftDrawerOpen = false, rightDrawerOpen = false)
                                else -> state
                            }
                    },
                )
            }
        }
        return { state }
    }

    // ── Commit next period (left swipe past threshold) ────────────────────────

    @Test
    fun `full left swipe on dashboard body commits the next period`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        val currentState =
            setStatefulDashboard(
                initialPeriod = Period.Month(YearMonth.of(2026, 4)),
                onCapturedEvent = { capturedEvents += it },
            )

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeLeft() }

        composeTestRule.waitForIdle()

        assertTrue(
            "left swipe past the settle threshold must emit NextPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.NextPeriod),
        )
        assertFalse(
            "left swipe must not emit PreviousPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.PreviousPeriod),
        )
        assertTrue(
            "committed period must advance to May 2026",
            currentState().period == Period.Month(YearMonth.of(2026, 5)),
        )
    }

    @Test
    fun `full right swipe on dashboard body commits the previous period`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        val currentState =
            setStatefulDashboard(
                initialPeriod = Period.Month(YearMonth.of(2026, 4)),
                onCapturedEvent = { capturedEvents += it },
            )

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.waitForIdle()

        assertTrue(
            "right swipe past the settle threshold must emit PreviousPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.PreviousPeriod),
        )
        assertFalse(
            "right swipe must not emit NextPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.NextPeriod),
        )
        assertTrue(
            "committed period must step back to March 2026",
            currentState().period == Period.Month(YearMonth.of(2026, 3)),
        )
    }

    // ── Small drag snaps back (below settle threshold) ────────────────────────

    @Test
    fun `small drag released below the settle threshold does not change the period`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        // A very short, slow drag (~5% of width, immediately released) stays below the
        // HorizontalPager's settle threshold and snaps back to the centre page.
        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput {
                val startX = centerX
                val endX = startX + (width * 0.05f)
                down(Offset(startX, centerY))
                moveTo(Offset(endX, centerY))
                up()
            }

        composeTestRule.waitForIdle()

        assertFalse(
            "small drag below settle threshold must not emit NextPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.NextPeriod),
        )
        assertFalse(
            "small drag below settle threshold must not emit PreviousPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.PreviousPeriod),
        )
    }

    // ── Period.All disables paging (G13) ──────────────────────────────────────

    @Test
    fun `swiping left on Period All does not emit NextPeriod`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeLeft() }

        composeTestRule.waitForIdle()

        assertFalse(
            "swiping on Period.All must not emit NextPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.NextPeriod),
        )
    }

    @Test
    fun `swiping right on Period All does not emit PreviousPeriod`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.waitForIdle()

        assertFalse(
            "swiping on Period.All must not emit PreviousPeriod; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.PreviousPeriod),
        )
    }

    // ── No-regression: horizontal swipe must not open the left drawer ─────────

    @Test
    fun `right swipe on dashboard body does not open the left drawer`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.waitForIdle()

        assertFalse(
            "a right swipe on the pager body must not emit LeftDrawerToggled; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.LeftDrawerToggled),
        )
    }

    @Test
    fun `left swipe on dashboard body does not open the left drawer`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeLeft() }

        composeTestRule.waitForIdle()

        assertFalse(
            "a left swipe on the pager body must not emit LeftDrawerToggled; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.LeftDrawerToggled),
        )
    }

    // ── Hamburger still opens the left drawer ─────────────────────────────────

    @Test
    fun `hamburger button opens the left drawer after a swipe gesture`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        val currentState =
            setStatefulDashboard(
                initialPeriod = Period.Month(YearMonth.of(2026, 4)),
                onCapturedEvent = { capturedEvents += it },
            )

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.waitForIdle()

        // Reset captured events so we can isolate the next tap.
        capturedEvents.clear()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_menu))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        assertTrue(
            "the hamburger button must still emit LeftDrawerToggled even after a swipe; got $capturedEvents",
            capturedEvents.contains(DashboardEvent.LeftDrawerToggled),
        )
        assertTrue(
            "the left drawer must be open after tapping the hamburger",
            currentState().leftDrawerOpen,
        )
    }

    // ── Period.All shows the All-label in the toolbar ─────────────────────────

    @Test
    fun `Period All shows the period-all label in the top bar with paging disabled`() {
        val allLabel = targetString(R.string.period_all)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.All, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(allLabel)
            .assertIsDisplayed()
    }
}
