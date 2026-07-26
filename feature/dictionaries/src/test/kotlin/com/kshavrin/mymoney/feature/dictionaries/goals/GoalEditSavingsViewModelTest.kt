package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalStatus
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.usecase.ContributionCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalLoanCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalSavingsProjector
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeGoalRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

// Robolectric supplies a real android.os.Bundle so savedStateHandle.toRoute<Destinations.FinancialGoalEdit>()
// can decode its route args; the android.jar stub throws "not mocked" at VM construction (SPEC-19 type-safe nav).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
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

    private fun rubCurrency(id: Long = 1L) =
        Currency(
            id = id,
            code = "RUB",
            symbol = "₽",
            name = "Russian Ruble",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun usdCurrency(id: Long = 2L) =
        Currency(
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
        downPayment = null,
        termMonths = null,
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
            contributionCalculator = ContributionCalculator(),
            savedStateHandle = SavedStateHandle(mapOf("id" to goalId)),
        )

    @Test
    fun `selecting an account formats current balance with money formatter`() =
        runTest {
            val currency = rubCurrency(id = 1L)
            val rawBalance = BigDecimal("-1182337.0799999996")
            currencyRepo.seed(currency)
            accountRepo.seed(
                account(id = 1L, name = "Cash", currencyId = 1L),
                account(id = 2L, name = "Card", currencyId = 1L),
            )
            accountRepo.setBalance(2L, rawBalance)

            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AccountSelected(2L))
            advanceUntilIdle()

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(2L, state.accountId)
                assertNotNull(state.currentBalance)
                assertEquals(0, rawBalance.compareTo(state.currentBalance!!))
                assertEquals(currency.symbol, state.currencySymbol)
                assertEquals(
                    MoneyFormatter.format(
                        amount = rawBalance,
                        currencySymbol = currency.symbol,
                        decimalDigits = 2,
                        locale = Locale.getDefault(),
                        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
                    ),
                    state.currentBalanceFormatted,
                )
                assertFalse(state.currentBalanceFormatted.contains("0799999996"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `selecting an account with a USD currency loads dollar symbol`() =
        runTest {
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
    fun `capital-delta is positive when starting capital is less than account balance`() =
        runTest {
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
    fun `capital-delta is negative when starting capital exceeds account balance`() =
        runTest {
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
    fun `capital delta amount is formatted with money formatter when capital exceeds balance`() =
        runTest {
            val currency = rubCurrency()
            val rawBalance = BigDecimal("-1182337.0799999996")
            currencyRepo.seed(currency)
            accountRepo.seed(account(id = 1L, currencyId = 1L))
            accountRepo.setBalance(1L, rawBalance)

            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
            advanceUntilIdle()
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertNotNull(state.capitalDelta)
                assertEquals(0, rawBalance.compareTo(state.capitalDelta!!))
                assertEquals(
                    MoneyFormatter.format(
                        amount = rawBalance.abs(),
                        currencySymbol = currency.symbol,
                        decimalDigits = 2,
                        locale = Locale.getDefault(),
                        symbolPosition = MoneyFormatter.SymbolPosition.AFTER,
                    ),
                    state.capitalDeltaAmountFormatted,
                )
                assertFalse(state.capitalDeltaAmountFormatted!!.contains("0799999996"))
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `capital-delta is zero when starting capital exactly equals account balance`() =
        runTest {
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
    fun `achievement status is ON_TRACK when target exceeds capital and monthly is positive`() =
        runTest {
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
                    projection.achievementDate!!.isAfter(
                        java.time.LocalDate
                            .now()
                            .minusDays(1),
                    ),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `achievement status is ALREADY_ACHIEVED when starting capital meets or exceeds target`() =
        runTest {
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
    fun `achievement status is ALREADY_ACHIEVED when starting capital exceeds target`() =
        runTest {
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
    fun `achievement status is UNREACHABLE when monthly contribution is zero`() =
        runTest {
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
    fun `achievement status is UNREACHABLE when monthly contribution is empty`() =
        runTest {
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
    fun `SaveClicked upserts a SAVINGS Goal with parsed fields and null credit-specific fields then emits NavigateBack`() =
        runTest {
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
                assertNull("downPayment must be null for SAVINGS variant", upserted.downPayment)
                assertNull("termMonths must be null for SAVINGS variant", upserted.termMonths)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SaveClicked with create mode assigns id 0 so repository generates the id`() =
        runTest {
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
    fun `editing an existing goal pre-fills form from GoalRepository findById`() =
        runTest {
            currencyRepo.seed(rubCurrency())
            val existing =
                goal(
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
    fun `editing an existing goal and saving updates the same id`() =
        runTest {
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
    fun `BackClicked emits NavigateBack action without saving`() =
        runTest {
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
    fun `NameChanged updates name in state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.NameChanged("Dream House"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("Dream House", state.name)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `IconSelected updates iconKey in state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.IconSelected("ic_goal_car"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("ic_goal_car", state.iconKey)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `VariantChanged to CREDIT updates variant in state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.VariantChanged(GoalVariant.CREDIT))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(GoalVariant.CREDIT, state.variant)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `default state has SAVINGS variant and create mode`() =
        runTest {
            val viewModel = buildViewModel(goalId = -1L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(GoalVariant.SAVINGS, state.variant)
                assertEquals(true, state.isCreateMode)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ── Advanced contribution toggle ─────────────────────────────────────────────

    @Test
    fun `AdvancedToggled true seeds one empty income row and one empty expense row`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue("advancedContribution must be enabled", state.advancedContribution)
                assertEquals("must have exactly one income row", 1, state.incomeRows.size)
                assertEquals("must have exactly one expense row", 1, state.expenseRows.size)
                assertEquals("seeded income row name must be empty", "", state.incomeRows[0].name)
                assertEquals("seeded income row amount must be empty", "", state.incomeRows[0].amount)
                assertEquals("seeded expense row name must be empty", "", state.expenseRows[0].name)
                assertEquals("seeded expense row amount must be empty", "", state.expenseRows[0].amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `AdvancedToggled true makes monthlyContribution derived from ContributionCalculator`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "50000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "20000"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue("advanced must remain enabled", state.advancedContribution)
                assertEquals(
                    "monthly = 50000 − 20000 = 30000",
                    0,
                    state.monthlyContribution.toBigDecimalOrNull()?.compareTo(BigDecimal("30000")),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `entering multiple income and expense rows recomputes monthly as sum-incomes minus sum-expenses`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "50000"))
            viewModel.onEvent(GoalEditEvent.IncomeAdded)
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(1, "10000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "20000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAdded)
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(1, "5000"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(
                    "monthly = (50000 + 10000) − (20000 + 5000) = 35000",
                    0,
                    state.monthlyContribution.toBigDecimalOrNull()?.compareTo(BigDecimal("35000")),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `expenses greater than incomes produces negative monthly and UNREACHABLE projection`() =
        runTest {
            currencyRepo.seed(rubCurrency())
            accountRepo.seed(account(id = 1L, currencyId = 1L))

            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
            viewModel.onEvent(GoalEditEvent.TargetChanged("100000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "5000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "10000"))

            viewModel.state.test {
                val state = expectMostRecentItem()
                val monthly = state.monthlyContribution.toBigDecimalOrNull() ?: BigDecimal.ZERO
                assertTrue("monthly must be negative when expenses > incomes", monthly.signum() < 0)
                assertNotNull("projection must be computed", state.savingsProjection)
                assertEquals(GoalStatus.UNREACHABLE, state.savingsProjection!!.status)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `AdvancedToggled false keeps last computed monthly value and retains rows`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "30000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "10000"))
            viewModel.onEvent(GoalEditEvent.AdvancedToggled(false))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertFalse("advancedContribution must be disabled after toggle-off", state.advancedContribution)
                assertEquals(
                    "monthly must retain the last computed value 20000",
                    0,
                    state.monthlyContribution.toBigDecimalOrNull()?.compareTo(BigDecimal("20000")),
                )
                assertEquals("income rows must be retained", 1, state.incomeRows.size)
                assertEquals("expense rows must be retained", 1, state.expenseRows.size)
                assertEquals("income row amount must be retained", "30000", state.incomeRows[0].amount)
                assertEquals("expense row amount must be retained", "10000", state.expenseRows[0].amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `IncomeAdded appends an empty row and recomputes total`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "20000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "5000"))
            viewModel.onEvent(GoalEditEvent.IncomeAdded)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("two income rows after add", 2, state.incomeRows.size)
                assertEquals("second income row amount is empty", "", state.incomeRows[1].amount)
                assertEquals(
                    "monthly = (20000 + 0) − 5000 = 15000",
                    0,
                    state.monthlyContribution.toBigDecimalOrNull()?.compareTo(BigDecimal("15000")),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `IncomeRemoved removes the row at the given index and recomputes total`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "40000"))
            viewModel.onEvent(GoalEditEvent.IncomeAdded)
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(1, "10000"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "5000"))
            viewModel.onEvent(GoalEditEvent.IncomeRemoved(0))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("one income row remains", 1, state.incomeRows.size)
                assertEquals("remaining income amount is 10000", "10000", state.incomeRows[0].amount)
                assertEquals(
                    "monthly = 10000 − 5000 = 5000",
                    0,
                    state.monthlyContribution.toBigDecimalOrNull()?.compareTo(BigDecimal("5000")),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `blank amount in a row parses to zero and does not affect total`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, ""))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, ""))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(
                    "blank amounts → monthly contribution = 0",
                    0,
                    state.monthlyContribution.toBigDecimalOrNull()?.compareTo(BigDecimal.ZERO),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `SaveClicked persists a Goal whose contributionBreakdown has enabled=true with the rows`() =
        runTest {
            currencyRepo.seed(rubCurrency())
            accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Main"))

            val viewModel = buildViewModel()

            viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
            viewModel.onEvent(GoalEditEvent.NameChanged("Vacation"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("200000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
            viewModel.onEvent(GoalEditEvent.IncomeNameChanged(0, "Salary"))
            viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "50000"))
            viewModel.onEvent(GoalEditEvent.ExpenseNameChanged(0, "Rent"))
            viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "15000"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                awaitItem()

                val upserted = goalRepo.lastUpserted
                assertNotNull("goal must have been upserted", upserted)
                val breakdown = upserted!!.contributionBreakdown
                assertTrue("breakdown must be enabled", breakdown.enabled)
                assertEquals("one income row persisted", 1, breakdown.incomes.size)
                assertEquals("income name persisted", "Salary", breakdown.incomes[0].name)
                assertEquals(0, breakdown.incomes[0].amount.compareTo(BigDecimal("50000")))
                assertEquals("one expense row persisted", 1, breakdown.expenses.size)
                assertEquals("expense name persisted", "Rent", breakdown.expenses[0].name)
                assertEquals(0, breakdown.expenses[0].amount.compareTo(BigDecimal("15000")))
                assertEquals(
                    "monthlyContribution on Goal = 50000 − 15000 = 35000",
                    0,
                    upserted.monthlyContribution.compareTo(BigDecimal("35000")),
                )

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `advanced contribution rows round derived monthly contribution and persisted breakdown amounts to currency scale`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.US)
                currencyRepo.seed(rubCurrency())
                accountRepo.seed(account(id = 1L, currencyId = 1L, name = "Main"))

                val viewModel = buildViewModel()

                viewModel.onEvent(GoalEditEvent.AccountSelected(1L))
                advanceUntilIdle()
                viewModel.onEvent(GoalEditEvent.NameChanged("Vacation"))
                viewModel.onEvent(GoalEditEvent.TargetChanged("200000"))
                viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
                viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))
                viewModel.onEvent(GoalEditEvent.IncomeNameChanged(0, "Salary"))
                viewModel.onEvent(GoalEditEvent.IncomeAmountChanged(0, "12.3456"))
                viewModel.onEvent(GoalEditEvent.ExpenseNameChanged(0, "Fees"))
                viewModel.onEvent(GoalEditEvent.ExpenseAmountChanged(0, "0"))

                assertEquals("12.35", viewModel.state.value.monthlyContribution)

                viewModel.actions.test {
                    viewModel.onEvent(GoalEditEvent.SaveClicked)
                    awaitItem()

                    val upserted = goalRepo.lastUpserted
                    assertNotNull(upserted)
                    assertEquals(0, BigDecimal("12.35").compareTo(upserted!!.monthlyContribution))
                    assertEquals(
                        0,
                        BigDecimal("12.35").compareTo(
                            upserted.contributionBreakdown.incomes
                                .single()
                                .amount,
                        ),
                    )
                    assertEquals(
                        0,
                        BigDecimal.ZERO.compareTo(
                            upserted.contributionBreakdown.expenses
                                .single()
                                .amount,
                        ),
                    )
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    @Test
    fun `loading an existing goal with enabled breakdown pre-fills checkbox and rows`() =
        runTest {
            currencyRepo.seed(rubCurrency())
            val breakdown =
                com.kshavrin.mymoney.core.domain.model.ContributionBreakdown(
                    enabled = true,
                    incomes =
                        listOf(
                            com.kshavrin.mymoney.core.domain.model
                                .ContributionItem("Side job", BigDecimal("25000")),
                        ),
                    expenses =
                        listOf(
                            com.kshavrin.mymoney.core.domain.model
                                .ContributionItem("Gym", BigDecimal("3000")),
                        ),
                )
            val existing =
                goal(id = 55L, name = "Holiday", accountId = 1L).copy(
                    contributionBreakdown = breakdown,
                    monthlyContribution = BigDecimal("22000"),
                )
            goalRepo.seed(listOf(existing))
            accountRepo.seed(account(id = 1L, currencyId = 1L))

            val viewModel = buildViewModel(goalId = 55L)

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertTrue("advancedContribution must be enabled from saved breakdown", state.advancedContribution)
                assertEquals("one income row loaded", 1, state.incomeRows.size)
                assertEquals("income name loaded", "Side job", state.incomeRows[0].name)
                assertEquals("income amount loaded", "25000", state.incomeRows[0].amount)
                assertEquals("one expense row loaded", 1, state.expenseRows.size)
                assertEquals("expense name loaded", "Gym", state.expenseRows[0].name)
                assertEquals("expense amount loaded", "3000", state.expenseRows[0].amount)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `AdvancedToggled true does not re-seed rows when rows already exist from a loaded goal`() =
        runTest {
            currencyRepo.seed(rubCurrency())
            val breakdown =
                com.kshavrin.mymoney.core.domain.model.ContributionBreakdown(
                    enabled = false,
                    incomes =
                        listOf(
                            com.kshavrin.mymoney.core.domain.model
                                .ContributionItem("Bonus", BigDecimal("10000")),
                        ),
                    expenses =
                        listOf(
                            com.kshavrin.mymoney.core.domain.model
                                .ContributionItem("Transport", BigDecimal("2000")),
                        ),
                )
            val existing =
                goal(id = 66L, name = "Trip", accountId = 1L).copy(
                    contributionBreakdown = breakdown,
                )
            goalRepo.seed(listOf(existing))
            accountRepo.seed(account(id = 1L, currencyId = 1L))

            val viewModel = buildViewModel(goalId = 66L)
            viewModel.onEvent(GoalEditEvent.AdvancedToggled(true))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals("rows must not be re-seeded when already populated", 1, state.incomeRows.size)
                assertEquals("existing income row must be preserved", "Bonus", state.incomeRows[0].name)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
