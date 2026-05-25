package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.feature.lockscreen.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.lockscreen.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockControllerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appSettings: FakeAppSettingsRepository

    /**
     * A minimal [LifecycleOwner] so the [LockController.onPause]/[LockController.onResume]
     * overrides (inherited from DefaultLifecycleObserver) can be driven directly without
     * standing up ProcessLifecycleOwner.
     */
    private val lifecycleOwner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry(this)
    }

    private fun buildController(
        initialSettings: AppSettings = AppSettings(),
    ): LockController {
        appSettings = FakeAppSettingsRepository(initialSettings)
        // A separate scope on the UnconfinedTestDispatcher: the init collector is a
        // never-completing collect on a hot StateFlow, so it must NOT live on the
        // runTest job (that would hang completion). Unconfined runs it eagerly, so the
        // first settings emission is observed synchronously at construction.
        return LockController(appSettings, CoroutineScope(mainDispatcherRule.testDispatcher))
    }

    private fun AppSettings.lockEnabled(timeoutSec: Int = 60) =
        copy(biometricLockEnabled = true, biometricIdleTimeoutSec = timeoutSec)

    // --- 1. shouldLockAfterIdle pure function ---------------------------------------------

    @Test
    fun `shouldLockAfterIdle is true when idle exceeds the timeout and lock is enabled`() {
        val controller = buildController()
        val settings = AppSettings().lockEnabled(timeoutSec = 60)

        val result = controller.shouldLockAfterIdle(
            pausedAt = 0L,
            now = 61_000L,
            settings = settings,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldLockAfterIdle is false when idle is below the timeout`() {
        val controller = buildController()
        val settings = AppSettings().lockEnabled(timeoutSec = 60)

        val result = controller.shouldLockAfterIdle(
            pausedAt = 0L,
            now = 59_000L,
            settings = settings,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldLockAfterIdle is true exactly at the timeout boundary`() {
        val controller = buildController()
        val settings = AppSettings().lockEnabled(timeoutSec = 60)

        val result = controller.shouldLockAfterIdle(
            pausedAt = 0L,
            now = 60_000L,
            settings = settings,
        )

        assertTrue(result)
    }

    @Test
    fun `shouldLockAfterIdle is false when lock is disabled regardless of idle`() {
        val controller = buildController()
        val settings = AppSettings(biometricLockEnabled = false, biometricIdleTimeoutSec = 60)

        val result = controller.shouldLockAfterIdle(
            pausedAt = 0L,
            now = 600_000L,
            settings = settings,
        )

        assertFalse(result)
    }

    @Test
    fun `shouldLockAfterIdle is false when there is no recorded pause time`() {
        val controller = buildController()
        val settings = AppSettings().lockEnabled(timeoutSec = 60)

        val result = controller.shouldLockAfterIdle(
            pausedAt = null,
            now = 600_000L,
            settings = settings,
        )

        assertFalse(result)
    }

    // --- 2. cold start --------------------------------------------------------------------

    @Test
    fun `cold start with lock enabled becomes locked after the first settings emission`() = runTest {
        val controller = buildController(initialSettings = AppSettings().lockEnabled())

        controller.shouldShowLock.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cold start with lock disabled stays unlocked`() = runTest {
        val controller = buildController(initialSettings = AppSettings(biometricLockEnabled = false))

        controller.shouldShowLock.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 3. pause / resume idle transition ------------------------------------------------

    @Test
    fun `resume after idle exceeds the timeout shows the lock`() = runTest {
        val controller = buildController(initialSettings = AppSettings().lockEnabled(timeoutSec = 60))
        controller.markUnlocked()

        controller.now = { 0L }
        controller.onPause(lifecycleOwner)
        controller.now = { 61_000L }
        controller.onResume(lifecycleOwner)

        controller.shouldShowLock.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume within the timeout stays unlocked`() = runTest {
        val controller = buildController(initialSettings = AppSettings().lockEnabled(timeoutSec = 60))
        controller.markUnlocked()

        controller.now = { 0L }
        controller.onPause(lifecycleOwner)
        controller.now = { 30_000L }
        controller.onResume(lifecycleOwner)

        controller.shouldShowLock.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `resume without a preceding pause stays unlocked`() = runTest {
        val controller = buildController(initialSettings = AppSettings().lockEnabled(timeoutSec = 60))
        controller.markUnlocked()

        controller.now = { 600_000L }
        controller.onResume(lifecycleOwner)

        controller.shouldShowLock.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 4. markUnlocked ------------------------------------------------------------------

    @Test
    fun `markUnlocked clears the lock`() = runTest {
        val controller = buildController(initialSettings = AppSettings().lockEnabled())

        controller.markUnlocked()

        controller.shouldShowLock.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markUnlocked resets pausedAt so a resume within the timeout stays unlocked`() = runTest {
        val controller = buildController(initialSettings = AppSettings().lockEnabled(timeoutSec = 60))

        controller.now = { 0L }
        controller.onPause(lifecycleOwner)
        controller.markUnlocked()
        controller.now = { 600_000L }
        controller.onResume(lifecycleOwner)

        controller.shouldShowLock.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- 5. lockNow -----------------------------------------------------------------------

    @Test
    fun `lockNow shows the lock`() = runTest {
        val controller = buildController(initialSettings = AppSettings(biometricLockEnabled = false))

        controller.lockNow()

        controller.shouldShowLock.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
