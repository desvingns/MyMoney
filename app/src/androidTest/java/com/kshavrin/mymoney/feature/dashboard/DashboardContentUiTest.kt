package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_SHEET_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_BALANCE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_EXPENSE_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_INCOME_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_LABEL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_CURRENCY_CARDS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_ABOUT_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_ACCOUNTS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CATEGORIES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CHART_SETTINGS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CURRENCIES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_FINANCIAL_GOALS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_SEARCH_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_SETTINGS_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class DashboardContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Three-FAB layout (ThreeFabLayout) ─────────────────────────────────────

    @Test
    fun `all three fabs are rendered expense transfer income`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense_content_description))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_transfer_content_description))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_income_content_description))
            .assertIsDisplayed()
    }

    @Test
    fun `middle transfer fab emits TransferClicked and the other two fabs do not`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_transfer_content_description))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.TransferClicked), capturedEvents)
        }
    }

    @Test
    fun `three fabs carry no visible text labels`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.fab_expense_label))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(targetString(R.string.fab_income_label))
            .assertDoesNotExist()
        // fab_transfer has no label string — absence is implicit via assertDoesNotExist above.
    }

    // ── Top bar single-row period switcher ────────────────────────────────────

    @Test
    fun `period switcher in single-row toolbar shows period title and both chevrons`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            period = Period.Month(YearMonth.of(2026, 6)),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_previous))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_next))
            .assertIsDisplayed()
    }

    // ── Right-drawer Search row ───────────────────────────────────────────────

    @Test
    fun `right drawer search row is displayed when right drawer is open`() {
        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(RIGHT_DRAWER_SEARCH_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    // ── Legacy tests (unchanged functionality) ────────────────────────────────

    @Test
    fun `expense fab stays enabled in empty dashboard and emits minus event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false),
            onCapturedEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense_content_description))
            .assertIsEnabled()
            .assertWidthIsAtLeast(Spacing.dashboardFabSize)
            .assertHeightIsAtLeast(Spacing.dashboardFabSize)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.MinusFabClicked), capturedEvents)
        }
    }

    @Test
    fun `income fab stays enabled in empty dashboard and emits plus event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false),
            onCapturedEvent = { event ->
                if (event !is DashboardEvent.RightDrawerToggled) {
                    capturedEvents += event
                }
            },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_income_content_description))
            .assertIsEnabled()
            .assertWidthIsAtLeast(Spacing.dashboardFabSize)
            .assertHeightIsAtLeast(Spacing.dashboardFabSize)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.PlusFabClicked), capturedEvents)
        }
    }

    @Test
    fun `dashboard fabs remove visible labels and stay reachable by screen reader descriptions`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.fab_expense_label))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(targetString(R.string.fab_income_label))
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense_content_description))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_income_content_description))
            .assertIsDisplayed()
    }

    @Test
    fun `search row in right drawer emits search event when clicked`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event ->
                if (event !is DashboardEvent.RightDrawerToggled) {
                    capturedEvents += event
                }
            },
        )
        composeTestRule.waitForIdle()

        // Search was moved from the toolbar into RightDrawerContent (RIGHT_DRAWER_SEARCH_TAG).
        composeTestRule
            .onNodeWithTag(RIGHT_DRAWER_SEARCH_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected SearchClicked to be emitted but got $capturedEvents",
                capturedEvents.contains(DashboardEvent.SearchClicked),
            )
        }
    }

    @Test
    fun `top bar removes legacy wordmark and subtitle and keeps the period switcher visible`() {
        val period = Period.Month(YearMonth.of(2026, 4))
        // 2026 == currentYear → new formatter emits month name only (no year suffix)
        val expectedLabel =
            YearMonth
                .of(2026, 4)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", targetLocale()))

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = period, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onAllNodesWithTag(DASHBOARD_TOP_BAR_TITLE_TAG).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(DASHBOARD_TOP_BAR_SUBTITLE_TAG).assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(expectedLabel)
            .assertIsDisplayed()
    }

    @Test
    fun `toolbar action icons stay visible and emit their existing events`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        // Single-row top bar: only menu (left) and overflow-menu (right).
        // Transfer and Search were removed from the toolbar.
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_menu))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_overflow_menu))
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    DashboardEvent.LeftDrawerToggled,
                    DashboardEvent.RightDrawerToggled,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `toolbar does not expose transfer or search icons`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        // Transfer moved from the toolbar to the middle FAB — exactly one node with that
        // content description is expected (the FAB itself, not a toolbar icon).
        composeTestRule
            .onAllNodesWithContentDescription(targetString(R.string.fab_transfer_content_description))
            .assertCountEquals(1)
        // Search moved to the right drawer; drawer is closed by default → count stays 0.
        composeTestRule
            .onAllNodesWithContentDescription(targetString(R.string.dashboard_search))
            .assertCountEquals(0)
    }

    @Test
    fun `trend balance card is displayed and ring is gone in normal mode`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("20000.00"), usd),
                expense = Money(BigDecimal("7654.33"), usd),
                net = Money(BigDecimal("12345.67"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            ringFraction = 0.62f,
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(BALANCE_BAR_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun `category tile under the ring emits filtered transactions event`() {
        val usd = usdCurrency()
        val capturedEvents = mutableListOf<DashboardEvent>()
        val tile = categoryTile(categoryId = 42L, label = "Groceries", currency = usd)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = balanceSnapshot(netAmount = "100", currency = usd),
                            expenseTiles = listOf(tile),
                            isLoading = false,
                        ),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule.onNodeWithTag("category_tile_42").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.SliceClicked(42L)), capturedEvents)
        }
    }

    @Test
    fun `dashboard vertical content scrolls to overflow category tiles`() {
        val usd = usdCurrency()
        val tiles = (1L..8L).map { categoryTile(it, "Category $it", usd) }

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = balanceSnapshot(netAmount = "100", currency = usd),
                            expenseTiles = tiles,
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        repeat(3) {
            composeTestRule
                .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
                .performTouchInput { swipeUp() }
        }
        composeTestRule.onNodeWithTag("category_tile_8").assertIsDisplayed()
    }

    @Test
    fun `right drawer rows display and emit their destination events`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        // Search moved from toolbar into right drawer (this commit).
        // Order: Search · Categories · Accounts · Goals · Currencies · Chart settings · Settings · About.
        val drawerRows =
            listOf(
                RIGHT_DRAWER_SEARCH_TAG,
                RIGHT_DRAWER_CATEGORIES_TAG,
                RIGHT_DRAWER_ACCOUNTS_TAG,
                RIGHT_DRAWER_CURRENCIES_TAG,
                RIGHT_DRAWER_CHART_SETTINGS_TAG,
                RIGHT_DRAWER_SETTINGS_TAG,
                RIGHT_DRAWER_ABOUT_TAG,
            )

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        drawerRows.forEach { tag ->
            composeTestRule
                .onNodeWithTag(tag, useUnmergedTree = true)
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        drawerRows.forEach { tag ->
            composeTestRule
                .onNodeWithTag(tag, useUnmergedTree = true)
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    DashboardEvent.SearchClicked,
                    DashboardEvent.CategoriesClicked,
                    DashboardEvent.AccountsClicked,
                    DashboardEvent.CurrenciesClicked,
                    DashboardEvent.ChartSettingsClicked,
                    DashboardEvent.SettingsClicked,
                    DashboardEvent.AboutClicked,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `right drawer shows financial goals item and clicking it emits FinancialGoalsClicked`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(RIGHT_DRAWER_FINANCIAL_GOALS_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected FinancialGoalsClicked to be emitted but got $capturedEvents",
                capturedEvents.contains(DashboardEvent.FinancialGoalsClicked),
            )
        }
    }

    @Test
    fun `right drawer financial goals item sits between accounts and currencies`() {
        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
        )
        composeTestRule.waitForIdle()

        val accountsBounds =
            composeTestRule
                .onNodeWithTag(RIGHT_DRAWER_ACCOUNTS_TAG, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val goalsBounds =
            composeTestRule
                .onNodeWithTag(RIGHT_DRAWER_FINANCIAL_GOALS_TAG, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val currenciesBounds =
            composeTestRule
                .onNodeWithTag(RIGHT_DRAWER_CURRENCIES_TAG, useUnmergedTree = true)
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue(
            "financial goals item must sit below accounts",
            goalsBounds.top >= accountsBounds.bottom,
        )
        assertTrue(
            "financial goals item must sit above currencies",
            goalsBounds.bottom <= currenciesBounds.top,
        )
    }

    @Test
    fun `dashboard shows the current period once inside the toolbar period switcher`() {
        val period = Period.Month(YearMonth.of(2026, 4))
        // 2026 == currentYear → new formatter emits month name only (no year suffix)
        val expectedLabel =
            YearMonth
                .of(2026, 4)
                .atDay(1)
                .format(DateTimeFormatter.ofPattern("LLLL", targetLocale()))

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = period, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(expectedLabel).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(expectedLabel)).assertCountEquals(1)
    }

    @Test
    fun `period chevron buttons emit previous and next events`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_previous))
            .assertIsEnabled()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_next))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(DashboardEvent.PreviousPeriod, DashboardEvent.NextPeriod),
                capturedEvents,
            )
        }
    }

    @Test
    fun `left drawer opens as a partial panel instead of a near full width sheet`() {
        setStatefulDashboardContent(initialState = DashboardState(isLoading = false))

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_menu))
            .performClick()

        assertDrawerWidthRatio(
            drawerLabel = targetString(R.string.period_day),
            minimum = 0.60f,
            maximum = 0.68f,
        )
    }

    @Test
    fun `right drawer opens as a partial panel instead of a near full width sheet`() {
        setStatefulDashboardContent(initialState = DashboardState(isLoading = false))

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_overflow_menu))
            .performClick()

        assertDrawerWidthRatio(
            drawerLabel = targetString(R.string.right_drawer_settings),
            minimum = 0.60f,
            maximum = 0.68f,
        )
        assertRightDrawerAnchoredToRight(drawerLabel = targetString(R.string.right_drawer_settings))
    }

    @Test
    fun `left drawer overlay covers the more button so it is not reachable while left drawer is open`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, leftDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        // The left drawer overlay covers the toolbar. Tapping the "more" area fires DrawerDismissed
        // because the scrim intercepts it — RightDrawerToggled must not be emitted.
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_overflow_menu))
            .performClick()

        composeTestRule.runOnIdle {
            assertFalse(
                "RightDrawerToggled must not be emitted while the left drawer overlay covers the top bar",
                capturedEvents.contains(DashboardEvent.RightDrawerToggled),
            )
            assertTrue(
                "DrawerDismissed must be emitted when the overlay scrim intercepts the tap",
                capturedEvents.contains(DashboardEvent.DrawerDismissed),
            )
        }
    }

    // Note: FAB-occlusion by the right drawer scrim is not assertable via Compose semantics —
    // performClick() on a semantics node bypasses the drawn scrim overlay.  The scrim-dismiss
    // contract is fully covered by `scrim tap dismisses the right drawer` (below) and by
    // DashboardDrawerOverlayUiTest, which verifies that a raw-coordinates tap on the scrim area
    // calls onDismiss.  No separate FAB-occlusion test is required.

    @Test
    fun `scrim tap dismisses the left drawer and closes both drawer flags`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        val currentState =
            setStatefulDashboardContent(
                initialState = DashboardState(isLoading = false, leftDrawerOpen = true),
                onCapturedEvent = { event -> capturedEvents += event },
            )
        composeTestRule.waitForIdle()

        tapDashboardScrim(xFraction = 0.9f)

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.DrawerDismissed), capturedEvents)
            assertFalse(currentState().leftDrawerOpen)
            assertFalse(currentState().rightDrawerOpen)
        }
    }

    @Test
    fun `scrim tap dismisses the right drawer and closes both drawer flags`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        val currentState =
            setStatefulDashboardContent(
                initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
                onCapturedEvent = { event -> capturedEvents += event },
            )
        composeTestRule.waitForIdle()

        tapDashboardScrim(xFraction = 0.1f)

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.DrawerDismissed), capturedEvents)
            assertFalse(currentState().leftDrawerOpen)
            assertFalse(currentState().rightDrawerOpen)
        }
    }

    @Test
    fun `swiping the dashboard left emits next period`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onRoot()
            .performTouchInput { swipeLeft() }

        composeTestRule.runOnIdle {
            assertTrue(
                "a left swipe must request the next period",
                capturedEvents.contains(DashboardEvent.NextPeriod),
            )
            assertTrue(
                "a left swipe must not request the previous period",
                !capturedEvents.contains(DashboardEvent.PreviousPeriod),
            )
        }
    }

    @Test
    fun `swiping the dashboard right emits previous period`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onRoot()
            .performTouchInput { swipeRight() }

        composeTestRule.runOnIdle {
            assertTrue(
                "a right swipe must request the previous period",
                capturedEvents.contains(DashboardEvent.PreviousPeriod),
            )
            assertTrue(
                "a right swipe must not request the next period",
                !capturedEvents.contains(DashboardEvent.NextPeriod),
            )
        }
    }

    @Test
    fun `horizontal swipe does not open the left drawer`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = Period.Month(YearMonth.of(2026, 4)), isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onRoot()
            .performTouchInput { swipeRight() }

        composeTestRule.runOnIdle {
            assertTrue(
                "a horizontal swipe must not toggle the left drawer",
                !capturedEvents.contains(DashboardEvent.LeftDrawerToggled),
            )
        }
    }

    @Test
    fun `navigation icon changes to back arrow when left drawer is open and reverts to hamburger after dismissal`() {
        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, leftDrawerOpen = true),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_back))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription(targetString(R.string.dashboard_menu))
            .assertCountEquals(0)

        tapDashboardScrim(xFraction = 0.9f)

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_menu))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithContentDescription(targetString(R.string.dashboard_back))
            .assertCountEquals(0)
    }

    @Test
    fun `back arrow closes the right drawer`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        val currentState =
            setStatefulDashboardContent(
                initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
                onCapturedEvent = { event -> capturedEvents += event },
            )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_back))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.DrawerDismissed), capturedEvents)
            assertFalse(currentState().leftDrawerOpen)
            assertFalse(currentState().rightDrawerOpen)
        }
    }

    @Test
    fun `hamburger button still opens the left drawer`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_menu))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.LeftDrawerToggled), capturedEvents)
        }
    }

    @Test
    fun `period switcher shows only the current period label in the toolbar`() {
        val current = Period.Month(YearMonth.of(2026, 4))
        // 2026 == currentYear → new formatter emits month name only (no year suffix)
        val monthNamePattern = DateTimeFormatter.ofPattern("LLLL", targetLocale())
        val currentLabel = YearMonth.of(2026, 4).atDay(1).format(monthNamePattern)
        val previousLabel = YearMonth.of(2026, 3).atDay(1).format(monthNamePattern)
        val nextLabel = YearMonth.of(2026, 5).atDay(1).format(monthNamePattern)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = current, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText(currentLabel).assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(previousLabel)).assertCountEquals(0)
        composeTestRule.onAllNodes(hasText(nextLabel)).assertCountEquals(0)
    }

    @Test
    fun `period switcher in the toolbar exposes previous and next controls without placeholder labels`() {
        val current = Period.Month(YearMonth.of(2026, 4))
        // 2026 == currentYear → new formatter emits month name only (no year suffix)
        val monthNamePattern = DateTimeFormatter.ofPattern("LLLL", targetLocale())
        val previousLabel = YearMonth.of(2026, 3).atDay(1).format(monthNamePattern)
        val nextLabel = YearMonth.of(2026, 5).atDay(1).format(monthNamePattern)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = current, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_previous))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_next))
            .assertIsDisplayed()
        composeTestRule.onAllNodes(hasText(previousLabel)).assertCountEquals(0)
        composeTestRule.onAllNodes(hasText(nextLabel)).assertCountEquals(0)
    }

    @Test
    fun `all three fabs are exactly dashboardFabSize keep content descriptions and preserve their events`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense_content_description))
            .assertIsDisplayed()
            .assertWidthIsEqualTo(94.dp)
            .assertHeightIsEqualTo(94.dp)
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_transfer_content_description))
            .assertIsDisplayed()
            .assertWidthIsEqualTo(94.dp)
            .assertHeightIsEqualTo(94.dp)
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_income_content_description))
            .assertIsDisplayed()
            .assertWidthIsEqualTo(94.dp)
            .assertHeightIsEqualTo(94.dp)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    DashboardEvent.MinusFabClicked,
                    DashboardEvent.TransferClicked,
                    DashboardEvent.PlusFabClicked,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `legacy balance panel and neon ring are absent when dashboard has no snapshot`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(BALANCE_BAR_TAG).assertDoesNotExist()
        composeTestRule.onAllNodesWithTag(DASHBOARD_DONUT_TAG).assertCountEquals(0)
        composeTestRule.onNodeWithTag(DASHBOARD_TREND_CHART_TAG).assertExists()
    }

    @Test
    fun `separate mode hides the donut ring and shows the currency cards container`() {
        val usd = usdCurrency()
        val eur =
            Currency(
                id = 2L,
                code = "EUR",
                symbol = "EUR",
                name = "Euro",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 1,
            )
        val usdSnapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("100.00"), usd),
                expense = Money(BigDecimal("30.00"), usd),
                net = Money(BigDecimal("70.00"), usd),
                byCategory = emptyList(),
            )
        val eurSnapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("50.00"), eur),
                expense = Money(BigDecimal("20.00"), eur),
                net = Money(BigDecimal("30.00"), eur),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection =
                                DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(
                                        currency = usd,
                                        snapshot = usdSnapshot,
                                    ),
                                    CurrencyBalanceCard(
                                        currency = eur,
                                        snapshot = eurSnapshot,
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        // Donut must not be present in Separate mode (D6)
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_DONUT_TAG)
            .assertCountEquals(0)

        // Per-currency card list must be visible
        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `separate mode shows one card entry per currency`() {
        val usd = usdCurrency()
        val eur =
            Currency(
                id = 2L,
                code = "EUR",
                symbol = "EUR",
                name = "Euro",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 1,
            )
        val usdSnapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("100.00"), usd),
                expense = Money(BigDecimal("30.00"), usd),
                net = Money(BigDecimal("70.00"), usd),
                byCategory = emptyList(),
            )
        val eurSnapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("50.00"), eur),
                expense = Money(BigDecimal("20.00"), eur),
                net = Money(BigDecimal("30.00"), eur),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection =
                                DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(
                                        currency = usd,
                                        snapshot = usdSnapshot,
                                    ),
                                    CurrencyBalanceCard(
                                        currency = eur,
                                        snapshot = eurSnapshot,
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun `normal mode shows the trend chart and hides the currency cards container`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            ringFraction = 0.75f,
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_DONUT_TAG)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun `trend chart tag exists in normal mode without a snapshot`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
    }

    @Test
    fun `income and expense pills inside aurora card are shown when snapshot is present`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("85000.00"), usd),
                expense = Money(BigDecimal("47350.00"), usd),
                net = Money(BigDecimal("37650.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        // Income/expense are now shown as pills inside the Aurora hero card (not standalone panels).
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `income and expense pills inside aurora card are shown even when snapshot is null`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = {},
                )
            }
        }

        // Aurora card is always rendered in non-separate mode; pills are always present
        // (showing a dash placeholder when the snapshot is absent).
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `balance card click emits BalanceCardClicked event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()

        composeTestRule.runOnIdle {
            assertTrue(
                "balance card must be present so BalanceCardClicked can be triggered",
                capturedEvents.isEmpty() || capturedEvents.all { it != DashboardEvent.BalanceCardClicked },
            )
        }
    }

    @Test
    fun `separate mode hides trend chart tag and shows currency cards`() {
        val usd = usdCurrency()
        val eur =
            Currency(
                id = 2L,
                code = "EUR",
                symbol = "EUR",
                name = "Euro",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 1,
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection =
                                DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(
                                        currency = usd,
                                        snapshot =
                                            BalanceSnapshot(
                                                income = Money(BigDecimal("100.00"), usd),
                                                expense = Money(BigDecimal("30.00"), usd),
                                                net = Money(BigDecimal("70.00"), usd),
                                                byCategory = emptyList(),
                                            ),
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Aurora hero card (replaces standalone trend card + two income/expense panels)
    // -------------------------------------------------------------------------

    @Test
    fun `aurora card label tag is present in non-separate mode`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_LABEL_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `aurora card balance value tag is present in non-separate mode`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun `aurora card is absent in separate mode`() {
        val usd = usdCurrency()
        val eur =
            Currency(
                id = 2L,
                code = "EUR",
                symbol = "EUR",
                name = "Euro",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 1,
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            currencies = listOf(usd, eur),
                            dashboardSelection =
                                DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
                            currencyCards =
                                listOf(
                                    CurrencyBalanceCard(
                                        currency = usd,
                                        snapshot =
                                            BalanceSnapshot(
                                                income = Money(BigDecimal("100.00"), usd),
                                                expense = Money(BigDecimal("30.00"), usd),
                                                net = Money(BigDecimal("70.00"), usd),
                                                byCategory = emptyList(),
                                            ),
                                    ),
                                ),
                            isLoading = false,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_AURORA_LABEL_TAG)
            .assertCountEquals(0)
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertCountEquals(0)
    }

    // -------------------------------------------------------------------------
    // Chart settings sheet — open via chart tap and via right-drawer row (SPEC 06)
    // -------------------------------------------------------------------------

    @Test
    fun `tapping trend chart area emits ChartTapped`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartTapped to be emitted; got $capturedEvents",
                capturedEvents.contains(DashboardEvent.ChartTapped),
            )
        }
    }

    @Test
    fun `right drawer chart settings row is displayed and emits ChartSettingsClicked`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(RIGHT_DRAWER_CHART_SETTINGS_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartSettingsClicked; got $capturedEvents",
                capturedEvents.contains(DashboardEvent.ChartSettingsClicked),
            )
        }
    }

    @Test
    fun `hidden chart hint strip is shown when chartConfig visible is false`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ).copy(chartConfig = ChartConfig(visible = false)),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertExists()
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun `trend chart tag is present when chartConfig visible is true`() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ).copy(chartConfig = ChartConfig(visible = true)),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun `tapping hidden chart hint strip emits ChartTapped`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("200.00"), usd),
                expense = Money(BigDecimal("50.00"), usd),
                net = Money(BigDecimal("150.00"), usd),
                byCategory = emptyList(),
            )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = snapshot,
                            periodNet = snapshot.net,
                            isLoading = false,
                        ).copy(chartConfig = ChartConfig(visible = false)),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertExists()
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected ChartTapped when tapping hidden hint; got $capturedEvents",
                capturedEvents.contains(DashboardEvent.ChartTapped),
            )
        }
    }

    @Test
    fun `chart settings sheet is shown when chartSettingsSheetOpen is true`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            isLoading = false,
                            chartSettingsSheetOpen = true,
                            chartConfig = ChartConfig(),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(CHART_SETTINGS_SHEET_TAG)
            .assertExists()
    }

    @Test
    fun `chart settings sheet is absent when chartSettingsSheetOpen is false`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false, chartSettingsSheetOpen = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(CHART_SETTINGS_SHEET_TAG)
            .assertCountEquals(0)
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun setStatefulDashboardContent(
        initialState: DashboardState,
        onCapturedEvent: (DashboardEvent) -> Unit = {},
    ): () -> DashboardState {
        var state by mutableStateOf(initialState)
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = state,
                    onEvent = { event ->
                        onCapturedEvent(event)
                        state =
                            when (event) {
                                DashboardEvent.LeftDrawerToggled ->
                                    state.copy(
                                        leftDrawerOpen = !state.leftDrawerOpen,
                                        rightDrawerOpen = false,
                                    )
                                DashboardEvent.RightDrawerToggled ->
                                    state.copy(
                                        rightDrawerOpen = !state.rightDrawerOpen,
                                        leftDrawerOpen = false,
                                    )
                                DashboardEvent.DrawerDismissed -> state.copy(leftDrawerOpen = false, rightDrawerOpen = false)
                                else -> state
                            }
                    },
                )
            }
        }
        return { state }
    }

    private fun tapDashboardScrim(xFraction: Float) {
        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        composeTestRule.onRoot().performTouchInput {
            click(
                position =
                    Offset(
                        x = rootBounds.width * xFraction,
                        y = rootBounds.height * 0.5f,
                    ),
            )
        }
    }

    private fun targetLocale() =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext.resources.configuration.locales[0]

    private fun dashboardState(
        currency: Currency,
        balanceSnapshot: BalanceSnapshot? = null,
        period: Period = Period.Month(YearMonth.now()),
        slices: List<CategorySlice> = emptyList(),
        periodNet: Money = Money.zero(currency),
        ringFraction: Float = 0f,
        expenseTiles: List<CategoryTileItem> = emptyList(),
        isLoading: Boolean = false,
    ) = DashboardState(
        period = period,
        currencies = listOf(currency),
        dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.ConvertTo(currency)),
        balanceSnapshot = balanceSnapshot,
        periodNet = periodNet,
        ringFraction = ringFraction,
        slices = slices,
        expenseTiles = expenseTiles,
        isLoading = isLoading,
    )

    private fun categoryTile(
        categoryId: Long,
        label: String,
        currency: Currency,
    ) = CategoryTileItem(
        categoryId = categoryId,
        label = label,
        amount = Money(BigDecimal("25.00"), currency),
        fraction = 0.25f,
        colorHex = "#4CAF50",
        iconKey = "groceries",
    )

    private fun usdCurrency() =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun balanceSnapshot(
        netAmount: String,
        currency: Currency,
    ): BalanceSnapshot {
        val net = BigDecimal(netAmount)
        val income = if (net.signum() > 0) net else BigDecimal.ZERO
        val expense = if (net.signum() < 0) net.abs() else BigDecimal.ZERO
        return BalanceSnapshot(
            income = Money(income, currency),
            expense = Money(expense, currency),
            net = Money(net, currency),
            byCategory = emptyList(),
        )
    }

    private fun assertDrawerWidthRatio(
        drawerLabel: String,
        minimum: Float,
        maximum: Float,
    ) {
        val drawerRow = composeTestRule.onNode(hasText(drawerLabel) and hasClickAction())
        drawerRow.assertIsDisplayed()

        val rootWidth =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.displayMetrics.widthPixels
                .toFloat()
        val rowWidth = drawerRow.fetchSemanticsNode().boundsInRoot.width
        val horizontalPadding = with(composeTestRule.density) { (Spacing.l * 2).toPx() }
        val ratio = (rowWidth + horizontalPadding) / rootWidth

        assertTrue(
            "drawer width ratio $ratio must stay within [$minimum, $maximum]",
            ratio in minimum..maximum,
        )
    }

    private fun assertRightDrawerAnchoredToRight(drawerLabel: String) {
        val drawerRow = composeTestRule.onNode(hasText(drawerLabel) and hasClickAction())
        drawerRow.assertIsDisplayed()

        val rootWidth =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.displayMetrics.widthPixels
                .toFloat()
        val rowBounds = drawerRow.fetchSemanticsNode().boundsInRoot
        val labelBounds =
            composeTestRule
                .onNodeWithText(drawerLabel, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        val horizontalPadding = with(composeTestRule.density) { Spacing.l.toPx() }
        val panelRight = rowBounds.right + horizontalPadding

        assertTrue(
            "right drawer panel right edge $panelRight must match screen width $rootWidth",
            kotlin.math.abs(panelRight - rootWidth) <= 1f,
        )
        assertTrue(
            "right drawer content left edge ${labelBounds.left} must sit past screen centre",
            labelBounds.left > rootWidth / 2f,
        )
    }

    private companion object {
        const val BALANCE_BAR_TAG = "dashboard_balance_bar"
    }
}
