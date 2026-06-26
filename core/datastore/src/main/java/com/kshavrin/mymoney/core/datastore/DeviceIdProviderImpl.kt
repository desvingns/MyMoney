package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProviderImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : DeviceIdProvider {
        @Volatile
        private var cachedDeviceId: String? = null

        private val mutex = Mutex()

        override suspend fun deviceId(): String {
            cachedDeviceId?.let { return it }
            return mutex.withLock {
                cachedDeviceId ?: loadOrCreate().also { cachedDeviceId = it }
            }
        }

        private suspend fun loadOrCreate(): String {
            val existing = dataStore.data.first()[AppSettingsKeys.DEVICE_ID]
            return if (!existing.isNullOrBlank()) {
                existing
            } else {
                val generated = UUID.randomUUID().toString()
                dataStore.edit { prefs -> prefs[AppSettingsKeys.DEVICE_ID] = generated }
                generated
            }
        }
    }
