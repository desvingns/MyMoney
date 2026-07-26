package com.kshavrin.mymoney.feature.transactionslist.list

import androidx.lifecycle.SavedStateHandle
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.usecase.GetCategoryRecordsUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetOperationsSummaryUseCase
import com.kshavrin.mymoney.core.domain.usecase.GetTransferRecordsUseCase
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeAccountRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeCategoryRepository
import com.kshavrin.mymoney.feature.transactionslist.fake.FakeTransactionRepository
import com.kshavrin.mymoney.feature.transactionslist.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric supplies a real android.os.Bundle so savedStateHandle.toRoute<Destinations.TransactionsList>()
// can decode its route args; the android.jar stub throws "not mocked" at VM construction (SPEC-19 type-safe nav).
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class TransactionsListViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `route defaults fall back to current month instead of epoch custom range`() =
        runTest(mainDispatcherRule.testDispatcher.scheduler) {
            val viewModel =
                buildViewModel(
                    SavedStateHandle(
                        mapOf(
                            TransactionsListViewModel.KEY_ACCOUNT_ID to -1L,
                            TransactionsListViewModel.KEY_CURRENCY_ID to -1L,
                            TransactionsListViewModel.KEY_CATEGORY_ID to -1L,
                            TransactionsListViewModel.KEY_FROM to -1L,
                            TransactionsListViewModel.KEY_TO to -1L,
                        ),
                    ),
                )

            assertTrue(
                "missing from/to route args must not become an epoch CustomRange",
                viewModel.state.value.period is Period.Month,
            )
        }

    private fun buildViewModel(savedStateHandle: SavedStateHandle): TransactionsListViewModel {
        val accountRepository = FakeAccountRepository()
        val currencyRepository = FakeCurrencyRepository()
        val transactionRepository = FakeTransactionRepository()
        val getCategoryRecords =
            GetCategoryRecordsUseCase(
                accountRepository = accountRepository,
                currencyRepository = currencyRepository,
                transactionRepository = transactionRepository,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
        val getTransferRecords =
            GetTransferRecordsUseCase(
                currencyRepository = currencyRepository,
                transactionRepository = transactionRepository,
                defaultDispatcher = mainDispatcherRule.testDispatcher,
            )
        return TransactionsListViewModel(
            getOperationsSummary =
                GetOperationsSummaryUseCase(
                    getCategoryRecords = getCategoryRecords,
                    getTransferRecords = getTransferRecords,
                ),
            accountRepository = accountRepository,
            currencyRepository = currencyRepository,
            categoryRepository = FakeCategoryRepository(),
            transactionRepository = transactionRepository,
            savedStateHandle = savedStateHandle,
        )
    }
}
