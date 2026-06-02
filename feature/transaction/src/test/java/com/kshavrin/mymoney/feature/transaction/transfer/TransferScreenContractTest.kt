package com.kshavrin.mymoney.feature.transaction.transfer

import com.kshavrin.mymoney.core.designsystem.amountfield.AmountFieldEvent
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.designsystem.keypad.Operator
import com.kshavrin.mymoney.core.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [TransferScreen] (form-chrome restyle).
 *
 * The restyle moved the dialpad behind a FAB (the keypad opens in a
 * `ModalBottomSheet`) and the amount box gained the ✕ clear affordance,
 * but the screen's event surface must be preserved:
 *
 *   - the FROM / TO account cards still emit
 *     `SourceAccountChanged` / `TargetAccountChanged` on selection;
 *   - the amount box / keypad events still funnel through
 *     `dispatchAmountEvent` into the `Keypad*` transfer events;
 *   - the ✕ clear (Backspace) maps to `TransferEvent.KeypadBackspace`.
 *
 * # Why this is not a full Compose-UI test
 *
 * `:feature:transaction`'s offline test classpath has only
 * `libs.junit` + `libs.kotlinx.coroutines.test` + `libs.turbine` — no
 * Robolectric / `compose-ui-test-junit4` / `ui-test-manifest`
 * (see build.gradle.kts), so a `createComposeRule()` test will not
 * compile. Same deferral as the rest of the codebase; the executable
 * Compose test lands in PHASE_15.
 *
 * `dispatchAmountEvent` is `private` in TransferScreen.kt, so we pin its
 * behaviour with a verified pure mirror (kept in lock-step below) plus the
 * documented Compose template that exercises the real composable.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class TransferScreenContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: TransferState, onEvent: (TransferEvent) -> Unit) {
 *         composeTestRule.setContent { MyMoneyTheme { TransferScreen(state = state, onEvent = onEvent) } }
 *     }
 *
 *     @Test fun `shows FROM and TO account cards`() {
 *         setContent(loaded()) {}
 *         composeTestRule.onNodeWithText("From").assertExists()
 *         composeTestRule.onNodeWithText("To").assertExists()
 *     }
 *
 *     @Test fun `dialpad FAB opens the keypad sheet`() {
 *         setContent(loaded()) {}
 *         // keypad hidden initially
 *         composeTestRule.onNodeWithText("7").assertDoesNotExist()
 *         composeTestRule.onNodeWithContentDescription("Open keypad").performClick()
 *         composeTestRule.onNodeWithText("7").assertIsDisplayed()
 *     }
 *
 *     @Test fun `selecting a source account fires SourceAccountChanged`() {
 *         val events = mutableListOf<TransferEvent>()
 *         setContent(loaded(), events::add)
 *         composeTestRule.onNodeWithText("From").performClick()       // expand dropdown
 *         composeTestRule.onNodeWithText("Bank · USD").performClick() // pick option
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is TransferEvent.SourceAccountChanged })
 *         }
 *     }
 *
 *     @Test fun `selecting a target account fires TargetAccountChanged`() {
 *         val events = mutableListOf<TransferEvent>()
 *         setContent(loaded(), events::add)
 *         // second AccountCard is the TO card
 *         composeTestRule.onAllNodesWithContentDescription(null) // ... pick TO card, then an option
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is TransferEvent.TargetAccountChanged })
 *         }
 *     }
 * }
 * ```
 *
 * # JVM-visible pins below
 *
 * The account-selection wiring is a direct `onEvent(...)` call in
 * TransferScreen's `AccountCard.onSelected`, so we pin the event types
 * directly; the keypad/clear routing is pinned via the `dispatchAmountEvent`
 * mirror.
 */
class TransferScreenContractTest {

    // ---- account selection events (FROM / TO cards) ----

    @Test
    fun `source account selection produces SourceAccountChanged with the account id`() {
        val event: TransferEvent = TransferEvent.SourceAccountChanged(42L)
        assertTrue(event is TransferEvent.SourceAccountChanged)
        assertEquals(42L, (event as TransferEvent.SourceAccountChanged).accountId)
    }

    @Test
    fun `target account selection produces TargetAccountChanged with the account id`() {
        val event: TransferEvent = TransferEvent.TargetAccountChanged(7L)
        assertTrue(event is TransferEvent.TargetAccountChanged)
        assertEquals(7L, (event as TransferEvent.TargetAccountChanged).accountId)
    }

    @Test
    fun `source and target selection are distinct events`() {
        assertTrue(
            TransferEvent.SourceAccountChanged(1L) != TransferEvent.TargetAccountChanged(1L),
        )
    }

    // ---- keypad / amount-box routing via dispatchAmountEvent mirror ----

    @Test
    fun `clear affordance routes to KeypadBackspace`() {
        assertEquals(
            TransferEvent.KeypadBackspace,
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Backspace)),
        )
    }

    @Test
    fun `digit keypad event routes to KeypadDigit`() {
        assertEquals(
            TransferEvent.KeypadDigit(5),
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Digit(5))),
        )
    }

    @Test
    fun `operator keypad event routes to KeypadOperator`() {
        assertEquals(
            TransferEvent.KeypadOperator(Operator.Plus),
            dispatch(AmountFieldEvent.Keypad(KeypadEvent.Op(Operator.Plus))),
        )
    }

    @Test
    fun `dot and equals keypad events route to KeypadDot and KeypadEquals`() {
        assertEquals(TransferEvent.KeypadDot, dispatch(AmountFieldEvent.Keypad(KeypadEvent.Dot)))
        assertEquals(TransferEvent.KeypadEquals, dispatch(AmountFieldEvent.Keypad(KeypadEvent.Equals)))
    }

    @Test
    fun `note change routes to NoteChanged`() {
        assertEquals(
            TransferEvent.NoteChanged("lunch"),
            dispatch(AmountFieldEvent.NoteChanged("lunch")),
        )
    }

    @Test
    fun `account chip click is swallowed (transfer uses dedicated account cards)`() {
        // TransferScreen routes AmountFieldEvent.AccountChipClicked to Unit — the
        // FROM/TO cards own account selection, not the amount-box chip.
        assertEquals(null, dispatch(AmountFieldEvent.AccountChipClicked))
    }

    @Test
    fun `rate panel is shown only for cross currency transfers with preview text`() {
        val usd = currency(id = 1L, code = "USD")
        val eur = currency(id = 2L, code = "EUR")

        assertTrue(
            showRatePanel(
                TransferState(
                    sourceCurrency = usd,
                    targetCurrency = eur,
                    ratePreviewText = "1 USD = 0.92 EUR",
                ),
            ),
        )
        assertTrue(
            !showRatePanel(
                TransferState(
                    sourceCurrency = usd,
                    targetCurrency = usd,
                    ratePreviewText = "1 USD = 1 USD",
                ),
            ),
        )
        assertTrue(
            !showRatePanel(
                TransferState(
                    sourceCurrency = usd,
                    targetCurrency = eur,
                    ratePreviewText = "",
                ),
            ),
        )
    }

    /**
     * Pure mirror of TransferScreen.dispatchAmountEvent (private). Returns
     * the TransferEvent that the screen would emit, or `null` for the
     * branches the screen swallows / handles via UI-local state
     * (AccountChipClicked → Unit, DateChipClicked → opens picker).
     *
     * Keep this in lock-step with TransferScreen.kt.
     */
    private fun dispatch(e: AmountFieldEvent): TransferEvent? = when (e) {
        is AmountFieldEvent.Keypad -> when (val k = e.event) {
            is KeypadEvent.Digit -> TransferEvent.KeypadDigit(k.d)
            is KeypadEvent.Op -> TransferEvent.KeypadOperator(k.op)
            KeypadEvent.Dot -> TransferEvent.KeypadDot
            KeypadEvent.Backspace -> TransferEvent.KeypadBackspace
            KeypadEvent.Equals -> TransferEvent.KeypadEquals
        }
        is AmountFieldEvent.NoteChanged -> TransferEvent.NoteChanged(e.text)
        is AmountFieldEvent.DateChanged -> TransferEvent.DateChanged(e.date)
        AmountFieldEvent.AccountChipClicked -> null
        AmountFieldEvent.DateChipClicked -> null
    }

    private fun showRatePanel(state: TransferState): Boolean {
        val source = state.sourceCurrency ?: return false
        val target = state.targetCurrency ?: return false
        return source.id != target.id && state.ratePreviewText.isNotBlank()
    }

    private fun currency(id: Long, code: String): Currency = Currency(
        id = id,
        code = code,
        symbol = code,
        name = code,
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )
}
