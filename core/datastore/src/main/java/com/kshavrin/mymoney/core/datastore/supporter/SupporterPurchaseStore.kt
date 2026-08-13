package com.kshavrin.mymoney.core.datastore.supporter

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

interface SupporterPurchaseStore {
    suspend fun enqueue(outcome: PurchaseOutcome.Purchased): Result<Unit>

    suspend fun pendingPurchases(): Result<List<PurchaseOutcome.Purchased>>

    suspend fun remove(purchaseToken: String): Result<Unit>
}

@Singleton
class SupporterPurchaseStoreImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SupporterPurchaseStore {
        override suspend fun enqueue(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            runCatching {
                dataStore.edit { preferences ->
                    val purchases = preferences.pendingPurchases()
                    if (purchases.none { it.purchaseToken == outcome.purchaseToken }) {
                        preferences[PENDING_PURCHASES] = json.encodeToString(purchases + outcome.toStoredPurchase())
                    }
                }
            }

        override suspend fun pendingPurchases(): Result<List<PurchaseOutcome.Purchased>> =
            runCatching {
                dataStore.data
                    .first()
                    .pendingPurchases()
                    .map(StoredSupporterPurchase::toPurchaseOutcome)
            }

        override suspend fun remove(purchaseToken: String): Result<Unit> =
            runCatching {
                dataStore.edit { preferences ->
                    val remaining = preferences.pendingPurchases().filterNot { it.purchaseToken == purchaseToken }
                    if (remaining.isEmpty()) {
                        preferences.remove(PENDING_PURCHASES)
                    } else {
                        preferences[PENDING_PURCHASES] = json.encodeToString(remaining)
                    }
                }
            }

        private fun Preferences.pendingPurchases(): List<StoredSupporterPurchase> =
            this[PENDING_PURCHASES]
                ?.let { encoded -> json.decodeFromString<List<StoredSupporterPurchase>>(encoded) }
                .orEmpty()

        private companion object {
            val PENDING_PURCHASES = stringPreferencesKey("pending_supporter_purchases")
            val json = Json
        }
    }

@Serializable
private data class StoredSupporterPurchase(
    val productId: String,
    val purchaseToken: String,
    val purchasedAtMillis: Long,
)

private fun PurchaseOutcome.Purchased.toStoredPurchase(): StoredSupporterPurchase =
    StoredSupporterPurchase(
        productId = productId,
        purchaseToken = purchaseToken,
        purchasedAtMillis = purchasedAtMillis,
    )

private fun StoredSupporterPurchase.toPurchaseOutcome(): PurchaseOutcome.Purchased =
    PurchaseOutcome.Purchased(
        productId = productId,
        purchaseToken = purchaseToken,
        purchasedAtMillis = purchasedAtMillis,
    )
