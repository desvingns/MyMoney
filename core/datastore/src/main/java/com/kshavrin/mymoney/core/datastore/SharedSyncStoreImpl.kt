package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedSyncStoreImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SharedSyncStore {
        override suspend fun cursor(): Long = dataStore.data.first()[CURSOR] ?: 0L

        override suspend fun setCursor(sequence: Long) {
            dataStore.edit { prefs ->
                val current = prefs[CURSOR] ?: 0L
                if (sequence > current) prefs[CURSOR] = sequence
            }
        }

        override suspend fun isMembershipActive(): Boolean = dataStore.data.first()[MEMBERSHIP_ACTIVE] ?: false

        override suspend fun setMembershipActive(active: Boolean) {
            dataStore.edit { prefs -> prefs[MEMBERSHIP_ACTIVE] = active }
        }

        override suspend fun localOnlyState(): SharedLocalOnlyState? =
            dataStore.data.first().let { prefs ->
                val reason = prefs[LOCAL_ONLY_REASON]
                val sinceEpochMs = prefs[LOCAL_ONLY_SINCE_EPOCH_MS]
                if (reason != null && sinceEpochMs != null) {
                    SharedLocalOnlyState(reason = reason, sinceEpochMs = sinceEpochMs)
                } else {
                    null
                }
            }

        override suspend fun setLocalOnly(
            reason: String,
            sinceEpochMs: Long,
        ) {
            dataStore.edit { prefs ->
                prefs[LOCAL_ONLY_REASON] = reason
                prefs[LOCAL_ONLY_SINCE_EPOCH_MS] = sinceEpochMs
            }
        }

        override suspend fun clearLocalOnly() {
            dataStore.edit { prefs ->
                prefs -= LOCAL_ONLY_REASON
                prefs -= LOCAL_ONLY_SINCE_EPOCH_MS
            }
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs -= CURSOR
                prefs -= MEMBERSHIP_ACTIVE
                prefs -= LOCAL_ONLY_REASON
                prefs -= LOCAL_ONLY_SINCE_EPOCH_MS
            }
        }

        private companion object {
            val CURSOR = longPreferencesKey("shared_sync_cursor")
            val MEMBERSHIP_ACTIVE = booleanPreferencesKey("shared_sync_membership_active")
            val LOCAL_ONLY_REASON = stringPreferencesKey("shared_local_only_reason")
            val LOCAL_ONLY_SINCE_EPOCH_MS = longPreferencesKey("shared_local_only_since_epoch_ms")
        }
    }
