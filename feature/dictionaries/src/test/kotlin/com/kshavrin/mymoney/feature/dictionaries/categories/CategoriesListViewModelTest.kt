package com.kshavrin.mymoney.feature.dictionaries.categories

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.dictionaries.categories.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CategoriesListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeCategoryRepository
    private lateinit var viewModel: CategoriesListViewModel

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    @Before
    fun setUp() {
        fakeRepo = FakeCategoryRepository()
    }

    private fun category(
        id: Long,
        name: String,
        kind: CategoryKind,
        sortOrder: Int,
    ): Category =
        Category(
            id = id,
            name = name,
            kind = kind,
            iconKey = "ic_cat_${name.lowercase()}",
            colorHex = "#7AC794",
            sortOrder = sortOrder,
            isDefault = false,
            isArchived = false,
            createdAt = now,
        )

    private fun seedDefaultExpenseAndIncome() {
        fakeRepo.seed(
            category(1L, "Food", CategoryKind.Expense, sortOrder = 0),
            category(2L, "Transport", CategoryKind.Expense, sortOrder = 1),
            category(3L, "Bills", CategoryKind.Expense, sortOrder = 2),
            category(10L, "Salary", CategoryKind.Income, sortOrder = 0),
            category(11L, "Gifts", CategoryKind.Income, sortOrder = 1),
        )
    }

    @Test
    fun `state separates expense and income sorted by sortOrder`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            viewModel.state.test {
                val initial = expectMostRecentItem()
                assertEquals(listOf(1L, 2L, 3L), initial.expense.map { it.id })
                assertEquals(listOf(10L, 11L), initial.income.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reorder expense persists new order with sortOrder 0 to n`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val originalExpense = viewModel.state.value.expense
            val reordered = listOf(originalExpense[2], originalExpense[0], originalExpense[1])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, reordered))

            val persistedExpense =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Expense }
                    .sortedBy { it.sortOrder }

            assertEquals(listOf(3L, 1L, 2L), persistedExpense.map { it.id })
            assertEquals(listOf(0, 1, 2), persistedExpense.map { it.sortOrder })
        }

    @Test
    fun `reorder income persists new order with sortOrder 0 to n`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val originalIncome = viewModel.state.value.income
            val reordered = listOf(originalIncome[1], originalIncome[0])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Income, reordered))

            val persistedIncome =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Income }
                    .sortedBy { it.sortOrder }

            assertEquals(listOf(11L, 10L), persistedIncome.map { it.id })
            assertEquals(listOf(0, 1), persistedIncome.map { it.sortOrder })
        }

    @Test
    fun `reordering expense does not touch income sortOrder`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val incomeBefore =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Income }
                    .associate { it.id to it.sortOrder }

            val originalExpense = viewModel.state.value.expense
            val reorderedExpense = listOf(originalExpense[2], originalExpense[1], originalExpense[0])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, reorderedExpense))

            val incomeAfter =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Income }
                    .associate { it.id to it.sortOrder }

            assertEquals(incomeBefore, incomeAfter)
        }

    @Test
    fun `reordering income does not touch expense sortOrder`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val expenseBefore =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Expense }
                    .associate { it.id to it.sortOrder }

            val originalIncome = viewModel.state.value.income
            val reorderedIncome = listOf(originalIncome[1], originalIncome[0])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Income, reorderedIncome))

            val expenseAfter =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Expense }
                    .associate { it.id to it.sortOrder }

            assertEquals(expenseBefore, expenseAfter)
        }

    @Test
    fun `reorder updates uiState expense immediately to new order`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val originalExpense = viewModel.state.value.expense
            val reordered = listOf(originalExpense[1], originalExpense[2], originalExpense[0])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, reordered))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(2L, 3L, 1L), state.expense.map { it.id })
                assertEquals(listOf(0, 1, 2), state.expense.map { it.sortOrder })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reorder updates uiState income immediately to new order`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val originalIncome = viewModel.state.value.income
            val reordered = listOf(originalIncome[1], originalIncome[0])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Income, reordered))

            viewModel.state.test {
                val state = expectMostRecentItem()
                assertEquals(listOf(11L, 10L), state.income.map { it.id })
                assertEquals(listOf(0, 1), state.income.map { it.sortOrder })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `reorder preserves all non-sortOrder fields of expense items`() =
        runTest {
            seedDefaultExpenseAndIncome()
            viewModel = CategoriesListViewModel(fakeRepo)

            val originalExpense = viewModel.state.value.expense
            val reordered = listOf(originalExpense[2], originalExpense[0], originalExpense[1])

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, reordered))

            val persisted =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Expense }
                    .associateBy { it.id }

            val foodAfter = persisted[1L]!!
            assertEquals("Food", foodAfter.name)
            assertEquals("ic_cat_food", foodAfter.iconKey)
            assertEquals("#7AC794", foodAfter.colorHex)
            assertEquals(CategoryKind.Expense, foodAfter.kind)
            assertEquals(false, foodAfter.isDefault)
            assertEquals(false, foodAfter.isArchived)
        }

    @Test
    fun `reorder with single item assigns sortOrder zero`() =
        runTest {
            fakeRepo.seed(
                category(1L, "Food", CategoryKind.Expense, sortOrder = 5),
            )
            viewModel = CategoriesListViewModel(fakeRepo)

            val sole = viewModel.state.value.expense
            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, sole))

            val persisted =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Expense }
            assertEquals(1, persisted.size)
            assertEquals(0, persisted[0].sortOrder)
        }

    @Test
    fun `reorder with empty list does not crash and does not affect other section`() =
        runTest {
            fakeRepo.seed(
                category(10L, "Salary", CategoryKind.Income, sortOrder = 0),
                category(11L, "Gifts", CategoryKind.Income, sortOrder = 1),
            )
            viewModel = CategoriesListViewModel(fakeRepo)

            viewModel.onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, emptyList()))

            val income =
                fakeRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Income }
                    .sortedBy { it.sortOrder }
            assertEquals(listOf(10L, 11L), income.map { it.id })
            assertEquals(listOf(0, 1), income.map { it.sortOrder })

            val expense = fakeRepo.observeAll().first().filter { it.kind == CategoryKind.Expense }
            assertTrue(expense.isEmpty())
        }
}
