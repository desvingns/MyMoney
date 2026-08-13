package com.kshavrin.mymoney.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.AppSettingsRepositoryImpl
import com.kshavrin.mymoney.core.datastore.DeviceIdProviderImpl
import com.kshavrin.mymoney.core.datastore.DataStoreEntitlementCache
import com.kshavrin.mymoney.core.datastore.EncryptedSharedSessionStore
import com.kshavrin.mymoney.core.datastore.EntitlementCache
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStoreImpl
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.datastore.SecureStorageImpl
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.datastore.SharedSyncStoreImpl
import com.kshavrin.mymoney.core.datastore.supporter.SupporterRepositoryImpl
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.network.shared.SharedSessionStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + ioDispatcher),
            produceFile = { context.preferencesDataStoreFile(DATASTORE_FILE_NAME) },
        )

    private const val DATASTORE_FILE_NAME = "app_settings"
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataStoreBindings {
    @Binds
    @Singleton
    abstract fun bindAppSettingsRepository(impl: AppSettingsRepositoryImpl): AppSettingsRepository

    @Binds
    @Singleton
    abstract fun bindSupporterRepository(impl: SupporterRepositoryImpl): SupporterRepository

    @Binds
    @Singleton
    abstract fun bindSecureStorage(impl: SecureStorageImpl): SecureStorage

    @Binds
    @Singleton
    abstract fun bindSharedSessionStore(impl: EncryptedSharedSessionStore): SharedSessionStore

    @Binds
    @Singleton
    abstract fun bindDeviceIdProvider(impl: DeviceIdProviderImpl): DeviceIdProvider

    @Binds
    @Singleton
    abstract fun bindJournalSyncConfigStore(impl: JournalSyncConfigStoreImpl): JournalSyncConfigStore

    @Binds
    @Singleton
    abstract fun bindSharedSyncStore(impl: SharedSyncStoreImpl): SharedSyncStore

    @Binds
    @Singleton
    abstract fun bindEntitlementCache(impl: DataStoreEntitlementCache): EntitlementCache
}
