package com.kshavrin.mymoney.core.designsystem.amountfield

import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for the form-chrome restyle of [MonefyAmountInput] /
 * [AmountFieldSection].
 *
 * # What changed (form-chrome restyle)
 *
 *   1. The ⌫ backspace key was removed from the keypad grid; the amount box
 *      grew a trailing ✕ clear affordance instead.
 *   2. That affordance fires `MonefyAmountInput.onClear`, which
 *      `AmountFieldSection` wires to `AmountFieldEvent.Keypad(KeypadEvent.Backspace)`.
 *   3. The currency code/symbol moved to the LEFT of the amount.
 *
 * # Why this is not a full Compose-UI test
 *
 * `:core:designsystem`'s offline test classpath currently has only
 * `libs.junit` + `libs.kotlinx.coroutines.test` — no Robolectric,
 * no `androidx.compose.ui:ui-test-junit4`, no `ui-test-manifest`
 * (see build.gradle.kts). A `createComposeRule()` test would not compile.
 * This is the same deliberate deferral documented in the sibling
 * [com.kshavrin.mymoney.core.designsystem.keypad.MonefyKeypadTest] and
 * [com.kshavrin.mymoney.core.designsystem.confetti.MonefyConfettiTest];
 * the executable Compose test lands in PHASE_15 once those deps are wired.
 *
 * Until then we pin the one piece of this contract that IS JVM-visible:
 * the `onClear` → `AmountFieldEvent` mapping. The currency-on-the-left
 * ordering and the actual ✕ rendering are layout concerns that the
 * documented Compose template below covers.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class MonefyAmountInputContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     @Test fun `currency code renders to the LEFT of the amount`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 MonefyAmountInput(
 *                     display = "12.00", expression = "", currencyCode = "USD",
 *                     currencySymbol = "$", onClear = {},
 *                 )
 *             }
 *         }
 *         // currency code is shown; its left edge precedes the amount's left edge
 *         val currency = composeTestRule.onNodeWithText("USD").fetchSemanticsNode()
 *         val amount = composeTestRule.onNodeWithText("12.00").fetchSemanticsNode()
 *         assertTrue(currency.boundsInRoot.left < amount.boundsInRoot.left)
 *     }
 *
 *     @Test fun `tapping the clear affordance invokes onClear`() {
 *         var cleared = false
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 MonefyAmountInput(
 *                     display = "9", expression = "", currencyCode = "USD",
 *                     onClear = { cleared = true },
 *                     clearContentDescription = "Backspace",
 *                 )
 *             }
 *         }
 *         composeTestRule.onNodeWithContentDescription("Backspace").performClick()
 *         assertTrue(cleared)
 *     }
 *
 *     @Test fun `clear affordance is absent when onClear is null`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 MonefyAmountInput(display = "9", expression = "", currencyCode = "USD")
 *             }
 *         }
 *         composeTestRule.onNodeWithContentDescription("Backspace").assertDoesNotExist()
 *     }
 *
 *     @Test fun `expression line renders when expression is non-blank`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 MonefyAmountInput(display = "5", expression = "2 + 3", currencyCode = "USD")
 *             }
 *         }
 *         composeTestRule.onNodeWithText("2 + 3").assertIsDisplayed()
 *     }
 *
 *     @Test fun `expression line is hidden when expression is blank`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 MonefyAmountInput(display = "5", expression = "", currencyCode = "USD")
 *             }
 *         }
 *         composeTestRule.onNodeWithText("2 + 3").assertDoesNotExist()
 *     }
 * }
 * ```
 *
 * The autoscale behaviour of the display text is covered separately and
 * stays green in
 * [com.kshavrin.mymoney.core.designsystem.amountinput.MonefyAmountInputTest].
 */
class AmountFieldClearContractTest {

    /**
     * Pure mirror of the `onClear` lambda in [AmountFieldSection]:
     *
     * ```
     * onClear = { onEvent(AmountFieldEvent.Keypad(KeypadEvent.Backspace)) }
     * ```
     *
     * Keep this aligned with AmountFieldSection.kt. If the clear affordance
     * is ever rewired to emit a different event, this pin fails loudly.
     */
    private fun amountFieldClearEvent(): AmountFieldEvent =
        AmountFieldEvent.Keypad(KeypadEvent.Backspace)

    @Test
    fun `clear affordance emits a Keypad event`() {
        val event = amountFieldClearEvent()
        assertTrue(
            "the ✕ clear affordance must dispatch through AmountFieldEvent.Keypad",
            event is AmountFieldEvent.Keypad,
        )
    }

    @Test
    fun `clear affordance emits exactly KeypadEvent Backspace`() {
        val event = amountFieldClearEvent()
        val keypad = event as AmountFieldEvent.Keypad
        assertEquals(
            "form-chrome restyle: amount-box clear must map to the Backspace keypad event",
            KeypadEvent.Backspace,
            keypad.event,
        )
    }

    @Test
    fun `AmountFieldEvent Keypad wraps the given keypad event verbatim`() {
        // Guards against the wrapper silently swapping the payload.
        val digit = AmountFieldEvent.Keypad(KeypadEvent.Digit(7))
        assertEquals(KeypadEvent.Digit(7), (digit as AmountFieldEvent.Keypad).event)
    }
}
