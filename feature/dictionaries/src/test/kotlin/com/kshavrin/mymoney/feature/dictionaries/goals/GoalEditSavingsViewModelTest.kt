package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.usecase.GoalLoanCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalSavingsProjector
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeGoalRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GoalEditSavingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository
    private val projector = GoalSavingsProjector()
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

    private fun usdCurrency(id: Long = 2L) = Currency(
        id = id,
        code = "USD",
        symbol = "$",
        name = "US Dollar",
        decimalDigits = 2,
        isActive = true,
        sortOrder = 1,
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

    private fun goal(
        id: Long,
        name: String = "House",
        accountId: Long = 1L,
        targetAmount: BigDecimal = BigDecimal("100000"),
        startingCapital: BigDecimal = BigDecimal("10000"),
        monthlyContribution: BigDecimal = BigDecimal("5000"),
    ) = Goal(
        id = id,
        name = name,
        iconKey = "ic_goal_home",
        colorHex = "#9C5BB8",
        accountId = accountId,
        variant = GoalVariant.SAVINGS,
        targetAmount = targetAmount,
        startingCapital = startingCapital,
        monthlyContribution = monthlyContribution,
        annualRatePercent = null,
        termDate = null,
        createdAt = now,
        updatedAt = now,
        isArchived = false,
    )

    private fun buildViewModel(goalId: Long = -1L): GoalEditViewModel =
        GoalEditViewModel(
            goalRepository = goalRepo,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            savingsProjector = projector,
            loanCalculator = loanCalculator,
            savedStateHandle = SavedStateHandle(mapOf("id" to goalId)),
        )

    @Test
    fun `selecting an account loads its balance and currency symbol into state`() = runTest {
        currencyRepo.seed(rubCurrency(id = 1L))
        accountRepo.seed(
            account(id = 1L, name = "Cash", currencyId = 1L),
            account(id = 2L, name = "Card", currencyId = 1L),
        )
        accountRepo.setBalance(2L, BigDecimal("25000"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(2L))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(2L, state.accountId)
            assertEquals(BigDecimal("25000"), state.currentBalance)
            assertEquals("₽", state.currencySymbol)
            assertTrue("formatted balance must mention 25000", state.currentBalanceFormatted.contains("25000"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting an account with a USD currency loads dollar symbol`() = runTest {
        currencyRepo.seed(usdCurrency(id = 2L))
        accountRepo.seed(account(id = 5L, name = "USD Savings", currencyId = 2L))
        accountRepo.setBalance(5L, BigDecimal("1000"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(5L))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals("$", state.currencySymbol)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `capital-delta is positive when starting capital is less than account balance`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))
        accountRepo.setBalance(1L, BigDecimal("50000"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("30000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val delta = state.capitalDelta
            assertNotNull(delta)
            assertTrue("delta must be positive (remaining on account)", delta!!.signum() > 0)
            assertEquals(0, delta.compareTo(BigDecimal("20000")))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `capital-delta is negative when starting capital exceeds account balance`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))
        accountRepo.setBalance(1L, BigDecimal("10000"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("15000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val delta = state.capitalDelta
            assertNotNull(delta)
            assertTrue("delta must be negative (short on account)", delta!!.signum() < 0)
            assertEquals(0, delta.compareTo(BigDecimal("-5000")))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `capital-delta is zero when starting capital exactly equals account balance`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))
        accountRepo.setBalance(1L, BigDecimal("20000"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("20000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val delta = state.capitalDelta
            assertNotNull(delta)
            assertEquals("delta must be zero (exact match)", 0, delta!!.signum())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `achievement status is ON_TRACK when target exceeds capital and monthly is positive`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.TargetChanged("100000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("10000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("5000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.savingsProjection
            assertNotNull(projection)
            assertEquals(GoalStatus.ON_TRACK, projection!!.status)
            assertNotNull("achievement date must be set for ON_TRACK", projection.achievementDate)
            assertTrue(
                "achievement date must be in the future",
                projection.achievementDate!!.isAfter(java.time.LocalDate.now().minusDays(1)),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `achievement status is ALREADY_ACHIEVED when starting capital meets or exceeds target`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.TargetChanged("50000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("50000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("1000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.savingsProjection
            assertNotNull(projection)
            assertEquals(GoalStatus.ALREADY_ACHIEVED, projection!!.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `achievement status is ALREADY_ACHIEVED when starting capital exceeds target`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.TargetChanged("30000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("50000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("1000"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.savingsProjection
            assertNotNull(projection)
            assertEquals(GoalStatus.ALREADY_ACHIEVED, projection!!.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `achievement status is UNREACHABLE when monthly contribution is zero`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.TargetChanged("100000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("10000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("0"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.savingsProjection
            assertNotNull(projection)
            assertEquals(GoalStatus.UNREACHABLE, projection!!.status)
            assertNull("achievement date must be null for UNREACHABLE", projection.achievementDate)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `achievement status is UNREACHABLE when monthly contribution is empty`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.TargetChanged("100000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("10000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged(""))

        viewModel.state.test {
            val state = expectMostRecentItem()
            val projection = state.savingsProjection
            assertNotNull(projection)
            assertEquals(GoalStatus.UNREACHABLE, projection!!.status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SaveClicked upserts a SAVINGS Goal with parsed fields and null credit-specific fields then emits NavigateBack`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Main"))

        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.NameChanged("House"))
        viewModel.onEvent(GoalEditEvent.IconSelected("ic_goal_home"))
        viewModel.onEvent(GoalEditEvent.TargetChanged("500000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("50000"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("10000"))

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.SaveClicked)

            val action = awaitItem()
            assertEquals(GoalEditAction.NavigateBack, action)

            val upserted = goalRepo.lastUpserted
            assertNotNull("goal must have been upserted", upserted)
            assertEquals("House", upserted!!.name)
            assertEquals("ic_goal_home", upserted.iconKey)
            assertEquals(GoalVariant.SAVINGS, upserted.variant)
            assertEquals(1L, upserted.accountId)
            assertEquals(0, upserted.targetAmount.compareTo(BigDecimal("500000")))
            assertEquals(0, upserted.startingCapital.compareTo(BigDecimal("50000")))
            assertEquals(0, upserted.monthlyContribution.compareTo(BigDecimal("10000")))
            assertNull("annualRatePercent must be null for SAVINGS variant", upserted.annualRatePercent)
            assertNull("termDate must be null for SAVINGS variant", upserted.termDate)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `SaveClicked with create mode assigns id 0 so repository generates the id`() = runTest {
        currencyRepo.seed(rubCurrency())
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel(goalId = -1L)

        viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
        viewModel.onEvent(GoalEditEvent.NameChanged("Car"))
        viewModel.onEvent(GoalEditEvent.TargetChanged("200000"))
        viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
        viewModel.onEvent(GoalEditEvent.MonthlyChanged("5000"))

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            awaitItem()

            val upserted = goalRepo.lastUpserted
            assertNotNull(upserted)
            assertTrue("new goal must receive an auto-generated id > 0", upserted!!.id > 0L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing an existing goal pre-fills form from GoalRepository findById`() = runTest {
        currencyRepo.seed(rubCurrency())
        val existing = goal(
            id = 42L,
            name = "Vacation",
            accountId = 1L,
            targetAmount = BigDecimal("80000"),
            startingCapital = BigDecimal("20000"),
            monthlyContribution = BigDecimal("4000"),
        ).copy(iconKey = "ic_goal_travel")
        goalRepo.seed(listOf(existing))
        accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Savings"))

        val viewModel = buildViewModel(goalId = 42L)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(false, state.isCreateMode)
            assertEquals("Vacation", state.name)
            assertEquals("ic_goal_travel", state.iconKey)
            assertEquals(GoalVariant.SAVINGS, state.variant)
            assertEquals(1L, state.accountId)
            assertEquals("80000", state.targetAmount)
            assertEquals("20000", state.startingCapital)
            assertEquals("4000", state.monthlyContribution)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing an existing goal and saving updates the same id`() = runTest {
        currencyRepo.seed(rubCurrency())
        val existing = goal(id = 10L, name = "Old Name", accountId = 1L)
        goalRepo.seed(listOf(existing))
        accountRepo.seed(account(id = 1L, currencyId = 1L))

        val viewModel = buildViewModel(goalId = 10L)

        viewModel.onEvent(GoalEditEvent.NameChanged("New Name"))

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            awaitItem()

            val upserted = goalRepo.lastUpserted
            assertNotNull(upserted)
            assertEquals(10L, upserted!!.id)
            assertEquals("New Name", upserted.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BackClicked emits NavigateBack action without saving`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(GoalEditEvent.BackClicked)

            val action = awaitItem()
            assertEquals(GoalEditAction.NavigateBack, action)
            assertNull("back click must not upsert a goal", goalRepo.lastUpserted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `NameChanged updates name in state`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.NameChanged("Dream House"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals("Dream House", state.name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `IconSelected updates iconKey in state`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.IconSelected("ic_goal_car"))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals("ic_goal_car", state.iconKey)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `VariantChanged to CREDIT updates variant in state`() = runTest {
        val viewModel = buildViewModel()

        viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(GoalVariant.CREDIT, state.variant)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `default state has SAVINGS variant and create mode`() = runTest {
        val viewModel = buildViewModel(goalId = -1L)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(GoalVariant.SAVINGS, state.variant)
            assertEquals(true, state.isCreateMode)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
