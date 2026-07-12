package com.kshavrin.mymoney.core.testing.contract

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

abstract class CurrencyRepositoryContract {
    protected abstract fun createRepository(): CurrencyRepository

    @Test
    fun `upsert assigns an id and makes a currency findable by id and code`() =
        runTest {
            val repository = createRepository()
            val id = repository.upsert(currency(code = "USD"))

            assertTrue(id > 0L)
            assertEquals(id, repository.findById(id)?.id)
            assertEquals(id, repository.findByCode("usd")?.id)
        }

    @Test
    fun `observe all contains inactive currencies ordered by sort order`() =
        runTest {
            val repository = createRepository()
            repository.upsertAll(
                listOf(
                    currency(id = 20L, code = "EUR", sortOrder = 2, isActive = false),
                    currency(id = 10L, code = "USD", sortOrder = 1),
                ),
            )

            assertEquals(listOf(10L, 20L), repository.observeAll().first().map(Currency::id))
        }

    @Test
    fun `observe active excludes inactive currencies`() =
        runTest {
            val repository = createRepository()
            repository.upsertAll(
                listOf(
                    currency(id = 10L, code = "USD", isActive = true),
                    currency(id = 20L, code = "EUR", isActive = false),
                ),
            )

            assertEquals(listOf(10L), repository.observeActive().first().map(Currency::id))
        }

    @Test
    fun `set active updates active observation without removing the currency`() =
        runTest {
            val repository = createRepository()
            repository.upsert(currency(id = 10L, code = "USD", isActive = true))

            repository.setActive(id = 10L, active = false)

            assertEquals(emptyList<Long>(), repository.observeActive().first().map(Currency::id))
            assertNotNull(repository.findById(10L))
            assertEquals(false, repository.findById(10L)?.isActive)
        }

    private fun currency(
        id: Long = 0L,
        code: String,
        sortOrder: Int = 0,
        isActive: Boolean = true,
    ) =
        Currency(
            id = id,
            code = code,
            symbol = code.take(1),
            name = "$code currency",
            decimalDigits = 2,
            isActive = isActive,
            sortOrder = sortOrder,
        )
}
