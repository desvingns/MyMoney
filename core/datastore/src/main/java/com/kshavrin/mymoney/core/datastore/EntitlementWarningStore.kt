package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface EntitlementWarningStore {
    suspend fun previousState(): EntitlementState?

    suspend fun setPreviousState(state: EntitlementState)

    suspend fun wasNotified(
        warning: EntitlementWarning,
        expiresAt: Instant?,
    ): Boolean

    suspend fun markNotified(
        warning: EntitlementWarning,
        expiresAt: Instant?,
    )
}

@Singleton
class DataStoreEntitlementWarningStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : EntitlementWarningStore {
        override suspend fun previousState(): EntitlementState? {
            val stored = dataStore.data.first()[PREVIOUS_STATE] ?: return null
            return runCatching { EntitlementState.valueOf(stored) }.getOrNull()
        }

        override suspend fun setPreviousState(state: EntitlementState) {
            dataStore.edit { preferences -> preferences[PREVIOUS_STATE] = state.name }
        }

        override suspend fun wasNotified(
            warning: EntitlementWarning,
            expiresAt: Instant?,
        ): Boolean = dataStore.data.first()[notifiedKey(warning)] == discriminator(expiresAt)

        override suspend fun markNotified(
            warning: EntitlementWarning,
            expiresAt: Instant?,
        ) {
            dataStore.edit { preferences -> preferences[notifiedKey(warning)] = discriminator(expiresAt) }
        }

        private fun discriminator(expiresAt: Instant?): String = expiresAt?.toEpochMilli()?.toString() ?: "none"

        private fun notifiedKey(warning: EntitlementWarning): Preferences.Key<String> =
            stringPreferencesKey("entitlement_warning_notified_${warning.name}")

        private companion object {
            val PREVIOUS_STATE = stringPreferencesKey("entitlement_warning_previous_state")
        }
    }
