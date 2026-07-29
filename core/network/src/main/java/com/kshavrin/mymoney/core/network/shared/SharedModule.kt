package com.kshavrin.mymoney.core.network.shared

import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SharedModule {
    @Binds
    @Singleton
    abstract fun bindSharedAuth(impl: SupabaseSharedAuth): SharedAuth

    @Binds
    @Singleton
    abstract fun bindSharedWorkspaceRpc(impl: SupabaseSharedWorkspaceRpc): SharedWorkspaceRpc

    @Binds
    @Singleton
    abstract fun bindSharedJournalRpc(impl: SupabaseSharedJournalRpc): SharedJournalRpc

    @Binds
    @Singleton
    abstract fun bindSharedWorkspaceApi(impl: SupabaseSharedWorkspaceApi): SharedWorkspaceApi

    @Binds
    @Singleton
    abstract fun bindSharedJournalRepository(impl: SupabaseSharedJournalApi): SharedJournalRepository
}
