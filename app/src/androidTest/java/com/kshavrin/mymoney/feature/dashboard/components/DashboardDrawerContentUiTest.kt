package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.DashboardEvent
import com.kshavrin.mymoney.feature.dashboard.DashboardState
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DashboardDrawerContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `right drawer entries remain exposed and clickable after icon over label restyle`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val entries = listOf(
            R.string.right_drawer_categories to DashboardEvent.CategoriesClicked,
            R.string.right_drawer_accounts to DashboardEvent.AccountsClicked,
            R.string.right_drawer_currencies to DashboardEvent.CurrenciesClicked,
            R.string.right_drawer_settings to DashboardEvent.SettingsClicked,
            R.string.right_drawer_about to DashboardEvent.AboutClicked,
        )

        composeTestRule.setContent {
            MyMoneyTheme {
                RightDrawerContent(onEvent = { event -> capturedEvents += event })
            }
        }

        entries.forEach { (labelRes, _) ->
            drawerRow(labelRes)
                .assertIsDisplayed()
                .assertIsEnabled()
        }
        entries.forEach { (labelRes, _) ->
            drawerRow(labelRes).performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(entries.map { it.second }, capturedEvents)
        }
    }

    @Test
    fun `left drawer currency header expands to account selection and keeps all accounts disabled`() {
        val capturedEvents = mutableListOf<DashboardEvent>()
        val currency = currency()
        val cash = account(id = 1L, name = "Cash")
        val card = account(id = 2L, name = "Card")

        composeTestRule.setContent {
            MyMoneyTheme {
                LeftDrawerContent(
                    state = DashboardState(
                        accounts = listOf(cash, card),
                        currencies = listOf(currency),
                        currentAccount = cash,
                        currentCurrency = currency,
                        isLoading = false,
                    ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNode(hasText(currency.name) and hasClickAction()).assertIsDisplayed()
        composeTestRule.onNodeWithText(currency.code).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(cash.name).assertCountEquals(0)

        composeTestRule.onNode(hasText(currency.name) and hasClickAction()).performClick()

        composeTestRule
            .onNodeWithText(targetString(R.string.left_drawer_all_accounts))
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeTestRule
            .onAllNodesWithText(targetString(R.string.left_drawer_manage_accounts))
            .assertCountEquals(0)
        accountRow(cash.name)
            .assertIsDisplayed()
            .assertIsSelected()
        accountRow(card.name)
            .assertIsDisplayed()
            .assertIsNotSelected()
            .performClick()
        composeTestRule
            .onAllNodesWithText(targetString(R.string.left_drawer_all_accounts))
            .assertCountEquals(0)
        composeTestRule.onAllNodesWithText(card.name).assertCountEquals(0)

        composeTestRule.runOnIdle {
            assertEquals(listOf(DashboardEvent.AccountChanged(card.id)), capturedEvents)
        }
    }

    private fun drawerRow(labelRes: Int) =
        composeTestRule.onNode(hasText(targetString(labelRes)) and hasClickAction())

    private fun accountRow(name: String) =
        composeTestRule.onNode(hasText(name) and hasClickAction())

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun currency() = Currency(
        id = 1L,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun account(id: Long, name: String) = Account(
        id = id,
        name = name,
        currencyId = 1L,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#7AC794",
        iconKey = "wallet",
        isDefault = id == 1L,
        sortOrder = id.toInt(),
        createdAt = Instant.parse("2026-05-18T10:00:00Z"),
        updatedAt = Instant.parse("2026-05-18T10:00:00Z"),
        isArchived = false,
    )
}
