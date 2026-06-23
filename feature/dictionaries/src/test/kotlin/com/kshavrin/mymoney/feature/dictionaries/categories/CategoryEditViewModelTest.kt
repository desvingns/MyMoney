package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.feature.dictionaries.categories.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class CategoryEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val categoryRepo = FakeCategoryRepository()
    private val transactionRepo = FakeTransactionRepository()

    private val now: Instant = Instant.parse("2026-05-20T10:00:00Z")

    private fun buildViewModel(
        categoryRepository: CategoryRepository = categoryRepo,
        categoryId: Long = -1L,
        fromPicker: Boolean = false,
        kind: String? = null,
    ): CategoryEditViewModel {
        val args = mutableMapOf<String, Any>()
        if (categoryId != -1L) args["id"] = categoryId
        if (fromPicker) args["fromPicker"] = true
        if (kind != null) args["kind"] = kind
        return CategoryEditViewModel(
            categoryRepository = categoryRepository,
            transactionRepository = transactionRepo,
            savedStateHandle = SavedStateHandle(args),
        )
    }

    private fun seededCategory(
        id: Long,
        name: String = "Food",
        kind: CategoryKind = CategoryKind.Expense,
    ): Category =
        Category(
            id = id,
            name = name,
            kind = kind,
            iconKey = "ic_cat_food",
            colorHex = "#7A9685",
            textColor = "#FFFFFF",
            sortOrder = 0,
            isDefault = false,
            isArchived = false,
            createdAt = now,
        )

    private fun transaction(
        id: Long,
        categoryId: Long,
    ): Transaction =
        Transaction(
            id = id,
            accountId = 1L,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
            categoryId = categoryId,
            currencyId = 1L,
            amount = BigDecimal("15.00"),
            kind = TransactionKind.Expense,
            note = "",
            occurredAt = now,
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
        )

    // --- create mode: initial state ---

    @Test
    fun `create mode initial state has isCreateMode true and kind Expense by default`() =
        runTest {
            val viewModel = buildViewModel()

            val state = viewModel.state.value
            assertTrue(state.isCreateMode)
            assertEquals(CategoryKind.Expense, state.kind)
        }

    // --- edit mode: loads existing category ---

    @Test
    fun `edit mode loads category fields into state`() =
        runTest {
            categoryRepo.seed(seededCategory(id = 9L, name = "Gym", kind = CategoryKind.Expense))
            val viewModel = buildViewModel(categoryId = 9L)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(false, state.isCreateMode)
            assertEquals("Gym", state.name)
            assertEquals(CategoryKind.Expense, state.kind)
        }

    // --- validation: blank name ---

    @Test
    fun `save with blank name sets error and does not upsert`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("name_required_or_too_long", viewModel.state.value.errorMessage)
            assertTrue(categoryRepo.observeAll().first().isEmpty())
        }

    @Test
    fun `save with name longer than 32 characters sets error and does not upsert`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.NameChanged("B".repeat(33)))
            viewModel.onEvent(CategoryEditEvent.SaveClicked)
            advanceUntilIdle()

            assertEquals("name_required_or_too_long", viewModel.state.value.errorMessage)
            assertTrue(categoryRepo.observeAll().first().isEmpty())
        }

    // --- field change clears error ---

    @Test
    fun `NameChanged clears existing errorMessage`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.SaveClicked)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.errorMessage)

            viewModel.onEvent(CategoryEditEvent.NameChanged("Coffee"))
            assertNull(viewModel.state.value.errorMessage)
        }

    // --- successful save (create, regular flow) ---

    @Test
    fun `valid name emits NavigateBack and persists category`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.NameChanged("Transport"))

            viewModel.actions.test {
                viewModel.onEvent(CategoryEditEvent.SaveClicked)
                advanceUntilIdle()

                assertEquals(CategoryEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val persisted = categoryRepo.observeAll().first()
            assertEquals(1, persisted.size)
            assertEquals("Transport", persisted.first().name)
        }

    // --- kind and icon changes reflected in state ---

    @Test
    fun `KindChanged updates kind in state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.KindChanged(CategoryKind.Income))

            assertEquals(CategoryKind.Income, viewModel.state.value.kind)
        }

    @Test
    fun `IconChanged updates iconKey in state`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.IconChanged("ic_cat_car"))

            assertEquals("ic_cat_car", viewModel.state.value.iconKey)
        }

    @Test
    fun `save derives colorHex from iconKey not from user input`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CategoryEditEvent.NameChanged("Transport"))
            viewModel.onEvent(CategoryEditEvent.IconChanged("ic_cat_car"))

            viewModel.actions.test {
                viewModel.onEvent(CategoryEditEvent.SaveClicked)
                advanceUntilIdle()

                assertEquals(CategoryEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val persisted = categoryRepo.observeAll().first().first { it.name == "Transport" }
            assertEquals("ic_cat_car", persisted.iconKey)
            assertEquals("#FF8A80", persisted.colorHex)
        }

    // --- archive (delete with no transactions) ---

    @Test
    fun `DeleteClicked on category without transactions archives it and emits NavigateBack`() =
        runTest {
            categoryRepo.seed(seededCategory(id = 12L, name = "Hobbies"))
            val viewModel = buildViewModel(categoryId = 12L)
            advanceUntilIdle()

            viewModel.actions.test {
                viewModel.onEvent(CategoryEditEvent.DeleteClicked)
                advanceUntilIdle()

                assertEquals(CategoryEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val archived = categoryRepo.observeAll().first().first { it.id == 12L }
            assertTrue(archived.isArchived)
        }

    @Test
    fun `archived category is hidden from observeByKind but present in observeAll`() =
        runTest {
            categoryRepo.seed(seededCategory(id = 20L, name = "OldCategory"))
            val viewModel = buildViewModel(categoryId = 20L)
            advanceUntilIdle()

            viewModel.onEvent(CategoryEditEvent.DeleteClicked)
            advanceUntilIdle()

            val all = categoryRepo.observeAll().first()
            assertEquals(1, all.size)
            assertTrue(all.first().isArchived)
        }

    // --- archive blocked by transactions ---

    @Test
    fun `DeleteClicked on category with transactions sets blockedDeleteCount`() =
        runTest {
            categoryRepo.seed(seededCategory(id = 15L))
            transactionRepo.seed(
                transaction(id = 1L, categoryId = 15L),
                transaction(id = 2L, categoryId = 15L),
                transaction(id = 3L, categoryId = 15L),
            )
            val viewModel = buildViewModel(categoryId = 15L)
            advanceUntilIdle()

            viewModel.onEvent(CategoryEditEvent.DeleteClicked)
            advanceUntilIdle()

            assertEquals(3, viewModel.state.value.blockedDeleteCount)
        }

    @Test
    fun `DeleteClicked on category with transactions does not archive it`() =
        runTest {
            categoryRepo.seed(seededCategory(id = 16L))
            transactionRepo.seed(transaction(id = 5L, categoryId = 16L))
            val viewModel = buildViewModel(categoryId = 16L)
            advanceUntilIdle()

            viewModel.onEvent(CategoryEditEvent.DeleteClicked)
            advanceUntilIdle()

            val category = categoryRepo.observeAll().first().first { it.id == 16L }
            assertTrue(!category.isArchived)
        }

    @Test
    fun `BlockedDeleteDismissed clears blockedDeleteCount`() =
        runTest {
            categoryRepo.seed(seededCategory(id = 17L))
            transactionRepo.seed(transaction(id = 6L, categoryId = 17L))
            val viewModel = buildViewModel(categoryId = 17L)
            advanceUntilIdle()

            viewModel.onEvent(CategoryEditEvent.DeleteClicked)
            advanceUntilIdle()
            assertNotNull(viewModel.state.value.blockedDeleteCount)

            viewModel.onEvent(CategoryEditEvent.BlockedDeleteDismissed)
            assertNull(viewModel.state.value.blockedDeleteCount)
        }

    @Test
    fun `DeleteClicked in create mode emits no actions`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(CategoryEditEvent.DeleteClicked)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- BackClicked ---

    @Test
    fun `BackClicked emits NavigateBack`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(CategoryEditEvent.BackClicked)

                assertEquals(CategoryEditAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- double-save guard (pre-existing test preserved) ---

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() =
        runTest {
            val blockingRepo = BlockingCategoryRepository()
            val viewModel = buildViewModel(categoryRepository = blockingRepo)

            viewModel.onEvent(CategoryEditEvent.NameChanged("Food"))
            viewModel.onEvent(CategoryEditEvent.KindChanged(CategoryKind.Expense))

            viewModel.actions.test {
                viewModel.onEvent(CategoryEditEvent.SaveClicked)
                assertTrue(viewModel.state.value.isSaving)
                viewModel.onEvent(CategoryEditEvent.SaveClicked)

                assertEquals(1, blockingRepo.startedUpserts.size)

                blockingRepo.release()
                advanceUntilIdle()

                assertEquals(1, blockingRepo.persistedUpserts.size)
                assertEquals(CategoryEditAction.NavigateBack, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    private class BlockingCategoryRepository(
        private val delegate: FakeCategoryRepository = FakeCategoryRepository(),
    ) : CategoryRepository by delegate {
        val startedUpserts: MutableList<Category> = mutableListOf()
        val persistedUpserts: MutableList<Category> = mutableListOf()
        private val gate = CompletableDeferred<Unit>()

        override suspend fun upsert(category: Category): Long {
            startedUpserts += category
            gate.await()
            val id = delegate.upsert(category)
            persistedUpserts += category.copy(id = id)
            return id
        }

        fun release() {
            if (!gate.isCompleted) {
                gate.complete(Unit)
            }
        }
    }
}
