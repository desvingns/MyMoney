package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.reset.FactoryResetGateway
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryResetUseCaseTest {

    private class FakeFactoryResetGateway : FactoryResetGateway {
        val callOrder: MutableList<String> = mutableListOf()
        private val failures = mutableMapOf<String, Throwable>()

        fun willThrowOn(
            step: String,
            t: Throwable,
        ) {
            failures[step] = t
        }

        override suspend fun detachCloudSync() {
            callOrder += "detachCloudSync"
            failures["detachCloudSync"]?.let { throw it }
        }

        override suspend fun wipeLocalData() {
            callOrder += "wipeLocalData"
            failures["wipeLocalData"]?.let { throw it }
        }

        override suspend fun resetSettings() {
            callOrder += "resetSettings"
            failures["resetSettings"]?.let { throw it }
        }

        override suspend fun clearSecrets() {
            callOrder += "clearSecrets"
            failures["clearSecrets"]?.let { throw it }
        }
    }

    @Test
    fun `invoke calls all four gateway steps in declared order`() =
        runTest {
            val gateway = FakeFactoryResetGateway()
            val useCase = FactoryResetUseCase(gateway)

            useCase()

            assertEquals(
                listOf("detachCloudSync", "wipeLocalData", "resetSettings", "clearSecrets"),
                gateway.callOrder,
            )
        }

    @Test
    fun `detachCloudSync is called strictly before wipeLocalData`() =
        runTest {
            val gateway = FakeFactoryResetGateway()
            val useCase = FactoryResetUseCase(gateway)

            useCase()

            val detachIndex = gateway.callOrder.indexOf("detachCloudSync")
            val wipeIndex = gateway.callOrder.indexOf("wipeLocalData")
            assertTrue(
                "detachCloudSync must precede wipeLocalData — fail-safe invariant",
                detachIndex >= 0 && detachIndex < wipeIndex,
            )
        }

    @Test
    fun `invoke returns Result success when all steps succeed`() =
        runTest {
            val useCase = FactoryResetUseCase(FakeFactoryResetGateway())

            val result = useCase()

            assertTrue(result.isSuccess)
        }

    @Test
    fun `invoke calls remaining steps even when wipeLocalData throws`() =
        runTest {
            val failure = RuntimeException("wipe failed")
            val gateway = FakeFactoryResetGateway()
            gateway.willThrowOn("wipeLocalData", failure)
            val useCase = FactoryResetUseCase(gateway)

            useCase()

            assertTrue(
                "resetSettings must still be called after wipeLocalData throws",
                gateway.callOrder.contains("resetSettings"),
            )
            assertTrue(
                "clearSecrets must still be called after wipeLocalData throws",
                gateway.callOrder.contains("clearSecrets"),
            )
        }

    @Test
    fun `invoke returns Result failure with primary cause when wipeLocalData throws`() =
        runTest {
            val failure = RuntimeException("wipe failed")
            val gateway = FakeFactoryResetGateway()
            gateway.willThrowOn("wipeLocalData", failure)
            val useCase = FactoryResetUseCase(gateway)

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals(failure, result.exceptionOrNull())
        }

    @Test
    fun `invoke suppresses extra failures as suppressed exceptions under the primary cause`() =
        runTest {
            val primary = RuntimeException("detach failed")
            val secondary = RuntimeException("wipe also failed")
            val gateway = FakeFactoryResetGateway()
            gateway.willThrowOn("detachCloudSync", primary)
            gateway.willThrowOn("wipeLocalData", secondary)
            val useCase = FactoryResetUseCase(gateway)

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals(primary, result.exceptionOrNull())
            assertTrue(
                "secondary failure must appear in suppressed list",
                result.exceptionOrNull()!!.suppressed.contains(secondary),
            )
        }

    @Test
    fun `invoke calls resetSettings confirming the step is orchestrated`() =
        runTest {
            val gateway = FakeFactoryResetGateway()
            val useCase = FactoryResetUseCase(gateway)

            useCase()

            assertTrue(
                "resetSettings must be invoked — deviceId preservation inside it is covered by AppSettingsRepositoryTest",
                gateway.callOrder.contains("resetSettings"),
            )
        }

    @Test
    fun `invoke calls all four steps even when the first three each throw`() =
        runTest {
            val gateway = FakeFactoryResetGateway()
            gateway.willThrowOn("detachCloudSync", RuntimeException("detach failed"))
            gateway.willThrowOn("wipeLocalData", RuntimeException("wipe failed"))
            gateway.willThrowOn("resetSettings", RuntimeException("settings failed"))
            val useCase = FactoryResetUseCase(gateway)

            useCase()

            assertEquals(4, gateway.callOrder.size)
            assertTrue(gateway.callOrder.contains("clearSecrets"))
        }
}
