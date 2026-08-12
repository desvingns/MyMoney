package com.kshavrin.mymoney.feature.onboarding

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import com.kshavrin.mymoney.feature.onboarding.util.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class SplashViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initialise seeds empty data once and routes to onboarding`() =
        runTest {
            val currencyRepository = FakeCurrencyRepository()
            val accountRepository = FakeAccountRepository()
            val categoryRepository = FakeCategoryRepository()
            val viewModel =
                SplashViewModel(
                    createSeeder(currencyRepository, accountRepository, categoryRepository),
                )

            viewModel.state.test {
                assertEquals(SplashDestination.Pending, awaitItem().destination)

                viewModel.initialise()
                advanceUntilIdle()

                assertEquals(SplashDestination.Onboarding, awaitItem().destination)
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.initialise()
            advanceUntilIdle()

            assertEquals(1, currencyRepository.observeAllCalls)
            assertEquals(1, currencyRepository.upsertAllCalls)
            assertEquals(23, currencyRepository.snapshot().size)
            assertEquals(1, accountRepository.snapshot().size)
            assertEquals(17, categoryRepository.snapshot().size)
        }

    @Test
    fun `initialise keeps destination pending and marks seed failed so retry can succeed`() =
        runTest {
            val currencyRepository =
                FakeCurrencyRepository().apply {
                    observeAllFailure = IllegalStateException("boom")
                }
            val accountRepository = FakeAccountRepository()
            val categoryRepository = FakeCategoryRepository()
            val viewModel =
                SplashViewModel(
                    createSeeder(currencyRepository, accountRepository, categoryRepository),
                )

            viewModel.initialise()
            advanceUntilIdle()

            assertEquals(SplashDestination.Pending, viewModel.state.value.destination)
            assertTrue(viewModel.state.value.seedFailed)
            assertEquals(1, currencyRepository.observeAllCalls)
            assertEquals(0, currencyRepository.upsertAllCalls)

            currencyRepository.observeAllFailure = null

            viewModel.retry()

            assertFalse(viewModel.state.value.seedFailed)
            advanceUntilIdle()

            assertEquals(SplashDestination.Onboarding, viewModel.state.value.destination)
            assertFalse(viewModel.state.value.seedFailed)
            assertEquals(2, currencyRepository.observeAllCalls)
            assertEquals(1, currencyRepository.upsertAllCalls)
            assertEquals(23, currencyRepository.snapshot().size)
            assertEquals(1, accountRepository.snapshot().size)
            assertEquals(17, categoryRepository.snapshot().size)
        }

    @Test
    fun `initialise preserves pending state when seeding is cancelled`() =
        runTest {
            val currencyRepository =
                FakeCurrencyRepository().apply {
                    observeAllFailure = CancellationException("cancelled")
                }
            val accountRepository = FakeAccountRepository()
            val categoryRepository = FakeCategoryRepository()
            val viewModel =
                SplashViewModel(
                    createSeeder(currencyRepository, accountRepository, categoryRepository),
                )

            viewModel.initialise()
            advanceUntilIdle()

            assertEquals(SplashDestination.Pending, viewModel.state.value.destination)
            assertFalse(viewModel.state.value.seedFailed)
            assertEquals(1, currencyRepository.observeAllCalls)
            assertEquals(0, currencyRepository.upsertAllCalls)
        }

    private fun createSeeder(
        currencyRepository: CurrencyRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
    ) = InitialDataSeeder(
        currencyRepository = currencyRepository,
        currencyRateRepository = FakeCurrencyRateRepository(),
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRunner = NoOpTransactionRunner,
        ioDispatcher = mainDispatcherRule.testDispatcher,
    )

    private object NoOpTransactionRunner : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private class FakeCurrencyRateRepository : CurrencyRateRepository {
        private val state = MutableStateFlow<List<CurrencyRate>>(emptyList())

        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? =
            state.value.firstOrNull {
                it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId
            }

        override fun observeAll(): Flow<List<CurrencyRate>> = state.asStateFlow()

        override suspend fun upsert(rate: CurrencyRate): Long {
            val id = if (rate.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else rate.id
            state.value = state.value.filterNot { it.id == id } + rate.copy(id = id)
            return id
        }

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun refreshRatesFromNetwork(): Result<Int> = Result.success(0)
    }

    private class FakeCurrencyRepository : CurrencyRepository {
        private val state = MutableStateFlow<List<Currency>>(emptyList())

        var observeAllCalls: Int = 0
            private set
        var upsertAllCalls: Int = 0
            private set
        var observeAllFailure: Throwable? = null

        fun snapshot(): List<Currency> = state.value

        override fun observeActive(): StateFlow<List<Currency>> = state.asStateFlow()

        override fun observeAll(): Flow<List<Currency>> {
            observeAllCalls += 1
            val failure = observeAllFailure
            return if (failure == null) {
                state.asStateFlow()
            } else {
                flow { throw failure }
            }
        }

        override suspend fun findById(id: Long): Currency? = state.value.firstOrNull { it.id == id }

        override suspend fun findByCode(code: String): Currency? =
            state.value.firstOrNull { it.code.equals(code, ignoreCase = true) }

        override suspend fun upsert(currency: Currency): Long {
            val id = if (currency.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else currency.id
            state.value = state.value.filterNot { it.id == id } + currency.copy(id = id)
            return id
        }

        override suspend fun upsertAll(currencies: List<Currency>) {
            upsertAllCalls += 1
            currencies.forEach { upsert(it) }
        }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) {
            state.value = state.value.map { if (it.id == id) it.copy(isActive = active) else it }
        }
    }

    private class FakeAccountRepository : AccountRepository {
        private val state = MutableStateFlow<List<Account>>(emptyList())

        fun snapshot(): List<Account> = state.value

        override fun observeActive(): StateFlow<List<Account>> = state.asStateFlow()

        override suspend fun listAllIncludingArchived(): List<Account> = state.value

        override suspend fun findById(id: Long): Account? = state.value.firstOrNull { it.id == id }

        override suspend fun findDefault(): Account? = state.value.firstOrNull { it.isDefault }

        override suspend fun computeBalance(accountId: Long): BigDecimal =
            state.value.firstOrNull { it.id == accountId }?.initialBalance ?: BigDecimal.ZERO

        override suspend fun upsert(account: Account): Long {
            val id = if (account.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else account.id
            state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
            return id
        }

        override suspend fun uuidForId(id: Long): String? = null

        override suspend fun idForUuid(uuid: String): Long? = null

        override suspend fun applySharedUpsert(
            account: Account,
            uuid: String,
            deviceId: String,
        ) = Unit

        override suspend fun applySharedArchive(uuid: String) = Unit

        override suspend fun archive(id: Long) {
            state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
        }

        override suspend fun setDefault(id: Long) {
            state.value = state.value.map { it.copy(isDefault = it.id == id) }
        }

        override suspend fun countByCurrency(currencyId: Long): Int =
            state.value.count { it.currencyId == currencyId && !it.isArchived }
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val state = MutableStateFlow<List<Category>>(emptyList())

        fun snapshot(): List<Category> = state.value

        override fun observeByKind(kind: CategoryKind): StateFlow<List<Category>> = state.asStateFlow()

        override fun observeAll(): StateFlow<List<Category>> = state.asStateFlow()

        override suspend fun findById(id: Long): Category? = state.value.firstOrNull { it.id == id }

        override suspend fun upsert(category: Category): Long {
            val id = if (category.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else category.id
            state.value = state.value.filterNot { it.id == id } + category.copy(id = id)
            return id
        }

        override suspend fun upsertAll(categories: List<Category>) {
            categories.forEach { upsert(it) }
        }

        override suspend fun uuidForId(id: Long): String? = null

        override suspend fun idForUuid(uuid: String): Long? = null

        override suspend fun applySharedUpsert(
            category: Category,
            uuid: String,
            deviceId: String,
        ) = Unit

        override suspend fun applySharedArchive(uuid: String) = Unit

        override suspend fun archive(id: Long) {
            state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
        }
    }
}
