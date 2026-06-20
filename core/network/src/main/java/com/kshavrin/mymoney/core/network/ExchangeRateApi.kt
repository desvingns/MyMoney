package com.kshavrin.mymoney.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface ExchangeRateApi {
    @GET("v6/latest/EUR")
    suspend fun latest(): ExchangeRateResponseDto
}

@Serializable
data class ExchangeRateResponseDto(
    @SerialName("result") val result: String,
    @SerialName("time_last_update_unix") val timeLastUpdateUnix: Long,
    @SerialName("base_code") val baseCode: String,
    @SerialName("rates") val rates: Map<String, Double>,
)
