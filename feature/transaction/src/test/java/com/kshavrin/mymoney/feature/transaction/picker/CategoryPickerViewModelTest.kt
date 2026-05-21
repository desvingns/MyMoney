package com.kshavrin.mymoney.feature.transaction.picker

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.transaction.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CategoryPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var categoryRepo: FakeCategoryRepository

    @Before
    fun setUp() {
        categoryRepo = FakeCategoryRepository()
    }

    private fun category(
        id: Long,
        name: String,
        kind: CategoryKind,
        sortOrder: Int = 0,
        isArchived: Boolean = false,
    ): Category = Category(
        id = id,
        name = name,
        kind = kind,
        iconKey = "ic_cat_${name.lowercase()}",
        colorHex = "#7AC794",
        sortOrder = sortOrder,
        isDefault = false,
        isArchived = isArchived,
        createdAt = now,
    )

    private fun buildViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): CategoryPickerViewModel =
        CategoryPickerViewModel(
            categoryRepository = categoryRepo,
            savedStateHandle = savedStateHandle,
        )

    private fun seedMixedKinds() {
        categoryRepo.seed(
            category(id = 1L, name = "Food", kind = CategoryKind.Expense, sortOrder = 0),
            category(id = 2L, name = "Bills", kind = CategoryKind.Expense, sortOrder = 1),
            category(id = 10L, name = "Salary", kind = CategoryKind.Income, sortOrder = 0),
            category(id = 11L, name = "Gifts", kind = CategoryKind.Income, sortOrder = 1),
        )
    }

    @Test
    fun `kind EXPENSE in savedState filters categories to only Expense items`() = runTest {
        seedMixedKinds()
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Expense"))

        val viewModel = buildViewModel(handle)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(CategoryKind.Expense, state.kind)
            assertEquals(listOf(1L, 2L), state.categories.map { it.id })
            assertTrue(state.categories.all { it.kind == CategoryKind.Expense })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `kind INCOME in savedState filters categories to only Income items`() = runTest {
        seedMixedKinds()
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Income"))

        val viewModel = buildViewModel(handle)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(CategoryKind.Income, state.kind)
            assertEquals(listOf(10L, 11L), state.categories.map { it.id })
            assertTrue(state.categories.all { it.kind == CategoryKind.Income })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `kind missing in savedState defaults to Expense`() = runTest {
        seedMixedKinds()

        val viewModel = buildViewModel(SavedStateHandle())

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(CategoryKind.Expense, state.kind)
            assertEquals(listOf(1L, 2L), state.categories.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archived categories are excluded from the picker state`() = runTest {
        categoryRepo.seed(
            category(id = 1L, name = "Food", kind = CategoryKind.Expense, sortOrder = 0),
            category(id = 2L, name = "Old", kind = CategoryKind.Expense, sortOrder = 1, isArchived = true),
        )
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Expense"))

        val viewModel = buildViewModel(handle)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(1L), state.categories.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `categories in state are sorted by sortOrder ascending`() = runTest {
        categoryRepo.seed(
            category(id = 5L, name = "Z", kind = CategoryKind.Expense, sortOrder = 2),
            category(id = 6L, name = "A", kind = CategoryKind.Expense, sortOrder = 0),
            category(id = 7L, name = "M", kind = CategoryKind.Expense, sortOrder = 1),
        )
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Expense"))

        val viewModel = buildViewModel(handle)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(listOf(6L, 7L, 5L), state.categories.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `CategoryClicked emits ReturnWithId with the clicked id`() = runTest {
        seedMixedKinds()
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Expense"))
        val viewModel = buildViewModel(handle)

        viewModel.actions.test {
            viewModel.onEvent(CategoryPickerEvent.CategoryClicked(id = 2L))

            val action = awaitItem()
            assertTrue(
                "expected ReturnWithId(2) but was $action",
                action is CategoryPickerAction.ReturnWithId && action.id == 2L,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddCategoryClicked emits AddCategory with the picker kind EXPENSE`() = runTest {
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Expense"))
        val viewModel = buildViewModel(handle)

        viewModel.actions.test {
            viewModel.onEvent(CategoryPickerEvent.AddCategoryClicked)

            val action = awaitItem()
            assertTrue(
                "expected AddCategory(Expense) but was $action",
                action is CategoryPickerAction.AddCategory && action.kind == CategoryKind.Expense,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `AddCategoryClicked emits AddCategory with the picker kind INCOME`() = runTest {
        val handle = SavedStateHandle(mapOf(CategoryPickerViewModel.KEY_KIND to "Income"))
        val viewModel = buildViewModel(handle)

        viewModel.actions.test {
            viewModel.onEvent(CategoryPickerEvent.AddCategoryClicked)

            val action = awaitItem()
            assertTrue(
                "expected AddCategory(Income) but was $action",
                action is CategoryPickerAction.AddCategory && action.kind == CategoryKind.Income,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `BackClicked emits NavigateBack`() = runTest {
        val viewModel = buildViewModel()

        viewModel.actions.test {
            viewModel.onEvent(CategoryPickerEvent.BackClicked)

            assertEquals(CategoryPickerAction.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `amountPreview from savedState is reflected in state`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                CategoryPickerViewModel.KEY_KIND to "Expense",
                CategoryPickerViewModel.KEY_AMOUNT_PREVIEW to "12.50",
            ),
        )

        val viewModel = buildViewModel(handle)

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals("12.50", state.amountPreview)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `amountPreview is null in state when missing from savedState`() = runTest {
        val viewModel = buildViewModel(SavedStateHandle())

        viewModel.state.test {
            val state = expectMostRecentItem()
            assertEquals(null, state.amountPreview)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
