package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.CurrencyRate
import com.kshavrin.mymoney.core.domain.repository.CurrencyRateRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tests for ResolveRateUseCase.
 *
 * All rates follow SPEC-01 (currency-exchange-rate epic), base=EUR.
 * Clock is fixed so today is always deterministic. BigDecimal equality uses
 * compareTo (ignores scale). Fakes only — no mocking framework.
 */
class ResolveRateUseCaseTest {
    // -------------------------------------------------------------------------
    // Controllable fakes
    // -------------------------------------------------------------------------

    /**
     * Controllable fake for CurrencyRateRepository.
     *
     * - [refreshResult] controls what refreshRatesFromNetwork() returns.
     * - [onRefresh] is invoked right before returning [refreshResult], so the
     *   test can update stored rates to simulate a successful network refresh.
     * - [refreshCalled] records whether the method was invoked at all.
     */
    private inner class ControllableFakeCurrencyRateRepository : CurrencyRateRepository {
        private val state = MutableStateFlow<List<CurrencyRate>>(emptyList())

        var refreshResult: Result<Int> = Result.success(0)
        var onRefresh: suspend () -> Unit = {}
        var refreshCalled = false

        fun seedRate(rate: CurrencyRate) {
            val id = if (rate.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0L) + 1L else rate.id
            state.value = state.value.filterNot { it.id == id } + rate.copy(id = id)
        }

        fun updateRate(rate: CurrencyRate) {
            state.value = state.value.filterNot {
                it.fromCurrencyId == rate.fromCurrencyId && it.toCurrencyId == rate.toCurrencyId
            } + rate
        }

        override suspend fun findRate(
            fromCurrencyId: Long,
            toCurrencyId: Long,
        ): CurrencyRate? =
            state.value.firstOrNull {
                it.fromCurrencyId == fromCurrencyId && it.toCurrencyId == toCurrencyId
            }

        override fun observeAll(): Flow<List<CurrencyRate>> = state.asStateFlow()

        override suspend fun upsert(rate: CurrencyRate): Long {
            seedRate(rate)
            return rate.id
        }

        override suspend fun deleteById(id: Long) {
            state.value = state.value.filterNot { it.id == id }
        }

        override suspend fun refreshRatesFromNetwork(): Result<Int> {
            refreshCalled = true
            onRefresh()
            return refreshResult
        }
    }

    /**
     * Controllable fake for CurrencyRepository.
     * Backed by a simple list — seed currencies before the test.
     */
    private inner class ControllableFakeCurrencyRepository : CurrencyRepository {
        private val state = MutableStateFlow<List<Currency>>(emptyList())

        fun seed(vararg currencies: Currency) {
            state.value = (state.value + currencies).distinctBy { it.id }
        }

        override fun observeActive(): Flow<List<Currency>> = state.asStateFlow()

        override fun observeAll(): Flow<List<Currency>> = state.asStateFlow()

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
    }

    // -------------------------------------------------------------------------
    // Currency + rate fixtures
    // -------------------------------------------------------------------------

    private val zone: ZoneId = ZoneId.of("UTC")

    /** Fixed "today" seen by the use case: 2026-06-20 UTC. */
    private val todayInstant: Instant = Instant.parse("2026-06-20T12:00:00Z")
    private val today: LocalDate = LocalDate.of(2026, 6, 20)

    /** A fixed clock that returns [todayInstant] in [zone]. */
    private val fixedClock: Clock = Clock.fixed(todayInstant, zone)

