package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.dictionaries.categories.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class CategoryEditFromPickerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private lateinit var categoryRepo: FakeCategoryRepository
    private lateinit var transactionRepo: FakeTransactionRepository

    @Before
    fun setUp() {
        categoryRepo = FakeCategoryRepository()
        transactionRepo = FakeTransactionRepository()
    }

    private fun buildViewModel(savedStateHandle: SavedStateHandle): CategoryEditViewModel =
        CategoryEditViewModel(
            categoryRepository = categoryRepo,
            transactionRepository = transactionRepo,
            savedStateHandle = savedStateHandle,
        )

    private fun seededCategory(
        id: Long,
        name: String = "Food",
        kind: CategoryKind = CategoryKind.Expense,
    ): Category = Category(
        id = id,
        name = name,
        kind = kind,
        iconKey = "ic_cat_food",
        colorHex = "#7A9685",
        sortOrder = 0,
        isDefault = false,
        isArchived = false,
        createdAt = now,
    )

    @Test
    fun `create mode with kind INCOME pre-fills state kind to Income`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "id" to -1L,
                "kind" to "Income",
            ),
        )

        val viewModel = buildViewModel(handle)

        val state = viewModel.state.value
        assertEquals(true, state.isCreateMode)
        assertEquals(CategoryKind.Income, state.kind)
    }

    @Test
    fun `create mode without kind defaults to Expense`() = runTest {
        val handle = SavedStateHandle(mapOf("id" to -1L))

        val viewModel = buildViewModel(handle)

        val state = viewModel.state.value
        assertEquals(true, state.isCreateMode)
        assertEquals(CategoryKind.Expense, state.kind)
    }

    @Test
    fun `create mode with fromPicker true and valid name emits NavigateBackToPickerWithId on save`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "id" to -1L,
                "kind" to "Expense",
                "fromPicker" to true,
            ),
        )
        val viewModel = buildViewModel(handle)
        viewModel.onEvent(CategoryEditEvent.NameChanged("Coffee"))

        viewModel.actions.test {
            viewModel.onEvent(CategoryEditEvent.SaveClicked)

            val action = awaitItem()
            assertTrue(
                "expected NavigateBackToPickerWithId but was $action",
                action is CategoryEditAction.NavigateBackToPickerWithId,
            )
            val emitted = action as CategoryEditAction.NavigateBackToPickerWithId
            val persisted = categoryRepo.observeAll().first()
            assertEquals(1, persisted.size)
            assertEquals(emitted.id, persisted.single().id)
            assertEquals(1L, emitted.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `create mode with fromPicker false and valid name emits NavigateBack on save`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "id" to -1L,
                "kind" to "Expense",
                "fromPicker" to false,
            ),
        )
        val viewModel = buildViewModel(handle)
        viewModel.onEvent(CategoryEditEvent.NameChanged("Coffee"))

        viewModel.actions.test {
            viewModel.onEvent(CategoryEditEvent.SaveClicked)

            val action = awaitItem()
            assertEquals(CategoryEditAction.NavigateBack, action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `create mode without fromPicker key defaults to NavigateBack on save`() = runTest {
        val handle = SavedStateHandle(mapOf("id" to -1L))
        val viewModel = buildViewModel(handle)
        viewModel.onEvent(CategoryEditEvent.NameChanged("Coffee"))

        viewModel.actions.test {
            viewModel.onEvent(CategoryEditEvent.SaveClicked)

            val action = awaitItem()
            assertEquals(CategoryEditAction.NavigateBack, action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `edit mode with fromPicker true emits regular NavigateBack and not NavigateBackToPickerWithId`() = runTest {
        categoryRepo.seed(seededCategory(id = 5L, name = "Food"))
        val handle = SavedStateHandle(
            mapOf(
                "id" to 5L,
                "fromPicker" to true,
            ),
        )
        val viewModel = buildViewModel(handle)
        viewModel.onEvent(CategoryEditEvent.NameChanged("Food edited"))

        viewModel.actions.test {
            viewModel.onEvent(CategoryEditEvent.SaveClicked)

            val action = awaitItem()
            assertEquals(CategoryEditAction.NavigateBack, action)
            assertTrue(
                "edit mode must NOT emit NavigateBackToPickerWithId",
                action !is CategoryEditAction.NavigateBackToPickerWithId,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fromPicker save uses sequential id assignment starting at 1L when repo is empty`() = runTest {
        val handle = SavedStateHandle(
            mapOf(
                "id" to -1L,
                "kind" to "Income",
                "fromPicker" to true,
            ),
        )
        val viewModel = buildViewModel(handle)
        viewModel.onEvent(CategoryEditEvent.NameChanged("Salary"))

        viewModel.actions.test {
            viewModel.onEvent(CategoryEditEvent.SaveClicked)

            val action = awaitItem()
            assertTrue(action is CategoryEditAction.NavigateBackToPickerWithId)
            assertEquals(1L, (action as CategoryEditAction.NavigateBackToPickerWithId).id)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
