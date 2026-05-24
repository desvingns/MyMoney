package com.kshavrin.mymoney.core.sync.di

import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.dropbox.DropboxRepository
import com.kshavrin.mymoney.core.sync.gdrive.GoogleDriveRepository
import com.kshavrin.mymoney.core.sync.remoteconfig.RemoteConfigRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(impl: RemoteConfigRepositoryImpl): RemoteConfigRepository

    @Binds
    @IntoSet
    abstract fun bindDropboxBackend(impl: DropboxRepository): CloudSyncBackend

    @Binds
    @IntoSet
    abstract fun bindGdriveBackend(impl: GoogleDriveRepository): CloudSyncBackend
}
