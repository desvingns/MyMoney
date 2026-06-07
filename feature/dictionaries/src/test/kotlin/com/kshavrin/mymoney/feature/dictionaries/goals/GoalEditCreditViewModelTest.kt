package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.model.LoanGoalInput
import com.kshavrin.mymoney.core.domain.usecase.ContributionCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalLoanCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalSavingsProjector
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeGoalRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class GoalEditCreditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private val savingsProjector = GoalSavingsProjector()
    private val loanCalculator = GoalLoanCalculator()

    private val now: Instant = Instant.parse("2026-06-06T10:00:00Z")

    @Before
    fun setUp() {
        goalRepo = FakeGoalRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
    }

    private fun rubCurrency(id: Long = 1L) = Currency(
        id = id,
        code = "RUB",
        symbol = "₽",
        name = "Russian Ruble",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 0,
    )

    private fun account(
        id: Long,
        name: String = "Account $id",
        currencyId: Long = 1L,
        initialBalance: BigDecimal = BigDecimal.ZERO,
    ) = Account(
        id = id,
        name = name,
        currencyId = currencyId,
        initialBalance = initialBalance,
        type = AccountType.Cash,
        colorHex = "#4A8FCB",
        iconKey = "ic_account_wallet",
        isDefault = false,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
        isArchived = false,
    )

    private fun buildViewModel(goalId: Long = -1L): GoalEditViewModel =
        GoalEditViewModel(
            goalRepository = goalRepo,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            savingsProjector = savingsProjector,
            loanCalculator = loanCalculator,
            contributionCalculator = ContributionCalculator(),
            savedStateHandle = SavedStateHandle(mapOf("id" to goalId)),
        )

    // ── Variant toggle ───────────────────────────────────────────────────────────

    @Test
    fun `switching to CREDIT variant updates state to CREDIT`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(GoalVariant.CREDIT, state.variant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching back to SAVINGS clears annualRatePercent and termDate`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(
            GoalEditEvent.TermDateChanged(LocalDate.now().plusMonths(24)),
        )
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.SAVINGS))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(GoalVariant.SAVINGS, state.variant)
            assertTrue(
                "switching to SAVINGS must clear rate field",
                state.annualRatePercent.isBlank(),
            )
            assertNull("switching to SAVINGS must clear termDate", state.termDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── loanProjection computation delegates to the real GoalLoanCalculator ──────

    @Test
    fun `CREDIT with valid inputs produces a loanProjection matching GoalLoanCalculator directly`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val termDate = LocalDate.now().plusMonths(24)
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("1000000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("100000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("45000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull("loanProjection must be computed when all credit fields are set", projection)

            val termMonths = java.time.temporal.ChronoUnit.MONTHS.between(
                LocalDate.now(), termDate,
            ).toInt()
            val expected = loanCalculator(
                LoanGoalInput(
                    targetAmount = BigDecimal("1000000"),
                    startingCapital = BigDecimal("100000"),
                    annualRatePercent = BigDecimal("12"),
                    termMonths = termMonths,
                    monthlyContribution = BigDecimal("45000"),
                ),
            )

            assertEquals(0, projection!!.baseMonthlyPayment.compareTo(expected.baseMonthlyPayment))
            assertEquals(0, projection.totalInterest.compareTo(expected.totalInterest))
            assertEquals(0, projection.totalPaid.compareTo(expected.totalPaid))
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── underfunded flag ─────────────────────────────────────────────────────────

    @Test
    fun `loanProjection is underfunded when monthly is below the required annuity payment`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val termDate = LocalDate.now().plusMonths(12)
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("1000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull(projection)
            assertTrue("monthly below payment → underfunded must be true", projection!!.underfunded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loanProjection is not underfunded when monthly matches required annuity payment`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val termDate = LocalDate.now().plusMonths(12)
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("0"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("10000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull(projection)
            assertFalse(
                "monthly equal to payment → underfunded must be false",
                projection!!.underfunded,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── overpayment reduces total interest vs no-overpayment baseline ───────────

    @Test
    fun `overpayment reduces total interest compared to the no-overpayment baseline`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val termDate = LocalDate.now().plusMonths(12)
        val baseInput = LoanGoalInput(
            targetAmount = BigDecimal("120000"),
            startingCapital = BigDecimal.ZERO,
            annualRatePercent = BigDecimal("12"),
            termMonths = 12,
            monthlyContribution = BigDecimal.ZERO,
        )
        val basePayment = loanCalculator(baseInput).baseMonthlyPayment
        val overpaymentAmount = basePayment.add(BigDecimal("1000"))

        val viewModel = buildViewModel()
        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged(overpaymentAmount.toPlainString()))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull(projection)
            assertFalse("overpayment must not be underfunded", projection!!.underfunded)
            assertTrue("overpayment must be flagged", projection.overpaymentApplied)
            assertTrue(
                "overpayment reduces total interest vs baseline",
                projection.interestSavedVsBaseline.signum() > 0,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── termDate ≤ today disables Save ──────────────────────────────────────────

    @Test
    fun `canSave is false when termDate is today`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(LocalDate.now()))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertFalse(
                "termDate = today produces termMonths = 0, Save must be disabled",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is false when termDate is in the past`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(LocalDate.now().minusDays(1)))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertFalse(
                "termDate in the past produces termMonths < 1, Save must be disabled",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is true when termDate is at least one month in the future`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(LocalDate.now().plusMonths(1)))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue(
                "termDate one month out gives termMonths = 1, Save must be enabled",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is false when CREDIT variant is selected but termDate is null`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertFalse(
                "CREDIT with no termDate must disable Save (termMonths is null)",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loanProjection is null when termDate is null`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(
                "loanProjection must be null without a termDate",
                state.loanProjection,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SaveClicked upserts a CREDIT Goal ────────────────────────────────────────

    @Test
    fun `SaveClicked upserts a CREDIT Goal with rate and termDate then emits NavigateBack`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Main"))

        val termDate = LocalDate.now().plusMonths(24)
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.NameChanged("Mortgage"))
        viewModel.onEvent(GoalEditEvent.IconSelected("ic_goal_home"))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("3000000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("500000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("50000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("9.5"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.SaveClicked)

            val action = awaitItem()
            assertEquals(GoalEditAction.NavigateBack, action)

            val upserted = goalRepo.lastUpserted
            assertNotNull("goal must have been upserted", upserted)
            assertEquals("Mortgage", upserted!!.name)
            assertEquals("ic_goal_home", upserted.iconKey)
            assertEquals(GoalVariant.CREDIT, upserted.variant)
            assertEquals(1L, upserted.accountId)
            assertEquals(0, upserted.targetAmount.compareTo(BigDecimal("3000000")))
            assertEquals(0, upserted.startingCapital.compareTo(BigDecimal("500000")))
            assertEquals(0, upserted.monthlyContribution.compareTo(BigDecimal("50000")))
            assertNotNull("annualRatePercent must be set for CREDIT variant", upserted.annualRatePercent)
            assertEquals(0, upserted.annualRatePercent!!.compareTo(BigDecimal("9.5")))
            assertEquals(termDate, upserted.termDate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SaveClicked does not upsert when canSave is false`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))

        viewModel.onEvent(GoalEditEvent.SaveClicked)

        val upserted = goalRepo.lastUpserted
        assertNull("SaveClicked with canSave=false must not upsert", upserted)
    }

    @Test
    fun `SaveClicked CREDIT stores zero annualRatePercent when rate field is blank`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val termDate = LocalDate.now().plusMonths(12)
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("10000"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            awaitItem()

            val upserted = goalRepo.lastUpserted
            assertNotNull(upserted)
            assertNotNull(
                "CREDIT goal must always have annualRatePercent (zero when blank)",
                upserted!!.annualRatePercent,
            )
            assertEquals(
                "blank rate field must save as BigDecimal.ZERO",
                0,
                upserted.annualRatePercent!!.compareTo(BigDecimal.ZERO),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Formatted projection fields ──────────────────────────────────────────────

    @Test
    fun `loanProjectionMonthlyPaymentFormatted is non-null when loanProjection is present`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val termDate = LocalDate.now().plusMonths(12)
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("20000"))
        viewModel.onEvent(GoalEditEvent.TermDateChanged(termDate))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNotNull("monthly payment formatted string must be set", state.loanProjectionMonthlyPaymentFormatted)
            assertNotNull("total interest formatted string must be set", state.loanProjectionTotalInterestFormatted)
            assertNotNull("total paid formatted string must be set", state.loanProjectionTotalPaidFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loanProjection formatted fields are null when termDate is absent`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("200000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(state.loanProjectionMonthlyPaymentFormatted)
            assertNull(state.loanProjectionTotalInterestFormatted)
            assertNull(state.loanProjectionTotalPaidFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Pre-fill existing CREDIT goal ────────────────────────────────────────────

    @Test
    fun `editing an existing CREDIT goal pre-fills rate and termDate into state`() = runTest {
        currencyRepo.seed(rubCurrency())
        val termDate = LocalDate.now().plusMonths(36)
        val existing = Goal(
            id = 7L,
            name = "Car loan",
            iconKey = "ic_goal_car",
            colorHex = "#9C5BB8",
            accountId = 1L,
            variant = GoalVariant.CREDIT,
            targetAmount = BigDecimal("800000"),
            startingCapital = BigDecimal("200000"),
            monthlyContribution = BigDecimal("20000"),
            annualRatePercent = BigDecimal("8.5"),
            termDate = termDate,
            createdAt = now,
            updatedAt = now,
            isArchived = false,
        )
        goalRepo.seed(listOf(existing))
        accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Bank"))

        val viewModel = buildViewModel(goalId = 7L)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(GoalVariant.CREDIT, state.variant)
            assertEquals("8.5", state.annualRatePercent)
            assertEquals(termDate, state.termDate)
            assertEquals(false, state.isCreateMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
