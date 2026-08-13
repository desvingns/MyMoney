package com.kshavrin.mymoney.core.billing

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.GetBillingConfigParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.di.MainDispatcher
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.domain.billing.BillingAvailability
import com.kshavrin.mymoney.core.domain.billing.BillingGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.billing.SupportProduct
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class PlayBillingGateway
    @Inject
    constructor(
        private val billingClientFactory: BillingClientFactory,
        private val foregroundActivityProvider: ForegroundActivityProvider,
        @ApplicationScope private val applicationScope: CoroutineScope,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
        private val plusSubscriptionClient: PlusSubscriptionClient,
        private val entitlementRepository: EntitlementRepository,
        private val supporterSync: SupporterSync = NoOpSupporterSync,
    ) : BillingGateway {
        private val pendingPurchase = AtomicReference<PendingPurchase?>(null)
        private val billingConnectionMutex = Mutex()
        private val purchaseProcessor =
            PurchaseProcessor(
                acknowledge = { purchaseToken -> billingClient.acknowledge(purchaseToken).responseCode },
                consume = { purchaseToken -> billingClient.consume(purchaseToken).responseCode },
            )
        private val purchasesUpdatedListener =
            PurchasesUpdatedListener { billingResult, purchases ->
                applicationScope.launch(ioDispatcher) {
                    handlePurchasesUpdated(billingResult, purchases)
                }
            }
        private val billingClient by lazy { billingClientFactory.create(purchasesUpdatedListener) }

        override fun availability(): Flow<BillingAvailability> =
            flow {
                emit(
                    if (BuildConfig.BILLING_ENABLED) {
                        refreshAvailability()
                    } else {
                        BillingAvailability.DisabledInBuild
                    },
                )
            }

        override fun products(): Result<List<SupportProduct>> =
            if (!BuildConfig.BILLING_ENABLED) {
                Result.success(emptyList())
            } else {
                runCatching {
                    runBlocking(ioDispatcher) {
                        availableClientOrThrow()
                        querySupportProductDetails().mapNotNull { productDetails ->
                            productDetails.toSupportProduct()
                        }
                    }
                }
            }

        override fun purchase(productId: String): Flow<PurchaseOutcome> =
            flow {
                if (!BuildConfig.BILLING_ENABLED) {
                    emit(PurchaseOutcome.Unavailable(DISABLED_IN_BUILD_REASON))
                    return@flow
                }

                val availability = refreshAvailability()
                if (availability != BillingAvailability.Available) {
                    emit(availability.toPurchaseOutcome())
                    return@flow
                }

                val activity = foregroundActivityProvider.currentActivity()
                if (activity == null) {
                    emit(PurchaseOutcome.Unavailable(NO_FOREGROUND_ACTIVITY_REASON))
                    return@flow
                }

                val currentPurchase = PendingPurchase(productId, PurchaseOutcomeBridge())
                if (!pendingPurchase.compareAndSet(null, currentPurchase)) {
                    emit(PurchaseOutcome.Unavailable(BILLING_FLOW_IN_PROGRESS_REASON))
                    return@flow
                }

                try {
                    val productDetails = querySupportProductDetails().firstOrNull { it.productId == productId }
                    val offerToken =
                        productDetails
                            ?.oneTimePurchaseOfferDetailsList
                            ?.firstOrNull()
                            ?.offerToken
                    if (productDetails == null || offerToken == null) {
                        pendingPurchase.compareAndSet(currentPurchase, null)
                        emit(PurchaseOutcome.Unavailable(productId))
                        return@flow
                    }

                    val startResult =
                        withContext(mainDispatcher) {
                            billingClient.launchBillingFlow(
                                activity,
                                BillingFlowParams
                                    .newBuilder()
                                    .setProductDetailsParamsList(
                                        listOf(
                                            BillingFlowParams.ProductDetailsParams
                                                .newBuilder()
                                                .setProductDetails(productDetails)
                                                .setOfferToken(offerToken)
                                                .build(),
                                        ),
                                    ).build(),
                            )
                        }
                    if (startResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        pendingPurchase.compareAndSet(currentPurchase, null)
                        emit(startResult.toPurchaseOutcome())
                        return@flow
                    }
                    while (true) {
                        val outcome = currentPurchase.outcomes.awaitNext() ?: return@flow
                        emit(outcome)
                        if (outcome.isTerminal()) {
                            return@flow
                        }
                    }
                } catch (throwable: Throwable) {
                    emit(throwable.toPurchaseOutcome())
                } finally {
                    pendingPurchase.compareAndSet(currentPurchase, null)
                    currentPurchase.outcomes.close()
                }
            }

        override fun resolvePendingPurchases(): Result<List<PurchaseOutcome>> =
            if (!BuildConfig.BILLING_ENABLED) {
                Result.success(emptyList())
            } else {
                runCatching {
                    runBlocking(ioDispatcher) {
                        val availability = refreshAvailability()
                        if (availability != BillingAvailability.Available) {
                            return@runBlocking emptyList()
                        }

                        val purchasesResult = billingClient.queryInAppPurchases()
                        if (purchasesResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                            throw BillingOperationException(purchasesResult.billingResult)
                        }

                        purchasesResult.purchasesList
                            .filter(::containsSupportProduct)
                            .map { purchase -> processPurchase(purchase) }
                    }
                }
            }

        override fun querySubscriptions(): Result<List<SupportProduct>> =
            if (!BuildConfig.BILLING_ENABLED) {
                Result.success(emptyList())
            } else {
                runCatching {
                    runBlocking(ioDispatcher) {
                        availableClientOrThrow()
                        plusSubscriptionClient
                            .queryProductDetails(billingClient)
                            .mapNotNull(plusSubscriptionClient::toSupportProduct)
                    }
                }
            }

        override fun launchSubscriptionFlow(productId: String): Flow<PurchaseOutcome> =
            flow {
                if (!BuildConfig.BILLING_ENABLED) {
                    emit(PurchaseOutcome.Unavailable(DISABLED_IN_BUILD_REASON))
                    return@flow
                }

                val availability = refreshAvailability()
                if (availability != BillingAvailability.Available) {
                    emit(availability.toPurchaseOutcome())
                    return@flow
                }

                val activity = foregroundActivityProvider.currentActivity()
                if (activity == null) {
                    emit(PurchaseOutcome.Unavailable(NO_FOREGROUND_ACTIVITY_REASON))
                    return@flow
                }

                val currentPurchase = PendingPurchase(productId, PurchaseOutcomeBridge())
                if (!pendingPurchase.compareAndSet(null, currentPurchase)) {
                    emit(PurchaseOutcome.Unavailable(BILLING_FLOW_IN_PROGRESS_REASON))
                    return@flow
                }

                try {
                    val productDetails =
                        plusSubscriptionClient
                            .queryProductDetails(billingClient)
                            .firstOrNull { it.productId == productId }
                    val offer = productDetails?.let(plusSubscriptionClient::offerFor)
                    if (productDetails == null || offer == null) {
                        pendingPurchase.compareAndSet(currentPurchase, null)
                        emit(PurchaseOutcome.Unavailable(productId))
                        return@flow
                    }

                    val startResult =
                        withContext(mainDispatcher) {
                            plusSubscriptionClient.launchBillingFlow(
                                billingClient = billingClient,
                                activity = activity,
                                productDetails = productDetails,
                                offer = offer,
                            )
                        }
                    if (startResult.responseCode != BillingClient.BillingResponseCode.OK) {
                        pendingPurchase.compareAndSet(currentPurchase, null)
                        emit(startResult.toPurchaseOutcome())
                        return@flow
                    }
                    while (true) {
                        val outcome = currentPurchase.outcomes.awaitNext() ?: return@flow
                        emit(outcome)
                        if (outcome.isTerminal()) {
                            return@flow
                        }
                    }
                } catch (throwable: Throwable) {
                    emit(throwable.toPurchaseOutcome())
                } finally {
                    pendingPurchase.compareAndSet(currentPurchase, null)
                    currentPurchase.outcomes.close()
                }
            }

        override suspend fun acknowledge(purchaseToken: String): Result<Unit> =
            if (!BuildConfig.BILLING_ENABLED) {
                Result.failure(IllegalStateException(DISABLED_IN_BUILD_REASON))
            } else {
                withContext(ioDispatcher) {
                    runCatching { availableClientOrThrow() }
                        .fold(
                            onSuccess = { plusSubscriptionClient.acknowledge(billingClient, purchaseToken) },
                            onFailure = Result.Companion::failure,
                        )
                }
            }

        override fun resolveSubscriptionPurchases(): Result<List<PurchaseOutcome>> =
            if (!BuildConfig.BILLING_ENABLED) {
                Result.success(emptyList())
            } else {
                runCatching {
                    runBlocking(ioDispatcher) {
                        val availability = refreshAvailability()
                        if (availability != BillingAvailability.Available) {
                            return@runBlocking emptyList()
                        }

                        plusSubscriptionClient
                            .queryPurchases(billingClient)
                            .map { purchase -> processSubscriptionPurchase(purchase, refreshAfterPurchase = false) }
                            .also { purchases ->
                                if (purchases.isNotEmpty()) {
                                    entitlementRepository.refresh()
                                }
                            }
                    }
                }
            }

        private suspend fun refreshAvailability(): BillingAvailability {
            when (val connection = awaitConnection()) {
                ConnectionResult.Ready -> Unit
                is ConnectionResult.Failed -> return connection.billingResult.toAvailability()
            }

            val configResult = billingClient.queryBillingConfig()
            if (configResult.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                return configResult.billingResult.toAvailability()
            }
            return billingAvailabilityForCountryCode(configResult.countryCode)
        }

        private suspend fun availableClientOrThrow(): BillingClient {
            val availability = refreshAvailability()
            if (availability != BillingAvailability.Available) {
                throw BillingUnavailableException(availability)
            }
            return billingClient
        }

        private suspend fun awaitConnection(): ConnectionResult =
            billingConnectionMutex.withLock {
                billingClient.awaitConnection()
            }

        private suspend fun querySupportProductDetails(): List<ProductDetails> {
            val result = billingClient.querySupportProductDetails()
            if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                throw BillingOperationException(result.billingResult)
            }
            return result.productDetailsList.filter { it.productId in SUPPORT_PRODUCT_IDS }
        }

        private suspend fun handlePurchasesUpdated(
            billingResult: BillingResult,
            purchases: List<Purchase>?,
        ) {
            when (billingResult.responseCode) {
                BillingClient.BillingResponseCode.OK -> {
                    val knownPurchases = purchases.orEmpty().filter(::containsKnownProduct)
                    if (knownPurchases.isEmpty()) {
                        deliverToActivePurchase(PurchaseOutcome.Unavailable(EMPTY_PURCHASE_RESULT_REASON))
                    } else {
                        knownPurchases.forEach { purchase ->
                            deliverToActivePurchase(processPurchase(purchase))
                        }
                    }
                }

                BillingClient.BillingResponseCode.USER_CANCELED -> {
                    deliverToActivePurchase(PurchaseOutcome.Cancelled)
                }

                else -> deliverToActivePurchase(billingResult.toPurchaseOutcome())
            }
        }

        private suspend fun processPurchase(purchase: Purchase): PurchaseOutcome =
            when {
                containsSupportProduct(purchase) -> {
                    val outcome =
                        purchaseProcessor.process(
                            PurchaseProcessingInput(
                                productId = purchase.products.first { it in SUPPORT_PRODUCT_IDS },
                                purchaseToken = purchase.purchaseToken,
                                purchasedAtMillis = purchase.purchaseTime,
                                state = purchase.purchaseState.toPurchaseProcessingState(),
                                isAcknowledged = purchase.isAcknowledged,
                            ),
                        )
                    if (outcome is PurchaseOutcome.Purchased) {
                        supporterSync.syncPurchase(outcome)
                    }
                    outcome
                }

                containsPlusProduct(purchase) -> processSubscriptionPurchase(purchase)
                else -> PurchaseOutcome.Unavailable(UNSUPPORTED_PURCHASE_STATE_REASON)
            }

        private suspend fun processSubscriptionPurchase(
            purchase: Purchase,
            refreshAfterPurchase: Boolean = true,
        ): PurchaseOutcome =
            plusSubscriptionClient
                .processPurchase(billingClient, purchase)
                .also { outcome ->
                    if (refreshAfterPurchase && outcome is PurchaseOutcome.Purchased) {
                        entitlementRepository.refresh()
                    }
                }

        private fun deliverToActivePurchase(outcome: PurchaseOutcome) {
            val currentPurchase = pendingPurchase.get() ?: return
            if (outcome is PurchaseOutcome.Purchased && outcome.productId != currentPurchase.productId) {
                return
            }
            if (outcome.isTerminal() && !pendingPurchase.compareAndSet(currentPurchase, null)) {
                return
            }
            currentPurchase.outcomes.emit(outcome)
        }

        private fun containsSupportProduct(purchase: Purchase): Boolean =
            purchase.products.any { it in SUPPORT_PRODUCT_IDS }

        private fun containsPlusProduct(purchase: Purchase): Boolean =
            purchase.products.any { it in PlusSku.productIds }

        private fun containsKnownProduct(purchase: Purchase): Boolean =
            containsSupportProduct(purchase) || containsPlusProduct(purchase)

        private fun ProductDetails.toSupportProduct(): SupportProduct? =
            oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.let { offer ->
                    SupportProduct(
                        id = productId,
                        formattedPrice = offer.formattedPrice,
                        title = title,
                    )
                }

        private fun BillingAvailability.toPurchaseOutcome(): PurchaseOutcome = purchaseOutcomeForAvailability(this)

        private fun BillingResult.toAvailability(): BillingAvailability = billingAvailabilityForResponseCode(responseCode)

        private fun BillingResult.toPurchaseOutcome(): PurchaseOutcome = purchaseOutcomeForResponseCode(responseCode)

        private fun Throwable.toPurchaseOutcome(): PurchaseOutcome =
            when (this) {
                is BillingOperationException -> billingResult.toPurchaseOutcome()
                is BillingUnavailableException -> availability.toPurchaseOutcome()
                else -> PurchaseOutcome.NetworkError
            }

        private data class PendingPurchase(
            val productId: String,
            val outcomes: PurchaseOutcomeBridge,
        )

        internal sealed interface ConnectionResult {
            data object Ready : ConnectionResult

            data class Failed(
                val billingResult: BillingResult,
            ) : ConnectionResult
        }

        internal data class BillingConfigResult(
            val billingResult: BillingResult,
            val countryCode: String?,
        )

        internal data class ProductDetailsResult(
            val billingResult: BillingResult,
            val productDetailsList: List<ProductDetails>,
        )

        internal data class PurchasesResult(
            val billingResult: BillingResult,
            val purchasesList: List<Purchase>,
        )

        internal class BillingOperationException(
            val billingResult: BillingResult,
        ) : IllegalStateException(billingResult.debugMessage)

        private class BillingUnavailableException(
            val availability: BillingAvailability,
        ) : IllegalStateException(availability.toString())

        internal companion object {
            val SUPPORT_PRODUCT_IDS = setOf("coffee_small", "coffee_large")
            const val RUSSIA_COUNTRY_CODE = "RU"
            const val DISABLED_IN_BUILD_REASON = "billing_disabled_in_build"
            const val NO_FOREGROUND_ACTIVITY_REASON = "no_foreground_activity"
            const val BILLING_FLOW_IN_PROGRESS_REASON = "billing_flow_in_progress"
            const val EMPTY_PURCHASE_RESULT_REASON = "empty_purchase_result"
            const val UNSUPPORTED_PURCHASE_STATE_REASON = "unsupported_purchase_state"
            const val UNKNOWN_AVAILABILITY_REASON = "unknown_availability"
            const val BILLING_UNAVAILABLE_REASON = "billing_unavailable"
            const val REGION_UNAVAILABLE_REASON = "billing_unavailable_in_region"
        }
    }

