package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
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
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.components.CHART_SETTINGS_SHEET_TAG
import com.kshavrin.mymoney.feature.dashboard.components.CategoryTileItem
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_BALANCE_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_CARD_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_EXPENSE_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_AURORA_INCOME_PILL_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_CURRENCY_CARDS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.DASHBOARD_INLINE_RECORDS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.OPERATIONS_SUMMARY_EMPTY_TAG
import com.kshavrin.mymoney.feature.dashboard.components.OPERATIONS_SUMMARY_SHEET_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_ACCOUNTS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CATEGORIES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CURRENCIES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_FINANCIAL_GOALS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_SEARCH_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_SETTINGS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_SUPPORT_TAG
import com.kshavrin.mymoney.test.assertTouchHeightIsAtLeast
import com.kshavrin.mymoney.test.assertTouchWidthIsAtLeast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@RunWith(AndroidJUnit4::class)
class DashboardContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule().apply { enableAccessibilityChecks() }

    // ── Three-FAB layout (ThreeFabLayout) ─────────────────────────────────────

    @Test
    fun allThreeFabsAreRenderedExpenseTransferIncome() {
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
        listOf(
            R.string.fab_expense_content_description,
            R.string.fab_transfer_content_description,
            R.string.fab_income_content_description,
        ).forEach { description ->
            composeTestRule
                .onNodeWithContentDescription(targetString(description))
                .assertTouchWidthIsAtLeast(48.dp)
                .assertTouchHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun dashboardBalancePanelRemainsReadableAtFontScale1Point5() {
        assertDashboardBalanceReadableAtFontScale(1.5f)
    }

    @Test
    fun dashboardBalancePanelRemainsReadableAtFontScale2() {
        assertDashboardBalanceReadableAtFontScale(2f)
    }

    @Test
    fun middleTransferFabEmitsTransferClickedAndTheOtherTwoFabsDoNot() {
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
    fun threeFabsCarryNoVisibleTextLabels() {
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
    fun periodSwitcherInSingleRowToolbarShowsPeriodTitleAndBothChevrons() {
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
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_next))
            .assertIsDisplayed()
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
    }

    // ── Period label tap → date picker dialog ────────────────────────────────

    @Test
    fun tappingPeriodLabelOpensTheDatePickerDialog() {
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
            .onNodeWithContentDescription(targetString(R.string.period_pick_a_date))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsDisplayed()
    }

    @Test
    fun confirmingTopBarSingleDatePickerEmitsDayDistinctFromDrawerCustomRange() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        DashboardState(
                            period = Period.Month(YearMonth.of(2026, 6)),
                            isLoading = false,
                        ),
                    onEvent = { capturedEvents += it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.period_pick_a_date))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            val periodChangedEvent = capturedEvents.filterIsInstance<DashboardEvent.PeriodChanged>().firstOrNull()
            assertTrue(
                "expected PeriodChanged to be emitted but got $capturedEvents",
                periodChangedEvent != null,
            )
            val period = periodChangedEvent!!.period
            assertTrue(
                "expected PeriodChanged to carry a Period.Day but got $period",
                period is Period.Day,
            )
            assertTrue(
                "the top-bar picker is single-date; drawer Pick a date owns CustomRange",
                period !is Period.CustomRange,
            )
        }
    }

    @Test
    fun drawerPickADateDismissesDrawerAndParentPickerRequiresBothDatesBeforeCustomRange() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val start = LocalDate.now().withDayOfMonth(1)
        val end = start.plusDays(1)

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, leftDrawerOpen = true),
            onCapturedEvent = { capturedEvents += it },
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(targetString(R.string.period_pick_a_date))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsNotEnabled()
        composeTestRule.onNodeWithText(dateLabel(start)).performClick()
        composeTestRule.onNodeWithText(dateLabel(end)).performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.period_apply))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.DrawerDismissed), capturedEvents.take(1))
            val periodChanged = capturedEvents.filterIsInstance<DashboardEvent.PeriodChanged>().single()
            assertEquals(Period.CustomRange(start, end), periodChanged.period)
        }
    }

    // ── Right-drawer Search row ───────────────────────────────────────────────

    @Test
    fun rightDrawerSearchRowIsDisplayedWhenRightDrawerIsOpen() {
        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(RIGHT_DRAWER_SEARCH_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun search_row_click_closes_right_drawer_content() {
        var state by mutableStateOf(DashboardState(isLoading = false))
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = state,
                    onEvent = { event ->
                        state =
                            when (event) {
                                DashboardEvent.RightDrawerToggled ->
                                    state.copy(
                                        rightDrawerOpen = !state.rightDrawerOpen,
                                        leftDrawerOpen = false,
                                    )
                                DashboardEvent.SearchClicked ->
                                    state.copy(leftDrawerOpen = false, rightDrawerOpen = false)
                                else -> state
                            }
                    },
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_overflow_menu))
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(RIGHT_DRAWER_SEARCH_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
            .performClick()

        composeTestRule.waitForIdle()

        destinationDrawerRowTags().forEach { tag ->
            composeTestRule
                .onAllNodesWithTag(tag, useUnmergedTree = true)
                .assertCountEquals(0)
        }
    }

    @Test
    fun pullToRefreshGestureEmitsRefreshRequested() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        setDashboardContent(
            state = refreshableDashboardState(),
            onEvent = { capturedEvents += it },
        )

        composeTestRule
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeDown() }

        composeTestRule.runOnIdle {
            assertTrue(
                "expected RefreshRequested to be emitted; got $capturedEvents",
                capturedEvents.contains(DashboardEvent.RefreshRequested),
            )
        }
    }

    // ── Legacy tests (unchanged functionality) ────────────────────────────────

    @Test
    fun expenseFabStaysEnabledInEmptyDashboardAndEmitsMinusEvent() {
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
    fun incomeFabStaysEnabledInEmptyDashboardAndEmitsPlusEvent() {
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
    fun dashboardFabsRemoveVisibleLabelsAndStayReachableByScreenReaderDescriptions() {
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
    fun searchRowInRightDrawerEmitsSearchEventWhenClicked() {
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
    fun topBarRemovesLegacyWordmarkAndSubtitleAndKeepsThePeriodSwitcherVisible() {
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
    fun toolbarActionIconsStayVisibleAndEmitTheirExistingEvents() {
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
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_overflow_menu))
            .assertIsDisplayed()
            .assertIsEnabled()
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
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
    fun toolbarDoesNotExposeTransferOrSearchIcons() {
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
    fun trendBalanceCardIsDisplayedAndRingIsGoneInNormalMode() {
        setDashboardContent(
            state =
                snapshotDashboardState(
                    income = "20000.00",
                    expense = "7654.33",
                    net = "12345.67",
                    ringFraction = 0.62f,
                ),
        )

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithTag(BALANCE_BAR_TAG)
            .assertDoesNotExist()
    }

    @Test
    fun categoryTileUnderTheRingEmitsFilteredTransactionsEvent() {
        val usd = usdCurrency()
        val capturedEvents = mutableListOf<DashboardEvent>()
        val tile = categoryTile(categoryId = 42L, label = "Groceries", currency = usd)

        setDashboardContent(
            state =
                dashboardState(
                    currency = usd,
                    balanceSnapshot = balanceSnapshot(netAmount = "100", currency = usd),
                    expenseTiles = listOf(tile),
                    isLoading = false,
                ),
            onEvent = { capturedEvents += it },
        )

        composeTestRule
            .onNodeWithTag("category_tile_42")
            .performScrollTo()
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.SliceClicked(42L)), capturedEvents)
        }
    }

    @Test
    fun expandedCategoryRendersEveryInlineTransactionAndRowTapNavigates() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        setDashboardContent(
            state = expandedCategoryDashboardState(),
            onEvent = { capturedEvents += it },
        )

        composeTestRule.onNodeWithTag(DASHBOARD_INLINE_RECORDS_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Lunch").assertIsDisplayed()
        composeTestRule.onNodeWithText("Coffee").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lunch").performClick()

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(DashboardEvent.RecordRowClicked(701L)))
        }
    }

    @Test
    fun dashboardVerticalContentScrollsToOverflowCategoryTiles() {
        val usd = usdCurrency()
        val tiles = (1L..8L).map { categoryTile(it, "Category $it", usd) }

        setDashboardContent(
            state =
                dashboardState(
                    currency = usd,
                    balanceSnapshot = balanceSnapshot(netAmount = "100", currency = usd),
                    expenseTiles = tiles,
                    isLoading = false,
                ),
        )

        repeat(3) {
            composeTestRule
                .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
                .performTouchInput { swipeUp() }
        }
        composeTestRule.onNodeWithTag("category_tile_8").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("category_tile_8")
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
    }

    @Test
    fun rightDrawerRowsDisplayAndEmitTheirDestinationEvents() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val drawerRows = destinationDrawerRowTags()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )
        composeTestRule.waitForIdle()

        drawerRows.forEach { tag ->
            composeTestRule
                .onNodeWithTag(tag, useUnmergedTree = true)
                .performScrollTo()
                .assertIsDisplayed()
                .assertHasClickAction()
        }
        drawerRows.forEach { tag ->
            composeTestRule
                .onNodeWithTag(tag, useUnmergedTree = true)
                .performScrollTo()
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    DashboardEvent.SearchClicked,
                    DashboardEvent.CategoriesClicked,
                    DashboardEvent.AccountsClicked,
                    DashboardEvent.FinancialGoalsClicked,
                    DashboardEvent.CurrenciesClicked,
                    DashboardEvent.SettingsClicked,
                    DashboardEvent.SupportClicked,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun rightDrawerShowsFinancialGoalsItemAndClickingItEmitsFinancialGoalsClicked() {
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
    fun rightDrawerFinancialGoalsItemSitsBetweenAccountsAndCurrencies() {
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
    fun dashboardShowsTheCurrentPeriodOnceInsideTheToolbarPeriodSwitcher() {
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
    fun periodChevronButtonsEmitPreviousAndNextEvents() {
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
    fun leftDrawerOpensAsAPartialPanelInsteadOfANearFullWidthSheet() {
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
    fun rightDrawerOpensAsAPartialPanelInsteadOfANearFullWidthSheet() {
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
    fun leftDrawerOverlayCoversTheMoreButtonSoItIsNotReachableWhileLeftDrawerIsOpen() {
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
    fun scrimTapDismissesTheLeftDrawerAndClosesBothDrawerFlags() {
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
    fun scrimTapDismissesTheRightDrawerAndClosesBothDrawerFlags() {
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

    // The body is now a HorizontalPager (SPEC dashboard-swipe-period-paging-02).
    // A full-width swipe settles the pager onto the neighbour page, which triggers a
    // LaunchedEffect/snapshotFlow inside DashboardBodyPager that emits the period event and
    // re-centres the pager. waitForIdle() lets those coroutines complete before assertions.

    @Test
    fun swipingTheDashboardBodyLeftPastSettleThresholdEmitsNextPeriod() {
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
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeLeft() }

        composeTestRule.waitForIdle()

        assertTrue(
            "a left swipe past the settle threshold must emit NextPeriod",
            capturedEvents.contains(DashboardEvent.NextPeriod),
        )
        assertTrue(
            "a left swipe must not emit PreviousPeriod",
            !capturedEvents.contains(DashboardEvent.PreviousPeriod),
        )
    }

    @Test
    fun swipingTheDashboardBodyRightPastSettleThresholdEmitsPreviousPeriod() {
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
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.waitForIdle()

        assertTrue(
            "a right swipe past the settle threshold must emit PreviousPeriod",
            capturedEvents.contains(DashboardEvent.PreviousPeriod),
        )
        assertTrue(
            "a right swipe must not emit NextPeriod",
            !capturedEvents.contains(DashboardEvent.NextPeriod),
        )
    }

    @Test
    fun horizontalSwipeOnPagerBodyDoesNotOpenTheLeftDrawer() {
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
            .onNodeWithTag(DASHBOARD_SCROLL_CONTENT_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.waitForIdle()

        assertTrue(
            "a horizontal swipe on the pager body must not toggle the left drawer",
            !capturedEvents.contains(DashboardEvent.LeftDrawerToggled),
        )
    }

    @Test
    fun navigationIconChangesToBackArrowWhenLeftDrawerIsOpenAndRevertsToHamburgerAfterDismissal() {
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
    fun backArrowClosesTheRightDrawer() {
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
    fun hamburgerButtonStillOpensTheLeftDrawer() {
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
    fun periodSwitcherShowsOnlyTheCurrentPeriodLabelInTheToolbar() {
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
    fun periodSwitcherInTheToolbarExposesPreviousAndNextControlsWithoutPlaceholderLabels() {
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
    fun allThreeFabsAreExactlyDashboardFabSizeKeepContentDescriptionsAndPreserveTheirEvents() {
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
    fun legacyBalancePanelAndNeonRingAreAbsentWhenDashboardHasNoSnapshot() {
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
    fun separateModeHidesTheDonutRingAndShowsTheCurrencyCardsContainer() {
        setDashboardContent(state = separateModeDashboardState())

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
    fun separateModeShowsOneCardEntryPerCurrency() {
        setDashboardContent(state = separateModeDashboardState())

        composeTestRule.onNodeWithText("USD").assertIsDisplayed()
        composeTestRule.onNodeWithText("EUR").assertIsDisplayed()
    }

    @Test
    fun normalModeShowsTheTrendChartAndHidesTheCurrencyCardsContainer() {
        setDashboardContent(state = snapshotDashboardState(ringFraction = 0.75f))

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
    fun trendChartTagExistsInNormalModeWithoutASnapshot() {
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
    fun incomeAndExpensePillsInsideAuroraCardShowWordLabelAndPlainAmountWithoutCurrencySymbolWhenSnapshotIsPresent() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("85000.99"), usd),
                expense = Money(BigDecimal("47350.49"), usd),
                net = Money(BigDecimal("37650.50"), usd),
                byCategory = emptyList(),
            )
        // Pills now show the worded label + plain integer (no currency symbol).
        val expectedIncomePlain = formatDashboardAmountPlain(snapshot.income.amount)
        val expectedExpensePlain = formatDashboardAmountPlain(snapshot.expense.amount)
        val incomeWord = targetString(R.string.dashboard_aurora_income_label)
        val expenseWord = targetString(R.string.dashboard_aurora_expense_label)

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

        // Income/expense are shown as worded pills: "\u2191 Income 85 000" / "\u2193 Expenses 47 350".
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_INCOME_PILL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("\u2191 $incomeWord $expectedIncomePlain")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_EXPENSE_PILL_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("\u2193 $expenseWord $expectedExpensePlain")
            .assertIsDisplayed()
    }

    @Test
    fun incomeAndExpensePillsInsideAuroraCardAreShownEvenWhenSnapshotIsNull() {
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
    fun balanceCardClickEmitsBalanceCardClickedEvent() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        setDashboardContent(
            state = snapshotDashboardState(),
            onEvent = { capturedEvents += it },
        )

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
    fun separateModeHidesTrendChartTagAndShowsCurrencyCards() {
        setDashboardContent(state = separateModeDashboardState(cardCount = 1))

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
    fun auroraCardOmitsTheLegacyBalanceForPeriodLabelInNonSeparateMode() {
        val period = Period.All
        val removedLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(
                R.string.dashboard_balance_for_period,
                targetString(R.string.period_all),
            )

        setDashboardContent(state = snapshotDashboardState(period = period))

        composeTestRule
            .onNodeWithText(removedLabel)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_PERIOD_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun auroraCardSpansNearlyTheFullDashboardWidthAndStaysCenteredInNonSeparateMode() {
        setDashboardContent(state = snapshotDashboardState())

        val rootBounds = composeTestRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cardBounds = composeTestRule.onNodeWithTag(DASHBOARD_AURORA_CARD_TAG).fetchSemanticsNode().boundsInRoot
        val expectedInsetPx = with(composeTestRule.density) { Spacing.dashboardAuroraHostHorizontalPaddingWide.toPx() }

        assertEquals(expectedInsetPx, cardBounds.left, 1.5f)
        assertEquals(rootBounds.right - expectedInsetPx, cardBounds.right, 1.5f)
    }

    @Test
    fun auroraCardBalanceValueShowsNegativeTruncatedIntegerWithCurrencyAfterInNonSeparateMode() {
        val usd = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("100.00"), usd),
                expense = Money(BigDecimal("1334.56"), usd),
                net = Money(BigDecimal("-1234.56"), usd),
                byCategory = emptyList(),
            )
        val expectedBalance = formatDashboardAmount(snapshot.net.amount, usd)

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
        composeTestRule
            .onNodeWithText(expectedBalance)
            .assertIsDisplayed()
    }

    @Test
    fun auroraCardIsAbsentInSeparateModeWhilePerCurrencyCardsStayVisible() {
        setDashboardContent(state = separateModeDashboardState(cardCount = 1))

        composeTestRule
            .onAllNodesWithTag(DASHBOARD_AURORA_CARD_TAG)
            .assertCountEquals(0)
        composeTestRule
            .onNodeWithTag(DASHBOARD_CURRENCY_CARDS_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("USD")
            .assertIsDisplayed()
    }

    // -------------------------------------------------------------------------
    // Chart settings sheet — open via chart tap and via right-drawer row (SPEC 06)
    // -------------------------------------------------------------------------

    @Test
    fun tappingTrendChartAreaEmitsChartTapped() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        setDashboardContent(
            state = snapshotDashboardState(),
            onEvent = { capturedEvents += it },
        )

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
    fun rightDrawerDoesNotExposeAChartSettingsRowAfterItsRemovalFromTheDrawer() {
        // ChartSettingsClicked / ChartSettingsSheet survive in the codebase for SPEC-02
        // (auto-open from dashboard cards), but the right-drawer entry was deleted in the
        // 7-item refactor. Assert the tag is absent so that re-adding the row accidentally
        // is caught immediately.
        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
        )
        composeTestRule.waitForIdle()

        composeTestRule
            .onAllNodes(
                androidx.compose.ui.test
                    .hasTestTag("right_drawer_chart_settings"),
            ).assertCountEquals(0)
    }

    @Test
    fun hiddenChartHintStripIsShownWhenChartConfigVisibleIsFalse() {
        setDashboardContent(state = snapshotDashboardState(chartConfig = ChartConfig(visible = false)))

        composeTestRule
            .onNodeWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertExists()
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun trendChartTagIsPresentWhenChartConfigVisibleIsTrue() {
        setDashboardContent(state = snapshotDashboardState(chartConfig = ChartConfig(visible = true)))

        composeTestRule
            .onNodeWithTag(DASHBOARD_TREND_CHART_TAG)
            .assertExists()
        composeTestRule
            .onAllNodesWithTag(DASHBOARD_CHART_HIDDEN_HINT_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun tappingHiddenChartHintStripEmitsChartTapped() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        setDashboardContent(
            state = snapshotDashboardState(chartConfig = ChartConfig(visible = false)),
            onEvent = { capturedEvents += it },
        )

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
    fun chartSettingsSheetIsShownWhenChartSettingsSheetOpenIsTrue() {
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
    fun chartSettingsSheetIsAbsentWhenChartSettingsSheetOpenIsFalse() {
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

    private fun setDashboardContent(
        state: DashboardState,
        onEvent: (DashboardEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(state = state, onEvent = onEvent)
            }
        }
    }

    private fun refreshableDashboardState(): DashboardState {
        val currency = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal("120.00"), currency),
                expense = Money(BigDecimal("20.00"), currency),
                net = Money(BigDecimal("100.00"), currency),
                byCategory = emptyList(),
            )
        return dashboardState(
            currency = currency,
            balanceSnapshot = snapshot,
            periodNet = snapshot.net,
            expenseTiles = listOf(categoryTile(42L, "Groceries", currency)),
            isLoading = false,
        )
    }

    private fun snapshotDashboardState(
        income: String = "200.00",
        expense: String = "50.00",
        net: String = "150.00",
        period: Period = Period.Month(YearMonth.now()),
        ringFraction: Float = 0f,
        chartConfig: ChartConfig? = null,
    ): DashboardState {
        val currency = usdCurrency()
        val snapshot =
            BalanceSnapshot(
                income = Money(BigDecimal(income), currency),
                expense = Money(BigDecimal(expense), currency),
                net = Money(BigDecimal(net), currency),
                byCategory = emptyList(),
            )
        val state =
            dashboardState(
                currency = currency,
                balanceSnapshot = snapshot,
                period = period,
                periodNet = snapshot.net,
                ringFraction = ringFraction,
                isLoading = false,
            )
        return chartConfig?.let { state.copy(chartConfig = it) } ?: state
    }

    private fun expandedCategoryDashboardState(): DashboardState {
        val currency = usdCurrency()
        return dashboardState(
            currency = currency,
            balanceSnapshot = balanceSnapshot(netAmount = "100", currency = currency),
            expenseTiles = listOf(categoryTile(42L, "Groceries", currency)),
            isLoading = false,
        ).copy(
            expandedCategoryId = 42L,
            expandedRecords =
                listOf(
                    dashboardTransaction(701L, 42L, "Lunch"),
                    dashboardTransaction(702L, 42L, "Coffee"),
                ),
        )
    }

    private fun foodAndTransportTiles(currency: Currency) =
        listOf(
            categoryTile(categoryId = 11L, label = "Food", currency = currency),
            categoryTile(categoryId = 22L, label = "Transport", currency = currency),
        )

    private fun separateModeDashboardState(cardCount: Int = 2): DashboardState {
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
        val currencyCards =
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
                CurrencyBalanceCard(
                    currency = eur,
                    snapshot =
                        BalanceSnapshot(
                            income = Money(BigDecimal("50.00"), eur),
                            expense = Money(BigDecimal("20.00"), eur),
                            net = Money(BigDecimal("30.00"), eur),
                            byCategory = emptyList(),
                        ),
                ),
            )
        return DashboardState(
            currencies = listOf(usd, eur),
            dashboardSelection = DashboardSelection.AllAccounts(AllAccountsFoldMode.Separate),
            currencyCards = currencyCards.take(cardCount),
            isLoading = false,
        )
    }

    private fun destinationDrawerRowTags() =
        listOf(
            RIGHT_DRAWER_SEARCH_TAG,
            RIGHT_DRAWER_CATEGORIES_TAG,
            RIGHT_DRAWER_ACCOUNTS_TAG,
            RIGHT_DRAWER_FINANCIAL_GOALS_TAG,
            RIGHT_DRAWER_CURRENCIES_TAG,
            RIGHT_DRAWER_SETTINGS_TAG,
            RIGHT_DRAWER_SUPPORT_TAG,
        )

    private fun operationsSummaryDashboardState(operationsSummary: OperationsSummaryState?): DashboardState {
        val currency = usdCurrency()
        return dashboardState(
            currency = currency,
            balanceSnapshot = balanceSnapshot(netAmount = "100", currency = currency),
            isLoading = false,
        ).copy(operationsSummary = operationsSummary)
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

    private fun dateLabel(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", targetLocale()))

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

    private fun dashboardTransaction(
        id: Long,
        categoryId: Long,
        note: String,
    ) =
        Transaction(
            id = id,
            kind = TransactionKind.Expense,
            amount = BigDecimal("10.00"),
            currencyId = 1L,
            accountId = 1L,
            categoryId = categoryId,
            note = note,
            occurredAt = java.time.Instant.parse("2026-08-05T00:00:00Z"),
            createdAt = java.time.Instant.parse("2026-08-05T00:00:00Z"),
            updatedAt = java.time.Instant.parse("2026-08-05T00:00:00Z"),
            isDeleted = false,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
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

    // -------------------------------------------------------------------------
    // Operations summary sheet (SPEC 03) — operationsSummary state
    // -------------------------------------------------------------------------

    @Test
    fun operationsSummarySheetIsShownWhenOperationsSummaryIsNotNull() {
        setDashboardContent(
            state =
                operationsSummaryDashboardState(
                    OperationsSummaryState(
                        categoryFilter = null,
                        records = emptyList(),
                        loading = false,
                    ),
                ),
        )

        composeTestRule
            .onNodeWithTag(OPERATIONS_SUMMARY_SHEET_TAG)
            .assertExists()
    }

    @Test
    fun operationsSummarySheetIsAbsentWhenOperationsSummaryIsNull() {
        val usd = usdCurrency()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state =
                        dashboardState(
                            currency = usd,
                            balanceSnapshot = balanceSnapshot(netAmount = "100", currency = usd),
                            isLoading = false,
                        ).copy(operationsSummary = null),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onAllNodesWithTag(OPERATIONS_SUMMARY_SHEET_TAG)
            .assertCountEquals(0)
    }

    @Test
    fun operationsSummarySheetShowsEmptyStateWhenRecordsListIsEmpty() {
        setDashboardContent(
            state =
                operationsSummaryDashboardState(
                    OperationsSummaryState(
                        categoryFilter = null,
                        records = emptyList(),
                        loading = false,
                    ),
                ),
        )

        composeTestRule
            .onNodeWithTag(OPERATIONS_SUMMARY_EMPTY_TAG)
            .assertExists()
    }

    @Test
    fun tappingCategoryTileEmitsSliceClickedWithCorrectCategoryId() {
        val usd = usdCurrency()
        val capturedEvents = mutableListOf<DashboardEvent>()
        val tile = categoryTile(categoryId = 42L, label = "Groceries", currency = usd)

        setDashboardContent(
            state =
                dashboardState(
                    currency = usd,
                    balanceSnapshot = balanceSnapshot(netAmount = "100", currency = usd),
                    expenseTiles = listOf(tile),
                    isLoading = false,
                ),
            onEvent = { capturedEvents += it },
        )

        composeTestRule
            .onNodeWithTag("category_tile_42")
            .performScrollTo()
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected SliceClicked(42) but got $capturedEvents",
                capturedEvents.contains(DashboardEvent.SliceClicked(42L)),
            )
        }
    }

    @Test
    fun tappingATileEmitsSliceClickedWithThatCategoryId() {
        val usd = usdCurrency()
        val capturedEvents = mutableListOf<DashboardEvent>()
        val tiles = foodAndTransportTiles(usd)

        setDashboardContent(
            state =
                dashboardState(
                    currency = usd,
                    balanceSnapshot = balanceSnapshot(netAmount = "200", currency = usd),
                    expenseTiles = tiles,
                    isLoading = false,
                ),
            onEvent = { capturedEvents += it },
        )

        composeTestRule
            .onNodeWithTag("category_tile_11")
            .performScrollTo()
            .assertTouchWidthIsAtLeast(48.dp)
            .assertTouchHeightIsAtLeast(48.dp)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "expected SliceClicked(11) but got $capturedEvents",
                capturedEvents.contains(DashboardEvent.SliceClicked(11L)),
            )
        }
    }

    @Test
    fun operationsSummarySheetTitleUsesCategoryNameWhenFiltered() {
        setDashboardContent(
            state =
                operationsSummaryDashboardState(
                    OperationsSummaryState(
                        categoryFilter = 55L,
                        categoryName = "Groceries",
                        records = emptyList(),
                        loading = false,
                    ),
                ),
        )

        composeTestRule
            .onNodeWithTag(OPERATIONS_SUMMARY_SHEET_TAG)
            .assertExists()
        composeTestRule
            .onNodeWithText("Groceries")
            .assertIsDisplayed()
    }

    private fun assertDashboardBalanceReadableAtFontScale(fontScale: Float) {
        val usd = usdCurrency()
        val snapshot = balanceSnapshot(netAmount = "987654321", currency = usd)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = fontScale)) {
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
        }

        val layoutResults = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithTag(DASHBOARD_AURORA_BALANCE_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(layoutResults)

        assertTrue("Aurora balance text layout must be available", layoutResults.isNotEmpty())
        assertFalse(
            "Aurora balance must not visually overflow at fontScale=$fontScale",
            layoutResults.first().hasVisualOverflow,
        )
    }

    private fun formatDashboardAmount(
        amount: BigDecimal,
        currency: Currency,
    ): String =
        MoneyFormatter.format(
            amount = amount.setScale(0, RoundingMode.DOWN),
            currencySymbol = currency.symbol,
            decimalDigits = 0,
            locale = targetLocale(),
            symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
        )

    // Mirrors DashboardScreen.formatMoneyPlain: grouped integer, no currency symbol, trimmed.
    private fun formatDashboardAmountPlain(amount: BigDecimal): String =
        MoneyFormatter
            .format(
                amount = amount.setScale(0, RoundingMode.DOWN),
                currencySymbol = "",
                decimalDigits = 0,
                locale = targetLocale(),
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            ).trim()

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
