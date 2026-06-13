package com.kshavrin.mymoney.core.domain.seed

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCategoryRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
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
            val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

            val seeded = seeder.seedIfNeeded(now, Locale.US)

            assertTrue(seeded)
            assertEquals(20, currencyRepo.observeAll().first().size)
            assertEquals(1, accountRepo.observeActive().first().size)
            assertEquals(17, categoryRepo.observeAll().first().size)
        }

    @Test
    fun idempotent_on_second_run() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

            seeder.seedIfNeeded(now, Locale.US)
            val secondRun = seeder.seedIfNeeded(now, Locale.US)

            assertFalse(secondRun)
            assertEquals(20, currencyRepo.observeAll().first().size)
            assertEquals(1, accountRepo.observeActive().first().size)
        }

    @Test
    fun seeds_russian_names_for_ru_locale() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

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
            val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    ruCategoryRepo,
                    UnconfinedTestDispatcher(),
                )
            val enCategoryRepo = FakeCategoryRepository()
            val enSeeder =
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    enCategoryRepo,
                    UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    accountRepo,
                    FakeCategoryRepository(),
                    UnconfinedTestDispatcher(),
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
            InitialDataSeeder(
                FakeCurrencyRepository(),
                FakeAccountRepository(),
                ruCategoryRepo,
                UnconfinedTestDispatcher(),
            ).seedIfNeeded(now, Locale("ru"))
            InitialDataSeeder(
                FakeCurrencyRepository(),
                FakeAccountRepository(),
                enCategoryRepo,
                UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    accountRepo,
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
    fun `one-shot guard leaves repositories untouched on second call`() =
        runTest {
            val currencyRepo = FakeCurrencyRepository()
            val accountRepo = FakeAccountRepository()
            val categoryRepo = FakeCategoryRepository()
            val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
                InitialDataSeeder(
                    FakeCurrencyRepository(),
                    FakeAccountRepository(),
                    categoryRepo,
                    UnconfinedTestDispatcher(),
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
}
