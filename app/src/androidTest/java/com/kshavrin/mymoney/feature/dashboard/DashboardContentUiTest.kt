package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.designsystem.donut.CategorySlice
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_ABOUT_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_ACCOUNTS_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CATEGORIES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_CURRENCIES_TAG
import com.kshavrin.mymoney.feature.dashboard.components.RIGHT_DRAWER_SETTINGS_TAG
import org.junit.Assert.assertEquals
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

    @Test
    fun `expense fab stays enabled in empty dashboard and emits minus event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false),
            onCapturedEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense))
            .assertIsEnabled()
            .assertWidthIsAtLeast(96.dp)
            .assertHeightIsAtLeast(96.dp)
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
            .onNodeWithContentDescription(targetString(R.string.fab_income))
            .assertIsEnabled()
            .assertWidthIsAtLeast(96.dp)
            .assertHeightIsAtLeast(96.dp)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.PlusFabClicked), capturedEvents)
        }
    }

    @Test
    fun `top bar transfer button stays enabled in empty dashboard and emits transfer event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        val transferButtons = composeTestRule
            .onAllNodesWithContentDescription(targetString(R.string.dashboard_transfer))

        transferButtons.assertCountEquals(1)
        transferButtons[0].assertIsEnabled().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.TransferClicked), capturedEvents)
        }
    }

    @Test
    fun `search button stays enabled in empty dashboard and emits search event`() {
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
            .onNodeWithContentDescription(targetString(R.string.dashboard_search))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.SearchClicked), capturedEvents)
        }
    }

    @Test
    fun `top bar renders wordmark title and currency subtitle`() {
        val usd = Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = dashboardState(currency = usd, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_TITLE_TAG)
            .assertIsDisplayed()
            .assertTextEquals(targetString(R.string.dashboard_title))
        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_SUBTITLE_TAG)
            .assertIsDisplayed()
            .assertTextEquals(usd.name)
    }

    @Test
    fun `balance bar formats grouped net balance with label and emits balance card event`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val usd = Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = dashboardState(
                        currency = usd,
                        balanceSnapshot = BalanceSnapshot(
                            income = Money(BigDecimal("20000.00"), usd),
                            expense = Money(BigDecimal("7654.33"), usd),
                            net = Money(BigDecimal("12345.67"), usd),
                            byCategory = emptyList(),
                        ),
                        isLoading = false,
                    ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        val expectedBalanceText = "${targetString(DesignSystemR.string.balance_bar_label)} ${
            MoneyFormatter.format(
                amount = BigDecimal("12345.67"),
                currencySymbol = usd.symbol,
                decimalDigits = usd.decimalDigits,
                locale = targetLocale(),
                symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
            )
        }"

        composeTestRule
            .onNodeWithTag(BALANCE_BAR_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule
            .onNode(hasText(expectedBalanceText))
            .assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(
                "${targetString(DesignSystemR.string.balance_bar_label)} 12345.67",
                substring = true,
            )
            .assertCountEquals(0)

        composeTestRule.onNodeWithTag(BALANCE_BAR_TAG).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.BalanceCardClicked), capturedEvents)
        }
    }

    @Test
    fun `balance bar sits between the donut and the expense fab`() {
        val usd = Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = dashboardState(
                        currency = usd,
                        balanceSnapshot = BalanceSnapshot(
                            income = Money(BigDecimal("20000.00"), usd),
                            expense = Money(BigDecimal("7654.33"), usd),
                            net = Money(BigDecimal("12345.67"), usd),
                            byCategory = emptyList(),
                        ),
                        isLoading = false,
                    ),
                    onEvent = {},
                )
            }
        }

        val barTop = composeTestRule.onNodeWithTag(BALANCE_BAR_TAG)
            .fetchSemanticsNode().boundsInRoot.top
        // The donut announces its income/expense totals; match on the localized prefix
        // ahead of the format placeholders so the chart node is uniquely located.
        val donutCdPrefix = targetString(DesignSystemR.string.donut_chart_cd).substringBefore('%').trim()
        val donutTop = composeTestRule
            .onNode(hasContentDescription(donutCdPrefix, substring = true))
            .fetchSemanticsNode().boundsInRoot.top
        val expenseFabTop = composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense))
            .fetchSemanticsNode().boundsInRoot.top

        assertTrue("balance bar must sit below the donut", barTop > donutTop)
        assertTrue("balance bar must sit above the expense fab", barTop < expenseFabTop)
    }

    @Test
    fun `year dashboard gives the donut stage and fabs reference scale`() {
        val usd = Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = dashboardState(
                        currency = usd,
                        balanceSnapshot = BalanceSnapshot(
                            income = Money(BigDecimal("2442740.80"), usd),
                            expense = Money(BigDecimal("1699483.00"), usd),
                            net = Money(BigDecimal("743257.80"), usd),
                            byCategory = emptyList(),
                        ),
                        period = Period.Year(2026),
                        slices = listOf(
                            CategorySlice(1L, Color(0xFFECC400), 0.27f, "Car", "car"),
                            CategorySlice(2L, Color(0xFF77C99B), 0.21f, "Pets", "pets"),
                            CategorySlice(3L, Color(0xFFE879B0), 0.17f, "Groceries", "groceries"),
                            CategorySlice(4L, Color(0xFF9CC7DB), 0.05f, "Home", "home"),
                        ),
                        isLoading = false,
                    ),
                    onEvent = {},
                )
            }
        }

        val donutBounds = composeTestRule
            .onNodeWithTag(DASHBOARD_DONUT_TAG)
            .assertIsDisplayed()
            .assertWidthIsAtLeast(360.dp)
            .assertHeightIsAtLeast(360.dp)
            .fetchSemanticsNode()
            .boundsInRoot
        val balanceBounds = composeTestRule
            .onNodeWithTag(BALANCE_BAR_TAG)
            .assertIsDisplayed()
            .fetchSemanticsNode()
            .boundsInRoot
        val expenseFab = composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_expense))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(96.dp)
            .assertHeightIsAtLeast(96.dp)
            .fetchSemanticsNode()
            .boundsInRoot
        val incomeFab = composeTestRule
            .onNodeWithContentDescription(targetString(R.string.fab_income))
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(96.dp)
            .assertHeightIsAtLeast(96.dp)
            .fetchSemanticsNode()
            .boundsInRoot
        val rootWidth = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.widthPixels.toFloat()
        val donutWidthRatio = donutBounds.width / rootWidth

        assertTrue(
            "donut stage width ratio $donutWidthRatio must stay near full width",
            donutWidthRatio >= 0.92f,
        )
        assertTrue("balance bar must sit below the expanded donut stage", balanceBounds.top > donutBounds.bottom)
        assertTrue("expense FAB must sit below the balance bar", expenseFab.top > balanceBounds.bottom)
        assertTrue("income FAB must sit below the balance bar", incomeFab.top > balanceBounds.bottom)
    }

    @Test
    fun `right drawer rows display and emit their destination events`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val drawerRows = listOf(
            RIGHT_DRAWER_SETTINGS_TAG,
            RIGHT_DRAWER_CATEGORIES_TAG,
            RIGHT_DRAWER_ACCOUNTS_TAG,
            RIGHT_DRAWER_CURRENCIES_TAG,
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
                    DashboardEvent.SettingsClicked,
                    DashboardEvent.CategoriesClicked,
                    DashboardEvent.AccountsClicked,
                    DashboardEvent.CurrenciesClicked,
                    DashboardEvent.AboutClicked,
                ),
                capturedEvents,
            )
        }
    }

    @Test
    fun `dashboard shows the current period as a static localized label`() {
        val period = Period.Month(YearMonth.of(2026, 4))
        val expectedLabel = YearMonth.of(2026, 4)
            .atDay(1)
            .format(DateTimeFormatter.ofPattern("LLLL yyyy", targetLocale()))

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = period, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText(expectedLabel).assertIsDisplayed()
        composeTestRule
            .onAllNodes(hasText(expectedLabel) and hasClickAction())
            .assertCountEquals(0)
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
    }

    @Test
    fun `left drawer keeps the top bar visible and search clickable`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, leftDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_TITLE_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_search))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.SearchClicked), capturedEvents)
        }
    }

    @Test
    fun `right drawer keeps the top bar visible and transfer clickable`() {
        val capturedEvents = mutableListOf<DashboardEvent>()

        setStatefulDashboardContent(
            initialState = DashboardState(isLoading = false, rightDrawerOpen = true),
            onCapturedEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithTag(DASHBOARD_TOP_BAR_TITLE_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_transfer))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.TransferClicked), capturedEvents)
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
            .onNodeWithTag(BALANCE_BAR_TAG)
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
            .onNodeWithTag(BALANCE_BAR_TAG)
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

        // A rightward swipe from the left edge is the drawer-open gesture in Monefy; with the
        // left drawer gesturesEnabled=false it must navigate the period instead of toggling the drawer.
        composeTestRule
            .onNodeWithTag(BALANCE_BAR_TAG)
            .performTouchInput { swipeRight() }

        composeTestRule.runOnIdle {
            assertTrue(
                "a horizontal swipe must not toggle the left drawer",
                !capturedEvents.contains(DashboardEvent.LeftDrawerToggled),
            )
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
    fun `period label peeks the adjacent periods around the current one`() {
        val current = Period.Month(YearMonth.of(2026, 4))
        val pattern = DateTimeFormatter.ofPattern("LLLL yyyy", targetLocale())
        val currentLabel = YearMonth.of(2026, 4).atDay(1).format(pattern)
        val previousLabel = YearMonth.of(2026, 3).atDay(1).format(pattern)
        val nextLabel = YearMonth.of(2026, 5).atDay(1).format(pattern)

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(period = current, isLoading = false),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText(currentLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(previousLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(nextLabel).assertIsDisplayed()
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun setStatefulDashboardContent(
        initialState: DashboardState,
        onCapturedEvent: (DashboardEvent) -> Unit = {},
    ) {
        composeTestRule.setContent {
            var state by mutableStateOf(initialState)
            MyMoneyTheme {
                DashboardContent(
                    state = state,
                    onEvent = { event ->
                        onCapturedEvent(event)
                        state = when (event) {
                            DashboardEvent.LeftDrawerToggled -> state.copy(
                                leftDrawerOpen = !state.leftDrawerOpen,
                                rightDrawerOpen = false,
                            )
                            DashboardEvent.RightDrawerToggled -> state.copy(
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
    }

    private fun targetLocale() = InstrumentationRegistry.getInstrumentation()
        .targetContext.resources.configuration.locales[0]

    private fun dashboardState(
        currency: Currency,
        balanceSnapshot: BalanceSnapshot? = null,
        period: Period = Period.Month(YearMonth.now()),
        slices: List<CategorySlice> = emptyList(),
        isLoading: Boolean = false,
    ) = DashboardState(
        period = period,
        currencies = listOf(currency),
        dashboardSelection = DashboardSelection.AllAccounts(currency),
        balanceSnapshot = balanceSnapshot,
        slices = slices,
        isLoading = isLoading,
    )

    private fun assertDrawerWidthRatio(
        drawerLabel: String,
        minimum: Float,
        maximum: Float,
    ) {
        val drawerRow = composeTestRule.onNode(hasText(drawerLabel) and hasClickAction())
        drawerRow.assertIsDisplayed()

        val rootWidth = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.widthPixels.toFloat()
        val rowWidth = drawerRow.fetchSemanticsNode().boundsInRoot.width
        val horizontalPadding = with(composeTestRule.density) { (Spacing.l * 2).toPx() }
        val ratio = (rowWidth + horizontalPadding) / rootWidth

        assertTrue(
            "drawer width ratio $ratio must stay within [$minimum, $maximum]",
            ratio in minimum..maximum,
        )
    }

    private companion object {
        const val BALANCE_BAR_TAG = "dashboard_balance_bar"
    }
}
