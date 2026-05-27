package com.kshavrin.mymoney.feature.transaction.transfer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transaction.R
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    fun `amount press reveals keypad and digit emits transfer event`() {
        val capturedEvents = mutableListOf<TransferEvent>()

        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = { event -> capturedEvents += event },
                )
            }
        }

        composeTestRule.onNodeWithText("1").assertDoesNotExist()
        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithText("1").performScrollTo().performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.KeypadDigit(1)), capturedEvents)
        }
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

        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.keypad_backspace_cd))
            .performScrollTo()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.KeypadBackspace), capturedEvents)
        }
    }

    @Test
    fun `note focus hides revealed transfer keypad`() {
        composeTestRule.setContent {
            MyMoneyTheme {
                TransferScreen(
                    state = TransferState(),
                    onEvent = {},
                )
            }
        }

        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText(targetString(DesignSystemR.string.amountfield_note_hint)).performClick()
        composeTestRule.onNodeWithText("1").assertDoesNotExist()
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
    fun `picking a date hides keypad and emits transfer date changed event`() {
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

        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.amountfield_pick_date_cd))
            .performClick()
        composeTestRule.onNodeWithText(dateLabel(chosenDate)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.pick_date)).performClick()
        composeTestRule.onNodeWithText("1").assertDoesNotExist()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.DateChanged(chosenDate)), capturedEvents)
        }
    }

    @Test
    fun `choosing a source account hides keypad and emits transfer event`() {
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

        composeTestRule.onNodeWithText("0").performClick()
        composeTestRule
            .onNode(hasText(targetString(R.string.source_label)) and hasClickAction())
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithText(sourceAccount.name).performClick()
        composeTestRule.onNodeWithText("1").assertDoesNotExist()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransferEvent.SourceAccountChanged(sourceAccount.id)), capturedEvents)
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)

    private fun dateLabel(date: LocalDate): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }

    private fun account(id: Long, name: String): Account {
        val now = Instant.parse("2026-05-27T00:00:00Z")
        return Account(
            id = id,
            name = name,
            currencyId = 1L,
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
}
