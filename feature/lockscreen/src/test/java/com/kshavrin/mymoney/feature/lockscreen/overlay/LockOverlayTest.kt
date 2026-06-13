package com.kshavrin.mymoney.feature.lockscreen.overlay

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.SecureSettings
import com.kshavrin.mymoney.feature.lockscreen.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.lockscreen.fake.FakeSecureStorage
import com.kshavrin.mymoney.feature.lockscreen.setup.PinHasher
import com.kshavrin.mymoney.feature.lockscreen.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.InvocationTargetException
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

@OptIn(ExperimentalCoroutinesApi::class)
class LockOverlayTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val hasher = PinHasher()

    @Test
    fun `recordFailedPinAttempt persists 30 60 and 120 second deadlines at thresholds 5 10 and 15`() =
        runTest {
            val storage = FakeSecureStorage()
            val dependencies = TestLockOverlayEntryPoint(storage, mainDispatcherRule.testDispatcher)
            val expectedDelays = mapOf(5 to 30_000L, 10 to 60_000L, 15 to 120_000L)

            for (attempt in 1..15) {
                val before = System.currentTimeMillis()
                val deadline = recordFailedPinAttempt(dependencies)
                val after = System.currentTimeMillis()

                assertEquals(attempt, storage.read().failedPinAttempts)
                assertEquals(deadline, storage.read().pinLockoutDeadlineEpochMs)

                val expectedDelay = expectedDelays[attempt]
                if (expectedDelay == null) {
                    assertNull(deadline)
                } else {
                    assertNotNull(deadline)
                    assertTrue(deadline!! in (before + expectedDelay)..(after + expectedDelay))
                    assertEquals(deadline, currentLockoutDeadlineEpochMs(dependencies))
                    storage.writePinLockout(attempt, deadlineEpochMs = null)
                }
            }
        }

    @Test
    fun `lockoutDelayMs caps at thirty minutes`() {
        assertEquals(30L * 60L * 1_000L, lockoutDelayMs(35))
        assertEquals(30L * 60L * 1_000L, lockoutDelayMs(40))
    }

    @Test
    fun `verifyPin rehashes a legacy hash and clears stored lockout state on success`() =
        runTest {
            val storage =
                FakeSecureStorage(
                    SecureSettings(
                        pinHash = legacyHash("1234", fixedSalt),
                        failedPinAttempts = 10,
                        pinLockoutDeadlineEpochMs = System.currentTimeMillis() + 60_000L,
                    ),
                )
            val dependencies = TestLockOverlayEntryPoint(storage, mainDispatcherRule.testDispatcher)

            val verified = verifyPin(dependencies, "1234")

            assertTrue(verified)
            assertEquals(1, storage.writtenPinHashes.size)
            val rewritten = storage.read().pinHash
            assertNotNull(rewritten)
            assertTrue(rewritten!!.startsWith("v2:600000:"))
            assertTrue(hasher.verify("1234", rewritten))
            assertEquals(0, storage.read().failedPinAttempts)
            assertNull(storage.read().pinLockoutDeadlineEpochMs)
        }

    @Test
    fun `verifyPin clears the counter without rehashing when the stored hash is already current`() =
        runTest {
            val currentHash = hasher.hash("1234", fixedSalt)
            val storage =
                FakeSecureStorage(
                    SecureSettings(
                        pinHash = currentHash,
                        failedPinAttempts = 4,
                        pinLockoutDeadlineEpochMs = 123L,
                    ),
                )
            val dependencies = TestLockOverlayEntryPoint(storage, mainDispatcherRule.testDispatcher)

            val verified = verifyPin(dependencies, "1234")

            assertTrue(verified)
            assertTrue(storage.writtenPinHashes.isEmpty())
            assertEquals(currentHash, storage.read().pinHash)
            assertEquals(0, storage.read().failedPinAttempts)
            assertNull(storage.read().pinLockoutDeadlineEpochMs)
        }

    @Test
    fun `currentLockoutDeadlineEpochMs clears an expired deadline while preserving the failed count`() =
        runTest {
            val storage =
                FakeSecureStorage(
                    SecureSettings(
                        failedPinAttempts = 5,
                        pinLockoutDeadlineEpochMs = System.currentTimeMillis() - 1_000L,
                    ),
                )
            val dependencies = TestLockOverlayEntryPoint(storage, mainDispatcherRule.testDispatcher)

            val deadline = currentLockoutDeadlineEpochMs(dependencies)

            assertNull(deadline)
            assertEquals(5, storage.read().failedPinAttempts)
            assertNull(storage.read().pinLockoutDeadlineEpochMs)
        }

    private suspend fun verifyPin(
        dependencies: LockOverlayEntryPoint,
        pin: String,
    ): Boolean =
        invokePrivateSuspendMethod(
            methodName = "verifyPin",
            parameterTypes = arrayOf(LockOverlayEntryPoint::class.java, String::class.java),
            dependencies,
            pin,
        )

    private suspend fun currentLockoutDeadlineEpochMs(
        dependencies: LockOverlayEntryPoint,
    ): Long? =
        invokePrivateSuspendMethod(
            methodName = "currentLockoutDeadlineEpochMs",
            parameterTypes = arrayOf(LockOverlayEntryPoint::class.java),
            dependencies,
        )

    private suspend fun recordFailedPinAttempt(
        dependencies: LockOverlayEntryPoint,
    ): Long? =
        invokePrivateSuspendMethod(
            methodName = "recordFailedPinAttempt",
            parameterTypes = arrayOf(LockOverlayEntryPoint::class.java),
            dependencies,
        )

    private fun lockoutDelayMs(failedPinAttempts: Int): Long {
        val method = lockOverlayKtClass.getDeclaredMethod("lockoutDelayMs", Int::class.javaPrimitiveType)
        method.isAccessible = true
        return method.invoke(null, failedPinAttempts) as Long
    }

    private suspend fun <T> invokePrivateSuspendMethod(
        methodName: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): T =
        suspendCancellableCoroutine { continuation ->
            val method =
                lockOverlayKtClass.getDeclaredMethod(
                    methodName,
                    *parameterTypes,
                    Continuation::class.java,
                )
            method.isAccessible = true
            val callback =
                object : Continuation<Any?> {
                    override val context = continuation.context

                    override fun resumeWith(result: Result<Any?>) {
                        continuation.resumeWith(result as Result<T>)
                    }
                }
            val result =
                try {
                    method.invoke(null, *args, callback)
                } catch (exception: InvocationTargetException) {
                    continuation.resumeWith(Result.failure(exception.targetException))
                    return@suspendCancellableCoroutine
                }
            if (result !== COROUTINE_SUSPENDED) {
                @Suppress("UNCHECKED_CAST")
                continuation.resumeWith(Result.success(result as T))
            }
        }

    private fun legacyHash(
        pin: String,
        salt: ByteArray,
    ): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 10_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return "${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(derived)}"
    }

    private class TestLockOverlayEntryPoint(
        private val secureStorage: FakeSecureStorage,
        private val dispatcher: CoroutineDispatcher,
        private val appSettingsRepository: AppSettingsRepository = FakeAppSettingsRepository(),
    ) : LockOverlayEntryPoint {
        override fun secureStorage() = secureStorage

        override fun appSettingsRepository() = appSettingsRepository

        override fun ioDispatcher(): CoroutineDispatcher = dispatcher
    }

    private companion object {
        val fixedSalt =
            byteArrayOf(
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x11.toByte(),
                0x22.toByte(),
                0x33.toByte(),
                0x44.toByte(),
                0x55.toByte(),
                0x66.toByte(),
                0x77.toByte(),
                0x12.toByte(),
                0x34.toByte(),
                0x56.toByte(),
                0x78.toByte(),
                0x09.toByte(),
            )

        val lockOverlayKtClass: Class<*> =
            Class.forName("com.kshavrin.mymoney.feature.lockscreen.overlay.LockOverlayKt")
    }
}
