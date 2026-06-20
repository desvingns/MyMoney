package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Deterministic tests for ConvertMoneyUseCase.
 *
 * All worked examples come from SPEC 01 (currency-exchange-rate epic).
 * Rates stored as EUR→X (base=EUR).  RatesLookup is a fun interface — the
 * test provides a simple map-backed lambda, no mocking framework needed.
 *
 * BigDecimal equality uses compareTo (ignores scale); scale assertions are
 * explicit where the SPEC pins the precision contract.
 */
class ConvertMoneyUseCaseTest {
    private val useCase = ConvertMoneyUseCase()

    // ---- currency fixtures -----------------------------------------------

    private fun currency(
        id: Long,
        code: String,
        symbol: String,
        name: String,
        decimalDigits: Int = 2,
    ) = Currency(
        id = id,
        code = code,
        symbol = symbol,
        name = name,
        decimalDigits = decimalDigits,
        isActive = true,
        sortOrder = 0,
    )

    private val eur = currency(id = 1L, code = "EUR", symbol = "€", name = "Euro")
    private val usd = currency(id = 2L, code = "USD", symbol = "$", name = "US Dollar")
    private val rub = currency(id = 3L, code = "RUB", symbol = "₽", name = "Russian Ruble")
    private val kzt = currency(id = 4L, code = "KZT", symbol = "₸", name = "Kazakhstani Tenge")
    private val aed = currency(id = 5L, code = "AED", symbol = "د.إ", name = "UAE Dirham")

    // ---- SPEC-01 seed rates (EUR→X) -------------------------------------

    private val specRates: Map<String, BigDecimal> =
        mapOf(
            "USD" to BigDecimal("1.146893"),
            "RUB" to BigDecimal("84.181245"),
            "KZT" to BigDecimal("559.417885"),
            "AED" to BigDecimal("4.210630"),
        )

    /** Lookup backed by specRates; EUR itself returns BigDecimal.ONE (handled inside the use-case). */
    private fun specLookup(): RatesLookup =
        RatesLookup { currency ->
            specRates[currency.code]
        }

    /** Lookup that has no entries at all — simulates a database with no rates synced yet. */
    private fun emptyLookup(): RatesLookup = RatesLookup { null }

    // ---- helper assertions -----------------------------------------------

    private fun assertAmount(
        actual: BigDecimal,
        expected: String,
    ) {
        assertEquals(
            "Amount mismatch: expected $expected but got $actual",
            0,
            BigDecimal(expected).compareTo(actual),
        )
    }

    private fun assertScale(
        actual: BigDecimal,
        expected: Int,
    ) {
        assertEquals("Scale mismatch", expected, actual.scale())
    }

    private fun assertConverted(
        result: ConversionResult,
        expectedAmount: String,
        expectedScale: Int = 2,
    ): ConversionResult.Converted {
        val converted = result as ConversionResult.Converted
        assertAmount(converted.money.amount, expectedAmount)
        assertScale(converted.money.amount, expectedScale)
        return converted
    }

    private fun assertRateMissing(
        result: ConversionResult,
        expectedCode: String,
    ) {
        val missing = result as ConversionResult.RateMissing
        assertEquals(expectedCode, missing.currencyCode)
    }

    // =====================================================================
    // SPEC worked examples
    // =====================================================================

