package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalStatus
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
    fun `switching back to SAVINGS clears annualRatePercent downPayment and termYears`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("500000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("10"))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.SAVINGS))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(GoalVariant.SAVINGS, state.variant)
            assertTrue(
                "switching to SAVINGS must clear rate field",
                state.annualRatePercent.isBlank(),
            )
            assertTrue(
                "switching to SAVINGS must clear downPayment field",
                state.downPayment.isBlank(),
            )
            assertTrue(
                "switching to SAVINGS must clear termYears field",
                state.termYears.isBlank(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── loanProjection computation delegates to the real GoalLoanCalculator ──────

    @Test
    fun `CREDIT with valid inputs produces a loanProjection matching GoalLoanCalculator directly`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("2000000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("50000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("500000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull("loanProjection must be computed when all credit fields are set", projection)

            val expected = loanCalculator(
                LoanGoalInput(
                    targetAmount = BigDecimal("2000000"),
                    startingCapital = BigDecimal("0"),
                    downPayment = BigDecimal("500000"),
                    annualRatePercent = BigDecimal("0"),
                    termMonths = 120,
                    monthlyContribution = BigDecimal("50000"),
                ),
            )

            assertEquals(0, projection!!.baseMonthlyPayment.compareTo(expected.baseMonthlyPayment))
            assertEquals(0, projection.totalInterest.compareTo(expected.totalInterest))
            assertEquals(0, projection.totalPaid.compareTo(expected.totalPaid))
            assertEquals(expected.accumulationMonths, projection.accumulationMonths)
            assertEquals(expected.totalMonthsToPayoff, projection.totalMonthsToPayoff)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `worked example target=2000000 downPayment=500000 rate=0 termYears=10 monthly=50000 produces expected values`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("2000000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("50000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("500000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull(projection)
            assertEquals(
                "monthly payment = 1500000 / 120 = 12500.00",
                0,
                projection!!.baseMonthlyPayment.compareTo(BigDecimal("12500.00")),
            )
            assertEquals(
                "accumulation months = ceil(500000 / 50000) = 10",
                10,
                projection.accumulationMonths,
            )
            assertEquals(
                "total months to payoff = 10 + 120 = 130",
                130,
                projection.totalMonthsToPayoff,
            )
            assertEquals(
                "total interest = 0 for zero-rate loan",
                0,
                projection.totalInterest.compareTo(BigDecimal.ZERO),
            )
            assertEquals(
                "state loanProjectionAccumulationMonths must equal projection accumulationMonths",
                10,
                state.loanProjectionAccumulationMonths,
            )
            assertEquals(
                "state loanProjectionTotalMonths must equal projection totalMonthsToPayoff",
                130,
                state.loanProjectionTotalMonths,
            )
            assertNotNull("loanProjectionMonthlyPaymentFormatted must be set", state.loanProjectionMonthlyPaymentFormatted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── termYears empty or "0" disables projection and canSave ───────────────────

    @Test
    fun `termYears empty produces null loanProjection and canSave=false`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("10"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged(""))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(
                "loanProjection must be null when termYears is empty",
                state.loanProjection,
            )
            assertFalse(
                "canSave must be false when termYears is empty",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `termYears zero produces null loanProjection and canSave=false`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("10"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("0"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(
                "loanProjection must be null when termYears is 0 (termMonths=0 < 1)",
                state.loanProjection,
            )
            assertFalse(
                "canSave must be false when termYears is 0",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── UNREACHABLE accumulation with termYears ≥ 1 still allows Save ────────────

    @Test
    fun `monthly=0 with capital less than downPayment and valid termYears produces UNREACHABLE status and canSave=true`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("2000000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("500000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.loanProjection
            assertNotNull("projection must be present even when accumulation is unreachable", projection)
            assertEquals(
                "status must be UNREACHABLE when monthly=0 and startingCapital < downPayment",
                GoalStatus.UNREACHABLE,
                projection!!.status,
            )
            assertNull(
                "accumulationMonths must be null when unreachable",
                projection.accumulationMonths,
            )
            assertNull(
                "totalMonthsToPayoff must be null when accumulation is unreachable",
                projection.totalMonthsToPayoff,
            )
            assertNull(
                "loanProjectionAccumulationMonths state field must be null when unreachable",
                state.loanProjectionAccumulationMonths,
            )
            assertNull(
                "loanProjectionTotalMonths state field must be null when accumulation is unreachable",
                state.loanProjectionTotalMonths,
            )
            assertTrue(
                "canSave must be true — Save is gated by termYears, not by reachability",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── underfunded flag ─────────────────────────────────────────────────────────

    @Test
    fun `loanProjection is underfunded when monthly is below the required annuity payment`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("1"))
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

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("0"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("1"))
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

    // ── canSave gating ────────────────────────────────────────────────────────────

    @Test
    fun `canSave is false when CREDIT variant is selected but termYears is blank`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertFalse(
                "CREDIT with no termYears must disable Save",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `canSave is true when CREDIT has valid termYears of at least 1`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("1"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue(
                "termYears=1 gives termMonths=12 >= 1, Save must be enabled",
                state.canSave,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loanProjection is null when termYears is absent`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(
                "loanProjection must be null without a termYears value",
                state.loanProjection,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Formatted projection fields ───────────────────────────────────────────────

    @Test
    fun `loanProjection formatted fields are non-null when projection is present`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("0"))
        viewModel.onEvent(GoalEditEvent.RateChanged("12"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("20000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("1"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNotNull("monthly payment formatted must be set", state.loanProjectionMonthlyPaymentFormatted)
            assertNotNull("total interest formatted must be set", state.loanProjectionTotalInterestFormatted)
            assertNotNull("total paid formatted must be set", state.loanProjectionTotalPaidFormatted)
            assertNotNull("loanProjectionAccumulationMonths must be set (downPayment=0, monthly>0)", state.loanProjectionAccumulationMonths)
            assertNotNull("loanProjectionTotalMonths must be set when projection present", state.loanProjectionTotalMonths)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loanProjection formatted fields are null when termYears is absent`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("200000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("10"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertNull(state.loanProjectionMonthlyPaymentFormatted)
            assertNull(state.loanProjectionTotalInterestFormatted)
            assertNull(state.loanProjectionTotalPaidFormatted)
            assertNull(state.loanProjectionAccumulationMonths)
            assertNull(state.loanProjectionTotalMonths)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── SaveClicked upserts a CREDIT Goal ────────────────────────────────────────

    @Test
    fun `SaveClicked upserts a CREDIT Goal with downPayment and termMonths then emits NavigateBack`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Main"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.NameChanged("Mortgage"))
        viewModel.onEvent(GoalEditEvent.IconSelected("ic_goal_home"))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("3000000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("500000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("50000"))
        viewModel.onEvent(GoalEditEvent.RateChanged("9.5"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("600000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("20"))

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
            assertNotNull("downPayment must be set for CREDIT variant", upserted.downPayment)
            assertEquals(0, upserted.downPayment!!.compareTo(BigDecimal("600000")))
            assertEquals("termMonths must be termYears × 12 = 240", 240, upserted.termMonths)

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

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.DownPaymentChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("10000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("1"))

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

    @Test
    fun `SaveClicked CREDIT stores downPayment=0 when downPayment field is blank`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))
        viewModel.onEvent(GoalEditEvent.TargetChanged("120000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("10000"))
        viewModel.onEvent(GoalEditEvent.TermYearsChanged("1"))

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            awaitItem()

            val upserted = goalRepo.lastUpserted
            assertNotNull(upserted)
            assertNotNull("downPayment must be non-null for CREDIT (zero when blank)", upserted!!.downPayment)
            assertEquals(
                "blank downPayment field must save as BigDecimal.ZERO",
                0,
                upserted.downPayment!!.compareTo(BigDecimal.ZERO),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── Pre-fill existing CREDIT goal ─────────────────────────────────────────────

    @Test
    fun `editing an existing CREDIT goal pre-fills rate downPayment and termYears into state`() = runTest {
        currencyRepo.seed(rubCurrency())
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
            downPayment = BigDecimal("150000"),
            termMonths = 36,
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
            assertEquals("150000", state.downPayment)
            assertEquals("3", state.termYears)
            assertEquals(false, state.isCreateMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
