package com.kshavrin.mymoney.navigation

import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
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
import com.kshavrin.mymoney.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class DecisionRouterViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private fun buildSeeder(): InitialDataSeeder =
        InitialDataSeeder(
            currencyRepository = StubCurrencyRepository(),
            currencyRateRepository = NoRatesCurrencyRateRepository,
            accountRepository = StubAccountRepository(),
            categoryRepository = StubCategoryRepository(),
            transactionRunner = NoOpTransactionRunner,
            ioDispatcher = testDispatcher,
        )

    private fun buildViewModel(
        settings: AppSettings = AppSettings(),
        showOnboarding: Boolean = true,
        seeder: InitialDataSeeder = buildSeeder(),
    ): Pair<DecisionRouterViewModel, FakeAppSettingsRepository> {
        val repo = FakeAppSettingsRepository(settings)
        return DecisionRouterViewModel(
            appSettingsRepository = repo,
            initialDataSeeder = seeder,
            showOnboarding = showOnboarding,
        ) to repo
    }

    @Test
    fun `routes to Splash when flag is true and onboarding not yet completed`() =
        runTest {
            val (viewModel, _) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = null),
                    showOnboarding = true,
                )

            viewModel.state.test {
                advanceUntilIdle()
                val finalState = expectMostRecentItem()
                assertEquals(DecisionDestination.Splash, finalState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `routes to Dashboard when onboarding already completed with flag true`() =
        runTest {
            val completedAt = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
            val (viewModel, _) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = completedAt),
                    showOnboarding = true,
                )

            viewModel.state.test {
                advanceUntilIdle()
                val finalState = expectMostRecentItem()
                assertEquals(DecisionDestination.Dashboard, finalState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `routes to Dashboard when onboarding already completed with flag false`() =
        runTest {
            val completedAt = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
            val (viewModel, _) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = completedAt),
                    showOnboarding = false,
                )

            viewModel.state.test {
                advanceUntilIdle()
                val finalState = expectMostRecentItem()
                assertEquals(DecisionDestination.Dashboard, finalState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `skip path routes to Dashboard when flag is false and onboarding not completed`() =
        runTest {
            val (viewModel, _) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = null),
                    showOnboarding = false,
                )

            viewModel.state.test {
                advanceUntilIdle()
                val finalState = expectMostRecentItem()
                assertEquals(DecisionDestination.Dashboard, finalState)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `skip path stamps onboardingCompletedAt so next cold start skips Splash`() =
        runTest {
            val (_, repo) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = null),
                    showOnboarding = false,
                )

            advanceUntilIdle()

            assertNotNull(
                "onboardingCompletedAt must be set after skip path so future cold starts route directly to Dashboard",
                repo.settings.value.onboardingCompletedAt,
            )
        }

    @Test
    fun `skip path calls seedIfNeeded so initial data is present even without onboarding`() =
        runTest {
            val tracker = SeedCallTracker()
            val seeder =
                InitialDataSeeder(
                    currencyRepository = tracker.TrackingCurrencyRepository(),
                    currencyRateRepository = NoRatesCurrencyRateRepository,
                    accountRepository = StubAccountRepository(),
                    categoryRepository = StubCategoryRepository(),
                    transactionRunner = NoOpTransactionRunner,
                    ioDispatcher = testDispatcher,
                )
            val (_, _) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = null),
                    showOnboarding = false,
                    seeder = seeder,
                )

            advanceUntilIdle()

            assertTrue(
                "seedIfNeeded must be called on the skip path so data is available at first Dashboard load",
                tracker.observedAll,
            )
        }

    @Test
    fun `state resolves away from Pending after routing coroutine completes`() =
        runTest {
            val (viewModel, _) =
                buildViewModel(
                    settings = AppSettings(onboardingCompletedAt = null),
                    showOnboarding = true,
                )

            viewModel.state.test {
                advanceUntilIdle()
                val resolved = expectMostRecentItem()
                assertTrue(
                    "State must not remain Pending after the routing coroutine finishes",
                    resolved != DecisionDestination.Pending,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    private object NoOpTransactionRunner : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private object NoRatesCurrencyRateRepository : CurrencyRateRepository {
        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? = null

        override fun observeAll(): Flow<List<CurrencyRate>> = flowOf(emptyList())

        override suspend fun upsert(rate: CurrencyRate): Long = rate.id

        override suspend fun deleteById(id: Long) = Unit

        override suspend fun refreshRatesFromNetwork(): Result<Int> = Result.success(0)
    }

    private class StubCurrencyRepository(
        private val state: MutableStateFlow<List<Currency>> = MutableStateFlow(emptyList()),
    ) : CurrencyRepository {
        override fun observeActive(): Flow<List<Currency>> = state.asStateFlow()

        override fun observeAll(): Flow<List<Currency>> = state.asStateFlow()

        override suspend fun findById(id: Long): Currency? = state.value.firstOrNull { it.id == id }

        override suspend fun findByCode(code: String): Currency? =
            state.value.firstOrNull { it.code.equals(code, ignoreCase = true) }

        override suspend fun upsert(currency: Currency): Long {
            val id =
                if (currency.id == 0L) {
                    (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
                } else {
                    currency.id
                }
            state.value = state.value.filterNot { it.id == id } + currency.copy(id = id)
            return id
        }

        override suspend fun upsertAll(currencies: List<Currency>) = currencies.forEach { upsert(it) }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) {
            state.value = state.value.map { if (it.id == id) it.copy(isActive = active) else it }
        }
    }

    private class StubAccountRepository : AccountRepository {
        private val state = MutableStateFlow<List<Account>>(emptyList())

        override fun observeActive(): Flow<List<Account>> = state.asStateFlow()

        override suspend fun findById(id: Long): Account? = null

        override suspend fun findDefault(): Account? = null

        override suspend fun computeBalance(accountId: Long): BigDecimal = BigDecimal.ZERO

        override suspend fun upsert(account: Account): Long {
            val id =
                if (account.id == 0L) {
                    (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
                } else {
                    account.id
                }
            state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
            return id
        }

        override suspend fun archive(id: Long) = Unit

        override suspend fun setDefault(id: Long) = Unit

        override suspend fun countByCurrency(currencyId: Long): Int = 0
    }

    private class StubCategoryRepository : CategoryRepository {
        private val state = MutableStateFlow<List<Category>>(emptyList())

        override fun observeByKind(kind: CategoryKind): Flow<List<Category>> = state.asStateFlow()

        override fun observeAll(): Flow<List<Category>> = state.asStateFlow()

        override suspend fun findById(id: Long): Category? = null

        override suspend fun upsert(category: Category): Long {
            val id =
                if (category.id == 0L) {
                    (state.value.maxOfOrNull { it.id } ?: 0L) + 1L
                } else {
                    category.id
                }
            state.value = state.value.filterNot { it.id == id } + category.copy(id = id)
            return id
        }

        override suspend fun upsertAll(categories: List<Category>) = categories.forEach { upsert(it) }

        override suspend fun archive(id: Long) = Unit
    }

    private class SeedCallTracker {
        val currencyFlow: MutableStateFlow<List<Currency>> = MutableStateFlow(emptyList())
        var observedAll: Boolean = false

        inner class TrackingCurrencyRepository : CurrencyRepository {
            override fun observeActive(): Flow<List<Currency>> = currencyFlow.asStateFlow()

            override fun observeAll(): Flow<List<Currency>> {
                observedAll = true
                return currencyFlow.asStateFlow()
            }

            override suspend fun findById(id: Long): Currency? = null

            override suspend fun findByCode(code: String): Currency? = null

            override suspend fun upsert(currency: Currency): Long = 0L

            override suspend fun upsertAll(currencies: List<Currency>) = Unit

            override suspend fun setActive(
                id: Long,
                active: Boolean,
            ) = Unit
        }
    }
}
