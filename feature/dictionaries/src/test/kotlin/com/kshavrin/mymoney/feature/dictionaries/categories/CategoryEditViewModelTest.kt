package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.feature.dictionaries.categories.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.dictionaries.currencies.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.dictionaries.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val categoryRepo = FakeCategoryRepository()
    private val transactionRepo = FakeTransactionRepository()

    private fun buildViewModel(
        categoryRepository: CategoryRepository = categoryRepo,
    ): CategoryEditViewModel = CategoryEditViewModel(
        categoryRepository = categoryRepository,
        transactionRepository = transactionRepo,
        savedStateHandle = SavedStateHandle(),
    )

    @Test
    fun `double SaveClicked performs one upsert and emits one NavigateBack`() = runTest {
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
