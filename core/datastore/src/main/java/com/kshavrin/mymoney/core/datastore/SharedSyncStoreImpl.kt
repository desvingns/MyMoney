package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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

        override suspend fun workspaceAccessContext(): SharedWorkspaceAccessContext? =
            dataStore.data.first().let { prefs ->
                val billingState = prefs[WORKSPACE_BILLING_STATE]
                val isWorkspaceOwner = prefs[WORKSPACE_IS_OWNER]
                if (billingState != null || isWorkspaceOwner != null) {
                    SharedWorkspaceAccessContext(
                        billingState = billingState,
                        isWorkspaceOwner = isWorkspaceOwner,
                    )
                } else {
                    null
                }
            }

        override suspend fun setWorkspaceAccessContext(
            billingState: String?,
            isWorkspaceOwner: Boolean?,
        ) {
            dataStore.edit { prefs ->
                prefs.setOrRemove(WORKSPACE_BILLING_STATE, billingState)
                prefs.setOrRemove(WORKSPACE_IS_OWNER, isWorkspaceOwner)
            }
        }

        override suspend fun localOnlyState(): SharedLocalOnlyState? =
            dataStore.data.first().let { prefs ->
                val reason = prefs[LOCAL_ONLY_REASON]
                val sinceEpochMs = prefs[LOCAL_ONLY_SINCE_EPOCH_MS]
                if (reason != null && sinceEpochMs != null) {
                    SharedLocalOnlyState(
                        reason = reason,
                        sinceEpochMs = sinceEpochMs,
                        workspaceBillingState = prefs[LOCAL_ONLY_WORKSPACE_BILLING_STATE],
                        isWorkspaceOwner = prefs[LOCAL_ONLY_IS_OWNER],
                    )
                } else {
                    null
                }
            }

        override suspend fun setLocalOnly(
            reason: String,
            sinceEpochMs: Long,
            workspaceBillingState: String?,
            isWorkspaceOwner: Boolean?,
        ) {
            dataStore.edit { prefs ->
                prefs[LOCAL_ONLY_REASON] = reason
                prefs[LOCAL_ONLY_SINCE_EPOCH_MS] = sinceEpochMs
                prefs.setOrRemove(LOCAL_ONLY_WORKSPACE_BILLING_STATE, workspaceBillingState)
                prefs.setOrRemove(LOCAL_ONLY_IS_OWNER, isWorkspaceOwner)
            }
        }

        override suspend fun clearLocalOnly() {
            dataStore.edit { prefs ->
                prefs -= LOCAL_ONLY_REASON
                prefs -= LOCAL_ONLY_SINCE_EPOCH_MS
                prefs -= LOCAL_ONLY_WORKSPACE_BILLING_STATE
                prefs -= LOCAL_ONLY_IS_OWNER
            }
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs -= CURSOR
                prefs -= MEMBERSHIP_ACTIVE
                prefs -= LOCAL_ONLY_REASON
                prefs -= LOCAL_ONLY_SINCE_EPOCH_MS
                prefs -= LOCAL_ONLY_WORKSPACE_BILLING_STATE
                prefs -= LOCAL_ONLY_IS_OWNER
                prefs -= WORKSPACE_BILLING_STATE
                prefs -= WORKSPACE_IS_OWNER
            }
        }

        private fun <T> MutablePreferences.setOrRemove(
            key: Preferences.Key<T>,
            value: T?,
        ) {
            if (value == null) {
                this -= key
            } else {
                this[key] = value
            }
        }

        private companion object {
            val CURSOR = longPreferencesKey("shared_sync_cursor")
            val MEMBERSHIP_ACTIVE = booleanPreferencesKey("shared_sync_membership_active")
            val LOCAL_ONLY_REASON = stringPreferencesKey("shared_local_only_reason")
            val LOCAL_ONLY_SINCE_EPOCH_MS = longPreferencesKey("shared_local_only_since_epoch_ms")
            val LOCAL_ONLY_WORKSPACE_BILLING_STATE = stringPreferencesKey("shared_local_only_workspace_billing_state")
            val LOCAL_ONLY_IS_OWNER = booleanPreferencesKey("shared_local_only_is_owner")
            val WORKSPACE_BILLING_STATE = stringPreferencesKey("shared_workspace_billing_state")
            val WORKSPACE_IS_OWNER = booleanPreferencesKey("shared_workspace_is_owner")
        }
    }
