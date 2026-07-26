package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.ContributionBreakdown
import com.kshavrin.mymoney.core.domain.model.ContributionItem
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
import com.kshavrin.mymoney.core.domain.usecase.ContributionCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalLoanCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalSavingsProjector
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeGoalRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class GoalEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository

    private val savingsProjector = GoalSavingsProjector()
    private val loanCalculator = GoalLoanCalculator()
    private val now: Instant = Instant.parse("2026-06-11T10:00:00Z")
    private val createdEarlier: Instant = Instant.parse("2026-06-01T08:00:00Z")

    @Before
    fun setUp() {
        goalRepo = FakeGoalRepository()
        accountRepo = FakeAccountRepository()
        currencyRepo = FakeCurrencyRepository()
        currencyRepo.seed(
            Currency(
                id = 1L,
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            ),
        )
        accountRepo.seed(
            Account(
                id = 1L,
                name = "Savings",
                currencyId = 1L,
                initialBalance = BigDecimal.ZERO,
                type = AccountType.Cash,
                colorHex = "#4A8FCB",
                iconKey = "ic_account_wallet",
                isDefault = true,
                sortOrder = 0,
                createdAt = now,
                updatedAt = now,
                isArchived = false,
            ),
        )
    }

    private fun buildViewModel(
        goalRepository: GoalRepository = goalRepo,
        goalId: Long = -1L,
    ): GoalEditViewModel =
        GoalEditViewModel(
            goalRepository = goalRepository,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            savingsProjector = savingsProjector,
            loanCalculator = loanCalculator,
            contributionCalculator = ContributionCalculator(),
            savedStateHandle = if (goalId == -1L) SavedStateHandle() else SavedStateHandle(mapOf("id" to goalId)),
        )

    private fun existingGoal(id: Long = 10L): Goal =
        Goal(
            id = id,
            name = "Emergency",
            iconKey = "ic_goal_other",
            colorHex = "#9C5BB8",
            accountId = 1L,
            variant = GoalVariant.SAVINGS,
            targetAmount = BigDecimal("50000"),
            startingCapital = BigDecimal("1000"),
            monthlyContribution = BigDecimal("500"),
            annualRatePercent = null,
            downPayment = null,
            termMonths = null,
            createdAt = createdEarlier,
            updatedAt = now,
            isArchived = false,
            contributionBreakdown = ContributionBreakdown(),
        )

    @Test
    fun `edit mode displays existing goal amounts with Russian decimal separators`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.forLanguageTag("ru-RU"))
                goalRepo.seed(
                    listOf(
                        existingGoal().copy(
                            targetAmount = BigDecimal("50000.50"),
                            startingCapital = BigDecimal("1000.25"),
                            monthlyContribution = BigDecimal("500.75"),
                            annualRatePercent = BigDecimal("7.5"),
                            downPayment = BigDecimal("200.20"),
                        ),
                    ),
                )

                val viewModel = buildViewModel(goalId = 10L)
                advanceUntilIdle()

                val state = viewModel.state.value
                assertEquals("50000,50", state.targetAmount)
                assertEquals("1000,25", state.startingCapital)
                assertEquals("500,75", state.monthlyContribution)
                assertEquals("7,5", state.annualRatePercent)
                assertEquals("200,2", state.downPayment)
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    @Test
    fun `edit mode displays advanced contribution rows and derived monthly amount in Russian`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.forLanguageTag("ru-RU"))
                goalRepo.seed(
                    listOf(
                        existingGoal().copy(
                            contributionBreakdown =
                                ContributionBreakdown(
                                    enabled = true,
                                    incomes = listOf(ContributionItem("Salary", BigDecimal("123.45"))),
                                    expenses = listOf(ContributionItem("Rent", BigDecimal("22.33"))),
                                ),
                        ),
                    ),
                )

                val viewModel = buildViewModel(goalId = 10L)
                advanceUntilIdle()

                val state = viewModel.state.value
                assertEquals("123,45", state.incomeRows.single().amount)
                assertEquals("22,33", state.expenseRows.single().amount)
                assertEquals("101,12", state.monthlyContribution)
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    // --- comma as decimal separator ---

    @Test
    fun `target with comma separator is saved as correct BigDecimal`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("10000,50"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("500"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = goalRepo.lastUpserted
            assertNotNull(saved)
            assertEquals(0, BigDecimal("10000.50").compareTo(saved!!.targetAmount))
        }

    @Test
    fun `starting capital with comma separator is accepted on save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("5000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("1000,75"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("200"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, BigDecimal("1000.75").compareTo(goalRepo.lastUpserted!!.startingCapital))
        }

    @Test
    fun `monthly contribution with comma separator is accepted on save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("5000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("250,50"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, BigDecimal("250.50").compareTo(goalRepo.lastUpserted!!.monthlyContribution))
        }

    @Test
    fun `save rounds goal money fields to the selected account currency scale`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("1000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("10.005"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("12.3456"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = goalRepo.lastUpserted
            assertNotNull(saved)
            assertEquals(0, BigDecimal("10.01").compareTo(saved!!.startingCapital))
            assertEquals(0, BigDecimal("12.35").compareTo(saved.monthlyContribution))
        }

    // --- non-numeric input → validation error, not zero ---

    @Test
    fun `alphabetic target amount sets amount_format error and blocks save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("abc"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("500"))
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("amount_format", viewModel.state.value.errorMessage)
        }

    @Test
    fun `input with digit-space-currency-suffix sets amount_format error and blocks save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("10 000р"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("500"))
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("amount_format", viewModel.state.value.errorMessage)
        }

    @Test
    fun `non-numeric target does not call upsert`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("not-a-number"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("500"))
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            advanceUntilIdle()

            assertNull(goalRepo.lastUpserted)
        }

    @Test
    fun `non-numeric target does not emit NavigateBack`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("abc"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("500"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `non-numeric starting capital sets amount_format error`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("5000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("??"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("500"))
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("amount_format", viewModel.state.value.errorMessage)
        }

    @Test
    fun `non-numeric monthly contribution sets amount_format error`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("5000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("bad"))
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("amount_format", viewModel.state.value.errorMessage)
        }

    // --- field change clears error ---

    @Test
    fun `TargetChanged clears existing errorMessage`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("abc"))
            viewModel.onEvent(GoalEditEvent.SaveClicked)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.errorMessage)

            viewModel.onEvent(GoalEditEvent.TargetChanged("5000"))
            assertNull(viewModel.state.value.errorMessage)
        }

    // --- createdAt stability on edit ---

    @Test
    fun `editing goal preserves original createdAt on upsert`() =
        runTest {
            goalRepo.seed(listOf(existingGoal(id = 10L)))
            val viewModel = buildViewModel(goalId = 10L)
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Emergency Fund"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = goalRepo.lastUpserted
            assertNotNull(saved)
            assertEquals(createdEarlier, saved!!.createdAt)
        }

    @Test
    fun `creating new goal sets a non-null createdAt`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("New Goal"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("1000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("100"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertNotNull(goalRepo.lastUpserted!!.createdAt)
        }

    @Test
    fun `editing goal does not use null createdAt even when state was not pre-populated`() =
        runTest {
            goalRepo.seed(listOf(existingGoal(id = 11L)))
            val viewModel = buildViewModel(goalId = 11L)
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = goalRepo.lastUpserted
            assertNotNull(saved)
            assertEquals(createdEarlier, saved!!.createdAt)
        }

    // --- dot still works as decimal separator ---

    @Test
    fun `target with dot separator is saved correctly`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("9999.99"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("0"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("100"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                advanceUntilIdle()
                awaitItem()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(BigDecimal("9999.99"), goalRepo.lastUpserted!!.targetAmount)
        }

    // --- double-save guard (pre-existing test preserved) ---

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingGoalRepository()
            val viewModel = buildViewModel(goalRepository = blockingRepo)

            advanceUntilIdle()
            viewModel.onEvent(GoalEditEvent.NameChanged("Trip"))
            viewModel.onEvent(GoalEditEvent.TargetChanged("5000"))
            viewModel.onEvent(GoalEditEvent.StartingCapitalChanged("1000"))
            viewModel.onEvent(GoalEditEvent.MonthlyChanged("250"))

            viewModel.actions.test {
                viewModel.onEvent(GoalEditEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(GoalEditEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(GoalEditAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class BlockingGoalRepository(
        private val delegate: FakeGoalRepository = FakeGoalRepository(),
    ) : GoalRepository by delegate {
        val startedUpserts: MutableList<Goal> = mutableListOf()
        val persistedUpserts: MutableList<Goal> = mutableListOf()
        private val gate = CompletableDeferred<Unit>()

        override suspend fun upsert(goal: Goal): Long {
            startedUpserts += goal
            gate.await()
            val id = delegate.upsert(goal)
            persistedUpserts += goal.copy(id = id)
            return id
        }

        fun release() {
            if (!gate.isCompleted) {
                gate.complete(Unit)
            }
        }
    }
}