private object NoOpSupporterSync : SupporterSync {
    override suspend fun syncPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> = Result.success(Unit)

    override suspend fun restore(): Result<Unit> = Result.success(Unit)
}

internal class PurchaseOutcomeBridge {
    private val outcomes = Channel<PurchaseOutcome>(Channel.BUFFERED)

    fun emit(outcome: PurchaseOutcome) {
        outcomes.trySend(outcome)
        if (outcome.isTerminal()) {
            outcomes.close()
        }
    }

    suspend fun awaitNext(): PurchaseOutcome? = outcomes.receiveCatching().getOrNull()

    fun close() {
        outcomes.close()
    }
}

internal fun PurchaseOutcome.isTerminal(): Boolean = this !is PurchaseOutcome.Pending

internal fun billingAvailabilityForCountryCode(countryCode: String?): BillingAvailability =
    if (countryCode.equals(PlayBillingGateway.RUSSIA_COUNTRY_CODE, ignoreCase = true)) {
        BillingAvailability.UnavailableInRegion
    } else {
        BillingAvailability.Available
    }

internal fun billingAvailabilityForResponseCode(responseCode: Int): BillingAvailability =
    when (responseCode) {
        BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> BillingAvailability.UnavailableOnDevice
        BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE -> BillingAvailability.ServiceUnavailable
        BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
        BillingClient.BillingResponseCode.NETWORK_ERROR,
        -> BillingAvailability.NetworkUnavailable

        else -> BillingAvailability.UnknownFailure(responseCode)
    }

