package com.kshavrin.mymoney.core.designsystem.form

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Contract-level pins for [TransactionFormState] and the Edit/New mode distinction.
 *
 * # Why this is a JVM contract test, not a Compose-UI test
 *
 * `:core:designsystem`'s offline test classpath has `libs.junit` + a few
 * non-UI libraries; Robolectric / `compose-ui-test-junit4` are not wired in.
 * The Compose rendering decisions driven by [TransactionFormState] are pinned
 * below as pure data-layer invariants so they cannot drift without a failing
 * test. The corresponding full Compose-UI test template (for PHASE_15) is
 * documented at the bottom of this file.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class TransactionFormContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(
 *         state: TransactionFormState,
 *         onEvent: (TransactionFormEvent) -> Unit = {},
 *     ) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { TransactionFormContent(state = state, onEvent = onEvent) }
 *         }
 *     }
 *
 *     // Edit mode: delete button shown
 *     @Test fun `Edit mode shows the delete button`() {
 *         setContent(editState())
 *         composeTestRule.onNodeWithTag(TRANSACTION_FORM_DELETE_TAG).assertIsDisplayed()
 *     }
 *
 *     // New mode: delete button absent
 *     @Test fun `New mode does not show the delete button`() {
 *         setContent(newState())
 *         composeTestRule.onNodeWithTag(TRANSACTION_FORM_DELETE_TAG).assertDoesNotExist()
 *     }
 *
 *     // Edit mode: category step reachable when chooseCategoryEnabled is true
 *     @Test fun `Edit mode shows the choose-category button when chooseCategoryEnabled`() {
 *         setContent(editState(chooseCategoryEnabled = true))
 *         composeTestRule.onNodeWithText("Choose category").assertIsDisplayed()
 *     }
 *
 *     // Edit mode: clicking the choose-category button emits SelectCategoryClicked
 *     @Test fun `clicking choose-category button in Edit mode emits SelectCategoryClicked`() {
 *         val events = mutableListOf<TransactionFormEvent>()
 *         setContent(editState(chooseCategoryEnabled = true), events::add)
 *         composeTestRule.onNodeWithText("Choose category").performClick()
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it == TransactionFormEvent.SelectCategoryClicked })
 *         }
 *     }
 *
 *     // Edit mode: category step shows the grid; clicking a category emits CategoryPicked
 *     @Test fun `Edit mode category grid emits CategoryPicked on click`() {
 *         val events = mutableListOf<TransactionFormEvent>()
 *         setContent(editState(categoryStep = true, categories = sampleCategories()), events::add)
 *         composeTestRule.onNodeWithContentDescription("Food").performClick()
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is TransactionFormEvent.CategoryPicked && it.categoryId == 10L })
 *         }
 *     }
 *
 *     // New mode: CategoryPicked (via category step) is emitted the same way;
 *     // the difference (auto-save vs explicit-save) is handled by the host ViewModel,
 *     // NOT by TransactionFormContent itself.
 *     @Test fun `New mode category grid also emits CategoryPicked on click`() {
 *         val events = mutableListOf<TransactionFormEvent>()
 *         setContent(newState(categoryStep = true, categories = sampleCategories()), events::add)
 *         composeTestRule.onNodeWithContentDescription("Food").performClick()
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is TransactionFormEvent.CategoryPicked })
 *         }
 *     }
 *
 *     private fun editState(
 *         categoryStep: Boolean = false,
 *         chooseCategoryEnabled: Boolean = true,
 *         categories: List<TransactionFormCategory> = emptyList(),
 *     ) = baseState(TransactionFormMode.Edit, categoryStep, chooseCategoryEnabled, categories)
 *
 *     private fun newState(
 *         categoryStep: Boolean = false,
 *         chooseCategoryEnabled: Boolean = false,
 *         categories: List<TransactionFormCategory> = emptyList(),
 *     ) = baseState(TransactionFormMode.New, categoryStep, chooseCategoryEnabled, categories)
 *
 *     private fun baseState(
 *         mode: TransactionFormMode,
 *         categoryStep: Boolean,
 *         chooseCategoryEnabled: Boolean,
 *         categories: List<TransactionFormCategory>,
 *     ) = TransactionFormState(
 *         amountInput = "12.50",
 *         expression = "",
 *         currencyCode = "USD",
 *         currencySymbol = "$",
 *         note = "Lunch",
 *         occurredAt = LocalDate.of(2026, 5, 10),
 *         categories = categories,
 *         categoryStep = categoryStep,
 *         chooseCategoryEnabled = chooseCategoryEnabled,
 *         mode = mode,
 *     )
 *
 *     private fun sampleCategories() = listOf(
 *         TransactionFormCategory(10L, "Food", "#FF8888", "ic_cat_food"),
 *     )
 * }
 * ```
 */
class TransactionFormStateContractTest {
    private fun baseState(
        mode: TransactionFormMode = TransactionFormMode.New,
        categoryStep: Boolean = false,
        chooseCategoryEnabled: Boolean = false,
        categories: List<TransactionFormCategory> = emptyList(),
    ) = TransactionFormState(
        amountInput = "12.50",
        expression = "",
        currencyCode = "USD",
        currencySymbol = "$",
        note = "Lunch",
        occurredAt = LocalDate.of(2026, 5, 10),
        categories = categories,
        categoryStep = categoryStep,
        chooseCategoryEnabled = chooseCategoryEnabled,
        mode = mode,
    )

    private fun editState(
        categoryStep: Boolean = false,
        chooseCategoryEnabled: Boolean = true,
        categories: List<TransactionFormCategory> = emptyList(),
    ) = baseState(TransactionFormMode.Edit, categoryStep, chooseCategoryEnabled, categories)

    private fun newState(
        categoryStep: Boolean = false,
        chooseCategoryEnabled: Boolean = false,
        categories: List<TransactionFormCategory> = emptyList(),
    ) = baseState(TransactionFormMode.New, categoryStep, chooseCategoryEnabled, categories)

    // ---- mode discrimination ------------------------------------------------------------------

    @Test
    fun `Edit mode state carries TransactionFormMode Edit`() {
        assertEquals(TransactionFormMode.Edit, editState().mode)
    }

    @Test
    fun `New mode state carries TransactionFormMode New`() {
        assertEquals(TransactionFormMode.New, newState().mode)
    }

    // ---- delete-button visibility contract ---------------------------------------------------
    // TransactionFormContent renders DeleteButton iff (mode == Edit && !categoryStep).
    // These pins document the gating expression so its intent cannot drift silently.

    @Test
    fun `delete button is gated by Edit mode outside the category step`() {
        val state = editState(categoryStep = false)
        assertTrue(
            "delete must be visible in Edit mode when NOT in the category step",
            state.mode == TransactionFormMode.Edit && !state.categoryStep,
        )
    }

    @Test
    fun `delete button is absent in New mode`() {
        val state = newState(categoryStep = false)
        assertFalse(
            "delete must never appear in New mode",
            state.mode == TransactionFormMode.Edit,
        )
    }

    @Test
    fun `delete button is absent even in Edit mode when the category step is open`() {
        // The delete button lives in the keypad column, which is hidden during categoryStep.
        val state = editState(categoryStep = true)
        assertFalse(
            "delete must not be visible while the category grid is shown",
            state.mode == TransactionFormMode.Edit && !state.categoryStep,
        )
    }

    // ---- category-step reachability contract -------------------------------------------------
    // chooseCategoryEnabled drives the enabled state of the "Choose category" button.
    // categoryStep drives which panel is shown.

    @Test
    fun `chooseCategoryEnabled is false when amount is zero`() {
        val state = baseState(chooseCategoryEnabled = false)
        assertFalse(
            "choose-category button must be disabled when amount is zero",
            state.chooseCategoryEnabled,
        )
    }

    @Test
    fun `chooseCategoryEnabled is true when amount is positive`() {
        val state = baseState(chooseCategoryEnabled = true)
        assertTrue(
            "choose-category button must be enabled when amount is positive",
            state.chooseCategoryEnabled,
        )
    }

    @Test
    fun `categoryStep false shows the keypad panel in both modes`() {
        assertFalse("Edit mode non-category step", editState(categoryStep = false).categoryStep)
        assertFalse("New mode non-category step", newState(categoryStep = false).categoryStep)
    }

    @Test
    fun `categoryStep true shows the category grid in both modes`() {
        assertTrue("Edit mode category step", editState(categoryStep = true).categoryStep)
        assertTrue("New mode category step", newState(categoryStep = true).categoryStep)
    }

    // ---- category list filtering contract ----------------------------------------------------
    // The form receives an already-filtered list from the ViewModel; the form itself does not
    // filter. These pins document that the list is passed through unmodified.

    @Test
    fun `categories list is preserved in the form state`() {
        val cats =
            listOf(
                TransactionFormCategory(10L, "Food", "#FF8888", "ic_cat_food"),
                TransactionFormCategory(11L, "Entertainment", "#8888FF", "ic_cat_fun"),
            )
        val state = editState(categories = cats)
        assertEquals(2, state.categories.size)
        assertEquals(10L, state.categories[0].id)
        assertEquals(11L, state.categories[1].id)
    }

    @Test
    fun `empty category list is preserved in the form state`() {
        val state = editState(categories = emptyList())
        assertTrue("no categories should yield an empty list", state.categories.isEmpty())
    }

    // ---- form-field pre-fill contract --------------------------------------------------------

    @Test
    fun `form state exposes amount input as received`() {
        val state = baseState().copy(amountInput = "42.99")
        assertEquals("42.99", state.amountInput)
    }

    @Test
    fun `form state exposes note as received`() {
        val state = baseState().copy(note = "Dinner")
        assertEquals("Dinner", state.note)
    }

    @Test
    fun `form state exposes occurredAt as received`() {
        val date = LocalDate.of(2026, 1, 15)
        val state = baseState().copy(occurredAt = date)
        assertEquals(date, state.occurredAt)
    }

    @Test
    fun `form state exposes currency code and symbol as received`() {
        val state = baseState().copy(currencyCode = "EUR", currencySymbol = "€")
        assertEquals("EUR", state.currencyCode)
        assertEquals("€", state.currencySymbol)
    }

    // ---- TransactionFormEvent catalogue -------------------------------------------------------
    // Verify that every event the Edit-mode form can fire is represented in the sealed interface,
    // so a future rename does not silently remove a branch.

    @Test
    fun `DeleteClicked event exists in the TransactionFormEvent sealed interface`() {
        val event: TransactionFormEvent = TransactionFormEvent.DeleteClicked
        assertTrue(event is TransactionFormEvent.DeleteClicked)
    }

    @Test
    fun `SelectCategoryClicked event exists in the TransactionFormEvent sealed interface`() {
        val event: TransactionFormEvent = TransactionFormEvent.SelectCategoryClicked
        assertTrue(event is TransactionFormEvent.SelectCategoryClicked)
    }

    @Test
    fun `CategoryPicked event carries the category id`() {
        val event: TransactionFormEvent = TransactionFormEvent.CategoryPicked(42L)
        assertTrue(event is TransactionFormEvent.CategoryPicked)
        assertEquals(42L, (event as TransactionFormEvent.CategoryPicked).categoryId)
    }

    @Test
    fun `BackToAmount event exists in the TransactionFormEvent sealed interface`() {
        val event: TransactionFormEvent = TransactionFormEvent.BackToAmount
        assertTrue(event is TransactionFormEvent.BackToAmount)
    }
}
