package com.kshavrin.mymoney.core.billing

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingWiringContractTest {
    @Test
    fun `support feature module is registered and wired into the app`() {
        val settings = file("settings.gradle.kts").readText()
        val app = file("app/build.gradle.kts").readText()
        val support = file("feature/support/build.gradle.kts").readText()

        assertTrue(settings.contains("include(\":feature:support\")"))
        assertTrue(app.contains("implementation(project(\":feature:support\"))"))
        assertContainsAll(
            support,
            listOf(
                "alias(libs.plugins.mymoney.android.feature)",
                "namespace = \"com.kshavrin.mymoney.feature.support\"",
                "implementation(project(\":core:ui\"))",
                "implementation(project(\":core:designsystem\"))",
                "implementation(project(\":core:domain\"))",
                "implementation(project(\":core:common\"))",
            ),
        )
    }

    @Test
    fun `billing module is registered and disabled by default`() {
        val settings = file("settings.gradle.kts").readText()
        val build = file("core/billing/build.gradle.kts").readText()
        val app = file("app/build.gradle.kts").readText()

        assertTrue(settings.contains("include(\":core:billing\")"))
        assertContainsAll(
            build,
            listOf(
                "alias(libs.plugins.mymoney.android.library)",
                "implementation(project(\":core:common\"))",
                "implementation(project(\":core:domain\"))",
                "implementation(libs.play.billing.ktx)",
                "val billingEnabled =",
                "providers.gradleProperty(\"billing.enabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "buildConfigField(\"boolean\", \"BILLING_ENABLED\", billingEnabled.toString())",
                "buildConfig = true",
            ),
        )
        assertTrue(app.contains("implementation(project(\":core:billing\"))"))
        assertContainsAll(
            app,
            listOf(
                "val billingEnabled =",
                "buildConfigField(\"boolean\", \"BILLING_ENABLED\", billingEnabled.toString())",
            ),
        )
    }

    @Test
    fun `billing module binds the gateway and SDK factory as singleton dependencies`() {
        val module = file("core/billing/src/main/java/com/kshavrin/mymoney/core/billing/di/BillingModule.kt").readText()

        assertContainsInOrder(
            module,
            listOf(
                "@Module",
                "@InstallIn(SingletonComponent::class)",
                "abstract class BillingModule",
                "@Binds",
                "@Singleton",
                "abstract fun bindBillingGateway(impl: PlayBillingGateway): BillingGateway",
                "@Binds",
                "@Singleton",
                "abstract fun bindBillingClientFactory(impl: PlayBillingClientFactory): BillingClientFactory",
            ),
        )
    }

    @Test
    fun `app restores subscription purchases during startup`() {
        val appSource = file("app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt").readText()

        assertContainsInOrder(
            appSource,
            listOf(
                "lateinit var billingGateway: Lazy<BillingGateway>",
                "recoverSubscriptions()",
                "private fun recoverSubscriptions()",
                "applicationScope.launch(ioDispatcher)",
                "billingGateway.get().resolveSubscriptionPurchases()",
            ),
        )
    }

    @Test
    fun `app restores supporter purchases before starting shared Play reconciliation`() {
        val appSource = file("app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt").readText()

        assertContainsInOrder(
            appSource,
            listOf(
                "lateinit var supporterSync: Lazy<SupporterSync>",
                "lateinit var supportPurchaseReconciliationCoordinator:",
                "Lazy<SupportPurchaseReconciliationCoordinator>",
                "recoverSupporterPurchases()",
                "private fun recoverSupporterPurchases()",
                "supporterSync.get().restore()",
                "supportPurchaseReconciliationCoordinator.get().reconcile()",
            ),
        )
    }

    @Test
    fun `supporter repository outbox and sync bindings are registered`() {
        val dataStoreModule = file("core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/di/DataStoreModule.kt").readText()
        val syncModule = file("core/sync/src/main/java/com/kshavrin/mymoney/core/sync/di/SyncModule.kt").readText()

        assertContainsInOrder(
            dataStoreModule,
            listOf(
                "abstract fun bindSupporterRepository(impl: SupporterRepositoryImpl): SupporterRepository",
                "abstract fun bindSupporterPurchaseStore(impl: SupporterPurchaseStoreImpl): SupporterPurchaseStore",
            ),
        )
        assertContainsInOrder(
            syncModule,
            listOf(
                "abstract fun bindSupporterSync(impl: SupporterSyncImpl): SupporterSync",
                "abstract fun bindSupportPurchaseReconciliationCoordinator(",
                "SupportPurchaseReconciliationCoordinatorImpl",
                "): SupportPurchaseReconciliationCoordinator",
            ),
        )
    }

    @Test
    fun `Play gateway retains explicit cleanup and serializes purchase and connection processing`() {
        val source =
            file("core/billing/src/main/java/com/kshavrin/mymoney/core/billing/PlayBillingGateway.kt").readText()
        val pendingResolution = methodBody(source, "override fun resolvePendingPurchases()")
        val purchaseProcessing = methodBody(source, "private suspend fun processPurchase(")
        val delivery = methodBody(source, "private fun deliverToActivePurchase(")

        assertContainsInOrder(
            pendingResolution,
            listOf(
                "if (!BuildConfig.BILLING_ENABLED)",
                "refreshAvailability()",
                "val purchasesResult = billingClient.queryInAppPurchases()",
                "filter(::containsSupportProduct)",
                "map { purchase -> processPurchase(purchase) }",
            ),
        )
        assertFalse(methodBody(source, "private suspend fun querySupportProductDetails()").contains("queryPurchases"))
        assertTrue(source.contains("private val processingMutex = Mutex()"))
        assertContainsInOrder(
            source,
            listOf(
                "private val billingConnectionMutex = Mutex()",
                "private suspend fun awaitConnection(): ConnectionResult =",
                "billingConnectionMutex.withLock {",
                "billingClient.awaitConnection()",
            ),
        )
        assertTrue(source.contains("val SUPPORT_PRODUCT_IDS = setOf(\"coffee_small\", \"coffee_large\")"))
        assertContainsInOrder(
            source,
            listOf(
                "QueryProductDetailsParams.Product",
                "setProductId(productId)",
                "setProductType(BillingClient.ProductType.INAPP)",
            ),
        )
        assertContainsInOrder(
            purchaseProcessing,
            listOf(
                "purchaseProcessor.process(",
                "PurchaseProcessingInput(",
                "productId = purchase.products.first { it in SUPPORT_PRODUCT_IDS }",
                "state = purchase.purchaseState.toPurchaseProcessingState()",
                "if (outcome is PurchaseOutcome.Purchased)",
                "supporterSync.syncPurchase(outcome)",
            ),
        )
        assertContainsInOrder(
            source,
            listOf(
                "class PurchaseProcessor(",
                "private val processingMutex = Mutex()",
                "PurchaseProcessingState.Pending -> PurchaseOutcome.Pending",
                "if (!purchase.isAcknowledged)",
                "val acknowledgeResponseCode = acknowledge(purchase.purchaseToken)",
                "val consumeResponseCode = consume(purchase.purchaseToken)",
            ),
        )
        assertContainsInOrder(
            delivery,
            listOf(
                "outcome is PurchaseOutcome.Purchased && outcome.productId != currentPurchase.productId",
                "if (outcome.isTerminal() && !pendingPurchase.compareAndSet(currentPurchase, null))",
                "currentPurchase.outcomes.emit(outcome)",
            ),
        )
        assertContainsInOrder(
            source,
            listOf(
                "class PurchaseOutcomeBridge",
                "Channel<PurchaseOutcome>(Channel.BUFFERED)",
                "outcome.isTerminal()",
                "outcomes.close()",
            ),
        )
        assertTrue(source.contains("responseCode == BillingClient.BillingResponseCode.USER_CANCELED"))
        assertTrue(source.contains("BillingAvailability.UnavailableInRegion"))
        assertContainsInOrder(
            source,
            listOf(
                "enablePendingPurchases(",
                "enableOneTimeProducts()",
                "enableAutoServiceReconnection()",
            ),
        )
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private fun assertContainsInOrder(
        text: String,
        fragments: List<String>,
    ) {
        var startIndex = 0
        fragments.forEach { fragment ->
            val index = text.indexOf(fragment, startIndex)
            assertTrue("Expected to find '$fragment' after index $startIndex", index >= 0)
            startIndex = index + fragment.length
        }
    }

    private fun methodBody(
        source: String,
        signature: String,
    ): String {
        val start = source.indexOf(signature)
        assertTrue("Expected method '$signature'", start >= 0)
        val nextMethod = source.indexOf("\n        override fun ", startIndex = start + signature.length)
        val nextPrivate = source.indexOf("\n        private ", startIndex = start + signature.length)
        val end = listOf(nextMethod, nextPrivate).filter { it >= 0 }.minOrNull() ?: source.length
        return source.substring(start, end)
    }

    private fun file(path: String): File = File(repositoryRoot, path)

    private companion object {
        val repositoryRoot: File =
            generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile
                } ?: error("Unable to locate repository root")
    }
}
