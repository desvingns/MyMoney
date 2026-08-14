package com.kshavrin.mymoney.core.sync.usecase

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

data class CloudSyncAvailability(
    val dropboxEnabled: Boolean,
    val gdriveEnabled: Boolean,
    val sharedEnabled: Boolean,
)

class CloudSyncSettingsUseCase
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        private val remoteConfigRepository: RemoteConfigRepository,
    ) {
        fun availability(): CloudSyncAvailability =
            CloudSyncAvailability(
                dropboxEnabled = remoteConfigRepository.dropboxSyncEnabled(),
                gdriveEnabled = remoteConfigRepository.gdriveSyncEnabled(),
                sharedEnabled = remoteConfigRepository.sharedSyncEnabled(),
            )

        fun observeLastSyncAt(): Flow<Long?> = appSettingsRepository.settings.map { it.lastSyncAt }

        suspend fun isAutoSyncEnabled(): Boolean = appSettingsRepository.settings.first().autoSyncEnabled
    }