    @Test
    fun `converts 100 USD to RUB using cross-rate EUR base`() {
        // crossRate = rate(EUR→RUB) / rate(EUR→USD) = 84.181245 / 1.146893
        // converted  = 100 × crossRate = 7339.94…  → setScale(2, HALF_UP) = 7339.94
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = usd,
                to = rub,
                rates = specLookup(),
            )
        assertConverted(result, "7339.94")
    }

    @Test
    fun `converts 1000 RUB to EUR through base`() {
        // crossRate = 1 / 84.181245  (EUR→EUR = 1, so rateTo = 1)
        // converted  = 1000 / 84.181245 = 11.88…  → 11.88
        val result =
            useCase(
                amount = BigDecimal("1000"),
                from = rub,
                to = eur,
                rates = specLookup(),
            )
        assertConverted(result, "11.88")
    }

    @Test
    fun `converts 100 EUR to KZT using EUR-as-from shortcut`() {
        // crossRate = rate(EUR→KZT) / 1 = 559.417885
        // converted  = 100 × 559.417885 = 55941.79 (scale=2)
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = eur,
                to = kzt,
                rates = specLookup(),
            )
        assertConverted(result, "55941.79")
    }

    @Test
    fun `identity conversion returns same amount with scale 2`() {
        // from == to → cross-rate = 1, amount scaled to min(decimalDigits, 2)
        val result =
            useCase(
                amount = BigDecimal("50"),
                from = usd,
                to = usd,
                rates = specLookup(),
            )
        val converted = assertConverted(result, "50.00")
        assertEquals(0, BigDecimal.ONE.compareTo(converted.crossRate))
        assertScale(converted.money.amount, 2)
    }

    // =====================================================================
    // RateMissing cases
    // =====================================================================

    @Test
    fun `returns RateMissing for from-currency when rate is absent`() {
        // USD rate absent → RateMissing("USD")
        val noUsdLookup =
            RatesLookup { currency ->
                if (currency.code == "USD") null else specRates[currency.code]
            }
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = usd,
                to = rub,
                rates = noUsdLookup,
            )
        assertRateMissing(result, "USD")
    }

    @Test
    fun `returns RateMissing for to-currency when rate is absent`() {
        // RUB rate absent → RateMissing("RUB") (from-rate USD is present)
        val noRubLookup =
            RatesLookup { currency ->
                if (currency.code == "RUB") null else specRates[currency.code]
            }
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = usd,
                to = rub,
                rates = noRubLookup,
            )
        assertRateMissing(result, "RUB")
    }

    @Test
    fun `returns RateMissing with correct code when all rates are absent`() {
        // lookup returns null for everything → first failure = from-rate
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = usd,
                to = rub,
                rates = emptyLookup(),
            )
        assertRateMissing(result, "USD")
    }

    // =====================================================================
    // Edge cases
    // =====================================================================

    @Test
    fun `zero amount converts to zero with target scale`() {
        val result =
            useCase(
                amount = BigDecimal.ZERO,
                from = usd,
                to = rub,
                rates = specLookup(),
            )
        val converted = assertConverted(result, "0.00")
        assertScale(converted.money.amount, 2)
    }

    @Test
    fun `zero amount identity conversion produces scaled zero`() {
        val result =
            useCase(
                amount = BigDecimal.ZERO,
                from = eur,
                to = eur,
                rates = emptyLookup(),
            )
        val converted = assertConverted(result, "0.00")
        assertScale(converted.money.amount, 2)
        assertEquals(0, BigDecimal.ONE.compareTo(converted.crossRate))
    }

    @Test
    fun `EUR-as-to sets cross-rate to inverse of from-rate`() {
        // crossRate = 1 / rate(EUR→USD) = 1 / 1.146893
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = usd,
                to = eur,
                rates = specLookup(),
            )
        val converted = result as ConversionResult.Converted
        // cross = 1/1.146893 ≈ 0.87188…
        val expected = BigDecimal.ONE.divide(BigDecimal("1.146893"), java.math.MathContext.DECIMAL64)
        assertEquals(0, expected.compareTo(converted.crossRate))
        // resulting money: 100 × (1/1.146893) ≈ 87.19 EUR
        assertAmount(converted.money.amount, "87.19")
        assertEquals(eur, converted.money.currency)
    }

    @Test
    fun `EUR-as-from sets cross-rate equal to EUR-to-to rate`() {
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = eur,
                to = usd,
                rates = specLookup(),
            )
        val converted = result as ConversionResult.Converted
        // crossRate = rate(EUR→USD) / rate(EUR→EUR) = 1.146893 / 1 = 1.146893
        assertEquals(0, BigDecimal("1.146893").compareTo(converted.crossRate))
        assertAmount(converted.money.amount, "114.69")
        assertEquals(usd, converted.money.currency)
    }

    @Test
    fun `identity conversion cross-rate is exactly one`() {
        val result =
            useCase(
                amount = BigDecimal("999.99"),
                from = rub,
                to = rub,
                rates = specLookup(),
            )
        val converted = result as ConversionResult.Converted
        assertEquals(0, BigDecimal.ONE.compareTo(converted.crossRate))
        assertAmount(converted.money.amount, "999.99")
        assertEquals(rub, converted.money.currency)
    }

    @Test
    fun `converted money carries the to-currency`() {
        val result =
            useCase(
                amount = BigDecimal("100"),
                from = usd,
                to = kzt,
                rates = specLookup(),
            )
        val converted = result as ConversionResult.Converted
        assertEquals(kzt, converted.money.currency)
    }

    @Test
    fun `EUR-as-to missing from-rate returns RateMissing`() {
        // Even when to==EUR (whose rate is always 1), a missing from-rate
        // must still short-circuit and report RateMissing(from.code).
        val result =
            useCase(
                amount = BigDecimal("50"),
                from = aed,
                to = eur,
                rates = emptyLookup(),
            )
        assertRateMissing(result, "AED")
    }

    @Test
    fun `scale is capped at 2 even when currency decimalDigits exceeds 2`() {
        // decimalDigits=4 but Money.toMoneyScale caps at min(4,2)=2
        val highPrecision = currency(id = 6L, code = "HPX", symbol = "H", name = "HighPrec", decimalDigits = 4)
        val hpxRates =
            RatesLookup { currency ->
                if (currency.code == "HPX") BigDecimal("2.000000") else specRates[currency.code]
            }
        val result =
            useCase(
                amount = BigDecimal("10"),
                from = eur,
                to = highPrecision,
                rates = hpxRates,
            )
        val converted = result as ConversionResult.Converted
        // 10 × 2.0 = 20.00, scale = min(4,2) = 2
        assertAmount(converted.money.amount, "20.00")
        assertScale(converted.money.amount, 2)
    }

    @Test
    fun `scale is 0 for zero-decimal currency (JPY-like)`() {
        val jpy = currency(id = 7L, code = "JPY", symbol = "¥", name = "Japanese Yen", decimalDigits = 0)
        val jpyRates =
            RatesLookup { currency ->
                if (currency.code == "JPY") BigDecimal("160.500000") else specRates[currency.code]
            }
        val result =
            useCase(
                amount = BigDecimal("10"),
                from = usd,
                to = jpy,
                rates = jpyRates,
            )
        val converted = result as ConversionResult.Converted
        // crossRate = 160.5 / 1.146893; converted = 10 × crossRate → scale=0
        assertScale(converted.money.amount, 0)
    }
}
