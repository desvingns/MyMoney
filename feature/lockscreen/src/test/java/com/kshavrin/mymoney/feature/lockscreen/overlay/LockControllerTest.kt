package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import app.cash.turbine.test
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.datastore.model.VersionedAppSettings
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.lockscreen.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val lifecycleOwner =
        object : LifecycleOwner {
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

        val result =
            controller.shouldLockAfterIdle(
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

        val result =
            controller.shouldLockAfterIdle(
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

        val result =
            controller.shouldLockAfterIdle(
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

        val result =
            controller.shouldLockAfterIdle(
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

        val result =
            controller.shouldLockAfterIdle(
                pausedAt = null,
                now = 600_000L,
                settings = settings,
            )

        assertFalse(result)
    }

    // --- 2. cold start --------------------------------------------------------------------

    @Test
    fun `isResolved stays false before first settings emission and becomes true after enabled settings arrive`() =
        runTest {
            val appSettings = DeferredFirstEmissionAppSettingsRepository()
            val controller = buildController(appSettings)

            assertFalse(controller.isResolved.value)
            assertFalse(controller.shouldShowLock.value)

            appSettings.emit(AppSettings().lockEnabled())

            assertTrue(controller.isResolved.value)
            assertTrue(controller.shouldShowLock.value)
        }

    @Test
    fun `cold start with lock disabled stays unlocked`() =
        runTest {
            val controller = buildController(initialSettings = AppSettings(biometricLockEnabled = false))

            controller.shouldShowLock.test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `app content security mirrors the persisted recents privacy setting`() =
        runTest {
            val controller = buildController(AppSettings(hideAppContentInRecents = true))

            controller.appContentSecure.test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            appSettings.seed(AppSettings(hideAppContentInRecents = false))

            controller.appContentSecure.test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each activity start publishes its current lock and app content security state`() =
        runTest {
            val controller = buildController(AppSettings())

            val firstStartId = controller.onMainActivityCreated()

            assertEquals(firstStartId, controller.appContentSecurityState.value?.activityStartId)
            assertFalse(controller.appContentSecurityState.value?.shouldSecure ?: true)
            assertFalse(controller.shouldShowLock.value)

            appSettings.seed(
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                ),
            )

            val secondStartId = controller.onMainActivityCreated()

            assertTrue(secondStartId > firstStartId)
            assertEquals(secondStartId, controller.appContentSecurityState.value?.activityStartId)
            assertTrue(controller.appContentSecurityState.value?.shouldSecure ?: false)
            assertTrue(controller.shouldShowLock.value)
            assertTrue(controller.isActivityLockResolved.value)
        }

    @Test
    fun `activity start resolves from the collector emission after creation`() =
        runTest {
            val settings = DeferredFirstEmissionAppSettingsRepository()
            val controller = buildController(settings)

            val startId = controller.onMainActivityCreated()

            assertFalse(controller.isActivityLockResolved.value)

            settings.emit(
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                ),
            )

            assertEquals(startId, controller.appContentSecurityState.value?.activityStartId)
            assertTrue(controller.appContentSecurityState.value?.shouldSecure ?: false)
            assertTrue(controller.shouldShowLock.value)
            assertTrue(controller.isActivityLockResolved.value)
        }

    @Test
    fun `stale activity start cannot resolve a newer activity`() =
        runTest {
            val settings = DeferredFirstEmissionAppSettingsRepository()
            val controller = buildController(settings)

            val staleStartId = controller.onMainActivityCreated()
            val currentStartId = controller.onMainActivityCreated()
            settings.emit(
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                ),
            )

            assertTrue(currentStartId > staleStartId)
            assertEquals(currentStartId, controller.appContentSecurityState.value?.activityStartId)
            assertTrue(controller.appContentSecurityState.value?.shouldSecure ?: false)
            assertTrue(controller.shouldShowLock.value)
            assertTrue(controller.isActivityLockResolved.value)
        }

    @Test
    fun `newer settings emission cannot be overwritten by an older activity startup snapshot`() =
        runTest {
            val olderSnapshot = CompletableDeferred<AppSettings>()
            val newerSnapshot = CompletableDeferred<AppSettings>()
            val settings =
                DeferredFirstEmissionAppSettingsRepository(
                    startupSnapshots = listOf(olderSnapshot, newerSnapshot),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()
            val newerSettings =
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                )

            newerSnapshot.complete(newerSettings)

            assertEquals(newerStartId, controller.appContentSecurityState.value?.activityStartId)
            assertTrue(controller.appContentSecurityState.value?.shouldSecure ?: false)
            assertTrue(controller.appContentSecure.value)
            assertTrue(controller.shouldShowLock.value)

            olderSnapshot.complete(AppSettings())

            assertTrue(olderStartId < newerStartId)
            assertEquals(
                AppContentSecurityState(newerStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )
            assertTrue(controller.appContentSecure.value)
            assertTrue(controller.shouldShowLock.value)
        }

    @Test
    fun `activity startup snapshot is revalidated after a newer collector emission`() =
        runTest {
            val newerSettings =
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                )
            val controller = buildController(StartupRaceAppSettingsRepository(newerSettings))

            val startId = controller.onMainActivityCreated()

            assertEquals(
                AppContentSecurityState(startId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )
            assertTrue(controller.appContentSecure.value)
            assertTrue(controller.shouldShowLock.value)
            assertTrue(controller.isActivityLockResolved.value)
        }

    @Test
    fun `unlocking a newer activity start leaves an older lock visible`() =
        runTest {
            val controller = buildController(AppSettings(biometricLockEnabled = true))

            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()

            assertTrue(controller.lockStateFor(olderStartId).value)
            assertTrue(controller.lockStateFor(newerStartId).value)

            controller.markUnlocked(newerStartId)

            assertTrue(controller.lockStateFor(olderStartId).value)
            assertFalse(controller.lockStateFor(newerStartId).value)
        }

    @Test
    fun `lockNow locks every live activity start independently`() =
        runTest {
            val controller = buildController(AppSettings())

            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()

            controller.lockNow()
            controller.markUnlocked(newerStartId)

            assertTrue(controller.lockStateFor(olderStartId).value)
            assertFalse(controller.lockStateFor(newerStartId).value)
        }

    @Test
    fun `destroyed activity start no longer receives global lock requests`() =
        runTest {
            val controller = buildController(AppSettings())
            val startId = controller.onMainActivityCreated()
            val lockState = controller.lockStateFor(startId)

            controller.onMainActivityDestroyed(startId)
            controller.lockNow()

            assertFalse(lockState.value)
        }

    @Test
    fun `disabling biometric lock clears every live activity state`() =
        runTest {
            val controller = buildController(AppSettings(biometricLockEnabled = true))
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()

            appSettings.seed(AppSettings(biometricLockEnabled = false))

            assertFalse(controller.lockStateFor(olderStartId).value)
            assertFalse(controller.lockStateFor(newerStartId).value)
        }

    @Test
    fun `activity start ends locked after intermediate disabled then enabled emissions`() =
        runTest {
            val settings = DeferredFirstEmissionAppSettingsRepository()
            val controller = buildController(settings)
            val startId = controller.onMainActivityCreated()

            settings.emit(AppSettings(biometricLockEnabled = false))
            assertFalse(controller.lockStateFor(startId).value)

            settings.emit(AppSettings(biometricLockEnabled = true))

            assertTrue(controller.lockStateFor(startId).value)
            assertTrue(controller.isActivityLockResolved.value)
        }

    @Test
    fun `stale disabled emission does not unlock an activity resolved with biometric enabled`() =
        runTest {
            val settings = StaleDisabledEmissionAppSettingsRepository()
            val controller = buildController(settings)
            val startId = controller.onMainActivityCreated()

            assertTrue(controller.lockStateFor(startId).value)

            settings.emitStaleDisabled()

            assertTrue(controller.lockStateFor(startId).value)
            assertTrue(controller.appContentSecurityState.value?.shouldSecure ?: false)
        }

    @Test
    fun `runtime biometric disable clears every live activity state`() =
        runTest {
            val settings =
                VersionedAppSettingsRepository(
                    VersionedAppSettings(
                        settings = AppSettings(biometricLockEnabled = true, hideAppContentInRecents = true),
                        revision = 2L,
                    ),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()

            settings.emit(
                VersionedAppSettings(
                    settings = AppSettings(biometricLockEnabled = false, hideAppContentInRecents = false),
                    revision = 3L,
                ),
            )

            assertFalse(controller.lockStateFor(olderStartId).value)
            assertFalse(controller.lockStateFor(newerStartId).value)
            assertFalse(controller.shouldShowLock.value)
            assertFalse(controller.appContentSecurityState.value?.shouldSecure ?: true)
        }

    @Test
    fun `scoped unlock recomputes global state from remaining live locks`() =
        runTest {
            val controller = buildController(AppSettings(biometricLockEnabled = true))
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()

            controller.markUnlocked(newerStartId)

            assertTrue(controller.shouldShowLock.value)

            controller.markUnlocked(olderStartId)

            assertFalse(controller.shouldShowLock.value)
        }

    @Test
    fun `destroying newest activity reelects older activity for privacy updates`() =
        runTest {
            val settings =
                VersionedAppSettingsRepository(
                    VersionedAppSettings(
                        settings = AppSettings(hideAppContentInRecents = true),
                        revision = 1L,
                    ),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            yield()
            val newerStartId = controller.onMainActivityCreated()
            yield()

            controller.onMainActivityDestroyed(newerStartId)

            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )

            settings.emit(
                VersionedAppSettings(
                    settings = AppSettings(hideAppContentInRecents = false),
                    revision = 2L,
                ),
            )

            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = false),
                controller.appContentSecurityState.value,
            )
        }

    @Test
    fun `destroying resolved newest activity resolves skipped older startup from current settings`() =
        runTest {
            val olderSnapshot = CompletableDeferred<AppSettings>()
            val newerSnapshot = CompletableDeferred<AppSettings>()
            val settings =
                DeferredFirstEmissionAppSettingsRepository(
                    startupSnapshots = listOf(olderSnapshot, newerSnapshot),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()
            val currentSettings =
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                )

            newerSnapshot.complete(currentSettings)

            assertEquals(newerStartId, controller.appContentSecurityState.value?.activityStartId)
            assertTrue(controller.lockStateFor(newerStartId).value)

            controller.onMainActivityDestroyed(newerStartId)

            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )
            assertTrue(controller.isActivityLockResolved.value)
            assertTrue(controller.lockStateFor(olderStartId).value)

            olderSnapshot.complete(AppSettings())

            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )
            assertTrue(controller.lockStateFor(olderStartId).value)

            settings.emit(currentSettings.copy(hideAppContentInRecents = false))

            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = false),
                controller.appContentSecurityState.value,
            )
        }

    @Test
    fun `older startup callback resolves its local state while newer activity is live`() =
        runTest {
            val olderSnapshot = CompletableDeferred<AppSettings>()
            val newerSnapshot = CompletableDeferred<AppSettings>()
            val settings =
                DeferredFirstEmissionAppSettingsRepository(
                    startupSnapshots = listOf(olderSnapshot, newerSnapshot),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()
            val currentSettings = AppSettings(biometricLockEnabled = true)

            olderSnapshot.complete(currentSettings)

            assertTrue(controller.isActivityLockResolvedFor(olderStartId).value)
            assertTrue(controller.lockStateFor(olderStartId).value)
            assertFalse(controller.isActivityLockResolved.value)

            newerSnapshot.complete(currentSettings)

            assertEquals(newerStartId, controller.appContentSecurityState.value?.activityStartId)
        }

    @Test
    fun `destroying elected older activity resolves newer unresolved activity and publishes privacy`() =
        runTest {
            val olderSnapshot = CompletableDeferred<AppSettings>()
            val newerSnapshot = CompletableDeferred<AppSettings>()
            val settings =
                DeferredFirstEmissionAppSettingsRepository(
                    startupSnapshots = listOf(olderSnapshot, newerSnapshot),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val currentSettings =
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                )
            olderSnapshot.complete(currentSettings)
            val newerStartId = controller.onMainActivityCreated()

            controller.onMainActivityDestroyed(olderStartId)

            assertEquals(
                AppContentSecurityState(newerStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )
            assertTrue(controller.isActivityLockResolved.value)
            assertTrue(controller.lockStateFor(newerStartId).value)

            settings.emit(currentSettings.copy(hideAppContentInRecents = false))

            assertEquals(
                AppContentSecurityState(newerStartId, shouldSecure = false),
                controller.appContentSecurityState.value,
            )
        }

    @Test
    fun `late older startup callback cannot overwrite elected activity state`() =
        runTest {
            val olderSnapshot = CompletableDeferred<AppSettings>()
            val newerSnapshot = CompletableDeferred<AppSettings>()
            val settings =
                DeferredFirstEmissionAppSettingsRepository(
                    startupSnapshots = listOf(olderSnapshot, newerSnapshot),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val newerStartId = controller.onMainActivityCreated()
            val electedSettings =
                AppSettings(
                    biometricLockEnabled = true,
                    hideAppContentInRecents = true,
                )

            newerSnapshot.complete(electedSettings)
            olderSnapshot.complete(AppSettings())

            assertEquals(
                AppContentSecurityState(newerStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )
            assertTrue(controller.isActivityLockResolved.value)
            assertTrue(controller.isActivityLockResolvedFor(olderStartId).value)
            assertTrue(controller.lockStateFor(newerStartId).value)
        }

    @Test
    fun `privacy updates reach the elected activity while a newer startup is unresolved`() =
        runTest {
            val olderSnapshot = CompletableDeferred<AppSettings>()
            val newerSnapshot = CompletableDeferred<AppSettings>()
            val settings =
                DeferredFirstEmissionAppSettingsRepository(
                    startupSnapshots = listOf(olderSnapshot, newerSnapshot),
                )
            val controller = buildController(settings)
            val olderStartId = controller.onMainActivityCreated()
            val currentSettings = AppSettings(hideAppContentInRecents = true)

            olderSnapshot.complete(currentSettings)
            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = true),
                controller.appContentSecurityState.value,
            )

            val newerStartId = controller.onMainActivityCreated()
            assertFalse(controller.isActivityLockResolved.value)

            settings.emit(currentSettings.copy(hideAppContentInRecents = false))

            assertEquals(
                AppContentSecurityState(olderStartId, shouldSecure = false),
                controller.appContentSecurityState.value,
            )

            newerSnapshot.complete(currentSettings)

            assertEquals(newerStartId, controller.appContentSecurityState.value?.activityStartId)
        }

    // --- 3. pause / resume idle transition ------------------------------------------------

    @Test
    fun `resume after idle exceeds the timeout shows the lock`() =
        runTest {
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
    fun `resume within the timeout stays unlocked`() =
        runTest {
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
    fun `resume without a preceding pause stays unlocked`() =
        runTest {
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
    fun `markUnlocked clears the lock`() =
        runTest {
            val controller = buildController(initialSettings = AppSettings().lockEnabled())

            controller.markUnlocked()

            controller.shouldShowLock.test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `disabling biometric lock after the overlay is shown hides the lock immediately`() =
        runTest {
            val controller = buildController(initialSettings = AppSettings().lockEnabled())

            controller.shouldShowLock.test {
                assertTrue(awaitItem())

                appSettings.seed(AppSettings(biometricLockEnabled = false))

                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `markUnlocked resets pausedAt so a resume within the timeout stays unlocked`() =
        runTest {
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
    fun `lockNow shows the lock`() =
        runTest {
            val controller = buildController(initialSettings = AppSettings(biometricLockEnabled = false))

            controller.lockNow()

            controller.shouldShowLock.test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun buildController(
        appSettingsRepository: AppSettingsRepository,
    ): LockController =
        LockController(
            appSettingsRepository = appSettingsRepository,
            scope = CoroutineScope(mainDispatcherRule.testDispatcher),
        )

    private class DeferredFirstEmissionAppSettingsRepository(
        private val startupSnapshots: List<CompletableDeferred<AppSettings>> = emptyList(),
    ) : AppSettingsRepository {
        private val settingsFlow = MutableSharedFlow<AppSettings>(replay = 0, extraBufferCapacity = 1)
        private var latest = AppSettings()
        private var startupCollectorIndex = 0

        override val settings: Flow<AppSettings> =
            if (startupSnapshots.isEmpty()) {
                settingsFlow
            } else {
                kotlinx.coroutines.flow.flow {
                    val collectorIndex =
                        synchronized(this@DeferredFirstEmissionAppSettingsRepository) {
                            startupCollectorIndex++
                        }
                    if (collectorIndex == 0) {
                        emit(AppSettings())
                        emitAll(settingsFlow)
                    } else {
                        emit(startupSnapshots[collectorIndex - 1].await())
                    }
                }
            }

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            latest = transform(latest)
            settingsFlow.emit(latest)
        }

        suspend fun emit(settings: AppSettings) {
            latest = settings
            settingsFlow.emit(settings)
        }
    }

    private class StartupRaceAppSettingsRepository(
        private val newerSettings: AppSettings,
    ) : AppSettingsRepository {
        private val collectorUpdate = CompletableDeferred<AppSettings>()
        private var subscriptionCount = 0

        override val settings: Flow<AppSettings> =
            flow {
                val subscriptionIndex = synchronized(this@StartupRaceAppSettingsRepository) {
                    subscriptionCount++
                }
                if (subscriptionIndex == 0) {
                    emit(AppSettings())
                    emit(collectorUpdate.await())
                } else {
                    collectorUpdate.complete(newerSettings)
                    yield()
                    emit(AppSettings())
                }
            }

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    private class StaleDisabledEmissionAppSettingsRepository : AppSettingsRepository {
        private val staleDisabled = CompletableDeferred<Unit>()
        private var subscriptionCount = 0

        override val versionedSettings: Flow<VersionedAppSettings> =
            flow {
                val subscriptionIndex = synchronized(this@StaleDisabledEmissionAppSettingsRepository) {
                    subscriptionCount++
                }
                if (subscriptionIndex == 0) {
                    emit(
                        VersionedAppSettings(
                            settings = AppSettings(biometricLockEnabled = true, hideAppContentInRecents = true),
                            revision = 2L,
                        ),
                    )
                    staleDisabled.await()
                    emit(
                        VersionedAppSettings(
                            settings = AppSettings(biometricLockEnabled = false),
                            revision = 1L,
                        ),
                    )
                    awaitCancellation()
                } else {
                    emit(
                        VersionedAppSettings(
                            settings = AppSettings(biometricLockEnabled = true, hideAppContentInRecents = true),
                            revision = 2L,
                        ),
                    )
                }
            }

        override val settings: Flow<AppSettings> = versionedSettings.map { it.settings }

        fun emitStaleDisabled() {
            staleDisabled.complete(Unit)
        }

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }

    private class VersionedAppSettingsRepository(
        initialSettings: VersionedAppSettings,
    ) : AppSettingsRepository {
        private val state = MutableStateFlow(initialSettings)

        override val versionedSettings: Flow<VersionedAppSettings> = state
        override val settings: Flow<AppSettings> = state.map { it.settings }

        fun emit(settings: VersionedAppSettings) {
            state.value = settings
        }

        override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
    }
}
