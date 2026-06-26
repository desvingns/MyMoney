package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdProviderImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DeviceIdProvider {
        @Volatile
        private var cachedDeviceId: String? = null

        private val lock = Any()

        override fun deviceId(): String {
            cachedDeviceId?.let { return it }
            return synchronized(lock) {
                cachedDeviceId ?: loadOrCreate().also { cachedDeviceId = it }
            }
        }

        private fun loadOrCreate(): String =
            runBlocking(ioDispatcher) {
                val existing = dataStore.data.first()[AppSettingsKeys.DEVICE_ID]
                if (!existing.isNullOrBlank()) {
                    existing
                } else {
                    val generated = UUID.randomUUID().toString()
                    dataStore.edit { prefs -> prefs[AppSettingsKeys.DEVICE_ID] = generated }
                    generated
                }
            }
    }
