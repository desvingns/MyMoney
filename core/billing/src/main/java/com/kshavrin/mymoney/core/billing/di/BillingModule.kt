package com.kshavrin.mymoney.core.billing.di

import com.kshavrin.mymoney.core.billing.BillingClientFactory
import com.kshavrin.mymoney.core.billing.PlayBillingClientFactory
import com.kshavrin.mymoney.core.billing.PlayBillingGateway
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    @Singleton
    abstract fun bindBillingGateway(impl: PlayBillingGateway): BillingGateway

    @Binds
    @Singleton
    abstract fun bindBillingClientFactory(impl: PlayBillingClientFactory): BillingClientFactory
}
