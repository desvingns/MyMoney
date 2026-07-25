package com.kshavrin.mymoney.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JournalSyncConfigStoreImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : JournalSyncConfigStore {
        override suspend fun binding(): CloudBinding? =
            dataStore.data.first().let { prefs ->
                val provider = prefs[BINDING_PROVIDER]?.let(::parseProvider) ?: return@let null
                val stableAccountId = prefs[BINDING_ACCOUNT_ID]?.takeIf { it.isNotBlank() } ?: return@let null
                CloudBinding(
                    provider = provider,
                    stableAccountId = stableAccountId,
                    accountLabel = prefs[BINDING_ACCOUNT_LABEL].orEmpty(),
                )
            }

        override suspend fun setBinding(binding: CloudBinding) {
            require(binding.stableAccountId.isNotBlank())
            dataStore.edit { prefs ->
                prefs[BINDING_PROVIDER] = binding.provider.name
                prefs[BINDING_ACCOUNT_ID] = binding.stableAccountId
                prefs[BINDING_ACCOUNT_LABEL] = binding.accountLabel
            }
        }

        override suspend fun clearBinding() {
            dataStore.edit { prefs ->
                val current = prefs.toBindingOrNull()
                if (current != null) {
                    prefs.asMap().keys
                        .filter { it.name.startsWith(scopePrefix(current)) }
                        .forEach { prefs -= it }
                }
                prefs -= BINDING_PROVIDER
                prefs -= BINDING_ACCOUNT_ID
                prefs -= BINDING_ACCOUNT_LABEL
            }
        }

        override suspend fun peerHighWaterMs(fileId: String): Long =
            dataStore.data.first().let { prefs ->
                prefs.toBindingOrNull()?.let { binding -> prefs[peerKey(binding, fileId)] } ?: 0L
            }

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            dataStore.edit { prefs ->
                val binding = prefs.toBindingOrNull() ?: return@edit
                val key = peerKey(binding, fileId)
                val current = prefs[key] ?: 0L
                if (modifiedAtMs > current) prefs[key] = modifiedAtMs
            }
        }

        override suspend fun isBootstrapDone(): Boolean =
            dataStore.data.first().let { prefs ->
                prefs.toBindingOrNull()?.let { binding -> prefs[bootstrapKey(binding)] } ?: false
            }

        override suspend fun markBootstrapDone() {
            dataStore.edit { prefs ->
                prefs.toBindingOrNull()?.let { binding -> prefs[bootstrapKey(binding)] = true }
            }
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

        private fun Preferences.toBindingOrNull(): CloudBinding? {
            val provider = this[BINDING_PROVIDER]?.let(::parseProvider) ?: return null
            val stableAccountId = this[BINDING_ACCOUNT_ID]?.takeIf { it.isNotBlank() } ?: return null
            return CloudBinding(
                provider = provider,
                stableAccountId = stableAccountId,
                accountLabel = this[BINDING_ACCOUNT_LABEL].orEmpty(),
            )
        }

        private fun parseProvider(value: String): CloudProvider? =
            runCatching { CloudProvider.valueOf(value) }.getOrNull()

        private fun scopePrefix(binding: CloudBinding): String =
            "$SCOPED_KEY_PREFIX${binding.provider.name}_${binding.stableAccountId.encodeKey()}_"

        private fun peerKey(
            binding: CloudBinding,
            fileId: String,
        ) = longPreferencesKey("${scopePrefix(binding)}peer_${fileId.encodeKey()}")

        private fun bootstrapKey(binding: CloudBinding) = booleanPreferencesKey("${scopePrefix(binding)}bootstrap_done")

        private fun String.encodeKey(): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(Charsets.UTF_8))

        private companion object {
            const val KEY_PREFIX = "journal_"
            const val SCOPED_KEY_PREFIX = "journal_scope_"
            val BINDING_PROVIDER = stringPreferencesKey("journal_binding_provider")
            val BINDING_ACCOUNT_ID = stringPreferencesKey("journal_binding_account_id")
            val BINDING_ACCOUNT_LABEL = stringPreferencesKey("journal_binding_account_label")
        }
    }
