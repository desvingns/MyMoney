package com.kshavrin.mymoney.core.datastore.supporter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kshavrin.mymoney.core.datastore.AppSettingsKeys
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface SupporterPurchaseStore {
    suspend fun recordPurchase(
        outcome: PurchaseOutcome.Purchased,
        ownerUserId: String?,
    ): Result<Unit>

    suspend fun pendingPurchases(ownerUserId: String): Result<List<PurchaseOutcome.Purchased>>

    suspend fun remove(
        ownerUserId: String,
        purchaseToken: String,
    ): Result<Unit>
}

@Singleton
class SupporterPurchaseStoreImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SupporterPurchaseStore {
        override suspend fun recordPurchase(
            outcome: PurchaseOutcome.Purchased,
            ownerUserId: String?,
        ): Result<Unit> =
            runCatching {
                dataStore.edit { preferences ->
                    val purchases = preferences.pendingPurchases()
                    var changed = false
                    val purchaseTokens = preferences[AppSettingsKeys.SUPPORTER_PURCHASE_TOKENS].orEmpty()
                    if (outcome.purchaseToken !in purchaseTokens) {
                        preferences[AppSettingsKeys.SUPPORTER_BADGE_EARNED] = true
                        preferences[AppSettingsKeys.SUPPORT_PURCHASE_COUNT] =
                            (preferences[AppSettingsKeys.SUPPORT_PURCHASE_COUNT] ?: 0) + 1
                        preferences[AppSettingsKeys.SUPPORTER_PURCHASE_TOKENS] = purchaseTokens + outcome.purchaseToken
                        changed = true
                    }
                    if (ownerUserId != null && purchases.none { it.purchaseToken == outcome.purchaseToken }) {
                        preferences[SupporterPurchaseStoreKeys.PENDING_PURCHASES] =
                            json.encodeToString(purchases + outcome.toStoredPurchase(ownerUserId))
                        changed = true
                    }
                    if (changed) {
                        preferences[AppSettingsKeys.SETTINGS_REVISION] =
                            (preferences[AppSettingsKeys.SETTINGS_REVISION] ?: 0L) + 1L
                    }
                }
            }

        override suspend fun pendingPurchases(ownerUserId: String): Result<List<PurchaseOutcome.Purchased>> =
            runCatching {
                dataStore.data
                    .first()
                    .pendingPurchases()
                    .filter { it.ownerUserId == ownerUserId }
                    .map(StoredSupporterPurchase::toPurchaseOutcome)
            }

        override suspend fun remove(
            ownerUserId: String,
            purchaseToken: String,
        ): Result<Unit> =
            runCatching {
                dataStore.edit { preferences ->
                    val remaining =
                        preferences.pendingPurchases().filterNot { purchase ->
                            purchase.ownerUserId == ownerUserId && purchase.purchaseToken == purchaseToken
                        }
                    if (remaining.isEmpty()) {
                        preferences.remove(SupporterPurchaseStoreKeys.PENDING_PURCHASES)
                    } else {
                        preferences[SupporterPurchaseStoreKeys.PENDING_PURCHASES] = json.encodeToString(remaining)
                    }
                }
            }

        private fun Preferences.pendingPurchases(): List<StoredSupporterPurchase> =
            this[SupporterPurchaseStoreKeys.PENDING_PURCHASES]
                ?.let { encoded -> json.decodeFromString<List<StoredSupporterPurchase>>(encoded) }
                .orEmpty()

        private companion object {
            val json = Json
        }
    }

internal object SupporterPurchaseStoreKeys {
    val PENDING_PURCHASES = stringPreferencesKey("pending_supporter_purchases")
}

@Serializable
private data class StoredSupporterPurchase(
    val productId: String,
    val purchaseToken: String,
    val purchasedAtMillis: Long,
    val ownerUserId: String? = null,
)

private fun PurchaseOutcome.Purchased.toStoredPurchase(ownerUserId: String): StoredSupporterPurchase =
    StoredSupporterPurchase(
        productId = productId,
        purchaseToken = purchaseToken,
        purchasedAtMillis = purchasedAtMillis,
        ownerUserId = ownerUserId,
    )

private fun StoredSupporterPurchase.toPurchaseOutcome(): PurchaseOutcome.Purchased =
    PurchaseOutcome.Purchased(
        productId = productId,
        purchaseToken = purchaseToken,
        purchasedAtMillis = purchasedAtMillis,
    )