internal fun purchaseOutcomeForAvailability(availability: BillingAvailability): PurchaseOutcome =
    when (availability) {
        BillingAvailability.Available -> PurchaseOutcome.Unavailable(PlayBillingGateway.UNKNOWN_AVAILABILITY_REASON)
        BillingAvailability.UnavailableOnDevice -> PurchaseOutcome.Unavailable(PlayBillingGateway.BILLING_UNAVAILABLE_REASON)
        BillingAvailability.ServiceUnavailable,
        BillingAvailability.NetworkUnavailable,
        -> PurchaseOutcome.NetworkError

        BillingAvailability.UnavailableInRegion ->
            PurchaseOutcome.Unavailable(PlayBillingGateway.REGION_UNAVAILABLE_REASON)

        is BillingAvailability.UnknownFailure -> PurchaseOutcome.Unavailable(availability.responseCode.toString())
        BillingAvailability.DisabledInBuild -> PurchaseOutcome.Unavailable(PlayBillingGateway.DISABLED_IN_BUILD_REASON)
    }

internal fun purchaseOutcomeForResponseCode(responseCode: Int): PurchaseOutcome =
    if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
        PurchaseOutcome.Cancelled
    } else {
        purchaseOutcomeForAvailability(billingAvailabilityForResponseCode(responseCode))
    }

