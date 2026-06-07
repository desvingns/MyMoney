package com.kshavrin.mymoney.feature.transactionslist.detail

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.transactionslist.R
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.kshavrin.mymoney.core.designsystem.R as DesignSystemR

@RunWith(AndroidJUnit4::class)
class TransactionDetailContentUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits detail back event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.BackClicked), capturedEvents)
        }
    }

    @Test
    fun `save button is hidden until detail is dirty and valid`() {
        setContent(state = loadedExpenseState(isDirty = false))

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.detail_save))
            .assertDoesNotExist()
    }

    @Test
    fun `save button emits detail save event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(
            state = loadedExpenseState(isDirty = true),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.detail_save))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.SaveClicked), capturedEvents)
        }
    }

    @Test
    fun `delete button emits detail delete event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.detail_delete))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.DeleteClicked), capturedEvents)
        }
    }

    @Test
    fun `delete dialog cancel emits detail dismiss event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(
            state = loadedExpenseState(confirmDeleteVisible = true),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.delete_transaction_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.cancel))
            .performClick()
        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.DismissDelete), capturedEvents)
        }
    }

    @Test
    fun `delete dialog confirm emits detail confirm event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(
            state = loadedExpenseState(confirmDeleteVisible = true),
            onEvent = { event -> capturedEvents += event },
        )
        composeTestRule
            .onNodeWithText(targetString(R.string.delete_confirm))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.ConfirmDelete), capturedEvents)
        }
    }

    @Test
    fun `keypad and note controls emit detail edit events`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule.onNodeWithText("1").performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.keypad_backspace_cd))
            .performClick()
        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("Dinner")

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(TransactionDetailEvent.KeypadDigit(1)))
            assertTrue(capturedEvents.contains(TransactionDetailEvent.KeypadBackspace))
            assertTrue(capturedEvents.contains(TransactionDetailEvent.NoteChanged("Dinner")))
        }
    }

    @Test
    fun `picking a different date emits detail date changed event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()
        val initialDate = LocalDate.of(2026, 5, 17)
        val chosenDate = initialDate.plusDays(1)

        setContent(
            state = loadedExpenseState(occurredAt = initialDate),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(DesignSystemR.string.amountfield_pick_date_cd))
            .performClick()
        composeTestRule.onNodeWithText(dateLabel(chosenDate)).performClick()
        composeTestRule.onNodeWithText(targetString(R.string.detail_pick_date)).performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.DateChanged(chosenDate)), capturedEvents)
        }
    }

    @Test
    fun `account dropdown emits detail account changed event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule.onAllNodesWithText("Cash")[0].performClick()
        composeTestRule.onNodeWithText("Card").performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(TransactionDetailEvent.AccountChanged(2L)), capturedEvents)
        }
    }

    @Test
    fun `cross currency transfer controls emit target and rate events`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(
            state = loadedTransferState(),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNode(hasText("Card") and hasClickAction())
            .performScrollTo()
            .performClick()
        composeTestRule
            .onNode(hasText("Savings") and hasClickAction())
            .performClick()
        composeTestRule.onAllNodes(hasSetTextAction())[1]
            .performScrollTo()
            .performTextInput("0.92")

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(TransactionDetailEvent.TargetAccountChanged(3L)))
            assertTrue(capturedEvents.contains(TransactionDetailEvent.RateChanged("0.92")))
        }
    }

    @Test
    fun `error banner shows snackbar message and dismisses error state`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()
        val snackbarMessages = mutableListOf<String>()

        setContent(
            state = loadedExpenseState(errorBannerRes = R.string.detail_error_enter_amount),
            onEvent = { event -> capturedEvents += event },
            captureSnackbar = true,
            onSnackbarMessage = { message -> snackbarMessages += message },
        )

        composeTestRule.waitUntil(timeoutMillis = 2_000) {
            snackbarMessages.isNotEmpty() &&
                capturedEvents.contains(TransactionDetailEvent.DismissError)
        }
        composeTestRule.runOnIdle {
            assertEquals(listOf(targetString(R.string.detail_error_enter_amount)), snackbarMessages)
        }
    }

    private fun setContent(
        state: TransactionDetailState = loadedExpenseState(),
        onEvent: (TransactionDetailEvent) -> Unit = {},
        captureSnackbar: Boolean = false,
        onSnackbarMessage: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                if (captureSnackbar) {
                    LaunchedEffect(snackbarHostState) {
                        val data = snapshotFlow { snackbarHostState.currentSnackbarData }
                            .filterNotNull()
                            .first()
                        onSnackbarMessage(data.visuals.message)
                        data.dismiss()
                    }
                }
                TransactionDetailContent(
                    state = state,
                    snackbarHostState = snackbarHostState,
                    onEvent = onEvent,
                )
            }
        }
    }

    // ---- Edit-mode: in-form delete button (SPEC: edit screen uses TransactionFormContent) -----

    @Test
    fun `expense edit screen shows in-form delete button via TransactionFormContent`() {
        setContent(state = loadedExpenseState())

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_delete_button))
            .assertIsDisplayed()
    }

    @Test
    fun `in-form delete button emits detail delete event`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_delete_button))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(TransactionDetailEvent.DeleteClicked))
        }
    }

    @Test
    fun `choose-category button is shown in Edit mode when amount is positive`() {
        setContent(state = loadedExpenseState())

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_choose_category_button))
            .assertIsDisplayed()
    }

    @Test
    fun `choose-category button click emits SelectCategoryClicked`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()

        setContent(
            state = loadedExpenseState(),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_choose_category_button))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(TransactionDetailEvent.SelectCategoryClicked))
        }
    }

    @Test
    fun `category grid is shown when categoryStep is true`() {
        val category = category(id = 10L, name = "Food")
        setContent(
            state = loadedExpenseState().copy(
                categoryStep = true,
                categories = listOf(category),
            ),
        )

        composeTestRule
            .onNodeWithContentDescription("Food")
            .assertIsDisplayed()
    }

    @Test
    fun `clicking a category in category step emits CategoryPicked and does NOT emit NavigateBack`() {
        val capturedEvents = mutableListOf<TransactionDetailEvent>()
        val category = category(id = 10L, name = "Food")

        setContent(
            state = loadedExpenseState().copy(
                categoryStep = true,
                categories = listOf(category),
            ),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription("Food")
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(
                "CategoryPicked must be emitted when a category is selected in Edit mode",
                capturedEvents.any { it is TransactionDetailEvent.CategoryPicked && it.categoryId == 10L },
            )
            assertTrue(
                "NavigateBack must NOT be emitted by the form itself (explicit-save contract)",
                capturedEvents.none { it is TransactionDetailEvent.SaveClicked },
            )
        }
    }

    @Test
    fun `transfer edit screen does not show the in-form delete button`() {
        setContent(state = loadedTransferState())

        composeTestRule
            .onNodeWithText(targetString(DesignSystemR.string.transaction_form_delete_button))
            .assertDoesNotExist()
    }

    // ---- helpers -------------------------------------------------------------------------------

    private fun loadedExpenseState(
        isDirty: Boolean = false,
        confirmDeleteVisible: Boolean = false,
        occurredAt: LocalDate = LocalDate.of(2026, 5, 17),
        errorBannerRes: Int? = null,
    ): TransactionDetailState = TransactionDetailState(
        transactionId = 42L,
        kind = TransactionKind.Expense,
        amount = BigDecimal("12.34"),
        amountInput = "12.34",
        note = "",
        occurredAt = occurredAt,
        account = account(id = 1L, name = "Cash", currencyId = 1L),
        currency = currency(id = 1L, code = "USD"),
        category = category(id = 10L, name = "Food"),
        accounts = listOf(
            account(id = 1L, name = "Cash", currencyId = 1L),
            account(id = 2L, name = "Card", currencyId = 1L),
        ),
        isLoaded = true,
        isDirty = isDirty,
        confirmDeleteVisible = confirmDeleteVisible,
        errorBannerRes = errorBannerRes,
    )

    private fun loadedTransferState(): TransactionDetailState = TransactionDetailState(
        transactionId = 43L,
        kind = TransactionKind.Transfer,
        amount = BigDecimal("50"),
        amountInput = "50",
        occurredAt = LocalDate.of(2026, 5, 17),
        account = account(id = 1L, name = "Cash", currencyId = 1L),
        targetAccount = account(id = 2L, name = "Card", currencyId = 2L),
        currency = currency(id = 1L, code = "USD"),
        targetCurrency = currency(id = 2L, code = "EUR"),
        rateInput = "",
        accounts = listOf(
            account(id = 1L, name = "Cash", currencyId = 1L),
            account(id = 2L, name = "Card", currencyId = 2L),
            account(id = 3L, name = "Savings", currencyId = 2L),
        ),
        isLoaded = true,
        isDirty = true,
    )

    private fun account(id: Long, name: String, currencyId: Long): Account {
        val now = Instant.parse("2026-05-28T00:00:00Z")
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

    private fun category(id: Long, name: String): Category = Category(
        id = id,
        name = name,
        kind = CategoryKind.Expense,
        iconKey = "ic_cat_food",
        colorHex = "#7AC794",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = Instant.parse("2026-05-28T00:00:00Z"),
    )

    private fun currency(id: Long, code: String): Currency = Currency(
        id = id,
        code = code,
        symbol = code,
        name = code,
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun dateLabel(date: LocalDate): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        return date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", locale))
    }

    private fun targetString(resourceId: Int, vararg formatArgs: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)
}
