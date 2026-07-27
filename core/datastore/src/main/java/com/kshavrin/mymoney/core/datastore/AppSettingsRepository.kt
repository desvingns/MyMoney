package com.kshavrin.mymoney.core.datastore

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.datastore.model.VersionedAppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    val versionedSettings: Flow<VersionedAppSettings>
        get() = settings.map { VersionedAppSettings(settings = it, revision = 0L) }

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun reset() {
        update { AppSettings() }
    }
}
