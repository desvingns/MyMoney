package com.kshavrin.mymoney.core.sync.di

import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.domain.reset.FactoryResetGateway
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import com.kshavrin.mymoney.core.sync.CloudSyncBackend
import com.kshavrin.mymoney.core.sync.FactoryResetGatewayImpl
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.JournalSyncImpl
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SnapshotSyncRepository
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.SyncSchedulerImpl
import com.kshavrin.mymoney.core.sync.WorkScheduler
import com.kshavrin.mymoney.core.sync.WorkSchedulerImpl
import com.kshavrin.mymoney.core.sync.dropbox.DropboxJournalBackend
import com.kshavrin.mymoney.core.sync.dropbox.DropboxRepository
import com.kshavrin.mymoney.core.sync.gdrive.AuthorizationClientDriveAuthorizer
import com.kshavrin.mymoney.core.sync.gdrive.GoogleDriveAuthorizer
import com.kshavrin.mymoney.core.sync.gdrive.GoogleDriveJournalBackend
import com.kshavrin.mymoney.core.sync.gdrive.GoogleDriveRepository
import com.kshavrin.mymoney.core.sync.remoteconfig.RemoteConfigRepositoryImpl
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinator
import com.kshavrin.mymoney.core.sync.shared.SharedSyncCoordinatorImpl
import com.kshavrin.mymoney.core.sync.supporter.SupporterSyncImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    @Singleton
    abstract fun bindRemoteConfigRepository(impl: RemoteConfigRepositoryImpl): RemoteConfigRepository

    @Binds
    @Singleton
    abstract fun bindSnapshotSync(impl: SnapshotSyncRepository): SnapshotSync

    @Binds
    @Singleton
    abstract fun bindJournalSync(impl: JournalSyncImpl): JournalSync

    @Binds
    @Singleton
    abstract fun bindSyncScheduler(impl: SyncSchedulerImpl): SyncScheduler

    @Binds
    @Singleton
    abstract fun bindWorkScheduler(impl: WorkSchedulerImpl): WorkScheduler

    @Binds
    @IntoSet
    abstract fun bindDropboxBackend(impl: DropboxRepository): CloudSyncBackend

    @Binds
    @IntoSet
    abstract fun bindGdriveBackend(impl: GoogleDriveRepository): CloudSyncBackend

    @Binds
    @IntoSet
    abstract fun bindDropboxJournalBackend(impl: DropboxJournalBackend): JournalBackend

    @Binds
    @IntoSet
    abstract fun bindGoogleDriveJournalBackend(impl: GoogleDriveJournalBackend): JournalBackend

    @Binds
    @Singleton
    abstract fun bindGoogleDriveAuthorizer(impl: AuthorizationClientDriveAuthorizer): GoogleDriveAuthorizer

    @Binds
    @Singleton
    abstract fun bindFactoryResetGateway(impl: FactoryResetGatewayImpl): FactoryResetGateway

    @Binds
    @Singleton
    abstract fun bindSharedSyncCoordinator(impl: SharedSyncCoordinatorImpl): SharedSyncCoordinator

    @Binds
    @Singleton
    abstract fun bindSupporterSync(impl: SupporterSyncImpl): SupporterSync
}
