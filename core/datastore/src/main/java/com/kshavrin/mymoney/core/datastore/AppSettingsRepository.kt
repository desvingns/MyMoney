package com.kshavrin.mymoney.core.datastore

import com.kshavrin.mymoney.core.datastore.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun reset() {
        update { AppSettings() }
    }
}
