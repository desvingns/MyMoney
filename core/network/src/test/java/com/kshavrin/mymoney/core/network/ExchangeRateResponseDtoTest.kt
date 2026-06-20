package com.kshavrin.mymoney.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeRateResponseDtoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val successJson =
        """
        {
          "result": "success",
          "documentation": "https://www.exchangerate-api.com/docs",
          "terms_of_use": "https://www.exchangerate-api.com/terms",
          "time_last_update_unix": 1750377601,
          "time_last_update_utc": "Fri, 20 Jun 2025 00:00:01 +0000",
          "time_next_update_unix": 1750464001,
          "time_next_update_utc": "Sat, 21 Jun 2025 00:00:01 +0000",
          "base_code": "EUR",
          "rates": {
            "EUR": 1.0,
            "USD": 1.0718,
            "RUB": 97.48,
            "RSD": 117.14,
            "KZT": 546.38,
            "AED": 3.9364,
            "JPY": 168.97
          }
        }
        """.trimIndent()

    @Test
    fun `parses result field correctly`() {
        val dto = json.decodeFromString<ExchangeRateResponseDto>(successJson)

        assertEquals("success", dto.result)
    }

    @Test
    fun `parses base_code field correctly`() {
        val dto = json.decodeFromString<ExchangeRateResponseDto>(successJson)

        assertEquals("EUR", dto.baseCode)
    }

    @Test
    fun `parses time_last_update_unix field correctly`() {
        val dto = json.decodeFromString<ExchangeRateResponseDto>(successJson)

        assertEquals(1750377601L, dto.timeLastUpdateUnix)
    }

    @Test
    fun `parses all expected currency rates including EUR self-rate`() {
        val dto = json.decodeFromString<ExchangeRateResponseDto>(successJson)

        assertEquals(7, dto.rates.size)
        assertEquals(1.0718, dto.rates["USD"]!!, 0.0001)
        assertEquals(97.48, dto.rates["RUB"]!!, 0.0001)
        assertEquals(117.14, dto.rates["RSD"]!!, 0.0001)
        assertEquals(546.38, dto.rates["KZT"]!!, 0.0001)
        assertEquals(3.9364, dto.rates["AED"]!!, 0.0001)
        assertEquals(168.97, dto.rates["JPY"]!!, 0.0001)
        assertEquals(1.0, dto.rates["EUR"]!!, 0.0001)
    }

    @Test
    fun `ignores unknown top-level keys from open_er_api response`() {
        val dto = json.decodeFromString<ExchangeRateResponseDto>(successJson)

        assertTrue(dto.rates.isNotEmpty())
    }

    @Test
    fun `parses error result correctly`() {
        val errorJson =
            """
            {
              "result": "error",
              "error-type": "unsupported-code",
              "time_last_update_unix": 0,
              "base_code": "EUR",
              "rates": {}
            }
            """.trimIndent()

        val dto = json.decodeFromString<ExchangeRateResponseDto>(errorJson)

        assertEquals("error", dto.result)
        assertTrue(dto.rates.isEmpty())
    }
}
