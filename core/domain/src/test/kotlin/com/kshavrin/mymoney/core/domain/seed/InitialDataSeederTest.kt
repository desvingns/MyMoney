package com.kshavrin.mymoney.core.domain.seed

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCategoryRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.Locale

class InitialDataSeederTest {
    private val now = Instant.parse("2026-05-20T10:00:00Z")

    @Test
    fun seeds_on_first_run() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = createSeeder(currencyRepo, accountRepo, categoryRepo)

            val seeded = seeder.seedIfNeeded(now, Locale.US)

            assertTrue(seeded)
            assertEquals(21, currencyRepo.observeAll().first().size)
            assertEquals(1, accountRepo.observeActive().first().size)
            assertEquals(17, categoryRepo.observeAll().first().size)
        }

    @Test
    fun idempotent_on_second_run() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = createSeeder(currencyRepo, accountRepo, categoryRepo)

            seeder.seedIfNeeded(now, Locale.US)
            val secondRun = seeder.seedIfNeeded(now, Locale.US)

            assertFalse(secondRun)
            assertEquals(21, currencyRepo.observeAll().first().size)
            assertEquals(1, accountRepo.observeActive().first().size)
        }

    @Test
    fun `seeds serbian dinar with correct fields at sortOrder 20`() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val seeder =
                createSeeder(
                    currencyRepo,
                    FakeAccountRepository(),
                    FakeCategoryRepository(),
                )

            seeder.seedIfNeeded(now, Locale.US)

            val rsd = currencyRepo.observeAll().first().single { it.code == "RSD" }
            assertEquals("RSD", rsd.symbol)
            assertEquals("Serbian Dinar", rsd.name)
            assertEquals(2, rsd.decimalDigits)
            assertTrue(rsd.isActive)
            assertEquals(20, rsd.sortOrder)
        }

    @Test
    fun seeds_russian_names_for_ru_locale() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = createSeeder(currencyRepo, accountRepo, categoryRepo)

            seeder.seedIfNeeded(now, Locale("ru"))

            assertEquals(
                "Наличные",
                accountRepo
                    .observeActive()
                    .first()
                    .single()
                    .name,
            )
            val names = categoryRepo.observeAll().first().map { it.name }
            assertTrue("Продукты" in names)
            assertTrue("Зарплата" in names)
            assertTrue("Кафе и рестораны" in names)
            assertEquals(17, names.size)
        }

    @Test
    fun seeds_english_names_for_non_ru_locale() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = createSeeder(currencyRepo, accountRepo, categoryRepo)

            seeder.seedIfNeeded(now, Locale.ENGLISH)

            assertEquals(
                "Cash",
                accountRepo
                    .observeActive()
                    .first()
                    .single()
                    .name,
            )
            val names = categoryRepo.observeAll().first().map { it.name }
            assertTrue("Food" in names)
            assertTrue("Salary" in names)
            assertEquals(17, names.size)
        }

    @Test
    fun category_count_and_ordering_identical_across_locales() =
        runTest {
            val ruCategoryRepo = FakeCategoryRepository()
            val ruSeeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    ruCategoryRepo,
                )
            val enCategoryRepo = FakeCategoryRepository()
            val enSeeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    enCategoryRepo,
                )

            ruSeeder.seedIfNeeded(now, Locale("ru"))
            enSeeder.seedIfNeeded(now, Locale.ENGLISH)

            val ru = ruCategoryRepo.observeAll().first()
            val en = enCategoryRepo.observeAll().first()
            assertEquals(en.size, ru.size)
            assertEquals(en.map { it.kind }, ru.map { it.kind })
            assertEquals(en.map { it.sortOrder }, ru.map { it.sortOrder })
            assertEquals(en.map { it.iconKey }, ru.map { it.iconKey })
        }

    @Test
    fun `all seeded categories have isDefault true`() =
        runTest {
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale("ru"))

            val categories = categoryRepo.observeAll().first()
            assertTrue("every category must be marked isDefault", categories.all { it.isDefault })
        }

    @Test
    fun `seeded account has isDefault true and sortOrder zero`() =
        runTest {
            val accountRepo = FakeAccountRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    accountRepo,
                    FakeCategoryRepository(),
                )

            seeder.seedIfNeeded(now, Locale.ENGLISH)

            val account = accountRepo.observeActive().first().single()
            assertTrue(account.isDefault)
            assertEquals(0, account.sortOrder)
        }

    @Test
    fun `colorHex values are locale-invariant`() =
        runTest {
            val ruCategoryRepo = FakeCategoryRepository()
            val enCategoryRepo = FakeCategoryRepository()
            createSeeder(
                FakeCurrencyRepository(),
                FakeAccountRepository(),
                ruCategoryRepo,
            ).seedIfNeeded(now, Locale("ru"))
            createSeeder(
                FakeCurrencyRepository(),
                FakeAccountRepository(),
                enCategoryRepo,
            ).seedIfNeeded(now, Locale.ENGLISH)

            val ruColors = ruCategoryRepo.observeAll().first().map { it.colorHex }
            val enColors = enCategoryRepo.observeAll().first().map { it.colorHex }
            assertEquals(enColors, ruColors)
        }

    @Test
    fun `seeds 15 expense and 2 income categories`() =
        runTest {
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale.ENGLISH)

            val categories = categoryRepo.observeAll().first()
            assertEquals(15, categories.count { it.kind == CategoryKind.Expense })
            assertEquals(2, categories.count { it.kind == CategoryKind.Income })
        }

    @Test
    fun `all russian category names from monefy reference are present`() =
        runTest {
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale("ru"))

            val names =
                categoryRepo
                    .observeAll()
                    .first()
                    .map { it.name }
                    .toSet()
            val expected =
                setOf(
                    "Одежда",
                    "Счета",
                    "Продукты",
                    "Развлечения",
                    "Такси",
                    "Жильё",
                    "Здоровье",
                    "Питомцы",
                    "Спорт",
                    "Подарки",
                    "Телефон",
                    "Транспорт",
                    "Личная гигиена",
                    "Кафе и рестораны",
                    "Автомобиль",
                    "Зарплата",
                    "Прочее",
                )
            assertEquals(expected, names)
        }

    @Test
    fun `all english category names are present`() =
        runTest {
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale.ENGLISH)

            val names =
                categoryRepo
                    .observeAll()
                    .first()
                    .map { it.name }
                    .toSet()
            val expected =
                setOf(
                    "Clothing",
                    "Bills",
                    "Food",
                    "Entertainment",
                    "Taxi",
                    "Housing",
                    "Health",
                    "Pets",
                    "Sport",
                    "Gifts",
                    "Phone",
                    "Transport",
                    "Hygiene",
                    "Cafe",
                    "Car",
                    "Salary",
                    "Other",
                )
            assertEquals(expected, names)
        }

    @Test
    fun `any non-ru locale seeds english names`() =
        runTest {
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    accountRepo,
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale.FRENCH)

            assertEquals(
                "Cash",
                accountRepo
                    .observeActive()
                    .first()
                    .single()
                    .name,
            )
            val names = categoryRepo.observeAll().first().map { it.name }
            assertTrue("Food" in names)
            assertTrue("Наличные" !in names)
            assertTrue("Продукты" !in names)
        }

    @Test
    fun `rolls back currencies and accounts when category seeding fails so retry can seed fully`() =
        runTest {
            val currencyRepo = RollbackCurrencyRepository()
            val accountRepo = RollbackAccountRepository()
            val categoryRepo = RollbackCategoryRepository().apply { failOnUpsertAll = true }
            val transactionRunner = FakeRollbackTransactionRunner(currencyRepo, accountRepo, categoryRepo)
            val seeder = createSeeder(currencyRepo, accountRepo, categoryRepo, transactionRunner)

            try {
                seeder.seedIfNeeded(now, Locale.US)
            } catch (_: IllegalStateException) {
            }

            assertTrue(currencyRepo.observeAll().first().isEmpty())
            assertTrue(accountRepo.observeActive().first().isEmpty())
            assertTrue(categoryRepo.observeAll().first().isEmpty())

            categoryRepo.failOnUpsertAll = false

            val seeded = seeder.seedIfNeeded(now, Locale.US)

            assertTrue(seeded)
            assertEquals(21, currencyRepo.observeAll().first().size)
            assertEquals(1, accountRepo.observeActive().first().size)
            assertEquals(17, categoryRepo.observeAll().first().size)
        }

    @Test
    fun `one-shot guard leaves repositories untouched on second call`() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = createSeeder(currencyRepo, accountRepo, categoryRepo)

            seeder.seedIfNeeded(now, Locale.ENGLISH)
            val accountsBefore = accountRepo.observeActive().first().toList()
            val categoriesBefore = categoryRepo.observeAll().first().toList()

            val result = seeder.seedIfNeeded(now, Locale.ENGLISH)

            assertFalse(result)
            assertEquals(accountsBefore, accountRepo.observeActive().first().toList())
            assertEquals(categoriesBefore, categoryRepo.observeAll().first().toList())
        }

    @Test
    fun `expense category sortOrder starts at zero and is contiguous`() =
        runTest {
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale.ENGLISH)

            val expenseOrders =
                categoryRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Expense }
                    .map { it.sortOrder }
                    .sorted()
            assertEquals((0 until 15).toList(), expenseOrders)
        }

    @Test
    fun `income category sortOrder starts at zero and is contiguous`() =
        runTest {
            val categoryRepo = FakeCategoryRepository()
            val seeder =
                createSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                )

            seeder.seedIfNeeded(now, Locale.ENGLISH)

            val incomeOrders =
                categoryRepo
                    .observeAll()
                    .first()
                    .filter { it.kind == CategoryKind.Income }
                    .map { it.sortOrder }
                    .sorted()
            assertEquals((0 until 2).toList(), incomeOrders)
        }

    private fun createSeeder(
        currencyRepository: CurrencyRepository,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        transactionRunner: TransactionRunner = NoOpTransactionRunner,
    ) = InitialDataSeeder(
        currencyRepository = currencyRepository,
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        transactionRunner = transactionRunner,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    private object NoOpTransactionRunner : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
    }

    private interface RollbackRepository<T> {
        fun snapshot(): List<T>

        fun restore(snapshot: List<T>)
    }

    private class FakeRollbackTransactionRunner(
        private val currencyRepository: RollbackCurrencyRepository,
        private val accountRepository: RollbackAccountRepository,
        private val categoryRepository: RollbackCategoryRepository,
    ) : TransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            val currencies = currencyRepository.snapshot()
            val accounts = accountRepository.snapshot()
            val categories = categoryRepository.snapshot()
            return try {
                block()
            } catch (t: Throwable) {
                currencyRepository.restore(currencies)
                accountRepository.restore(accounts)
                categoryRepository.restore(categories)
                throw t
            }
        }
    }

    private class RollbackCurrencyRepository :
        CurrencyRepository,
        RollbackRepository<Currency> {
        private val state = MutableStateFlow<List<Currency>>(emptyList())

        override fun observeActive(): StateFlow<List<Currency>> = state.asStateFlow()

        override fun observeAll(): StateFlow<List<Currency>> = state.asStateFlow()

        override suspend fun findById(id: Long): Currency? = state.value.firstOrNull { it.id == id }

        override suspend fun findByCode(code: String): Currency? =
            state.value.firstOrNull { it.code.equals(code, ignoreCase = true) }

        override suspend fun upsert(currency: Currency): Long {
            val id = if (currency.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else currency.id
            state.value = state.value.filterNot { it.id == id } + currency.copy(id = id)
            return id
        }

        override suspend fun upsertAll(currencies: List<Currency>) {
            currencies.forEach { upsert(it) }
        }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) {
            state.value = state.value.map { if (it.id == id) it.copy(isActive = active) else it }
        }

        override fun snapshot(): List<Currency> = state.value.toList()

        override fun restore(snapshot: List<Currency>) {
            state.value = snapshot
        }
    }

    private class RollbackAccountRepository :
        AccountRepository,
        RollbackRepository<Account> {
        private val state = MutableStateFlow<List<Account>>(emptyList())

        override fun observeActive(): StateFlow<List<Account>> = state.asStateFlow()

        override suspend fun findById(id: Long): Account? = state.value.firstOrNull { it.id == id }

        override suspend fun findDefault(): Account? = state.value.firstOrNull { it.isDefault }

        override suspend fun computeBalance(accountId: Long): BigDecimal =
            state.value.firstOrNull { it.id == accountId }?.initialBalance ?: BigDecimal.ZERO

        override suspend fun upsert(account: Account): Long {
            val id = if (account.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else account.id
            state.value = state.value.filterNot { it.id == id } + account.copy(id = id)
            return id
        }

        override suspend fun archive(id: Long) {
            state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
        }

        override suspend fun setDefault(id: Long) {
            state.value = state.value.map { it.copy(isDefault = it.id == id) }
        }

        override suspend fun countByCurrency(currencyId: Long): Int =
            state.value.count { it.currencyId == currencyId && !it.isArchived }

        override fun snapshot(): List<Account> = state.value.toList()

        override fun restore(snapshot: List<Account>) {
            state.value = snapshot
        }
    }

    private class RollbackCategoryRepository :
        CategoryRepository,
        RollbackRepository<Category> {
        private val state = MutableStateFlow<List<Category>>(emptyList())

        var failOnUpsertAll: Boolean = false

        override fun observeByKind(kind: CategoryKind): StateFlow<List<Category>> = state.asStateFlow()

        override fun observeAll(): StateFlow<List<Category>> = state.asStateFlow()

        override suspend fun findById(id: Long): Category? = state.value.firstOrNull { it.id == id }

        override suspend fun upsert(category: Category): Long {
            val id = if (category.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else category.id
            state.value = state.value.filterNot { it.id == id } + category.copy(id = id)
            return id
        }

        override suspend fun upsertAll(categories: List<Category>) {
            if (failOnUpsertAll) {
                throw IllegalStateException("boom")
            }
            categories.forEach { upsert(it) }
        }

        override suspend fun archive(id: Long) {
            state.value = state.value.map { if (it.id == id) it.copy(isArchived = true) else it }
        }

        override fun snapshot(): List<Category> = state.value.toList()

        override fun restore(snapshot: List<Category>) {
            state.value = snapshot
        }
    }
}
