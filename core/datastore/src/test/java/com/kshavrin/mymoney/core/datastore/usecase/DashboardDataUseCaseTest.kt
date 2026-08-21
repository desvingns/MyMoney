package com.kshavrin.mymoney.core.datastore.usecase

import androidx.paging.PagingData
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.repository.TransferRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class DashboardDataUseCaseTest {
    @Test
    fun `observeInputs combines active accounts currencies and settings`() =
        runTest {
            val harness = Harness(AppSettings(chartShowProjection = true, chartColorRule = "always_green"))
            harness.accounts.seed(account)
            harness.currencies.seed(currency)

            val inputs = harness.useCase.observeInputs().first()

            assertEquals(listOf(account), inputs.accounts)
            assertEquals(listOf(currency), inputs.currencies)
            assertEquals(true, inputs.settings.chartShowProjection)
            assertEquals("always_green", inputs.settings.chartColorRule)
        }

    @Test
    fun `category flows expose all categories and only expenses`() =
        runTest {
            val harness = Harness()
            harness.categories.seed(expenseCategory, incomeCategory)

            assertEquals(listOf(expenseCategory, incomeCategory), harness.useCase.observeCategories().first())
            assertEquals(listOf(expenseCategory), harness.useCase.observeExpenseCategories().first())
        }

    @Test
    fun `transaction changes flow observes the most recent transaction`() =
        runTest {
            val harness = Harness()
            val olderTransaction = transaction.copy(id = 2L, note = "Dinner")
            harness.transactions.seed(transaction, olderTransaction)

            assertEquals(listOf(transaction), harness.useCase.observeTransactionChanges().first())
        }

    @Test
    fun `currentSettings reads and updateSettings writes through AppSettingsRepository`() =
        runTest {
            val initial = AppSettings(chartShowProjection = false, chartColorRule = "by_sign")
            val harness = Harness(initial)

            assertEquals(initial, harness.useCase.currentSettings())

            harness.useCase.updateSettings {
                it.copy(chartShowProjection = true, chartColorRule = "by_direction")
            }

            assertEquals(true, harness.settings.current().chartShowProjection)
            assertEquals("by_direction", harness.settings.current().chartColorRule)
            assertEquals(1, harness.settings.updateCalls)
        }

    @Test
    fun `findCurrency findTransactions and allTransactions delegate to repositories`() =
        runTest {
            val harness = Harness()
            harness.currencies.seed(currency)
            harness.transactions.seed(transaction)
            harness.transactions.seedForPeriod(account.id, period, transaction)

            assertEquals(currency, harness.useCase.findCurrency(currency.id))
            assertEquals(listOf(transaction), harness.useCase.findTransactions(account.id, period))
            assertEquals(listOf(transaction), harness.useCase.allTransactions())
            assertTrue(harness.transactions.findByPeriodCalls.contains(account.id to period))
        }

    private class Harness(
        initialSettings: AppSettings = AppSettings(),
    ) {
        val accounts = FakeAccountRepository()
        val currencies = FakeCurrencyRepository()
        val settings = FakeAppSettingsRepository(initialSettings)
        val transactions = FakeTransactionRepository()
        val categories = FakeCategoryRepository()
        val useCase =
            DashboardDataUseCase(
                accountRepository = accounts,
                currencyRepository = currencies,
                appSettingsRepository = settings,
                transactionRepository = transactions,
                categoryRepository = categories,
            )
    }

    private class FakeAppSettingsRepository(
        initial: AppSettings,
    ) : AppSettingsRepository {
        private val state = MutableStateFlow(initial)
        var updateCalls = 0
            private set

        override val settings: Flow<AppSettings> = state.asStateFlow()

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            updateCalls++
            state.value = transform(state.value)
        }

        fun current(): AppSettings = state.value
    }

    private class FakeAccountRepository : AccountRepository {
        private val state = MutableStateFlow<List<Account>>(emptyList())

        fun seed(vararg values: Account) {
            state.value = values.toList()
        }

        override fun observeActive(): Flow<List<Account>> = state.asStateFlow()

        override suspend fun listAllIncludingArchived(): List<Account> = state.value

        override suspend fun findById(id: Long): Account? = state.value.firstOrNull { it.id == id }

        override suspend fun findDefault(): Account? = state.value.firstOrNull { it.isDefault }

        override suspend fun computeBalance(accountId: Long): BigDecimal = BigDecimal.ZERO

        override suspend fun upsert(account: Account): Long = account.id

        override suspend fun uuidForId(id: Long): String? = null

        override suspend fun idForUuid(uuid: String): Long? = null

        override suspend fun applySharedUpsert(
            account: Account,
            uuid: String,
            deviceId: String,
        ) = Unit

        override suspend fun applySharedArchive(uuid: String) = Unit

        override suspend fun archive(id: Long) = Unit

        override suspend fun setDefault(id: Long) = Unit

        override suspend fun countByCurrency(currencyId: Long): Int = 0
    }

    private class FakeCurrencyRepository : CurrencyRepository {
        private val state = MutableStateFlow<List<Currency>>(emptyList())

        fun seed(vararg values: Currency) {
            state.value = values.toList()
        }

        override fun observeActive(): Flow<List<Currency>> = state.map { values -> values.filter(Currency::isActive) }

        override fun observeAll(): Flow<List<Currency>> = state.asStateFlow()

        override suspend fun findById(id: Long): Currency? = state.value.firstOrNull { it.id == id }

        override suspend fun findByCode(code: String): Currency? = state.value.firstOrNull { it.code == code }

        override suspend fun upsert(currency: Currency): Long = currency.id

        override suspend fun upsertAll(currencies: List<Currency>) = Unit

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) = Unit
    }

    private class FakeCategoryRepository : CategoryRepository {
        private val state = MutableStateFlow<List<Category>>(emptyList())

        fun seed(vararg values: Category) {
            state.value = values.toList()
        }

        override fun observeByKind(kind: CategoryKind): Flow<List<Category>> =
            state.map { values -> values.filter { it.kind == kind } }

        override fun observeAll(): Flow<List<Category>> = state.asStateFlow()

        override suspend fun findById(id: Long): Category? = state.value.firstOrNull { it.id == id }

        override suspend fun upsert(category: Category): Long = category.id

        override suspend fun upsertAll(categories: List<Category>) = Unit

        override suspend fun uuidForId(id: Long): String? = null

        override suspend fun idForUuid(uuid: String): Long? = null

        override suspend fun applySharedUpsert(
            category: Category,
            uuid: String,
            deviceId: String,
        ) = Unit

        override suspend fun applySharedArchive(uuid: String) = Unit

        override suspend fun archive(id: Long) = Unit
    }

    private class FakeTransactionRepository : TransactionRepository {
        private val state = MutableStateFlow<List<Transaction>>(emptyList())
        private val periodValues = mutableMapOf<Pair<Long, Period>, List<Transaction>>()
        val findByPeriodCalls = mutableListOf<Pair<Long, Period>>()

        fun seed(vararg values: Transaction) {
            state.value = values.toList()
        }

        fun seedForPeriod(
            accountId: Long,
            period: Period,
            vararg values: Transaction,
        ) {
            periodValues[accountId to period] = values.toList()
        }

        override fun observeRecent(limit: Int): Flow<List<Transaction>> = state.map { it.take(limit) }

        override fun observeAll(): Flow<List<Transaction>> = state.asStateFlow()

        override fun paged(
            accountId: Long,
            categoryId: Long?,
            from: Instant,
            to: Instant,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())

        override suspend fun findById(id: Long): Transaction? = state.value.firstOrNull { it.id == id }

        override suspend fun findByPeriod(
            accountId: Long,
            period: Period,
        ): List<Transaction> {
            findByPeriodCalls += accountId to period
            return periodValues[accountId to period].orEmpty()
        }

        override suspend fun getCategorySummary(
            accountId: Long,
            period: Period,
            kind: TransactionKind,
        ): List<CategorySummary> = emptyList()

        override suspend fun getCategoryGroups(
            accountId: Long,
            period: Period,
        ): List<CategoryGroup> = emptyList()

        override suspend fun getTransfers(
            accountId: Long?,
            period: Period,
        ): List<TransferRow> = emptyList()

        override suspend fun searchByNote(
            query: String,
            limit: Int,
        ): List<Transaction> = emptyList()

        override suspend fun upsert(transaction: Transaction): Long = transaction.id

        override suspend fun uuidForId(id: Long): String? = null

        override suspend fun applySharedUpsert(
            transaction: Transaction,
            uuid: String,
            deviceId: String,
        ) = Unit

        override suspend fun applySharedDelete(
            uuid: String,
            now: Instant,
        ) = Unit

        override suspend fun softDelete(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun restore(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun pruneDeleted(before: Instant) = Unit

        override suspend fun countByAccount(id: Long): Int = 0

        override suspend fun countByCategory(id: Long): Int = 0

        override suspend fun countByCurrency(id: Long): Int = 0
    }

    private companion object {
        val period = Period.Day(java.time.LocalDate.of(2026, 8, 21))
        val currency =
            Currency(
                id = 1L,
                code = "USD",
                symbol = "$",
                name = "US Dollar",
                decimalDigits = 2,
                isActive = true,
                sortOrder = 0,
            )
        val account =
            Account(
                id = 1L,
                name = "Cash",
                currencyId = currency.id,
                initialBalance = BigDecimal.ZERO,
                type = AccountType.Cash,
                colorHex = "#7AC794",
                iconKey = "cash",
                isDefault = true,
                sortOrder = 0,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                isArchived = false,
            )
        val expenseCategory =
            Category(
                id = 1L,
                name = "Food",
                kind = CategoryKind.Expense,
                iconKey = "food",
                colorHex = "#FF0000",
                textColor = "#FFFFFF",
                sortOrder = 0,
                isDefault = true,
                isArchived = false,
                createdAt = Instant.EPOCH,
            )
        val incomeCategory =
            Category(
                id = 2L,
                name = "Salary",
                kind = CategoryKind.Income,
                iconKey = "salary",
                colorHex = "#00FF00",
                textColor = "#FFFFFF",
                sortOrder = 1,
                isDefault = true,
                isArchived = false,
                createdAt = Instant.EPOCH,
            )
        val transaction =
            Transaction(
                id = 1L,
                kind = TransactionKind.Expense,
                amount = BigDecimal("12.34"),
                currencyId = currency.id,
                accountId = account.id,
                categoryId = expenseCategory.id,
                note = "Lunch",
                occurredAt = Instant.parse("2026-08-21T10:00:00Z"),
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH,
                isDeleted = false,
                toAccountId = null,
                toAmount = null,
                exchangeRate = null,
            )
    }
}
