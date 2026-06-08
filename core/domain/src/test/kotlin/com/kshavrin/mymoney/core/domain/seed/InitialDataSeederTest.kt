package com.kshavrin.mymoney.core.domain.seed

import com.kshavrin.mymoney.core.domain.fake.FakeAccountRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCategoryRepository
import com.kshavrin.mymoney.core.domain.fake.FakeCurrencyRepository
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
    fun seeds_on_first_run() = runTest {
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
    fun idempotent_on_second_run() = runTest {
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
    fun seeds_russian_names_for_ru_locale() = runTest {
        val currencyRepo = FakeCurrencyRepository()
        val accountRepo = FakeAccountRepository()
        val categoryRepo = FakeCategoryRepository()
        val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

        seeder.seedIfNeeded(now, Locale("ru"))

        assertEquals("Наличные", accountRepo.observeActive().first().single().name)
        val names = categoryRepo.observeAll().first().map { it.name }
        assertTrue("Продукты" in names)
        assertTrue("Зарплата" in names)
        assertTrue("Кафе и рестораны" in names)
        assertEquals(17, names.size)
    }

    @Test
    fun seeds_english_names_for_non_ru_locale() = runTest {
        val currencyRepo = FakeCurrencyRepository()
        val accountRepo = FakeAccountRepository()
        val categoryRepo = FakeCategoryRepository()
        val seeder = InitialDataSeeder(currencyRepo, accountRepo, categoryRepo, UnconfinedTestDispatcher())

        seeder.seedIfNeeded(now, Locale.ENGLISH)

        assertEquals("Cash", accountRepo.observeActive().first().single().name)
        val names = categoryRepo.observeAll().first().map { it.name }
        assertTrue("Food" in names)
        assertTrue("Salary" in names)
        assertEquals(17, names.size)
    }

    @Test
    fun category_count_and_ordering_identical_across_locales() = runTest {
        val ruCategoryRepo = FakeCategoryRepository()
        val ruSeeder = InitialDataSeeder(
            FakeCurrencyRepository(),
            FakeAccountRepository(),
            ruCategoryRepo,
            UnconfinedTestDispatcher(),
        )
        val enCategoryRepo = FakeCategoryRepository()
        val enSeeder = InitialDataSeeder(
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
}
