package com.kshavrin.mymoney.core.sync.di

import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.sync.BuildConfig
import com.kshavrin.mymoney.core.sync.analytics.FirebaseAnalyticsGateway
import com.kshavrin.mymoney.core.sync.analytics.NoOpAnalyticsGateway
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    @Singleton
    fun provideAnalyticsGateway(
        firebaseAnalyticsGateway: FirebaseAnalyticsGateway,
        noOpAnalyticsGateway: NoOpAnalyticsGateway,
    ): AnalyticsGateway =
        if (BuildConfig.HAS_FIREBASE) firebaseAnalyticsGateway else noOpAnalyticsGateway
}
