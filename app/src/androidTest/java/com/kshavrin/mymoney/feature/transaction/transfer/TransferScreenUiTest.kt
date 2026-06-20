package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

@RunWith(AndroidJUnit4::class)
class TransferScreenUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits transfer back event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNodeWithText(targetString(R.string.new_transfer_title)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.back))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.BackClicked), capturedEvents)
        }
    }

    @Test
    fun `selector stack keeps source row above arrow and target row below it`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = {},
                )
            }
        }

        val sourceBounds =
            composeTestRule
                .onNodeWithContentDescription(targetString(R.string.source_label))
                .fetchSemanticsNode()
                .boundsInRoot
        val arrowBounds =
            composeTestRule
                .onNodeWithContentDescription(targetString(R.string.transfer_direction_cd))
                .fetchSemanticsNode()
                .boundsInRoot
        val targetBounds =
            composeTestRule
                .onNodeWithContentDescription(targetString(R.string.target_label))
                .fetchSemanticsNode()
                .boundsInRoot

        assertTrue("source selector must stay above the arrow", sourceBounds.bottom <= arrowBounds.top)
        assertTrue("target selector must stay below the arrow", arrowBounds.bottom <= targetBounds.top)
        assertTrue(
            "arrow must stay centered between the selector rows",
            abs(((arrowBounds.left + arrowBounds.right) / 2f) - ((sourceBounds.left + sourceBounds.right) / 2f)) < 2f,
        )
    }

    @Test
    fun `source selector row shows selected account and currency code`() {
        val sourceAccount = account(id = 10L, name = "Primary wallet", currencyId = 1L)
        val sourceCurrency = currency(id = 1L, code = "USD")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            currencies = listOf(sourceCurrency),
                            sourceAccount = sourceAccount,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.source_label))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(sourceAccount.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(sourceCurrency.code).assertIsDisplayed()
    }

    @Test
    fun `target selector row shows selected account and currency code`() {
        val targetAccount = account(id = 20L, name = "Savings account", currencyId = 2L)
        val targetCurrency = currency(id = 2L, code = "EUR")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            targetAccount = targetAccount,
                            targetCurrency = targetCurrency,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.target_label))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetAccount.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetCurrency.code).assertIsDisplayed()
    }

    @Test
    fun `save button stays disabled until transfer is valid`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.currency_rate_save))
            .assertIsNotEnabled()
    }

    @Test
    fun `dialpad keypad digit emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        assertTrue(
            "keypad digits must start hidden",
            composeTestRule.onAllNodesWithText("1").fetchSemanticsNodes().isEmpty(),
        )
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transfer_open_keypad_cd))
            .performClick()
        composeTestRule.onNodeWithText("1").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.KeypadDigit(1)), capturedEvents)
        }
    }

    @Test
    fun `dialpad fab still opens the keypad sheet`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = {},
                )
            }
        }

        assertTrue(
            "keypad digits must start hidden",
            composeTestRule.onAllNodesWithText("1").fetchSemanticsNodes().isEmpty(),
        )
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transfer_open_keypad_cd))
            .assertIsDisplayed()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.keypad_backspace_cd))
            .assertIsDisplayed()
    }

    @Test
    fun `revealed keypad backspace emits transfer backspace event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transfer_open_keypad_cd))
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.keypad_backspace_cd))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.KeypadBackspace), capturedEvents)
        }
    }

    @Test
    fun `amount field stays form only until dialpad fab is pressed`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("0").performClick()
        assertTrue(
            "keypad must stay hidden until the FAB opens it",
            composeTestRule.onAllNodesWithText("1").fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `entering a note emits transfer note changed event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.amountfield_note_hint))
            .performTextInput("Move funds")

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.NoteChanged("Move funds")), capturedEvents)
        }
    }

    @Test
    fun `picking a date emits transfer date changed event`() {
        val capturedEvents = mutableListOf<TransferEvent>()
        val initialDate = LocalDate.of(2026, 5, 17)
        val chosenDate = initialDate.plusDays(1)

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(occurredAt = initialDate),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.amountfield_pick_date_cd))
            .performClick()
        composeTestRule.onNodeWithText(dateLabel(chosenDate)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.pick_date)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.DateChanged(chosenDate)), capturedEvents)
        }
    }

    @Test
    fun `choosing a source account emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()
        val sourceAccount = account(id = 10L, name = "Primary wallet")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(accounts = listOf(sourceAccount)),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNode(hasText(targetString(R.string.source_label)) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(sourceAccount.name).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.SourceAccountChanged(sourceAccount.id)), capturedEvents)
        }
    }

    @Test
    fun `choosing a target account emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()
        val targetAccount = account(id = 20L, name = "Savings account")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(accounts = listOf(targetAccount)),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNode(hasText(targetString(R.string.target_label)) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(targetAccount.name).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.TargetAccountChanged(targetAccount.id)), capturedEvents)
        }
    }

    @Test
    fun `changing a visible rate emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()
        val sourceCurrency = currency(id = 1L, code = "USD")
        val targetCurrency = currency(id = 2L, code = "EUR")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            sourceCurrency = sourceCurrency,
                            targetCurrency = targetCurrency,
                            ratePreviewText = "1 USD = 0.92 EUR",
                        ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithText(targetString(R.string.transfer_change_rate_cta))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.ChangeRateClicked), capturedEvents)
        }
    }

    @Test
    fun `cross currency transfer keeps the rate panel and keypad fab visible`() {
        val sourceAccount = account(id = 10L, name = "Primary wallet", currencyId = 1L)
        val targetAccount = account(id = 20L, name = "Savings account", currencyId = 2L)
        val sourceCurrency = currency(id = 1L, code = "USD")
        val targetCurrency = currency(id = 2L, code = "EUR")
        val ratePreview = "1 USD = 0.92 EUR"

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            sourceAccount = sourceAccount,
                            targetAccount = targetAccount,
                            sourceCurrency = sourceCurrency,
                            targetCurrency = targetCurrency,
                            ratePreviewText = ratePreview,
                        ),
                    onEvent = {},
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.source_label))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.target_label))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.currency_rate)).assertIsDisplayed()
        composeTestRule.onNodeWithText(ratePreview).assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(R.string.transfer_change_rate_cta)).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transfer_open_keypad_cd))
            .assertIsDisplayed()
    }

    @Test
    fun `valid transfer save emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()
        val sourceAccount = account(id = 10L, name = "Primary wallet")
        val targetAccount = account(id = 20L, name = "Savings account")
        val currency = currency(id = 1L, code = "USD")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            sourceAccount = sourceAccount,
                            targetAccount = targetAccount,
                            sourceCurrency = currency,
                            targetCurrency = currency,
                            amount = BigDecimal.ONE,
                            amountInput = "1",
                        ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.currency_rate_save))
            .assertIsEnabled()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.SaveClicked), capturedEvents)
        }
    }

    @Test
    fun `disabled save tap does not emit transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.currency_rate_save))
            .assertIsNotEnabled()
            .performTouchInput { click() }

        composeTestRule.runOnIdle {
            assertEquals(emptyList<TransferEvent>(), capturedEvents)
        }
    }

    @Test
    fun `rate dialog row renders confirm dialog and confirm emits rate confirmed event`() {
        val capturedEvents = mutableListOf<TransferEvent>()
        val sourceCurrency = currency(id = 1L, code = "USD")
        val targetCurrency = currency(id = 2L, code = "EUR")

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            sourceAccount = account(id = 10L, name = "Primary wallet", currencyId = 1L),
                            targetAccount = account(id = 20L, name = "Savings account", currencyId = 2L),
                            sourceCurrency = sourceCurrency,
                            targetCurrency = targetCurrency,
                            amount = BigDecimal("100"),
                            amountInput = "100",
                            isSaving = true,
                            rateDialogRow =
                                com.kshavrin.mymoney.core.designsystem.dialog.RateRow(
                                    fromCode = "USD",
                                    toCode = "EUR",
                                    lastUpdated = LocalDate.of(2026, 5, 20),
                                    displayRate = BigDecimal("0.92"),
                                    stale = false,
                                    missing = false,
                                ),
                            rateDialogFullRate = BigDecimal("0.92"),
                        ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(com.kshavrin.mymoney.core.designsystem.dialog.RATE_CONFIRM_DIALOG_TAG)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(com.kshavrin.mymoney.core.designsystem.dialog.RATE_CONFIRM_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.any { it is TransferEvent.RateDialogConfirmed })
        }
    }

    @Test
    fun `rate dialog dismiss emits rate dismissed event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state =
                        TransferState(
                            isSaving = true,
                            rateDialogRow =
                                com.kshavrin.mymoney.core.designsystem.dialog.RateRow(
                                    fromCode = "USD",
                                    toCode = "EUR",
                                    lastUpdated = LocalDate.of(2026, 5, 20),
                                    displayRate = BigDecimal("0.92"),
                                    stale = false,
                                    missing = false,
                                ),
                        ),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule
            .onNodeWithTag(com.kshavrin.mymoney.core.designsystem.dialog.RATE_DISMISS_BUTTON_TAG)
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.RateDialogDismissed), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }

    private fun account(
        id: Long,
        name: String,
        currencyId: Long = 1L,
    ): Account {
        val now = Instant.parse("2026-05-27T00:00:00Z")
        return Account(
            id = id,
            name = name,
            currencyId = currencyId,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#7AC794",
            iconKey = "ic_acc_cash",
            isDefault = false,
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
            isArchived = false,
        )
    }

    private fun currency(
        id: Long,
        code: String,
    ): Currency =
        Currency(
            id = id,
            code = code,
            symbol = code,
            name = code,
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )
}