    /** An instant that is one day in the past — stored rate considered stale. */
    private val yesterdayInstant: Instant = Instant.parse("2026-06-19T12:00:00Z")

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
        name: String,
    ): Currency =
        Currency(
            id = id,
            code = code,
            symbol = symbol,
            name = name,
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private val eur = currency(id = 1L, code = "EUR", symbol = "€", name = "Euro")
    private val usd = currency(id = 2L, code = "USD", symbol = "$", name = "US Dollar")
    private val rub = currency(id = 3L, code = "RUB", symbol = "₽", name = "Russian Ruble")

    /** SPEC-01 rates — stored as EUR→X in the fake repository. */
    private val eurToUsdRate = 1.146893
    private val eurToRubRate = 84.181245

    /**
     * Creates a CurrencyRate with the given fromCurrencyId, toCurrencyId, rate, and updatedAt.
     */
    private fun rate(
        fromCurrencyId: Long,
        toCurrencyId: Long,
        rate: Double,
        updatedAt: Instant,
    ) = CurrencyRate(
        id = 0L,
        fromCurrencyId = fromCurrencyId,
        toCurrencyId = toCurrencyId,
        rate = rate,
        updatedAt = updatedAt,
    )

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    private fun buildUseCase(
        rateRepo: ControllableFakeCurrencyRateRepository,
        currencyRepo: ControllableFakeCurrencyRepository,
    ): ResolveRateUseCase =
        ResolveRateUseCase(
            currencyRateRepository = rateRepo,
            currencyRepository = currencyRepo,
            convertMoney = ConvertMoneyUseCase(),
            clock = fixedClock,
            zoneId = zone,
        )

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Seeds EUR + target currencies and the EUR-based rates with a given updatedAt. */
    private fun seedFreshSetup(
        rateRepo: ControllableFakeCurrencyRateRepository,
        currencyRepo: ControllableFakeCurrencyRepository,
        updatedAt: Instant,
    ) {
        currencyRepo.seed(eur, usd, rub)
        rateRepo.seedRate(rate(eur.id, usd.id, eurToUsdRate, updatedAt))
        rateRepo.seedRate(rate(eur.id, rub.id, eurToRubRate, updatedAt))
    }

    private fun assertBigDecimalEquals(
        message: String,
        expected: BigDecimal,
        actual: BigDecimal?,
    ) {
        assertNotNull("$message — value must not be null", actual)
        assertEquals(message, 0, expected.compareTo(actual))
    }

    // =========================================================================
    // Scenario 1 — fresh rate (updatedAt == today): no network call, no staleness
    // =========================================================================

    @Test
    fun `fresh rate is returned without triggering network refresh`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            seedFreshSetup(rateRepo, currencyRepo, updatedAt = todayInstant)

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertFalse("refreshRatesFromNetwork must NOT be called for a fresh rate", rateRepo.refreshCalled)
            assertFalse("stale must be false for a fresh rate", result.stale)
            assertFalse("refreshedNow must be false when no refresh was triggered", result.refreshedNow)
            assertFalse("missing must be false when both rates are present", result.missing)
            assertEquals("lastUpdated must equal today", today, result.lastUpdated)
            assertNotNull("crossRate must be non-null", result.crossRate)
        }

    @Test
    fun `fresh EUR-as-from pair is returned without a network call`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            currencyRepo.seed(eur, rub)
            rateRepo.seedRate(rate(eur.id, rub.id, eurToRubRate, todayInstant))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(eur, rub)

            assertFalse("no refresh for fresh EUR-base pair", rateRepo.refreshCalled)
            assertFalse("stale must be false", result.stale)
            assertFalse("refreshedNow must be false", result.refreshedNow)
            assertFalse("missing must be false", result.missing)
        }

    // =========================================================================
    // Scenario 2 — stale + refresh succeeds
    // =========================================================================

    @Test
    fun `stale rate triggers network refresh and returns updated rate when refresh succeeds`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            seedFreshSetup(rateRepo, currencyRepo, updatedAt = yesterdayInstant)

            rateRepo.onRefresh = {
                // Simulate network updating stored rates to today
                rateRepo.updateRate(rate(eur.id, usd.id, eurToUsdRate, todayInstant))
                rateRepo.updateRate(rate(eur.id, rub.id, eurToRubRate, todayInstant))
            }
            rateRepo.refreshResult = Result.success(2)

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertTrue("refreshRatesFromNetwork must be called for a stale rate", rateRepo.refreshCalled)
            assertTrue("refreshedNow must be true when refresh succeeded", result.refreshedNow)
            assertFalse("stale must be false after successful refresh", result.stale)
            assertFalse("missing must be false", result.missing)
            assertEquals("lastUpdated must equal today after refresh", today, result.lastUpdated)
            assertNotNull("crossRate must be non-null after refresh", result.crossRate)
        }

    // =========================================================================
    // Scenario 3 — stale + refresh fails (offline)
    // =========================================================================

    @Test
    fun `stale rate with failed refresh returns last stored rate without throwing`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            seedFreshSetup(rateRepo, currencyRepo, updatedAt = yesterdayInstant)

            rateRepo.refreshResult = Result.failure(RuntimeException("offline"))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertTrue("refreshRatesFromNetwork must be called for a stale rate", rateRepo.refreshCalled)
            assertFalse("refreshedNow must be false when refresh failed", result.refreshedNow)
            assertTrue("stale must be true when refresh failed", result.stale)
            assertFalse("missing must be false — stale stored rate is still returned", result.missing)
            assertNotNull("crossRate must be non-null from the stored stale rate", result.crossRate)
            assertEquals(
                "lastUpdated must be yesterday (the stale stored date)",
                LocalDate.of(2026, 6, 19),
                result.lastUpdated,
            )
        }

    @Test
    fun `stale rate with failed refresh does not propagate the exception`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            seedFreshSetup(rateRepo, currencyRepo, updatedAt = yesterdayInstant)

            rateRepo.refreshResult = Result.failure(IllegalStateException("network unavailable"))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            // Must complete without throwing
            val result = useCase(usd, rub)
            assertNotNull("result must be returned even when refresh throws", result)
        }

    // =========================================================================
    // Scenario 4 — missing rate
    // =========================================================================

    @Test
    fun `missing returns true when no stored rate exists for the pair`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            // No rates seeded at all; refresh also yields nothing
            currencyRepo.seed(eur, usd, rub)
            rateRepo.refreshResult = Result.success(0)

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertTrue("missing must be true when no rate is stored", result.missing)
            assertNull("crossRate must be null when rate is missing", result.crossRate)
        }

    @Test
    fun `missing true when EUR is not seeded in CurrencyRepository`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            // EUR absent from repo — use case cannot locate the base currency
            currencyRepo.seed(usd, rub)
            rateRepo.seedRate(rate(eur.id, usd.id, eurToUsdRate, todayInstant))
            rateRepo.seedRate(rate(eur.id, rub.id, eurToRubRate, todayInstant))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertTrue("missing must be true when EUR base currency is absent", result.missing)
            assertNull("crossRate must be null when base currency lookup fails", result.crossRate)
        }

    // =========================================================================
    // Scenario 5 — cross-rate correctness through EUR
    // =========================================================================

    @Test
    fun `cross-rate USD to RUB is computed correctly through EUR base`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            seedFreshSetup(rateRepo, currencyRepo, updatedAt = todayInstant)

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertFalse("missing must be false", result.missing)
            assertNotNull("crossRate must be non-null", result.crossRate)

            // crossRate = rateTo(EUR→RUB) / rateFrom(EUR→USD)
            //           = 84.181245 / 1.146893
            //           ≈ 73.398...
            val expectedCrossRate =
                BigDecimal(eurToRubRate).divide(
                    BigDecimal(eurToUsdRate),
                    java.math.MathContext.DECIMAL64,
                )
            // Allow a small tolerance — the use case uses DECIMAL64 math
            val tolerance = BigDecimal("0.0001")
            val diff = result.crossRate!!.subtract(expectedCrossRate).abs()
            assertTrue(
                "crossRate expected ~${expectedCrossRate.toPlainString()} but was ${result.crossRate}",
                diff.compareTo(tolerance) <= 0,
            )
        }

    @Test
    fun `cross-rate EUR to RUB equals the stored EUR-to-RUB rate`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            currencyRepo.seed(eur, rub)
            rateRepo.seedRate(rate(eur.id, rub.id, eurToRubRate, todayInstant))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(eur, rub)

            assertFalse("missing must be false for EUR→RUB", result.missing)
            assertBigDecimalEquals(
                "EUR→RUB crossRate must equal the stored rate (EUR is implicit 1)",
                BigDecimal.valueOf(eurToRubRate),
                result.crossRate,
            )
        }

    @Test
    fun `cross-rate RUB to EUR equals 1 over the stored EUR-to-RUB rate`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            currencyRepo.seed(eur, rub)
            rateRepo.seedRate(rate(eur.id, rub.id, eurToRubRate, todayInstant))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(rub, eur)

            assertFalse("missing must be false for RUB→EUR", result.missing)
            val expectedInverse =
                BigDecimal.ONE.divide(
                    BigDecimal(eurToRubRate),
                    java.math.MathContext.DECIMAL64,
                )
            val tolerance = BigDecimal("0.0001")
            val diff = result.crossRate!!.subtract(expectedInverse).abs()
            assertTrue(
                "RUB→EUR crossRate must be ~${expectedInverse.toPlainString()} but was ${result.crossRate}",
                diff.compareTo(tolerance) <= 0,
            )
        }

    // =========================================================================
    // Identity conversion
    // =========================================================================

    @Test
    fun `same currency returns crossRate one without a network call`() =
        runTest {
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            currencyRepo.seed(usd)

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, usd)

            assertFalse("no refresh for identity pair", rateRepo.refreshCalled)
            assertFalse("not missing", result.missing)
            assertFalse("not stale", result.stale)
            assertFalse("refreshedNow false", result.refreshedNow)
            assertNull("lastUpdated null for identity", result.lastUpdated)
            assertBigDecimalEquals("crossRate must be ONE for identity", BigDecimal.ONE, result.crossRate)
        }

    // =========================================================================
    // Staleness boundary: null updatedAt treated as stale
    // =========================================================================

    @Test
    fun `absent updatedAt (null relevantLastUpdated) is treated as stale and triggers refresh`() =
        runTest {
            // If no rate exists at all, relevantLastUpdated returns null → stale=true → refresh attempted
            val rateRepo = ControllableFakeCurrencyRateRepository()
            val currencyRepo = ControllableFakeCurrencyRepository()
            currencyRepo.seed(eur, usd, rub)
            // No rates seeded — relevantLastUpdated returns null
            rateRepo.refreshResult = Result.failure(RuntimeException("still offline"))

            val useCase = buildUseCase(rateRepo, currencyRepo)
            val result = useCase(usd, rub)

            assertTrue("refresh must be attempted when stored rate is absent", rateRepo.refreshCalled)
            assertTrue("missing must be true when no rate was ever stored", result.missing)
            assertTrue("stale must be true when lastUpdated is null", result.stale)
        }
}
