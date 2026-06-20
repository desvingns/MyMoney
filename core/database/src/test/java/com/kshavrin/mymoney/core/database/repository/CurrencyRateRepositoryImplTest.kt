package com.kshavrin.mymoney.core.database.repository

import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.database.dao.CurrencyRateDao
import com.kshavrin.mymoney.core.database.entity.CurrencyRateEntity
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.network.ConnectivityChecker
import com.kshavrin.mymoney.core.network.ExchangeRateApi
import com.kshavrin.mymoney.core.network.ExchangeRateResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CurrencyRateRepositoryImplTest {
    private inner class FakeCurrencyRateDao : CurrencyRateDao {
        val upserted = mutableListOf<CurrencyRateEntity>()
        private val store = MutableStateFlow<List<CurrencyRateEntity>>(emptyList())

        override suspend fun findRate(
            from: Long,
            to: Long,
        ): CurrencyRateEntity? =
            store.value.firstOrNull { it.fromCurrencyId == from && it.toCurrencyId == to }

        override fun observeAll(): Flow<List<CurrencyRateEntity>> = store.asStateFlow()

        override suspend fun upsert(rate: CurrencyRateEntity): Long {
            upserted.add(rate)
            return upserted.size.toLong()
        }

        override suspend fun deleteById(id: Long) {
            store.value = store.value.filterNot { it.id == id }
        }
    }

    private inner class FakeCurrencyRepository : CurrencyRepository {
        private val currencies = mutableListOf<Currency>()

        fun seed(vararg items: Currency) {
            currencies.addAll(items)
        }

        override fun observeActive(): Flow<List<Currency>> = MutableStateFlow(currencies.toList())

        override fun observeAll(): Flow<List<Currency>> = MutableStateFlow(currencies.toList())

        override suspend fun findById(id: Long): Currency? = currencies.firstOrNull { it.id == id }

        override suspend fun findByCode(code: String): Currency? =
            currencies.firstOrNull { it.code.equals(code, ignoreCase = true) }

        override suspend fun upsert(currency: Currency): Long {
            val id = if (currency.id == 0L) (currencies.maxOfOrNull { it.id } ?: 0L) + 1L else currency.id
            currencies.removeIf { it.id == id }
            currencies.add(currency.copy(id = id))
            return id
        }

        override suspend fun upsertAll(currencies: List<Currency>) = currencies.forEach { upsert(it) }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) {
            val idx = currencies.indexOfFirst { it.id == id }
            if (idx >= 0) currencies[idx] = currencies[idx].copy(isActive = active)
        }
    }

    private fun fakeConnectivityChecker(online: Boolean): ConnectivityChecker =
        object : ConnectivityChecker {
            override fun isOnline(): Boolean = online
        }

    private class FakeExchangeRateApi(
        private val response: ExchangeRateResponseDto,
    ) : ExchangeRateApi {
        override suspend fun latest(): ExchangeRateResponseDto = response
    }

    private val fixedInstant = Instant.parse("2026-06-20T12:00:00Z")
    private val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()

    private lateinit var fakeDao: FakeCurrencyRateDao
    private lateinit var fakeCurrencyRepo: FakeCurrencyRepository

    private val eurCurrency = currency(id = 1L, code = "EUR")
    private val usdCurrency = currency(id = 2L, code = "USD")
    private val rubCurrency = currency(id = 3L, code = "RUB")
    private val rsdCurrency = currency(id = 4L, code = "RSD")
    private val kztCurrency = currency(id = 5L, code = "KZT")
    private val aedCurrency = currency(id = 6L, code = "AED")

    private val successDto =
        ExchangeRateResponseDto(
            result = "success",
            timeLastUpdateUnix = 1750377601L,
            baseCode = "EUR",
            rates =
                mapOf(
                    "EUR" to 1.0,
                    "USD" to 1.0718,
                    "RUB" to 97.48,
                    "RSD" to 117.14,
                    "KZT" to 546.38,
                    "AED" to 3.9364,
                    "JPY" to 168.97,
                ),
        )

    @Before
    fun setUp() {
        fakeDao = FakeCurrencyRateDao()
        fakeCurrencyRepo = FakeCurrencyRepository()
        fakeCurrencyRepo.seed(eurCurrency, usdCurrency, rubCurrency, rsdCurrency, kztCurrency, aedCurrency)
    }

    private fun buildRepo(
        api: ExchangeRateApi = FakeExchangeRateApi(successDto),
        online: Boolean = true,
    ): CurrencyRateRepositoryImpl =
        CurrencyRateRepositoryImpl(
            dao = fakeDao,
            exchangeRateApi = api,
            connectivityChecker = fakeConnectivityChecker(online),
            currencyRepository = fakeCurrencyRepo,
            clock = fixedClock,
            ioDispatcher = dispatcher,
        )

    @Test
    fun `refreshRatesFromNetwork happy path upserts 5 EUR-to-X rates skipping EUR self-rate and unknown JPY`() =
        runTest(dispatcher) {
            val result = buildRepo().refreshRatesFromNetwork()

            assertTrue(result.isSuccess)
            assertEquals(5, result.getOrThrow())
            assertEquals(5, fakeDao.upserted.size)
        }

    @Test
    fun `refreshRatesFromNetwork sets updatedAt to the fixed clock instant for all upserted rates`() =
        runTest(dispatcher) {
            buildRepo().refreshRatesFromNetwork()

            fakeDao.upserted.forEach { entity ->
                assertEquals(fixedInstant.toEpochMilli(), entity.updatedAt)
            }
        }

    @Test
    fun `refreshRatesFromNetwork stores all upserted rates with fromCurrencyId equal to EUR id`() =
        runTest(dispatcher) {
            buildRepo().refreshRatesFromNetwork()

            fakeDao.upserted.forEach { entity ->
                assertEquals(eurCurrency.id, entity.fromCurrencyId)
            }
        }

    @Test
    fun `refreshRatesFromNetwork skips EUR-to-EUR self-rate`() =
        runTest(dispatcher) {
            buildRepo().refreshRatesFromNetwork()

            val selfRateUpserted =
                fakeDao.upserted.any {
                    it.fromCurrencyId == eurCurrency.id && it.toCurrencyId == eurCurrency.id
                }
            assertFalse(selfRateUpserted)
        }

    @Test
    fun `refreshRatesFromNetwork skips unknown currency code JPY not present in CurrencyRepository`() =
        runTest(dispatcher) {
            buildRepo().refreshRatesFromNetwork()

            val knownTargetIds =
                setOf(
                    usdCurrency.id,
                    rubCurrency.id,
                    rsdCurrency.id,
                    kztCurrency.id,
                    aedCurrency.id,
                )
            fakeDao.upserted.forEach { entity ->
                assertTrue(
                    "unexpected toCurrencyId ${entity.toCurrencyId}",
                    entity.toCurrencyId in knownTargetIds,
                )
            }
        }

    @Test
    fun `refreshRatesFromNetwork stores USD rate without inversion`() =
        runTest(dispatcher) {
            buildRepo().refreshRatesFromNetwork()

            val usdRate = fakeDao.upserted.first { it.toCurrencyId == usdCurrency.id }
            assertEquals(1.0718, usdRate.rate, 0.0001)
        }

    @Test
    fun `refreshRatesFromNetwork stores RUB rate without inversion`() =
        runTest(dispatcher) {
            buildRepo().refreshRatesFromNetwork()

            val rubRate = fakeDao.upserted.first { it.toCurrencyId == rubCurrency.id }
            assertEquals(97.48, rubRate.rate, 0.0001)
        }

    @Test
    fun `refreshRatesFromNetwork returns failure and upserts nothing when result field is not success`() =
        runTest(dispatcher) {
            val errorDto = successDto.copy(result = "error", rates = emptyMap())
            val result = buildRepo(api = FakeExchangeRateApi(errorDto)).refreshRatesFromNetwork()

            assertTrue(result.isFailure)
            assertTrue(fakeDao.upserted.isEmpty())
        }

    @Test
    fun `refreshRatesFromNetwork wraps non-success result field in SyncException with Server error`() =
        runTest(dispatcher) {
            val errorDto = successDto.copy(result = "error", rates = emptyMap())
            val result = buildRepo(api = FakeExchangeRateApi(errorDto)).refreshRatesFromNetwork()

            val cause = result.exceptionOrNull()
            assertTrue(cause is SyncException)
            assertEquals(SyncError.Server, (cause as SyncException).syncError)
        }

    @Test
    fun `refreshRatesFromNetwork returns failure and upserts nothing when device is offline`() =
        runTest(dispatcher) {
            val result = buildRepo(online = false).refreshRatesFromNetwork()

            assertTrue(result.isFailure)
            assertTrue(fakeDao.upserted.isEmpty())
        }

    @Test
    fun `refreshRatesFromNetwork wraps offline state in SyncException with Network error`() =
        runTest(dispatcher) {
            val result = buildRepo(online = false).refreshRatesFromNetwork()

            val cause = result.exceptionOrNull()
            assertTrue(cause is SyncException)
            assertEquals(SyncError.Network, (cause as SyncException).syncError)
        }

    @Test
    fun `refreshRatesFromNetwork returns success with zero count when rates map contains only EUR self-rate`() =
        runTest(dispatcher) {
            val emptyRatesDto = successDto.copy(rates = mapOf("EUR" to 1.0))
            val result = buildRepo(api = FakeExchangeRateApi(emptyRatesDto)).refreshRatesFromNetwork()

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrThrow())
            assertTrue(fakeDao.upserted.isEmpty())
        }

    private fun currency(
        id: Long,
        code: String,
    ) =
        Currency(
            id = id,
            code = code,
            symbol = code,
            name = code,
            decimalDigits = 2,
            isActive = true,
            sortOrder = id.toInt(),
        )
}
