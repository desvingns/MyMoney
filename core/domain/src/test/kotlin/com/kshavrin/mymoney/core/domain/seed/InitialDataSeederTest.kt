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
}