internal data class PurchaseProcessingInput(
    val productId: String,
    val purchaseToken: String,
    val purchasedAtMillis: Long,
    val state: PurchaseProcessingState,
    val isAcknowledged: Boolean,
)

internal enum class PurchaseProcessingState {
    Pending,
    Purchased,
    Unsupported,
}

internal class PurchaseProcessor(
    private val acknowledge: suspend (String) -> Int,
    private val consume: suspend (String) -> Int,
) {
    private val processingMutex = Mutex()

    suspend fun process(purchase: PurchaseProcessingInput): PurchaseOutcome =
        processingMutex.withLock {
            when (purchase.state) {
                PurchaseProcessingState.Pending -> PurchaseOutcome.Pending
                PurchaseProcessingState.Purchased -> {
                    if (!purchase.isAcknowledged) {
                        val acknowledgeResponseCode = acknowledge(purchase.purchaseToken)
                        if (acknowledgeResponseCode != BillingClient.BillingResponseCode.OK) {
                            return@withLock purchaseOutcomeForResponseCode(acknowledgeResponseCode)
                        }
                    }

                    val consumeResponseCode = consume(purchase.purchaseToken)
                    if (consumeResponseCode != BillingClient.BillingResponseCode.OK) {
                        purchaseOutcomeForResponseCode(consumeResponseCode)
                    } else {
                        PurchaseOutcome.Purchased(
                            productId = purchase.productId,
                            purchaseToken = purchase.purchaseToken,
                            purchasedAtMillis = purchase.purchasedAtMillis,
                        )
                    }
                }

                PurchaseProcessingState.Unsupported ->
                    PurchaseOutcome.Unavailable(PlayBillingGateway.UNSUPPORTED_PURCHASE_STATE_REASON)
            }
        }
}

