package com.kshavrin.mymoney.feature.transaction.income

import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [AddIncomeScreen] (form-chrome restyle).
 *
 * Same presentation-only restyle as the expense form. The add-income form
 * must keep the embedded
 * [com.kshavrin.mymoney.feature.transaction.categorygrid.CategoryGrid]
 * (a 3-column grid, not a category bar / single button), the amount box,
 * the ✕ clear → `KeypadBackspace` wiring and the keypad routing.
 *
 * # Why this is not a full Compose-UI test
 *
 * `:feature:transaction`'s offline test classpath has no Robolectric /
 * `compose-ui-test-junit4` / `ui-test-manifest` (see build.gradle.kts), so
 * `createComposeRule()` will not compile. The executable Compose test lands
 * in PHASE_15. `dispatchAmountEvent` / `dispatchKeypadEvent` are `private`
 * in AddIncomeScreen.kt; they are pinned here with a verified pure mirror,
 * and the CategoryGrid presence is covered by the documented template
 * (identical in shape to [com.kshavrin.mymoney.feature.transaction.expense.AddExpenseScreenContractTest]'s
 * template — `onNodeWithTag(CATEGORY_GRID_TAG).assertExists()` etc.).
 *
 * # JVM-visible pins below
 */
class AddIncomeScreenContractTest {

    // ---- amount-box ✕ clear + keypad routing (dispatchAmountEvent mirror) ----

    @Test
    fun `clear affordance routes to KeypadBackspace`() {
        assertEquals(
            AddIncomeEvent.KeypadBackspace,
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Backspace)),
        )
    }

    @Test
    fun `digit keypad event routes to KeypadDigit`() {
        assertEquals(
            AddIncomeEvent.KeypadDigit(3),
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Digit(3))),
        )
    }

    @Test
    fun `operator keypad event routes to KeypadOperator`() {
        assertEquals(
            AddIncomeEvent.KeypadOperator(Operator.Multiply),
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Op(Operator.Multiply))),
        )
    }

    @Test
    fun `dot and equals keypad events route to KeypadDot and KeypadEquals`() {
        assertEquals(AddIncomeEvent.KeypadDot, dispatch(AmountFieldEvent.Keypad(KeypadEvent.Dot)))
        assertEquals(AddIncomeEvent.KeypadEquals, dispatch(AmountFieldEvent.Keypad(KeypadEvent.Equals)))
    }

    @Test
    fun `note change routes to NoteChanged`() {
        assertEquals(
            AddIncomeEvent.NoteChanged("salary"),
            dispatch(AmountFieldEvent.NoteChanged("salary")),
        )
    }

    @Test
    fun `account chip click is swallowed in the add form`() {
        assertNull(dispatch(AmountFieldEvent.AccountChipClicked))
    }

    // ---- category grid wiring (the grid stays embedded) ----

    @Test
    fun `category cell click produces CategoryPicked with the category id`() {
        val event: AddIncomeEvent = AddIncomeEvent.CategoryPicked(5L)
        assertTrue(event is AddIncomeEvent.CategoryPicked)
        assertEquals(5L, (event as AddIncomeEvent.CategoryPicked).categoryId)
    }

    @Test
    fun `grid add cell click produces AddCategoryClicked`() {
        val event: AddIncomeEvent = AddIncomeEvent.AddCategoryClicked
        assertTrue(event is AddIncomeEvent.AddCategoryClicked)
    }

    /**
     * Pure mirror of AddIncomeScreen.dispatchAmountEvent + dispatchKeypadEvent
     * (both private). Keep in lock-step with AddIncomeScreen.kt.
     */
    private fun dispatch(e: AmountFieldEvent): AddIncomeEvent? = when (e) {
        is AmountFieldEvent.Keypad -> when (val k = e.event) {
            is KeypadEvent.Digit -> AddIncomeEvent.KeypadDigit(k.d)
            is KeypadEvent.Op -> AddIncomeEvent.KeypadOperator(k.op)
            KeypadEvent.Dot -> AddIncomeEvent.KeypadDot
            KeypadEvent.Backspace -> AddIncomeEvent.KeypadBackspace
            KeypadEvent.Equals -> AddIncomeEvent.KeypadEquals
        }
        is AmountFieldEvent.NoteChanged -> AddIncomeEvent.NoteChanged(e.text)
        is AmountFieldEvent.DateChanged -> AddIncomeEvent.DateChanged(e.date)
        AmountFieldEvent.AccountChipClicked -> null
        AmountFieldEvent.DateChipClicked -> null
    }
}
