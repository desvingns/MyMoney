package com.kshavrin.mymoney.feature.transaction.rate

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.core.testing.fake.FakeCurrencyRepository
import com.kshavrin.mymoney.feature.transaction.util.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.util.Locale

class CurrencyRateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var currencyRepository: FakeCurrencyRepository
    private lateinit var currencyRateRepository: FakeCurrencyRateRepository

    private val usd =
        Currency(
            id = 1L,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private val eur =
        Currency(
            id = 2L,
            code = "EUR",
            symbol = "EUR",
            name = "Euro",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 1,
        )

    @Before
    fun setUp() {
        currencyRepository = FakeCurrencyRepository()
        currencyRateRepository = FakeCurrencyRateRepository()
        currencyRepository.seed(usd, eur)
    }

    private fun buildViewModel(): CurrencyRateViewModel =
        CurrencyRateViewModel(
            currencyRepository = currencyRepository,
            currencyRateRepository = currencyRateRepository,
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        CurrencyRateViewModel.KEY_FROM_ID to usd.id,
                        CurrencyRateViewModel.KEY_TO_ID to eur.id,
                    ),
                ),
        )

    @Test
    fun `SaveClicked for a new pair upserts id zero and stores a new rate`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("0.9"))

            viewModel.actions.test {
                viewModel.onEvent(CurrencyRateEvent.SaveClicked)

                assertEquals(CurrencyRateAction.NavigateBackWithRate(0.9), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, currencyRateRepository.upserts.size)
            assertEquals(0L, currencyRateRepository.upserts.single().id)
            assertEquals(0.9, currencyRateRepository.requireStoredRate(usd.id, eur.id).rate, 0.0001)
        }

    @Test
    fun `SaveClicked for an existing pair reuses the stored id and updates that rate`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.US)
                currencyRateRepository.seed(
                    CurrencyRate(
                        id = 7L,
                        fromCurrencyId = usd.id,
                        toCurrencyId = eur.id,
                        rate = 0.9,
                        updatedAt = Instant.parse("2026-06-11T10:00:00Z"),
                    ),
                )
                val viewModel = buildViewModel()
                advanceUntilIdle()

                assertEquals("0.9", viewModel.state.value.rateInput)

                viewModel.onEvent(CurrencyRateEvent.RateInputChanged("0.95"))

                viewModel.actions.test {
                    viewModel.onEvent(CurrencyRateEvent.SaveClicked)

                    assertEquals(CurrencyRateAction.NavigateBackWithRate(0.95), awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }

                assertEquals(1, currencyRateRepository.upserts.size)
                assertEquals(7L, currencyRateRepository.upserts.single().id)
                assertEquals(1, currencyRateRepository.storedRates().size)
                assertEquals(0.95, currencyRateRepository.requireStoredRate(usd.id, eur.id).rate, 0.0001)
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    @Test
    fun `existing rate is displayed with the Russian decimal separator`() =
        runTest {
            val originalLocale = Locale.getDefault()
            try {
                Locale.setDefault(Locale.forLanguageTag("ru-RU"))
                currencyRateRepository.seed(
                    CurrencyRate(
                        id = 7L,
                        fromCurrencyId = usd.id,
                        toCurrencyId = eur.id,
                        rate = 0.95,
                        updatedAt = Instant.parse("2026-06-11T10:00:00Z"),
                    ),
                )

                val viewModel = buildViewModel()
                advanceUntilIdle()

                assertEquals("0,95", viewModel.state.value.rateInput)
            } finally {
                Locale.setDefault(originalLocale)
            }
        }

    @Test
    fun `SaveClicked with an unparsable rate shows the invalid-rate error and does not write`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("abc"))
            viewModel.onEvent(CurrencyRateEvent.SaveClicked)

            assertEquals(R.string.currency_rate_invalid, viewModel.state.value.errorBannerRes)
            assertTrue(currencyRateRepository.upserts.isEmpty())
        }

    // --- comma as decimal separator ---

    @Test
    fun `rate input with comma separator is parsed as valid and isValid becomes true`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("0,855"))

            assertTrue(viewModel.state.value.isValid)
            assertEquals(0.86, viewModel.state.value.rate!!, 0.0001)
        }

    @Test
    fun `rate input with comma separator rounds half up to two decimals`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("10,005"))

            assertEquals(10.01, viewModel.state.value.rate!!, 0.0001)
        }

    @Test
    fun `SaveClicked with comma-separated rate upserts and emits NavigateBackWithRate`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("0,855"))

            viewModel.actions.test {
                viewModel.onEvent(CurrencyRateEvent.SaveClicked)
                advanceUntilIdle()

                val action = awaitItem()
                assertTrue(action is CurrencyRateAction.NavigateBackWithRate)
                assertEquals(0.86, (action as CurrencyRateAction.NavigateBackWithRate).rate, 0.0001)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, currencyRateRepository.upserts.size)
            assertEquals(0.86, currencyRateRepository.requireStoredRate(usd.id, eur.id).rate, 0.0001)
        }

    @Test
    fun `rate input with comma does not trigger the invalid-rate error`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("1,5"))
            viewModel.onEvent(CurrencyRateEvent.SaveClicked)
            advanceUntilIdle()

            assertNull(viewModel.state.value.errorBannerRes)
        }

    @Test
    fun `rate input with more than two decimals rounds half up before save`() =
        runTest {
            val viewModel = buildViewModel()
            advanceUntilIdle()

            viewModel.onEvent(CurrencyRateEvent.RateInputChanged("12.3456"))

            assertEquals(12.35, viewModel.state.value.rate!!, 0.0001)

            viewModel.actions.test {
                viewModel.onEvent(CurrencyRateEvent.SaveClicked)
                advanceUntilIdle()

                assertEquals(CurrencyRateAction.NavigateBackWithRate(12.35), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(12.35, currencyRateRepository.requireStoredRate(usd.id, eur.id).rate, 0.0001)
        }

    private class FakeCurrencyRateRepository : CurrencyRateRepository {
        val upserts: MutableList<CurrencyRate> = mutableListOf()

        private val rates = MutableStateFlow<List<CurrencyRate>>(emptyList())

        fun seed(vararg items: CurrencyRate) {
            rates.value = (rates.value + items).distinctBy { it.id }
        }

        fun storedRates(): List<CurrencyRate> = rates.value

        fun requireStoredRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate =
            rates.value.first {
                it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId
            }

        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? =
            rates.value.firstOrNull {
                it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId
            }

        override fun observeAll(): Flow<List<CurrencyRate>> = rates.asStateFlow()

        override suspend fun upsert(rate: CurrencyRate): Long {
            upserts += rate

            val existingPair = findRate(rate.fromCurrencyId, rate.toCurrencyId)
            return when {
                rate.id == 0L && existingPair != null -> 0L
                rate.id == 0L -> {
                    val newId = (rates.value.maxOfOrNull { it.id } ?: 0L) + 1L
                    rates.value = rates.value + rate.copy(id = newId)
                    newId
                }
                rates.value.any { it.id == rate.id } -> {
                    rates.value =
                        rates.value.map { current ->
                            if (current.id == rate.id) rate else current
                        }
                    rate.id
                }
                else -> 0L
            }
        }

        override suspend fun deleteById(id: Long) {
            rates.value = rates.value.filterNot { it.id == id }
        }

        override suspend fun refreshRatesFromNetwork(): Result<Int> = Result.success(0)
    }
}
