package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class GoalsListContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val now = Instant.parse("2026-06-06T10:00:00Z")

    private fun goal(
        id: Long,
        name: String,
        variant: GoalVariant = GoalVariant.SAVINGS,
        iconKey: String = "ic_goal_home",
    ): Goal =
        Goal(
            id = id,
            name = name,
            iconKey = iconKey,
            colorHex = "#4CAF50",
            accountId = 1L,
            variant = variant,
            targetAmount = BigDecimal("5000.00"),
            startingCapital = BigDecimal.ZERO,
            monthlyContribution = BigDecimal("200.00"),
            annualRatePercent = null,
            downPayment = null,
            termMonths = null,
            createdAt = now,
            updatedAt = now,
            isArchived = false,
        )

    @Test
    fun `empty state shows the empty message and the add FAB`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state = GoalsListState(rows = emptyList()),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.goals_empty))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.goals_add))
            .assertIsEnabled()
    }

    @Test
    fun `rows render goal name and variant chip for savings`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state =
                        GoalsListState(
                            rows = listOf(goal(1L, "Buy a house", GoalVariant.SAVINGS)),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Buy a house").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.goals_variant_savings), useUnmergedTree = true)
            .assertIsDisplayed()
        val amountText =
            MoneyFormatter.format(
                amount = BigDecimal("5000.00"),
                currencySymbol = "",
                decimalDigits = 2,
                locale = Locale.getDefault(),
            )
        composeTestRule
            .onNodeWithContentDescription(
                targetString(
                    R.string.dictionaries_goal_row_cd,
                    "Buy a house",
                    amountText,
                    targetString(R.string.goals_variant_savings),
                ),
            ).assertIsDisplayed()
    }

    @Test
    fun `rows render goal name and variant chip for credit`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state =
                        GoalsListState(
                            rows = listOf(goal(2L, "Mortgage", GoalVariant.CREDIT)),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Mortgage").assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.goals_variant_credit), useUnmergedTree = true)
            .assertIsDisplayed()
        val amountText =
            MoneyFormatter.format(
                amount = BigDecimal("5000.00"),
                currencySymbol = "",
                decimalDigits = 2,
                locale = Locale.getDefault(),
            )
        composeTestRule
            .onNodeWithContentDescription(
                targetString(
                    R.string.dictionaries_goal_row_cd,
                    "Mortgage",
                    amountText,
                    targetString(R.string.goals_variant_credit),
                ),
            ).assertIsDisplayed()
    }

    @Test
    fun `goal icon node is present for each row`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state =
                        GoalsListState(
                            rows =
                                listOf(
                                    goal(1L, "Travel fund", iconKey = "ic_goal_travel"),
                                    goal(2L, "Emergency", iconKey = "ic_goal_emergency"),
                                ),
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Travel fund").assertIsDisplayed()
        composeTestRule.onNodeWithText("Emergency").assertIsDisplayed()
    }

    @Test
    fun `empty state does not show any row items`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state = GoalsListState(rows = emptyList()),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Buy a house").assertDoesNotExist()
    }

    @Test
    fun `FAB click emits AddClicked event`() {
        val events = mutableListOf<GoalsListEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state = GoalsListState(rows = emptyList()),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.goals_add))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(GoalsListEvent.AddClicked), events)
        }
    }

    @Test
    fun `tapping a goal row emits ItemClicked with the correct id`() {
        val events = mutableListOf<GoalsListEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state =
                        GoalsListState(
                            rows = listOf(goal(7L, "Vacation")),
                        ),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule.onNodeWithText("Vacation").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(GoalsListEvent.ItemClicked(7L)), events)
        }
    }

    @Test
    fun `back button click emits BackClicked event`() {
        val events = mutableListOf<GoalsListEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                GoalsListContent(
                    state = GoalsListState(rows = emptyList()),
                    onEvent = { events += it },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.dictionaries_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(GoalsListEvent.BackClicked), events)
        }
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resourceId, *formatArgs)
}