private fun Int.toPurchaseProcessingState(): PurchaseProcessingState =
    when (this) {
        Purchase.PurchaseState.PENDING -> PurchaseProcessingState.Pending
        Purchase.PurchaseState.PURCHASED -> PurchaseProcessingState.Purchased
        else -> PurchaseProcessingState.Unsupported
    }

@Singleton
class PlayBillingClientFactory
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : BillingClientFactory {
        override fun create(listener: PurchasesUpdatedListener): BillingClient =
            BillingClient
                .newBuilder(context)
                .setListener(listener)
                .enablePendingPurchases(
                    PendingPurchasesParams
                        .newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                ).enableAutoServiceReconnection()
                .build()
    }

interface BillingClientFactory {
    fun create(listener: PurchasesUpdatedListener): BillingClient
}

@Singleton
class ForegroundActivityProvider
    @Inject
    constructor(
        application: Application,
    ) : Application.ActivityLifecycleCallbacks {
        @Volatile
        private var activity: Activity? = null

        init {
            application.registerActivityLifecycleCallbacks(this)
        }

        fun currentActivity(): Activity? = activity

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            this.activity = activity
        }

        override fun onActivityPaused(activity: Activity) {
            if (this.activity === activity) {
                this.activity = null
            }
        }

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (this.activity === activity) {
                this.activity = null
            }
        }
    }

