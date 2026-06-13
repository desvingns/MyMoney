package com.kshavrin.mymoney.feature.cloudsync

import app.cash.turbine.test
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.sync.SyncOutcome
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeRemoteConfigRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeSnapshotSync
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeSyncLogRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeSyncScheduler
import com.kshavrin.mymoney.feature.cloudsync.util.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var snapshotSync: FakeSnapshotSync
    private lateinit var scheduler: FakeSyncScheduler
    private lateinit var appSettings: FakeAppSettingsRepository
    private lateinit var syncLog: FakeSyncLogRepository
    private lateinit var remoteConfig: FakeRemoteConfigRepository

    private fun buildViewModel(
        initialSettings: AppSettings = AppSettings(),
        dropboxEnabled: Boolean = true,
        gdriveEnabled: Boolean = true,
    ): CloudSyncViewModel {
        snapshotSync = FakeSnapshotSync()
        scheduler = FakeSyncScheduler()
        appSettings = FakeAppSettingsRepository(initialSettings)
        syncLog = FakeSyncLogRepository()
        remoteConfig =
            FakeRemoteConfigRepository(
                dropboxEnabled = dropboxEnabled,
                gdriveEnabled = gdriveEnabled,
            )
        return CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)
    }

    private fun logEntry(target: String) =
        SyncLogEntry(
            id = 1L,
            target = target,
            event = "PUSH",
            entityKind = null,
            entityId = null,
            performedAt = Instant.parse("2026-05-18T10:00:00Z"),
            status = "SUCCESS",
            payloadHash = null,
            errorMessage = null,
        )

    // --- 1. init / state loading ----------------------------------------------------------

    @Test
    fun `init maps remote config flags onto each target card enabled`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = true, gdriveEnabled = false)

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.dropbox.enabled)
                assertFalse(state.drive.enabled)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init reflects connection status and account label for a connected target`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setAccountLabel(SyncTarget.Dropbox, Result.success("alice@dropbox.com"))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.dropbox.connected)
                assertEquals("alice@dropbox.com", state.dropbox.accountLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init leaves account label null for a disconnected target`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.state.test {
                val state = awaitItem()
                assertFalse(state.dropbox.connected)
                assertNull(state.dropbox.accountLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init loads recent log per target from the sync log repository`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog =
                FakeSyncLogRepository().apply {
                    seedRecent(SyncTarget.Dropbox.name, listOf(logEntry(SyncTarget.Dropbox.name)))
                }
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(1, state.dropbox.recentLog.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `init requests recent log by enum target name with a limit of five`() =
        runTest {
            buildViewModel()

            assertTrue(syncLog.recentByTargetCalls.contains("Dropbox" to 5))
            assertTrue(syncLog.recentByTargetCalls.contains("GoogleDrive" to 5))
        }

    @Test
    fun `init reflects auto sync flag and last sync timestamp from app settings`() =
        runTest {
            val viewModel =
                buildViewModel(
                    initialSettings = AppSettings(autoSyncEnabled = true, lastSyncAt = 1_700_000_000_000L),
                )

            viewModel.state.test {
                val state = awaitItem()
                assertTrue(state.autoSyncEnabled)
                assertEquals(1_700_000_000_000L, state.dropbox.lastSyncAtMs)
                assertEquals(1_700_000_000_000L, state.drive.lastSyncAtMs)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 2. sync now success --------------------------------------------------------------

    @Test
    fun `sync now on a connected target with a pushed outcome leaves no error or conflict`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(SyncTarget.Dropbox, Result.success(SyncOutcome.Pushed))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                val state = awaitItem()
                assertNull(state.errorBannerRes)
                assertNull(state.conflict)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(SyncTarget.Dropbox), snapshotSync.syncNowCalls)
        }

    @Test
    fun `sync now clears the syncing flag once the push completes`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(SyncTarget.Dropbox, Result.success(SyncOutcome.Pushed))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                assertFalse(awaitItem().dropbox.syncing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 3. sync now conflict -------------------------------------------------------------

    @Test
    fun `sync now with a conflict outcome sets a conflict prompt for the target`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(
                        SyncTarget.Dropbox,
                        Result.success(SyncOutcome.ConflictDetected(remoteModifiedMs = 200L, localLastSyncMs = 100L)),
                    )
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(SyncTarget.Dropbox, state.conflict?.target)
                assertEquals(200L, state.conflict?.remoteMs)
                assertEquals(100L, state.conflict?.localMs)
                assertNull(state.errorBannerRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 4. sync now failure --------------------------------------------------------------

    @Test
    fun `sync now failure maps the sync error to the matching string resource`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(SyncTarget.Dropbox, Result.failure(SyncException(SyncError.Server)))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                val state = awaitItem()
                assertEquals(viewModel.mapError(SyncError.Server), state.errorBannerRes)
                assertNull(state.conflict)
                assertFalse(state.dropbox.syncing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 5. sync now ignored when not connected -------------------------------------------

    @Test
    fun `sync now on a disconnected target is ignored`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                val state = awaitItem()
                assertNull(state.errorBannerRes)
                assertNull(state.conflict)
                assertFalse(state.dropbox.syncing)
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(snapshotSync.syncNowCalls.isEmpty())
        }

    // --- 6. auto sync toggle --------------------------------------------------------------

    @Test
    fun `auto sync toggled on persists the flag and enables periodic sync`() =
        runTest {
            val viewModel = buildViewModel(initialSettings = AppSettings(autoSyncEnabled = false))

            viewModel.onEvent(CloudSyncEvent.AutoSyncToggled(true))

            assertTrue(appSettings.settings.value.autoSyncEnabled)
            assertEquals(1, scheduler.enableCount)
            assertEquals(0, scheduler.disableCount)
        }

    @Test
    fun `auto sync toggled off persists the flag and disables periodic sync`() =
        runTest {
            val viewModel = buildViewModel(initialSettings = AppSettings(autoSyncEnabled = true))

            viewModel.onEvent(CloudSyncEvent.AutoSyncToggled(false))

            assertFalse(appSettings.settings.value.autoSyncEnabled)
            assertEquals(1, scheduler.disableCount)
            assertEquals(0, scheduler.enableCount)
        }

    @Test
    fun `auto sync toggled on re-emits state with the flag set`() =
        runTest {
            val viewModel = buildViewModel(initialSettings = AppSettings(autoSyncEnabled = false))

            viewModel.state.test {
                assertFalse(awaitItem().autoSyncEnabled)

                viewModel.onEvent(CloudSyncEvent.AutoSyncToggled(true))
                assertTrue(awaitItem().autoSyncEnabled)

                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 7. conflict resolution -----------------------------------------------------------

    @Test
    fun `keep remote invokes the keep remote seam and clears the conflict`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(
                        SyncTarget.Dropbox,
                        Result.success(SyncOutcome.ConflictDetected(remoteModifiedMs = 200L, localLastSyncMs = 100L)),
                    )
                    setKeepRemoteResult(SyncTarget.Dropbox, Result.success(SyncOutcome.Pulled))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)
            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.onEvent(CloudSyncEvent.ConflictKeepRemote)

            viewModel.state.test {
                assertNull(awaitItem().conflict)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(SyncTarget.Dropbox), snapshotSync.keepRemoteCalls)
            assertTrue(snapshotSync.keepLocalCalls.isEmpty())
        }

    @Test
    fun `keep local invokes the keep local seam and clears the conflict`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(
                        SyncTarget.Dropbox,
                        Result.success(SyncOutcome.ConflictDetected(remoteModifiedMs = 200L, localLastSyncMs = 100L)),
                    )
                    setKeepLocalResult(SyncTarget.Dropbox, Result.success(SyncOutcome.Pushed))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)
            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.onEvent(CloudSyncEvent.ConflictKeepLocal)

            viewModel.state.test {
                assertNull(awaitItem().conflict)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(SyncTarget.Dropbox), snapshotSync.keepLocalCalls)
            assertTrue(snapshotSync.keepRemoteCalls.isEmpty())
        }

    @Test
    fun `resolving a conflict with no active conflict does nothing`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.onEvent(CloudSyncEvent.ConflictKeepRemote)

            assertTrue(snapshotSync.keepRemoteCalls.isEmpty())
            assertTrue(snapshotSync.keepLocalCalls.isEmpty())
        }

    // --- 8. connect -----------------------------------------------------------------------

    @Test
    fun `connect on an enabled dropbox target emits the dropbox auth action`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = true)

            viewModel.actions.test {
                viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox))
                assertEquals(CloudSyncAction.LaunchDropboxAuth, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `connect on an enabled drive target emits the google sign in action`() =
        runTest {
            val viewModel = buildViewModel(gdriveEnabled = true)

            viewModel.actions.test {
                viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.GoogleDrive))
                assertEquals(CloudSyncAction.LaunchGoogleSignIn, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `connect on a disabled target emits no launch action`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = false)

            viewModel.actions.test {
                viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox))
                expectNoEvents()
            }
        }

    @Test
    fun `connect on a disabled target sets the not configured banner`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = false)

            viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                assertEquals(R.string.sync_not_configured, awaitItem().errorBannerRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 9. disconnect --------------------------------------------------------------------

    @Test
    fun `disconnect invokes the disconnect seam and clears the connection state`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setAccountLabel(SyncTarget.Dropbox, Result.success("alice@dropbox.com"))
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)

            viewModel.onEvent(CloudSyncEvent.DisconnectClicked(SyncTarget.Dropbox))

            viewModel.state.test {
                val state = awaitItem()
                assertFalse(state.dropbox.connected)
                assertNull(state.dropbox.accountLabel)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(SyncTarget.Dropbox), snapshotSync.disconnectCalls)
        }

    // --- 10. dismiss / back ---------------------------------------------------------------

    @Test
    fun `dismiss error clears an existing error banner`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = false)
            viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox))

            viewModel.onEvent(CloudSyncEvent.DismissError)

            viewModel.state.test {
                assertNull(awaitItem().errorBannerRes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismiss conflict clears an active conflict`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setSyncNowResult(
                        SyncTarget.Dropbox,
                        Result.success(SyncOutcome.ConflictDetected(remoteModifiedMs = 200L, localLastSyncMs = 100L)),
                    )
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            syncLog = FakeSyncLogRepository()
            remoteConfig = FakeRemoteConfigRepository()
            val viewModel = CloudSyncViewModel(snapshotSync, scheduler, appSettings, syncLog, remoteConfig)
            viewModel.onEvent(CloudSyncEvent.SyncNowClicked(SyncTarget.Dropbox))

            viewModel.onEvent(CloudSyncEvent.DismissConflict)

            viewModel.state.test {
                assertNull(awaitItem().conflict)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `back clicked emits the navigate back action`() =
        runTest {
            val viewModel = buildViewModel()

            viewModel.actions.test {
                viewModel.onEvent(CloudSyncEvent.BackClicked)
                assertEquals(CloudSyncAction.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // --- 11. pure error mapping -----------------------------------------------------------

    @Test
    fun `map error maps network to the network string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_network, viewModel.mapError(SyncError.Network))
    }

    @Test
    fun `map error maps auth to the auth string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_auth, viewModel.mapError(SyncError.Auth))
    }

    @Test
    fun `map error maps quota to the quota string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_quota, viewModel.mapError(SyncError.Quota))
    }

    @Test
    fun `map error maps server to the server string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_server, viewModel.mapError(SyncError.Server))
    }

    @Test
    fun `map error maps unknown to the unknown string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_unknown, viewModel.mapError(SyncError.Unknown))
    }

    @Test
    fun `map error maps conflict to the unknown fallback string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_unknown, viewModel.mapError(SyncError.Conflict))
    }
}
