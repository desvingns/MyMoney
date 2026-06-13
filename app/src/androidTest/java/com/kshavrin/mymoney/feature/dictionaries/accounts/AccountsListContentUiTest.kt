package com.kshavrin.mymoney.feature.dictionaries.accounts

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AccountsListContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun fabRowTapAndBackEmitEventsWithPopulatedRow() {
        val events = mutableListOf<AccountsListEvent>()
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountsListContent(
                    state =
                        AccountsListState(
                            rows =
                                listOf(
                                    AccountRow(
                                        account = account(1L, "Cash", currencyId = 10L, isDefault = true),
                                        balance = BigDecimal("100.00"),
                                        currency = currency(10L, "USD", "$"),
                                    ),
                                ),
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("Cash").assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_default_badge)).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.dictionaries_balance_label), substring = true)
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dictionaries_add))
            .assertIsEnabled()
            .performClick()
        // Click the name text (unmerged) rather than the merged row node: the row's geometric
        // centre can land on the trailing "Default" AssistChip, whose onClick={} swallows the tap.
        composeTestRule.onNodeWithText("Cash", useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_back)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    AccountsListEvent.AddClicked,
                    AccountsListEvent.ItemClicked(1L),
                    AccountsListEvent.BackClicked,
                ),
                events,
            )
        }
    }

    @Test
    fun emptyAccountsListRendersWithEnabledAddFab() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AccountsListContent(state = AccountsListState(), onEvent = {})
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.dictionaries_accounts_title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(targetString(R.string.dictionaries_add)).assertIsEnabled()
    }

    private fun account(
        id: Long,
        name: String,
        currencyId: Long,
        isDefault: Boolean,
    ): Account =
        Account(
            id = id,
            name = name,
            currencyId = currencyId,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#4A8FCB",
            iconKey = "ic_account_cash",
            isDefault = isDefault,
            sortOrder = 0,
            createdAt = Instant.parse("2026-05-29T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-29T00:00:00Z"),
            isArchived = false,
        )

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
    ): Currency =
        Currency(
            id = id,
            code = code,
            symbol = symbol,
            name = code,
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
