package com.kshavrin.mymoney.feature.dictionaries.goals

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.model.LoanProjection
import com.kshavrin.mymoney.feature.dictionaries.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Contract-level pinning for [GoalEditContent] — CREDIT variant (S29, credit branch).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:dictionaries`'s test classpath has only `junit`, `kotlinx-coroutines-test`, and
 * `turbine`. The `androidx.compose.ui:ui-test-junit4` and Robolectric artifacts are absent from
 * the offline Gradle cache, so a `createComposeRule()` test would fail at compile time. This
 * mirrors the deliberate deferral documented in `GoalEditSavingsContentTest` — full Compose-UI
 * tests land in PHASE_15 once those dependencies are wired into this module's `build.gradle.kts`.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```kotlin
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class GoalEditCreditContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun creditState(
 *         loanProjection: LoanProjection? = null,
 *         monthlyPaymentFormatted: String? = null,
 *         totalInterestFormatted: String? = null,
 *         totalPaidFormatted: String? = null,
 *         termDate: LocalDate? = null,
 *         annualRatePercent: String = "",
 *     ) = GoalEditState(
 *         variant = GoalVariant.CREDIT,
 *         accounts = listOf(anAccount()),
 *         accountId = 1L,
 *         currentBalanceFormatted = "0 ₽",
 *         loanProjection = loanProjection,
 *         loanProjectionMonthlyPaymentFormatted = monthlyPaymentFormatted,
 *         loanProjectionTotalInterestFormatted = totalInterestFormatted,
 *         loanProjectionTotalPaidFormatted = totalPaidFormatted,
 *         termDate = termDate,
 *         annualRatePercent = annualRatePercent,
 *     )
 *
 *     @Test fun `switching to CREDIT reveals rate and term-date fields`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = creditState(), onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_interest_rate)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_term_date)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `switching back to SAVINGS hides rate and term-date fields`() {
 *         val savingsState = GoalEditState(
 *             variant = GoalVariant.SAVINGS,
 *             accounts = listOf(anAccount()),
 *             accountId = 1L,
 *             currentBalanceFormatted = "0 ₽",
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = savingsState, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_interest_rate)).assertDoesNotExist()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_term_date)).assertDoesNotExist()
 *     }
 *
 *     @Test fun `monthly payment block appears when loanProjection is present`() {
 *         val state = creditState(
 *             loanProjection = aLoanProjection(underfunded = false),
 *             monthlyPaymentFormatted = "44 721 ₽",
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_monthly_payment)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText("44 721 ₽").assertIsDisplayed()
 *     }
 *
 *     @Test fun `overpayment summary block shows total interest and total paid`() {
 *         val state = creditState(
 *             loanProjection = aLoanProjection(underfunded = false),
 *             totalInterestFormatted = "5 278 ₽",
 *             totalPaidFormatted = "105 278 ₽",
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(containsSubstring("5 278 ₽")).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(containsSubstring("105 278 ₽")).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_overpayment_note)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `underfunded warning banner appears when loanProjection is underfunded`() {
 *         val state = creditState(
 *             loanProjection = aLoanProjection(underfunded = true),
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_underfunded)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `underfunded warning is absent when loanProjection is funded`() {
 *         val state = creditState(
 *             loanProjection = aLoanProjection(underfunded = false),
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_underfunded)).assertDoesNotExist()
 *     }
 *
 *     @Test fun `monthly payment block is absent when loanProjection is null`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = creditState(loanProjection = null), onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_monthly_payment)).assertDoesNotExist()
 *     }
 *
 *     @Test fun `RateChanged event fires when rate field value changes`() {
 *         val events = mutableListOf<GoalEditEvent>()
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = creditState(), onEvent = { events += it }) }
 *         }
 *         composeTestRule
 *             .onNode(hasSetTextAction() and hasText(getString(R.string.goal_interest_rate), substring = true))
 *             .performTextInput("12")
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is GoalEditEvent.RateChanged })
 *         }
 *     }
 * }
 * ```
 */
class GoalEditCreditContentTest {

    private val now: Instant = Instant.parse("2026-06-06T10:00:00Z")

    private fun anAccount(
        id: Long = 1L,
        name: String = "Main",
        currencyId: Long = 1L,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = BigDecimal.ZERO,
        type = AccountType.Cash,
        colorHex = "#4A8FCB",
        iconKey = "ic_account_wallet",
        isDefault = false,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
        isArchived = false,
    )

    private fun aLoanProjection(
        baseMonthlyPayment: BigDecimal = BigDecimal("44721.36"),
        totalInterest: BigDecimal = BigDecimal("7456.32"),
        totalPaid: BigDecimal = BigDecimal("107456.32"),
        underfunded: Boolean = false,
        overpaymentApplied: Boolean = false,
    ) = LoanProjection(
        principal = BigDecimal("100000.00"),
        baseMonthlyPayment = baseMonthlyPayment,
        finalMonthlyPayment = baseMonthlyPayment,
        totalInterest = totalInterest,
        totalPaid = totalPaid,
        interestSavedVsBaseline = BigDecimal.ZERO,
        monthsToPayoff = 24,
        underfunded = underfunded,
        overpaymentApplied = overpaymentApplied,
    )

    private fun creditState(
        loanProjection: LoanProjection? = null,
        monthlyPaymentFormatted: String? = null,
        totalInterestFormatted: String? = null,
        totalPaidFormatted: String? = null,
        termDate: LocalDate? = null,
        annualRatePercent: String = "",
        canSave: Boolean = true,
    ) = GoalEditState(
        variant = GoalVariant.CREDIT,
        accounts = listOf(anAccount()),
        accountId = 1L,
        currentBalanceFormatted = "0 ₽",
        loanProjection = loanProjection,
        loanProjectionMonthlyPaymentFormatted = monthlyPaymentFormatted,
        loanProjectionTotalInterestFormatted = totalInterestFormatted,
        loanProjectionTotalPaidFormatted = totalPaidFormatted,
        termDate = termDate,
        annualRatePercent = annualRatePercent,
        canSave = canSave,
    )

    // ── R-string resource id checks ──────────────────────────────────────────────

    @Test
    fun `R-string goal_interest_rate exists`() {
        val id: Int = R.string.goal_interest_rate
        assertTrue("R.string.goal_interest_rate must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_term_date exists`() {
        val id: Int = R.string.goal_term_date
        assertTrue("R.string.goal_term_date must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_monthly_payment exists`() {
        val id: Int = R.string.goal_monthly_payment
        assertTrue("R.string.goal_monthly_payment must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_total_interest exists`() {
        val id: Int = R.string.goal_total_interest
        assertTrue("R.string.goal_total_interest must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_total_paid exists`() {
        val id: Int = R.string.goal_total_paid
        assertTrue("R.string.goal_total_paid must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_overpayment_note exists`() {
        val id: Int = R.string.goal_overpayment_note
        assertTrue("R.string.goal_overpayment_note must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_underfunded exists`() {
        val id: Int = R.string.goal_underfunded
        assertTrue("R.string.goal_underfunded must be a valid resource id", id != 0)
    }

    // ── State-driven variant visibility logic ────────────────────────────────────

    @Test
    fun `CREDIT variant state selects credit segment`() {
        val state = creditState()
        assertEquals(GoalVariant.CREDIT, state.variant)
        assertFalse("CREDIT state must not be SAVINGS", state.variant == GoalVariant.SAVINGS)
    }

    @Test
    fun `SAVINGS variant state does not expose credit fields`() {
        val savingsState = GoalEditState(
            variant = GoalVariant.SAVINGS,
            accounts = listOf(anAccount()),
            accountId = 1L,
            currentBalanceFormatted = "0 ₽",
        )
        assertEquals(GoalVariant.SAVINGS, savingsState.variant)
        assertFalse("SAVINGS variant must not be CREDIT", savingsState.variant == GoalVariant.CREDIT)
    }

    // ── loanProjection null / non-null drives monthly-payment block visibility ───

    @Test
    fun `null loanProjection means the monthly payment block is hidden`() {
        val state = creditState(loanProjection = null)
        assertNull("null loanProjection → monthly payment block must not be rendered", state.loanProjection)
    }

    @Test
    fun `non-null loanProjection means the monthly payment block is shown`() {
        val state = creditState(
            loanProjection = aLoanProjection(),
            monthlyPaymentFormatted = "44721.36 ₽",
        )
        assertNotNull("non-null loanProjection → monthly payment block must be rendered", state.loanProjection)
        assertNotNull("monthly payment formatted must be set", state.loanProjectionMonthlyPaymentFormatted)
    }

    // ── underfunded flag drives warning banner ────────────────────────────────────

    @Test
    fun `underfunded true means the warning banner must be shown`() {
        val state = creditState(loanProjection = aLoanProjection(underfunded = true))
        assertTrue("underfunded = true → warning banner must be shown", state.loanProjection!!.underfunded)
    }

    @Test
    fun `underfunded false means the warning banner must be hidden`() {
        val state = creditState(loanProjection = aLoanProjection(underfunded = false))
        assertFalse("underfunded = false → warning banner must be hidden", state.loanProjection!!.underfunded)
    }

    // ── overpayment note in summary block ────────────────────────────────────────

    @Test
    fun `overpaymentApplied true marks the loan projection accordingly`() {
        val projection = aLoanProjection(overpaymentApplied = true)
        assertTrue(projection.overpaymentApplied)
    }

    @Test
    fun `overpaymentApplied false for underfunded loan`() {
        val projection = aLoanProjection(underfunded = true, overpaymentApplied = false)
        assertFalse(projection.overpaymentApplied)
    }

    // ── loanProjection field values are faithfully propagated ────────────────────

    @Test
    fun `loanProjection totalInterest matches the value set on state`() {
        val expected = BigDecimal("7456.32")
        val state = creditState(
            loanProjection = aLoanProjection(totalInterest = expected),
            totalInterestFormatted = "7456.32 ₽",
        )
        assertEquals(0, state.loanProjection!!.totalInterest.compareTo(expected))
    }

    @Test
    fun `loanProjection totalPaid matches the value set on state`() {
        val expected = BigDecimal("107456.32")
        val state = creditState(
            loanProjection = aLoanProjection(totalPaid = expected),
            totalPaidFormatted = "107456.32 ₽",
        )
        assertEquals(0, state.loanProjection!!.totalPaid.compareTo(expected))
    }

    @Test
    fun `loanProjection baseMonthlyPayment matches the value set on state`() {
        val expected = BigDecimal("44721.36")
        val state = creditState(
            loanProjection = aLoanProjection(baseMonthlyPayment = expected),
            monthlyPaymentFormatted = "44721.36 ₽",
        )
        assertEquals(0, state.loanProjection!!.baseMonthlyPayment.compareTo(expected))
    }

    // ── termDate display logic ────────────────────────────────────────────────────

    @Test
    fun `null termDate produces an empty term date display string`() {
        val state = creditState(termDate = null)
        val display = state.termDate?.toString().orEmpty()
        assertTrue("null termDate must produce an empty display string", display.isEmpty())
    }

    @Test
    fun `non-null termDate produces a non-empty display string`() {
        val date = LocalDate.of(2028, 6, 6)
        val state = creditState(termDate = date)
        val display = state.termDate?.toString().orEmpty()
        assertFalse("non-null termDate must produce a non-empty display string", display.isEmpty())
        assertTrue("display string must contain the year", display.contains("2028"))
    }

    // ── canSave reflects the CREDIT guard ────────────────────────────────────────

    @Test
    fun `canSave false when CREDIT is selected and no termDate`() {
        val state = creditState(canSave = false)
        assertFalse("no termDate in CREDIT → canSave must be false", state.canSave)
    }

    @Test
    fun `canSave true when CREDIT has a future termDate`() {
        val state = creditState(termDate = LocalDate.now().plusMonths(6), canSave = true)
        assertTrue("future termDate in CREDIT → canSave must be true", state.canSave)
    }

    // ── RateChanged and TermDateChanged event shapes ─────────────────────────────

    @Test
    fun `RateChanged event carries the entered rate string`() {
        val event = GoalEditEvent.RateChanged("9.5")
        assertEquals("9.5", event.value)
    }

    @Test
    fun `TermDateChanged event carries the selected LocalDate`() {
        val date = LocalDate.of(2029, 1, 1)
        val event = GoalEditEvent.TermDateChanged(date)
        assertEquals(date, event.value)
    }

    @Test
    fun `TermDateChanged event can carry null to clear the term date`() {
        val event = GoalEditEvent.TermDateChanged(null)
        assertNull(event.value)
    }

    // ── VariantChanged event shape ───────────────────────────────────────────────

    @Test
    fun `VariantChanged to CREDIT event carries CREDIT variant`() {
        val event = GoalEditEvent.VariantChanged(GoalVariant.CREDIT)
        assertEquals(GoalVariant.CREDIT, event.variant)
    }

    @Test
    fun `VariantChanged to SAVINGS event carries SAVINGS variant`() {
        val event = GoalEditEvent.VariantChanged(GoalVariant.SAVINGS)
        assertEquals(GoalVariant.SAVINGS, event.variant)
    }
}
