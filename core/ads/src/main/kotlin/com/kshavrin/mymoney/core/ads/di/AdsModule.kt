package com.kshavrin.mymoney.core.ads.di

import com.kshavrin.mymoney.core.ads.AdGateway
import com.kshavrin.mymoney.core.ads.admob.AdMobAdGateway
import com.kshavrin.mymoney.core.ads.admob.AdRuntimeConfig
import com.kshavrin.mymoney.core.ads.admob.BuildConfigAdRuntimeConfig
import com.kshavrin.mymoney.core.ads.admob.GoogleMobileAdsRewardedClient
import com.kshavrin.mymoney.core.ads.admob.NoFillStreak
import com.kshavrin.mymoney.core.ads.admob.RewardedAdClient
import com.kshavrin.mymoney.core.ads.consent.UmpConsentGateway
import com.kshavrin.mymoney.core.ads.consent.UmpConsentGatewayImpl
import com.kshavrin.mymoney.core.ads.token.RewardTokenSource
import com.kshavrin.mymoney.core.ads.token.SupabaseRewardTokenSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdsModule {
    @Binds
    @Singleton
    abstract fun bindAdGateway(impl: AdMobAdGateway): AdGateway

    @Binds
    @Singleton
    abstract fun bindAdRuntimeConfig(impl: BuildConfigAdRuntimeConfig): AdRuntimeConfig

    @Binds
    @Singleton
    abstract fun bindUmpConsentGateway(impl: UmpConsentGatewayImpl): UmpConsentGateway

    @Binds
    @Singleton
    abstract fun bindRewardTokenSource(impl: SupabaseRewardTokenSource): RewardTokenSource

    @Binds
    @Singleton
    abstract fun bindRewardedAdClient(impl: GoogleMobileAdsRewardedClient): RewardedAdClient

    companion object {
        @Provides
        @Singleton
        fun provideNoFillStreak(): NoFillStreak = NoFillStreak()
    }
}
