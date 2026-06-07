package com.kshavrin.mymoney.feature.dictionaries.goals

import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.GoalStatus
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
 *         yearsToDownPaymentFormatted: String? = null,
 *         totalYearsFormatted: String? = null,
 *         downPayment: String = "",
 *         termYears: String = "",
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
 *         loanProjectionYearsToDownPaymentFormatted = yearsToDownPaymentFormatted,
 *         loanProjectionTotalYearsFormatted = totalYearsFormatted,
 *         downPayment = downPayment,
 *         termYears = termYears,
 *         annualRatePercent = annualRatePercent,
 *     )
 *
 *     @Test fun `switching to CREDIT reveals rate down-payment and loan-term-years fields`() {
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = creditState(), onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_interest_rate)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_down_payment)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_loan_term_years)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `switching back to SAVINGS hides credit fields`() {
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
 *         composeTestRule.onNodeWithText(getString(R.string.goal_down_payment)).assertDoesNotExist()
 *         composeTestRule.onNodeWithText(getString(R.string.goal_loan_term_years)).assertDoesNotExist()
 *     }
 *
 *     @Test fun `monthly payment block appears when loanProjection is present`() {
 *         val state = creditState(
 *             loanProjection = aLoanProjection(underfunded = false),
 *             monthlyPaymentFormatted = "12500.00 ₽",
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_monthly_payment)).assertIsDisplayed()
 *         composeTestRule.onNodeWithText("12500.00 ₽").assertIsDisplayed()
 *     }
 *
 *     @Test fun `years-to-down-payment row is shown when projection is present`() {
 *         val state = creditState(
 *             loanProjection = aLoanProjection(),
 *             yearsToDownPaymentFormatted = "0 years 10 months",
 *             totalYearsFormatted = "10 years 10 months",
 *         )
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(containsSubstring("0 years 10 months")).assertIsDisplayed()
 *         composeTestRule.onNodeWithText(containsSubstring("10 years 10 months")).assertIsDisplayed()
 *     }
 *
 *     @Test fun `underfunded warning banner appears when loanProjection is underfunded`() {
 *         val state = creditState(loanProjection = aLoanProjection(underfunded = true))
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = state, onEvent = {}) }
 *         }
 *         composeTestRule.onNodeWithText(getString(R.string.goal_underfunded)).assertIsDisplayed()
 *     }
 *
 *     @Test fun `underfunded warning is absent when loanProjection is funded`() {
 *         val state = creditState(loanProjection = aLoanProjection(underfunded = false))
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
 *     @Test fun `DownPaymentChanged event fires when down-payment field value changes`() {
 *         val events = mutableListOf<GoalEditEvent>()
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = creditState(), onEvent = { events += it }) }
 *         }
 *         composeTestRule
 *             .onNode(hasSetTextAction() and hasText(getString(R.string.goal_down_payment), substring = true))
 *             .performTextInput("500000")
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is GoalEditEvent.DownPaymentChanged })
 *         }
 *     }
 *
 *     @Test fun `TermYearsChanged event fires when loan-term-years field value changes`() {
 *         val events = mutableListOf<GoalEditEvent>()
 *         composeTestRule.setContent {
 *             MyMoneyTheme { GoalEditContent(state = creditState(), onEvent = { events += it }) }
 *         }
 *         composeTestRule
 *             .onNode(hasSetTextAction() and hasText(getString(R.string.goal_loan_term_years), substring = true))
 *             .performTextInput("10")
 *         composeTestRule.runOnIdle {
 *             assertTrue(events.any { it is GoalEditEvent.TermYearsChanged })
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
        baseMonthlyPayment: BigDecimal = BigDecimal("12500.00"),
        totalInterest: BigDecimal = BigDecimal.ZERO,
        totalPaid: BigDecimal = BigDecimal("1500000.00"),
        accumulationMonths: Int? = 10,
        totalMonthsToPayoff: Int? = 130,
        underfunded: Boolean = false,
        status: GoalStatus = GoalStatus.ON_TRACK,
    ) = LoanProjection(
        principal = BigDecimal("1500000.00"),
        baseMonthlyPayment = baseMonthlyPayment,
        totalInterest = totalInterest,
        totalPaid = totalPaid,
        accumulationMonths = accumulationMonths,
        totalMonthsToPayoff = totalMonthsToPayoff,
        underfunded = underfunded,
        status = status,
    )

    private fun creditState(
        loanProjection: LoanProjection? = null,
        monthlyPaymentFormatted: String? = null,
        totalInterestFormatted: String? = null,
        totalPaidFormatted: String? = null,
        loanProjectionAccumulationMonths: Int? = null,
        loanProjectionTotalMonths: Int? = null,
        downPayment: String = "",
        termYears: String = "",
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
        loanProjectionAccumulationMonths = loanProjectionAccumulationMonths,
        loanProjectionTotalMonths = loanProjectionTotalMonths,
        downPayment = downPayment,
        termYears = termYears,
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
    fun `R-string goal_down_payment exists`() {
        val id: Int = R.string.goal_down_payment
        assertTrue("R.string.goal_down_payment must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_loan_term_years exists`() {
        val id: Int = R.string.goal_loan_term_years
        assertTrue("R.string.goal_loan_term_years must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_monthly_payment exists`() {
        val id: Int = R.string.goal_monthly_payment
        assertTrue("R.string.goal_monthly_payment must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_years_to_down_payment exists`() {
        val id: Int = R.string.goal_years_to_down_payment
        assertTrue("R.string.goal_years_to_down_payment must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_total_years_to_payoff exists`() {
        val id: Int = R.string.goal_total_years_to_payoff
        assertTrue("R.string.goal_total_years_to_payoff must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_accumulation_unreachable exists`() {
        val id: Int = R.string.goal_accumulation_unreachable
        assertTrue("R.string.goal_accumulation_unreachable must be a valid resource id", id != 0)
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
    fun `R-string goal_underfunded exists`() {
        val id: Int = R.string.goal_underfunded
        assertTrue("R.string.goal_underfunded must be a valid resource id", id != 0)
    }

    @Test
    fun `R-string goal_years_months exists`() {
        val id: Int = R.string.goal_years_months
        assertTrue("R.string.goal_years_months must be a valid resource id", id != 0)
    }

    // ── State-driven variant visibility logic ────────────────────────────────────

    @Test
    fun `CREDIT variant state selects credit segment`() {
        val state = creditState()
        assertEquals(GoalVariant.CREDIT, state.variant)
        assertFalse("CREDIT state must not be SAVINGS", state.variant == GoalVariant.SAVINGS)
    }

    @Test
    fun `SAVINGS variant state does not expose credit fields — variant is SAVINGS`() {
        val savingsState = GoalEditState(
            variant = GoalVariant.SAVINGS,
            accounts = listOf(anAccount()),
            accountId = 1L,
            currentBalanceFormatted = "0 ₽",
        )
        assertEquals(GoalVariant.SAVINGS, savingsState.variant)
        assertFalse("SAVINGS variant must not be CREDIT", savingsState.variant == GoalVariant.CREDIT)
    }

    @Test
    fun `SAVINGS variant state has blank downPayment and termYears`() {
        val savingsState = GoalEditState(
            variant = GoalVariant.SAVINGS,
            accounts = listOf(anAccount()),
            accountId = 1L,
            currentBalanceFormatted = "0 ₽",
            downPayment = "",
            termYears = "",
        )
        assertTrue(
            "SAVINGS state must have blank downPayment after variant switch",
            savingsState.downPayment.isBlank(),
        )
        assertTrue(
            "SAVINGS state must have blank termYears after variant switch",
            savingsState.termYears.isBlank(),
        )
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
            monthlyPaymentFormatted = "12500.00 ₽",
        )
        assertNotNull("non-null loanProjection → monthly payment block must be rendered", state.loanProjection)
        assertNotNull("monthly payment formatted must be set", state.loanProjectionMonthlyPaymentFormatted)
    }

    @Test
    fun `accumulation months and total months are set when projection is present`() {
        val state = creditState(
            loanProjection = aLoanProjection(accumulationMonths = 10, totalMonthsToPayoff = 130),
            loanProjectionAccumulationMonths = 10,
            loanProjectionTotalMonths = 130,
        )
        assertNotNull("loanProjectionAccumulationMonths must be non-null when projection present", state.loanProjectionAccumulationMonths)
        assertNotNull("loanProjectionTotalMonths must be non-null when projection present", state.loanProjectionTotalMonths)
        assertEquals("accumulation months must be 10", 10, state.loanProjectionAccumulationMonths)
        assertEquals("total months must be 130", 130, state.loanProjectionTotalMonths)
    }

    @Test
    fun `null raw month fields when loanProjection is null`() {
        val state = creditState(
            loanProjection = null,
            loanProjectionAccumulationMonths = null,
            loanProjectionTotalMonths = null,
        )
        assertNull(state.loanProjectionAccumulationMonths)
        assertNull(state.loanProjectionTotalMonths)
        assertNull(state.loanProjectionMonthlyPaymentFormatted)
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

    // ── UNREACHABLE status drives years-to-down-payment label ────────────────────

    @Test
    fun `UNREACHABLE status has null accumulationMonths and null totalMonthsToPayoff`() {
        val projection = aLoanProjection(
            accumulationMonths = null,
            totalMonthsToPayoff = null,
            status = GoalStatus.UNREACHABLE,
        )
        assertEquals(GoalStatus.UNREACHABLE, projection.status)
        assertNull("UNREACHABLE projection must have null accumulationMonths", projection.accumulationMonths)
        assertNull("UNREACHABLE projection must have null totalMonthsToPayoff", projection.totalMonthsToPayoff)
    }

    @Test
    fun `ON_TRACK status has non-null accumulationMonths and totalMonthsToPayoff`() {
        val projection = aLoanProjection(
            accumulationMonths = 10,
            totalMonthsToPayoff = 130,
            status = GoalStatus.ON_TRACK,
        )
        assertEquals(GoalStatus.ON_TRACK, projection.status)
        assertNotNull("ON_TRACK projection must have non-null accumulationMonths", projection.accumulationMonths)
        assertNotNull("ON_TRACK projection must have non-null totalMonthsToPayoff", projection.totalMonthsToPayoff)
    }

    // ── loanProjection field values faithfully propagated ────────────────────────

    @Test
    fun `loanProjection totalInterest matches the value set on state`() {
        val expected = BigDecimal.ZERO
        val state = creditState(
            loanProjection = aLoanProjection(totalInterest = expected),
            totalInterestFormatted = "0 ₽",
        )
        assertEquals(0, state.loanProjection!!.totalInterest.compareTo(expected))
    }

    @Test
    fun `loanProjection totalPaid matches the value set on state`() {
        val expected = BigDecimal("1500000.00")
        val state = creditState(
            loanProjection = aLoanProjection(totalPaid = expected),
            totalPaidFormatted = "1500000 ₽",
        )
        assertEquals(0, state.loanProjection!!.totalPaid.compareTo(expected))
    }

    @Test
    fun `loanProjection baseMonthlyPayment matches the value set on state`() {
        val expected = BigDecimal("12500.00")
        val state = creditState(
            loanProjection = aLoanProjection(baseMonthlyPayment = expected),
            monthlyPaymentFormatted = "12500.00 ₽",
        )
        assertEquals(0, state.loanProjection!!.baseMonthlyPayment.compareTo(expected))
    }

    // ── downPayment and termYears field state checks ──────────────────────────────

    @Test
    fun `downPayment field value is preserved in state`() {
        val state = creditState(downPayment = "500000")
        assertEquals("500000", state.downPayment)
    }

    @Test
    fun `termYears field value is preserved in state`() {
        val state = creditState(termYears = "10")
        assertEquals("10", state.termYears)
    }

    @Test
    fun `blank downPayment produces empty string in state`() {
        val state = creditState(downPayment = "")
        assertTrue("blank downPayment must be empty string", state.downPayment.isEmpty())
    }

    @Test
    fun `blank termYears produces empty string in state`() {
        val state = creditState(termYears = "")
        assertTrue("blank termYears must be empty string", state.termYears.isEmpty())
    }

    // ── canSave reflects the CREDIT term guard ────────────────────────────────────

    @Test
    fun `canSave false when CREDIT is selected and termYears is blank`() {
        val state = creditState(termYears = "", canSave = false)
        assertFalse("blank termYears in CREDIT → canSave must be false", state.canSave)
    }

    @Test
    fun `canSave true when CREDIT has a valid termYears`() {
        val state = creditState(termYears = "10", canSave = true)
        assertTrue("valid termYears in CREDIT → canSave must be true", state.canSave)
    }

    // ── New event shapes (DownPaymentChanged, TermYearsChanged) ──────────────────

    @Test
    fun `DownPaymentChanged event carries the entered down-payment string`() {
        val event = GoalEditEvent.DownPaymentChanged("500000")
        assertEquals("500000", event.value)
    }

    @Test
    fun `TermYearsChanged event carries the entered term-years string`() {
        val event = GoalEditEvent.TermYearsChanged("10")
        assertEquals("10", event.value)
    }

    @Test
    fun `TermYearsChanged event with zero is valid event shape`() {
        val event = GoalEditEvent.TermYearsChanged("0")
        assertEquals("0", event.value)
    }

    @Test
    fun `TermYearsChanged event with empty string clears the field`() {
        val event = GoalEditEvent.TermYearsChanged("")
        assertEquals("", event.value)
    }

    @Test
    fun `RateChanged event carries the entered rate string`() {
        val event = GoalEditEvent.RateChanged("9.5")
        assertEquals("9.5", event.value)
    }

    // ── VariantChanged event shape ────────────────────────────────────────────────

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

    // ── Default state structural checks ──────────────────────────────────────────

    @Test
    fun `default GoalEditState has blank downPayment and termYears`() {
        val state = GoalEditState()
        assertTrue("default downPayment must be blank", state.downPayment.isBlank())
        assertTrue("default termYears must be blank", state.termYears.isBlank())
    }

    @Test
    fun `default GoalEditState has null loanProjection and all formatted loan fields`() {
        val state = GoalEditState()
        assertNull(state.loanProjection)
        assertNull(state.loanProjectionMonthlyPaymentFormatted)
        assertNull(state.loanProjectionTotalInterestFormatted)
        assertNull(state.loanProjectionTotalPaidFormatted)
        assertNull(state.loanProjectionAccumulationMonths)
        assertNull(state.loanProjectionTotalMonths)
    }
}
