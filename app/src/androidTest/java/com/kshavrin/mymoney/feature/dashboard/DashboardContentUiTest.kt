package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.BalanceSnapshot
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Money
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class DashboardContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `expense fab stays enabled in empty dashboard and emits minus event`() {
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
            .onNodeWithContentDescription(targetString(R.string.fab_expense))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.MinusFabClicked), capturedEvents)
        }
    }

    @Test
    fun `income fab stays enabled in empty dashboard and emits plus event`() {
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
            .onNodeWithContentDescription(targetString(R.string.fab_income))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.PlusFabClicked), capturedEvents)
        }
    }

    @Test
    fun `both transfer buttons stay enabled in empty dashboard and emit transfer events`() {
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

        transferButtons.assertCountEquals(2)
        transferButtons[0].assertIsEnabled().performClick()
        transferButtons[1].assertIsEnabled().performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(DashboardEvent.TransferClicked, DashboardEvent.TransferClicked),
                capturedEvents,
            )
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
    fun `balance pill in populated dashboard emits balance card event`() {
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
                    state = DashboardState(
                        currentCurrency = usd,
                        balanceSnapshot = BalanceSnapshot(
                            income = Money(BigDecimal("125.00"), usd),
                            expense = Money(BigDecimal("75.00"), usd),
                            net = Money(BigDecimal("50.00"), usd),
                            byCategory = emptyList(),
                        ),
                        isLoading = false,
                    ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNode(hasText("\$50.00") and hasClickAction())
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.BalanceCardClicked), capturedEvents)
        }
    }

    @Test
    fun `right drawer rows display and emit their destination events`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val drawerRows = listOf(
            R.string.right_drawer_settings,
            R.string.right_drawer_categories,
            R.string.right_drawer_accounts,
            R.string.right_drawer_currencies,
            R.string.right_drawer_about,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                DashboardContent(
                    state = DashboardState(isLoading = false),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dashboard_settings_menu))
            .performClick()

        drawerRows.forEach { resourceId ->
            composeTestRule
                .onNode(hasText(targetString(resourceId)) and hasClickAction())
                .assertIsDisplayed()
        }
        drawerRows.forEach { resourceId ->
            composeTestRule
                .onNode(hasText(targetString(resourceId)) and hasClickAction())
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
    fun `left drawer manage accounts row stays enabled in empty dashboard and emits accounts event`() {
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
            .performClick()

        composeTestRule
            .onNode(hasText(targetString(R.string.left_drawer_manage_accounts)) and hasClickAction())
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.AccountsClicked), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
