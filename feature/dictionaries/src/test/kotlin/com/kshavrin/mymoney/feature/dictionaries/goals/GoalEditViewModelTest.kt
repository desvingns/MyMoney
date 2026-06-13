package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.repository.GoalRepository
import com.kshavrin.mymoney.core.domain.usecase.ContributionCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalLoanCalculator
import com.kshavrin.mymoney.core.domain.usecase.GoalSavingsProjector
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeGoalRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GoalEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var goalRepo: FakeGoalRepository
    private lateinit var accountRepo: FakeAccountRepository
    private lateinit var currencyRepo: FakeCurrencyRepository

    private val savingsProjector = GoalSavingsProjector()
    private val loanCalculator = GoalLoanCalculator()
    private val now: Instant = Instant.parse("2026-06-11T10:00:00Z")

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
    ): GoalEditViewModel =
        GoalEditViewModel(
            goalRepository = goalRepository,
            accountRepository = accountRepo,
            currencyRepository = currencyRepo,
            savingsProjector = savingsProjector,
            loanCalculator = loanCalculator,
            contributionCalculator = ContributionCalculator(),
            savedStateHandle = SavedStateHandle(),
        )

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
