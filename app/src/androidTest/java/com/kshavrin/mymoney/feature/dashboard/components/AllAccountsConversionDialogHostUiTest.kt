package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.dashboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class AllAccountsConversionDialogHostUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    // -------------------------------------------------------------------------
    // Mode dialog (fork: convert vs separate)
    // -------------------------------------------------------------------------

    @Test
    fun `mode dialog shows title and both action buttons`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog = AllAccountsConversionDialog.Mode,
                    onDismiss = {},
                    onConvertChosen = {},
                    onSeparateChosen = {},
                    onTargetChosen = {},
                    onRatesConfirmed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_MODE_DIALOG_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.all_accounts_mode_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_MODE_CONVERT_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_MODE_SEPARATE_TAG)
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `tapping convert button invokes onConvertChosen`() {
        var convertChosen = false

        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog = AllAccountsConversionDialog.Mode,
                    onDismiss = {},
                    onConvertChosen = { convertChosen = true },
                    onSeparateChosen = {},
                    onTargetChosen = {},
                    onRatesConfirmed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_MODE_CONVERT_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue("onConvertChosen must be called when the convert button is tapped", convertChosen)
        }
    }

    @Test
    fun `tapping separate button invokes onSeparateChosen`() {
        var separateChosen = false

        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog = AllAccountsConversionDialog.Mode,
                    onDismiss = {},
                    onConvertChosen = {},
                    onSeparateChosen = { separateChosen = true },
                    onTargetChosen = {},
                    onRatesConfirmed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_MODE_SEPARATE_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue("onSeparateChosen must be called when the separate button is tapped", separateChosen)
        }
    }

    // -------------------------------------------------------------------------
    // Target currency picker dialog
    // -------------------------------------------------------------------------

    @Test
    fun `target picker shows title and one row per currency`() {
        val usd = currency(id = 1L, code = "USD", name = "US Dollar")
        val eur = currency(id = 2L, code = "EUR", name = "Euro")

        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog = AllAccountsConversionDialog.TargetPicker(listOf(usd, eur)),
                    onDismiss = {},
                    onConvertChosen = {},
                    onSeparateChosen = {},
                    onTargetChosen = {},
                    onRatesConfirmed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_TARGET_DIALOG_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.all_accounts_target_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("${ALL_ACCOUNTS_TARGET_OPTION_TAG_PREFIX}${usd.id}")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeTestRule
            .onNodeWithTag("${ALL_ACCOUNTS_TARGET_OPTION_TAG_PREFIX}${eur.id}")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun `tapping a currency row invokes onTargetChosen with the correct currency id`() {
        val usd = currency(id = 1L, code = "USD", name = "US Dollar")
        val eur = currency(id = 2L, code = "EUR", name = "Euro")
        var chosenId: Long? = null

        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog = AllAccountsConversionDialog.TargetPicker(listOf(usd, eur)),
                    onDismiss = {},
                    onConvertChosen = {},
                    onSeparateChosen = {},
                    onTargetChosen = { id -> chosenId = id },
                    onRatesConfirmed = {},
                )
            }
        }

        composeTestRule
            .onNodeWithTag("${ALL_ACCOUNTS_TARGET_OPTION_TAG_PREFIX}${eur.id}")
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(eur.id, chosenId)
        }
    }

    // -------------------------------------------------------------------------
    // Null dialog — nothing rendered
    // -------------------------------------------------------------------------

    @Test
    fun `null dialog renders nothing`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog = null,
                    onDismiss = {},
                    onConvertChosen = {},
                    onSeparateChosen = {},
                    onTargetChosen = {},
                    onRatesConfirmed = {},
                )
            }
        }

        // Neither the mode dialog nor the target picker should appear.
        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_MODE_DIALOG_TAG)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(ALL_ACCOUNTS_TARGET_DIALOG_TAG)
            .assertDoesNotExist()
    }

    // -------------------------------------------------------------------------
    // RateConfirm dialog — delegate to RateConfirmDialog; verify host wires ids
    // -------------------------------------------------------------------------

    @Test
    fun `rate confirm dialog calls onRatesConfirmed with source currency id mapped from row index`() {
        val eurId = 2L
        val rateRow =
            com.kshavrin.mymoney.core.designsystem.dialog.RateRow(
                fromCode = "EUR",
                toCode = "USD",
                lastUpdated = null,
                displayRate = BigDecimal("1.10"),
                stale = false,
                missing = false,
            )
        var confirmedMap: Map<Long, BigDecimal>? = null

        composeTestRule.setContent {
            MyMoneyTheme {
                AllAccountsConversionDialogHost(
                    dialog =
                        AllAccountsConversionDialog.RateConfirm(
                            rows = listOf(rateRow),
                            sourceCurrencyIds = listOf(eurId),
                        ),
                    onDismiss = {},
                    onConvertChosen = {},
                    onSeparateChosen = {},
                    onTargetChosen = {},
                    onRatesConfirmed = { map -> confirmedMap = map },
                )
            }
        }

        // Tap the confirm button via its testTag (defined in RateConfirmDialog.kt).
        composeTestRule
            .onNodeWithTag(
                com.kshavrin.mymoney.core.designsystem.dialog.RATE_CONFIRM_BUTTON_TAG,
                useUnmergedTree = true,
            ).performClick()

        composeTestRule.runOnIdle {
            val map = confirmedMap
            assertTrue("onRatesConfirmed must be called after confirm; map was null", map != null)
            // Row 0 maps to sourceCurrencyIds[0] = eurId.
            // The dialog pre-fills displayRate as the resolved value, so the map contains eurId.
            assertTrue(
                "confirmed map must contain eurId as key; got $map",
                map!!.containsKey(eurId),
            )
            assertEquals(0, BigDecimal("1.10").compareTo(map[eurId]))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun currency(
        id: Long,
        code: String,
        name: String,
    ) = Currency(
        id = id,
        code = code,
        symbol = code,
        name = name,
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )
}
