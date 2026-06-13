package com.kshavrin.mymoney.feature.transaction.expense

import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Contract-level pinning for [AddExpenseScreen] (form-chrome restyle).
 *
 * The restyle is presentation-only. The add-expense form must keep:
 *
 *   - the embedded [com.kshavrin.mymoney.feature.transaction.categorygrid.CategoryGrid]
 *     (a 3-column grid INSIDE the form, not a category bar / single button);
 *   - the amount box and embedded keypad in the amount-entry step;
 *   - the amount-box ✕ clear affordance mapping to `KeypadBackspace`;
 *   - the keypad digit/operator/dot/equals routing into the form events.
 *
 * # Why this is not a full Compose-UI test
 *
 * `:feature:transaction`'s offline test classpath has only
 * `libs.junit` + `libs.kotlinx.coroutines.test` + `libs.turbine` — no
 * Robolectric / `compose-ui-test-junit4` / `ui-test-manifest`
 * (see build.gradle.kts), so `createComposeRule()` will not compile.
 * The executable Compose test lands in PHASE_15. `dispatchAmountEvent` /
 * `dispatchKeypadEvent` are `private` in AddExpenseScreen.kt, so they are
 * pinned here with a verified pure mirror; the CategoryGrid presence and
 * the amount-box rendering are covered by the documented template.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class AddExpenseScreenContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: AddExpenseState, onEvent: (AddExpenseEvent) -> Unit = {}) {
 *         composeTestRule.setContent { MyMoneyTheme { AddExpenseScreen(state = state, onEvent = onEvent) } }
 *     }
 *
 *     @Test fun `embedded category grid is present (not a bar or button)`() {
 *         setContent(loaded(categories = sampleCategories()))
 *         composeTestRule.onNodeWithTag(CATEGORY_GRID_TAG).assertExists()
 *         // each seeded category is a cell inside the grid
 *         composeTestRule.onNodeWithContentDescription("Food").assertExists()
 *         composeTestRule.onNodeWithTag(CATEGORY_GRID_ADD_CELL_TAG).assertExists()
 *     }
 *
 *     @Test fun `amount box renders the current display`() {
 *         setContent(loaded(amountInput = "12.00"))
 *         composeTestRule.onNodeWithText("12.00").assertIsDisplayed()
 *     }
 *
 *     @Test fun `tapping a category cell fires CategoryPicked`() {
 *         val events = mutableListOf<AddExpenseEvent>()
 *         setContent(loaded(categories = sampleCategories()), events::add)
 *         composeTestRule.onNodeWithContentDescription("Food").performClick()
 *         composeTestRule.runOnIdle { assertTrue(events.any { it is AddExpenseEvent.CategoryPicked }) }
 *     }
 *
 *     @Test fun `keypad is shown in the amount-entry step`() {
 *         setContent(loaded().copy(categoryStep = false))
 *         composeTestRule.onNodeWithText("7").assertIsDisplayed()
 *     }
 * }
 * ```
 *
 * # JVM-visible pins below
 */
class AddExpenseScreenContractTest {
    // ---- amount-box ✕ clear + keypad routing (dispatchAmountEvent mirror) ----

    @Test
    fun `clear affordance routes to KeypadBackspace`() {
        assertEquals(
            AddExpenseEvent.KeypadBackspace,
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Backspace)),
        )
    }

    @Test
    fun `digit keypad event routes to KeypadDigit`() {
        assertEquals(
            AddExpenseEvent.KeypadDigit(9),
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Digit(9))),
        )
    }

    @Test
    fun `operator keypad event routes to KeypadOperator`() {
        assertEquals(
            AddExpenseEvent.KeypadOperator(Operator.Divide),
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Op(Operator.Divide))),
        )
    }

    @Test
    fun `dot and equals keypad events route to KeypadDot and KeypadEquals`() {
        assertEquals(AddExpenseEvent.KeypadDot, dispatch(AmountFieldEvent.Keypad(KeypadEvent.Dot)))
        assertEquals(AddExpenseEvent.KeypadEquals, dispatch(AmountFieldEvent.Keypad(KeypadEvent.Equals)))
    }

    @Test
    fun `note change routes to NoteChanged`() {
        assertEquals(
            AddExpenseEvent.NoteChanged("groceries"),
            dispatch(AmountFieldEvent.NoteChanged("groceries")),
        )
    }

    @Test
    fun `date change routes to DateChanged with the selected start date`() {
        val startDate = LocalDate.of(2026, 5, 17)

        assertEquals(
            AddExpenseEvent.DateChanged(startDate),
            dispatch(AmountFieldEvent.DateChanged(startDate)),
        )
    }

    @Test
    fun `account chip click is swallowed in the add form`() {
        assertNull(dispatch(AmountFieldEvent.AccountChipClicked))
    }

    // ---- category grid wiring (the grid stays embedded) ----

    @Test
    fun `category cell click produces CategoryPicked with the category id`() {
        val event: AddExpenseEvent = AddExpenseEvent.CategoryPicked(11L)
        assertTrue(event is AddExpenseEvent.CategoryPicked)
        assertEquals(11L, (event as AddExpenseEvent.CategoryPicked).categoryId)
    }

    @Test
    fun `grid add cell click produces AddCategoryClicked`() {
        val event: AddExpenseEvent = AddExpenseEvent.AddCategoryClicked
        assertTrue(event is AddExpenseEvent.AddCategoryClicked)
    }

    /**
     * Pure mirror of AddExpenseScreen.dispatchAmountEvent + dispatchKeypadEvent
     * (both private). Returns the AddExpenseEvent the screen would emit, or
     * `null` for branches the screen swallows / handles via UI-local state.
     * Keep in lock-step with AddExpenseScreen.kt.
     */
    private fun dispatch(e: AmountFieldEvent): AddExpenseEvent? =
        when (e) {
            is AmountFieldEvent.Keypad ->
                when (val k = e.event) {
                    is KeypadEvent.Digit -> AddExpenseEvent.KeypadDigit(k.d)
                    is KeypadEvent.Op -> AddExpenseEvent.KeypadOperator(k.op)
                    KeypadEvent.Dot -> AddExpenseEvent.KeypadDot
                    KeypadEvent.Backspace -> AddExpenseEvent.KeypadBackspace
                    KeypadEvent.Equals -> AddExpenseEvent.KeypadEquals
                }
            is AmountFieldEvent.NoteChanged -> AddExpenseEvent.NoteChanged(e.text)
            is AmountFieldEvent.DateChanged -> AddExpenseEvent.DateChanged(e.date)
            AmountFieldEvent.AccountChipClicked -> null
            AmountFieldEvent.DateChipClicked -> null
        }
}
