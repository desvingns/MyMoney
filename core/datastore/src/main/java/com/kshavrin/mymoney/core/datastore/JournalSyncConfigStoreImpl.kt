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
class JournalSyncConfigStoreImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : JournalSyncConfigStore {
        override suspend fun folderId(): String = dataStore.data.first()[FOLDER_ID].orEmpty()

        override suspend fun setFolderId(folderId: String) {
            dataStore.edit { prefs -> prefs[FOLDER_ID] = folderId }
        }

        override suspend fun peerHighWaterMs(fileId: String): Long =
            dataStore.data.first()[peerKey(fileId)] ?: 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            dataStore.edit { prefs ->
                val current = prefs[peerKey(fileId)] ?: 0L
                if (modifiedAtMs > current) prefs[peerKey(fileId)] = modifiedAtMs
            }
        }

        override suspend fun isBootstrapDone(): Boolean = dataStore.data.first()[BOOTSTRAP_DONE] ?: false

        override suspend fun markBootstrapDone() {
            dataStore.edit { prefs -> prefs[BOOTSTRAP_DONE] = true }
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs
                    .asMap()
                    .keys
                    .filter { it.name.startsWith(KEY_PREFIX) }
                    .forEach { prefs -= it }
            }
        }

        private fun peerKey(fileId: String) = longPreferencesKey("$PEER_KEY_PREFIX$fileId")

        private companion object {
            const val KEY_PREFIX = "journal_"
            const val PEER_KEY_PREFIX = "journal_peer_hw_"
            val FOLDER_ID = stringPreferencesKey("journal_folder_id")
            val BOOTSTRAP_DONE = booleanPreferencesKey("journal_bootstrap_done")
        }
    }
