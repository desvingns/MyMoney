package com.kshavrin.mymoney.navigation

import com.kshavrin.mymoney.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ShowOnboarding

@Module
@InstallIn(SingletonComponent::class)
object OnboardingFlagModule {
    @Provides
    @Singleton
    @ShowOnboarding
    fun provideShowOnboarding(): Boolean = BuildConfig.SHOW_ONBOARDING
}
