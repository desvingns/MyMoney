package com.kshavrin.mymoney.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HttpModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY },
                    )
                }
            }.build()

    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        client: OkHttpClient,
        json: Json,
    ): Retrofit.Builder =
        Retrofit
            .Builder()
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))

    @Provides
    @Singleton
    fun provideExchangeRateApi(builder: Retrofit.Builder): ExchangeRateApi =
        builder
            .baseUrl(EXCHANGE_RATE_BASE_URL)
            .build()
            .create(ExchangeRateApi::class.java)

    private const val EXCHANGE_RATE_BASE_URL = "https://open.er-api.com/"
}