private suspend fun BillingClient.awaitConnection(): PlayBillingGateway.ConnectionResult =
    if (isReady) {
        PlayBillingGateway.ConnectionResult.Ready
    } else {
        suspendCancellableCoroutine { continuation ->
            startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) {
                            continuation.resume(
                                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                                    PlayBillingGateway.ConnectionResult.Ready
                                } else {
                                    PlayBillingGateway.ConnectionResult.Failed(billingResult)
                                },
                            )
                        }
                    }

                    override fun onBillingServiceDisconnected() = Unit
                },
            )
        }
    }

private suspend fun BillingClient.queryBillingConfig(): PlayBillingGateway.BillingConfigResult =
    suspendCancellableCoroutine { continuation ->
        getBillingConfigAsync(
            GetBillingConfigParams.newBuilder().build(),
        ) { billingResult, billingConfig ->
            if (continuation.isActive) {
                continuation.resume(
                    PlayBillingGateway.BillingConfigResult(
                        billingResult = billingResult,
                        countryCode = billingConfig?.countryCode,
                    ),
                )
            }
        }
    }

private suspend fun BillingClient.querySupportProductDetails(): PlayBillingGateway.ProductDetailsResult =
    suspendCancellableCoroutine { continuation ->
        val productList =
            PlayBillingGateway.SUPPORT_PRODUCT_IDS.map { productId ->
                QueryProductDetailsParams.Product
                    .newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            }
        queryProductDetailsAsync(
            QueryProductDetailsParams
                .newBuilder()
                .setProductList(productList)
                .build(),
        ) { billingResult, productDetailsResult ->
            if (continuation.isActive) {
                continuation.resume(
                    PlayBillingGateway.ProductDetailsResult(
                        billingResult = billingResult,
                        productDetailsList = productDetailsResult.productDetailsList,
                    ),
                )
            }
        }
    }

private suspend fun BillingClient.queryInAppPurchases(): PlayBillingGateway.PurchasesResult =
    suspendCancellableCoroutine { continuation ->
        queryPurchasesAsync(
            QueryPurchasesParams
                .newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
        ) { billingResult, purchasesList ->
            if (continuation.isActive) {
                continuation.resume(
                    PlayBillingGateway.PurchasesResult(
                        billingResult = billingResult,
                        purchasesList = purchasesList,
                    ),
                )
            }
        }
    }

private suspend fun BillingClient.acknowledge(purchaseToken: String): BillingResult =
    suspendCancellableCoroutine { continuation ->
        acknowledgePurchase(
            AcknowledgePurchaseParams
                .newBuilder()
                .setPurchaseToken(purchaseToken)
                .build(),
        ) { billingResult ->
            if (continuation.isActive) {
                continuation.resume(billingResult)
            }
        }
    }

private suspend fun BillingClient.consume(purchaseToken: String): BillingResult =
    suspendCancellableCoroutine { continuation ->
        consumeAsync(
            ConsumeParams
                .newBuilder()
                .setPurchaseToken(purchaseToken)
                .build(),
        ) { billingResult, _ ->
            if (continuation.isActive) {
                continuation.resume(billingResult)
            }
        }
    }
