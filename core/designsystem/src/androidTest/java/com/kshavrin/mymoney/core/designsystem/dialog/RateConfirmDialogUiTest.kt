package com.kshavrin.mymoney.core.designsystem.dialog

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RateConfirmDialogUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // ---- helpers ----

    private fun str(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun str(
        id: Int,
        vararg args: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun singleRow(
        fromCode: String = "USD",
        toCode: String = "KZT",
        lastUpdated: LocalDate? = LocalDate.of(2026, 6, 1),
        displayRate: BigDecimal? = BigDecimal("458.75"),
        stale: Boolean = false,
        missing: Boolean = false,
    ) = RateRow(
        fromCode = fromCode,
        toCode = toCode,
        lastUpdated = lastUpdated,
        displayRate = displayRate,
        stale = stale,
        missing = missing,
    )

    // ---- single mode: basic rendering ----

    @Test
    fun singleModeShowsDialogContainer() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow()),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_DIALOG_TAG)
            .assertIsDisplayed()
    }

    @Test
    fun singleModeShowsLastUpdatedDate() {
        val date = LocalDate.of(2026, 6, 1)

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(lastUpdated = date)),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_DATE_TAG_PREFIX}0")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(str(R.string.rate_last_updated, date.toString()))
            .assertIsDisplayed()
    }

    @Test
    fun singleModeShowsRateLastUpdatedNoneWhenLastUpdatedIsNull() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(lastUpdated = null)),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(str(R.string.rate_last_updated_none))
            .assertIsDisplayed()
    }

    @Test
    fun singleModeShowsCurrentRateWhenDisplayRateIsNonNull() {
        val rate = BigDecimal("458.75")

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(displayRate = rate)),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(str(R.string.rate_value_on_date, rate.toPlainString()))
            .assertIsDisplayed()
    }

    @Test
    fun singleModeShowsRateValueNoneWhenDisplayRateIsNull() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(displayRate = null, missing = true)),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(str(R.string.rate_value_none))
            .assertIsDisplayed()
    }

    @Test
    fun singleModePreFillsInputFieldWithDisplayRate() {
        val rate = BigDecimal("458.75")

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(displayRate = rate)),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // The field is pre-filled with the displayRate scaled to 2 dp
        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("458.75")
            .assertIsDisplayed()
    }

    // ---- single mode: confirm / dismiss ----

    @Test
    fun singleModeConfirmButtonFiresOnConfirmWithPreFilledDisplayRate() {
        val confirmed = mutableMapOf<Int, BigDecimal>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(displayRate = BigDecimal("458.75"))),
                    onConfirm = { confirmed.putAll(it) },
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, confirmed.size)
            assertEquals(BigDecimal("458.75"), confirmed[0])
        }
    }

    @Test
    fun singleModeDismissButtonFiresOnDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow()),
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_DISMISS_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    // ---- single mode: manual edit ----

    @Test
    fun editingFieldFiresOnRateEditedWithParsedBigDecimal() {
        val edits = mutableListOf<Pair<Int, BigDecimal>>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow()),
                    onConfirm = {},
                    onDismiss = {},
                    onRateEdited = { idx, value -> edits += idx to value },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextInput("500.00")

        composeTestRule.runOnIdle {
            assertTrue("onRateEdited must have been called at least once", edits.isNotEmpty())
            assertEquals(0, edits.last().first)
            assertEquals(BigDecimal("500.00"), edits.last().second)
        }
    }

    @Test
    fun editingFieldWithCommaSeparatorIsParsedAsDot() {
        val edits = mutableListOf<Pair<Int, BigDecimal>>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow()),
                    onConfirm = {},
                    onDismiss = {},
                    onRateEdited = { idx, value -> edits += idx to value },
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextInput("1,23")

        composeTestRule.runOnIdle {
            assertTrue("onRateEdited must fire for a valid comma-decimal input", edits.isNotEmpty())
            assertEquals(BigDecimal("1.23"), edits.last().second)
        }
    }

    @Test
    fun confirmFiresWithManuallyEnteredValueOverridingDisplayRate() {
        val confirmed = mutableMapOf<Int, BigDecimal>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(displayRate = BigDecimal("458.75"))),
                    onConfirm = { confirmed.putAll(it) },
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextInput("500.00")
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(BigDecimal("500.00"), confirmed[0])
        }
    }

    @Test
    fun emptyInputFallsBackToDisplayRateOnConfirm() {
        val confirmed = mutableMapOf<Int, BigDecimal>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(displayRate = BigDecimal("458.75"))),
                    onConfirm = { confirmed.putAll(it) },
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(BigDecimal("458.75"), confirmed[0])
        }
    }

    // ---- stale / missing hints ----

    @Test
    fun staleRowShowsStaleHintButConfirmButtonRemainsEnabled() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = listOf(singleRow(stale = true)),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(str(R.string.rate_stale_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsEnabled()
    }

    @Test
    fun missingRowWithoutInputShowsMissingHintAndDisablesConfirm() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(
                                displayRate = null,
                                missing = true,
                            ),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(str(R.string.rate_missing_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsNotEnabled()
    }

    @Test
    fun missingRowWithValidInputShowsMissingHintButEnablesConfirm() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(
                                displayRate = null,
                                missing = true,
                            ),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}0")
            .performTextInput("1.50")

        composeTestRule
            .onNodeWithText(str(R.string.rate_missing_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsEnabled()
    }

    // ---- list mode ----

    @Test
    fun listModeShowsDialogContainerAndListTitle() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT"),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = BigDecimal("530.00")),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_DIALOG_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(str(R.string.rate_confirm_list_title))
            .assertIsDisplayed()
    }

    @Test
    fun listModeRendersDateRowForEachPair() {
        val date0 = LocalDate.of(2026, 6, 1)
        val date1 = LocalDate.of(2026, 5, 15)

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT", lastUpdated = date0),
                            singleRow(fromCode = "EUR", toCode = "KZT", lastUpdated = date1),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${RATE_ROW_DATE_TAG_PREFIX}0")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("${RATE_ROW_DATE_TAG_PREFIX}1")
            .assertIsDisplayed()
    }

    @Test
    fun listModeShowsASingleConfirmButtonCoveringAllRows() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT"),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = BigDecimal("530.00")),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        // Only ONE confirm button exists even for multiple rows
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun listModeConfirmReturnsMapKeyedByRowIndex() {
        val confirmed = mutableMapOf<Int, BigDecimal>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT", displayRate = BigDecimal("458.75")),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = BigDecimal("530.00")),
                        ),
                    onConfirm = { confirmed.putAll(it) },
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(2, confirmed.size)
            assertEquals(BigDecimal("458.75"), confirmed[0])
            assertEquals(BigDecimal("530.00"), confirmed[1])
        }
    }

    @Test
    fun listModeDismissFiresOnDismiss() {
        var dismissed = false

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT"),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = BigDecimal("530.00")),
                        ),
                    onConfirm = {},
                    onDismiss = { dismissed = true },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_DISMISS_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(dismissed)
        }
    }

    @Test
    fun listModeStaleRowShowsStaleHintButConfirmStaysEnabled() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT", stale = true),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = BigDecimal("530.00")),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(str(R.string.rate_stale_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsEnabled()
    }

    @Test
    fun listModeMissingRowDisablesConfirmUntilValidInputProvided() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT", displayRate = BigDecimal("458.75")),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = null, missing = true),
                        ),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsNotEnabled()

        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}1")
            .performTextInput("530.00")

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .assertIsEnabled()
    }

    @Test
    fun listModeConfirmFiresWithManuallyEnteredValueInOneRowAndFallbackInOther() {
        val confirmed = mutableMapOf<Int, BigDecimal>()

        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows =
                        listOf(
                            singleRow(fromCode = "USD", toCode = "KZT", displayRate = BigDecimal("458.75")),
                            singleRow(fromCode = "EUR", toCode = "KZT", displayRate = BigDecimal("530.00")),
                        ),
                    onConfirm = { confirmed.putAll(it) },
                    onDismiss = {},
                )
            }
        }

        // Override only the second row's value
        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}1")
            .performTextClearance()
        composeTestRule
            .onNodeWithTag("${RATE_ROW_FIELD_TAG_PREFIX}1")
            .performTextInput("540.00")

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(BigDecimal("458.75"), confirmed[0])
            assertEquals(BigDecimal("540.00"), confirmed[1])
        }
    }

    // ---- empty list: nothing rendered ----

    @Test
    fun emptyRowsListRendersNothing() {
        composeTestRule.setContent {
            MyMoneyTheme {
                RateConfirmDialogContent(
                    rows = emptyList(),
                    onConfirm = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(RATE_CONFIRM_DIALOG_TAG)
            .assertDoesNotExist()
    }
}
