package com.kshavrin.mymoney.feature.dictionaries.goals

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.feature.dictionaries.goals.fake.FakeGoalRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class GoalsListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeGoalRepository
    private lateinit var viewModel: GoalsListViewModel

    private val now = Instant.parse("2026-06-06T10:00:00Z")

    @Before
    fun setUp() {
        fakeRepo = FakeGoalRepository()
        viewModel = GoalsListViewModel(fakeRepo)
    }

    private fun goal(
        id: Long,
        name: String = "Goal $id",
        variant: GoalVariant = GoalVariant.SAVINGS,
    ): Goal = Goal(
        id = id,
        name = name,
        iconKey = "ic_goal_home",
        colorHex = "#4CAF50",
        accountId = 1L,
        variant = variant,
        targetAmount = BigDecimal("10000.00"),
        startingCapital = BigDecimal.ZERO,
        monthlyContribution = BigDecimal("500.00"),
        annualRatePercent = null,
        downPayment = null,
        termMonths = null,
        createdAt = now,
        updatedAt = now,
        isArchived = false,
    )

    @Test
    fun `initial state has empty rows`() = runTest {
        viewModel.state.test {
            val state = awaitItem()
            assertTrue(state.rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seeded active goals populate rows`() = runTest {
        fakeRepo.seed(listOf(goal(1L, "House"), goal(2L, "Car")))

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(1L, 2L), state.rows.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archived goals are excluded from rows`() = runTest {
        fakeRepo.seed(
            listOf(
                goal(1L, "Active"),
                goal(2L, "Archived").copy(isArchived = true),
            ),
        )

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(1L), state.rows.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddClicked emits NavigateAdd action`() = runTest {
        viewModel.actions.test {
            viewModel.onEvent(GoalsListEvent.AddClicked)

            val action = awaitItem()
            assertEquals(GoalsListAction.NavigateAdd, action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ItemClicked emits NavigateEdit action with the correct id`() = runTest {
        fakeRepo.seed(listOf(goal(42L, "Education")))

        viewModel.actions.test {
            viewModel.onEvent(GoalsListEvent.ItemClicked(42L))

            val action = awaitItem()
            assertTrue("expected NavigateEdit but was $action", action is GoalsListAction.NavigateEdit)
            assertEquals(42L, (action as GoalsListAction.NavigateEdit).id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BackClicked emits NavigateBack action`() = runTest {
        viewModel.actions.test {
            viewModel.onEvent(GoalsListEvent.BackClicked)

            val action = awaitItem()
            assertEquals(GoalsListAction.NavigateBack, action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `rows update when repository emits a new list`() = runTest {
        viewModel.state.test {
            awaitItem()

            fakeRepo.seed(listOf(goal(1L, "First")))
            assertEquals(listOf(1L), awaitItem().rows.map { it.id })

            fakeRepo.seed(listOf(goal(1L, "First"), goal(2L, "Second")))
            assertEquals(listOf(1L, 2L), awaitItem().rows.map { it.id })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `empty repository keeps rows empty`() = runTest {
        fakeRepo.seed(emptyList())

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertTrue(state.rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
