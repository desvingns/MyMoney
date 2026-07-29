package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.database.dao.OperationDao
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.reset.FactoryResetGateway
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactoryResetGatewayImpl
    @Inject
    constructor(
        private val backupRepository: BackupRepository,
        private val appSettingsRepository: AppSettingsRepository,
        private val secureStorage: SecureStorage,
        private val sharedAuth: SharedAuth,
        private val journalSyncConfigStore: JournalSyncConfigStore,
        private val operationDao: OperationDao,
        private val syncScheduler: SyncScheduler,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : FactoryResetGateway {
        override suspend fun wipeLocalData() {
            backupRepository.clearDatabase().getOrThrow()
        }

        override suspend fun resetSettings() {
            appSettingsRepository.reset()
        }

        override suspend fun clearSecrets() {
            withContext(ioDispatcher) {
                sharedAuth.resetLocalSession(secureStorage::clearAll)
            }
        }

        override suspend fun detachCloudSync() {
            syncScheduler.disablePeriodicSync()
            operationDao.deleteAll()
            journalSyncConfigStore.clear()
        }
    }
