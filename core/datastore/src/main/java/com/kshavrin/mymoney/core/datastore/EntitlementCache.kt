package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kshavrin.mymoney.core.domain.model.EntitlementSnapshot
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class CachedEntitlement(
    val snapshot: EntitlementSnapshot?,
    val lastValidatedAt: Instant?,
)

interface EntitlementCache {
    val cachedEntitlement: Flow<CachedEntitlement>

    suspend fun update(
        snapshot: EntitlementSnapshot?,
        lastValidatedAt: Instant,
    )
}

@Singleton
class DataStoreEntitlementCache
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : EntitlementCache {
        override val cachedEntitlement: Flow<CachedEntitlement> =
            dataStore.data
                .map { preferences ->
                    CachedEntitlement(
                        snapshot = preferences.toEntitlementSnapshot(),
                        lastValidatedAt = preferences[LAST_VALIDATED_AT]?.let(Instant::ofEpochMilli),
                    )
                }.distinctUntilChanged()

        override suspend fun update(
            snapshot: EntitlementSnapshot?,
            lastValidatedAt: Instant,
        ) {
            dataStore.edit { preferences ->
                preferences[LAST_VALIDATED_AT] = lastValidatedAt.toEpochMilli()
                if (snapshot == null) {
                    preferences.removeSnapshot()
                } else {
                    preferences[SOURCE] = snapshot.source.name
                    preferences[STARTS_AT] = snapshot.startsAt.toEpochMilli()
                    preferences[IN_TRIAL] = snapshot.inTrial
                    snapshot.expiresAt?.let { preferences[EXPIRES_AT] = it.toEpochMilli() }
                        ?: preferences.remove(EXPIRES_AT)
                    snapshot.revokedAt?.let { preferences[REVOKED_AT] = it.toEpochMilli() }
                        ?: preferences.remove(REVOKED_AT)
                }
            }
        }

        private fun Preferences.toEntitlementSnapshot(): EntitlementSnapshot? {
            val source = this[SOURCE]?.let { value -> runCatching { EntitlementSource.valueOf(value) }.getOrNull() }
                ?: return null
            val startsAt = this[STARTS_AT]?.let(Instant::ofEpochMilli) ?: return null
            return EntitlementSnapshot(
                source = source,
                startsAt = startsAt,
                expiresAt = this[EXPIRES_AT]?.let(Instant::ofEpochMilli),
                inTrial = this[IN_TRIAL] ?: false,
                revokedAt = this[REVOKED_AT]?.let(Instant::ofEpochMilli),
            )
        }

        private fun androidx.datastore.preferences.core.MutablePreferences.removeSnapshot() {
            remove(SOURCE)
            remove(STARTS_AT)
            remove(EXPIRES_AT)
            remove(IN_TRIAL)
            remove(REVOKED_AT)
        }

        private companion object {
            val SOURCE = stringPreferencesKey("entitlement_source")
            val STARTS_AT = longPreferencesKey("entitlement_starts_at")
            val EXPIRES_AT = longPreferencesKey("entitlement_expires_at")
            val IN_TRIAL = booleanPreferencesKey("entitlement_in_trial")
            val REVOKED_AT = longPreferencesKey("entitlement_revoked_at")
            val LAST_VALIDATED_AT = longPreferencesKey("entitlement_last_validated_at")
        }
    }
