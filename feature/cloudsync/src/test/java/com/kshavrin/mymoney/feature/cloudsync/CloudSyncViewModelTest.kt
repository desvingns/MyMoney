package com.kshavrin.mymoney.feature.cloudsync

import app.cash.turbine.test
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.sync.JournalBackend
import com.kshavrin.mymoney.core.sync.JournalSync
import com.kshavrin.mymoney.core.sync.RemoteJournalFile
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeJournalSync
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeRemoteConfigRepository
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeSnapshotSync
import com.kshavrin.mymoney.feature.cloudsync.fake.FakeSyncScheduler
import com.kshavrin.mymoney.feature.cloudsync.util.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
class CloudSyncViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var snapshotSync: FakeSnapshotSync
    private lateinit var journalSync: FakeJournalSync
    private lateinit var journalBackend: FakeJournalBackend
    private lateinit var journalSyncConfig: FakeJournalSyncConfigStore
    private lateinit var scheduler: FakeSyncScheduler
    private lateinit var appSettings: FakeAppSettingsRepository
    private lateinit var remoteConfig: FakeRemoteConfigRepository
    private lateinit var deviceIdProvider: FakeDeviceIdProvider

    private fun buildViewModel(
        initialSettings: AppSettings = AppSettings(),
        dropboxEnabled: Boolean = true,
        gdriveEnabled: Boolean = true,
        initialFolderId: String = "",
        ownDeviceId: String = "device-self",
    ): CloudSyncViewModel {
        snapshotSync = FakeSnapshotSync()
        journalSync = FakeJournalSync()
        journalBackend = FakeJournalBackend()
        journalSyncConfig = FakeJournalSyncConfigStore(folderId = initialFolderId)
        scheduler = FakeSyncScheduler()
        appSettings = FakeAppSettingsRepository(initialSettings)
        remoteConfig =
            FakeRemoteConfigRepository(
                dropboxEnabled = dropboxEnabled,
                gdriveEnabled = gdriveEnabled,
            )
        deviceIdProvider = FakeDeviceIdProvider(ownDeviceId)
        return CloudSyncViewModel(
            snapshotSync = snapshotSync,
            journalSync = journalSync,
            journalBackend = journalBackend,
            journalSyncConfig = journalSyncConfig,
            syncScheduler = scheduler,
            appSettings = appSettings,
            remoteConfig = remoteConfig,
            deviceIdProvider = deviceIdProvider,
        )
    }

    @Test
    fun `init maps remote config flags onto each target card enabled`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = true, gdriveEnabled = false)

            runCurrent()

            assertTrue(viewModel.state.value.dropbox.enabled)
            assertFalse(viewModel.state.value.drive.enabled)
        }

    @Test
    fun `init reflects connection status and account label for a connected target`() =
        runTest {
            snapshotSync =
                FakeSnapshotSync().apply {
                    setConnected(SyncTarget.Dropbox, true)
                    setAccountLabel(SyncTarget.Dropbox, Result.success("alice@dropbox.com"))
                }
            journalSync = FakeJournalSync()
            journalBackend = FakeJournalBackend()
            journalSyncConfig = FakeJournalSyncConfigStore()
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")
            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertTrue(viewModel.state.value.dropbox.connected)
            assertEquals("alice@dropbox.com", viewModel.state.value.dropbox.accountLabel)
        }

    @Test
    fun `init reflects auto sync flag last sync timestamp and stored folder id`() =
        runTest {
            val viewModel =
                buildViewModel(
                    initialSettings = AppSettings(autoSyncEnabled = true, lastSyncAt = 1_700_000_000_000L),
                    initialFolderId = "shared-folder",
                )

            runCurrent()

            with(viewModel.state.value) {
                assertTrue(autoSyncEnabled)
                assertEquals(1_700_000_000_000L, lastSyncAtMs)
                assertEquals("shared-folder", folderId)
            }
        }

    @Test
    fun `init loads peer statuses from journal backend excluding own device`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            journalSync = FakeJournalSync()
            journalBackend =
                FakeJournalBackend().apply {
                    listPeerJournalsResult =
                        Result.success(
                            listOf(
                                RemoteJournalFile(fileId = "file-self", deviceId = "device-self", modifiedAtEpochMs = 200L),
                                RemoteJournalFile(fileId = "file-a", deviceId = "device-a", modifiedAtEpochMs = 150L),
                                RemoteJournalFile(fileId = "file-b", deviceId = "device-b", modifiedAtEpochMs = 300L),
                            ),
                        )
                }
            journalSyncConfig =
                FakeJournalSyncConfigStore(folderId = "shared-folder").apply {
                    seedPeerHighWater("file-a", 150L)
                    seedPeerHighWater("file-b", 0L)
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")
            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertEquals(
                listOf(
                    PeerJournalState(
                        deviceId = "device-a",
                        modifiedAtMs = 150L,
                        pulledThroughMs = 150L,
                    ),
                    PeerJournalState(
                        deviceId = "device-b",
                        modifiedAtMs = 300L,
                        pulledThroughMs = 0L,
                    ),
                ),
                viewModel.state.value.peerStatuses,
            )
            assertEquals(listOf("shared-folder"), journalBackend.listPeerJournalCalls)
        }

    @Test
    fun `setting folder id trims persists and reloads peer statuses`() =
        runTest {
            val viewModel = buildViewModel(initialSettings = AppSettings(autoSyncEnabled = true))
            journalBackend.listPeerJournalsResult =
                Result.success(
                    listOf(
                        RemoteJournalFile(fileId = "file-a", deviceId = "device-a", modifiedAtEpochMs = 120L),
                    ),
                )

            viewModel.onEvent(CloudSyncEvent.FolderIdChanged("  folder-123  "))
            runCurrent()

            assertEquals("  folder-123  ", viewModel.state.value.folderId)
            assertEquals("folder-123", journalSyncConfig.folderId())
            assertEquals(1, scheduler.enableCount)
            assertEquals(listOf("folder-123"), journalBackend.listPeerJournalCalls)
            assertEquals(
                listOf(
                    PeerJournalState(
                        deviceId = "device-a",
                        modifiedAtMs = 120L,
                        pulledThroughMs = 0L,
                    ),
                ),
                viewModel.state.value.peerStatuses,
            )
        }

    @Test
    fun `setting blank folder id disables periodic sync when auto sync is enabled`() =
        runTest {
            val viewModel =
                buildViewModel(
                    initialSettings = AppSettings(autoSyncEnabled = true),
                    initialFolderId = "configured-folder",
                )

            runCurrent()
            viewModel.onEvent(CloudSyncEvent.FolderIdChanged("   "))
            runCurrent()

            assertEquals("", journalSyncConfig.folderId())
            assertEquals(1, scheduler.disableCount)
            assertTrue(
                viewModel.state.value.peerStatuses
                    .isEmpty(),
            )
        }

    @Test
    fun `sync now toggles is syncing and reloads journal status on success`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val blockingJournalSync =
                object : JournalSync {
                    var syncNowCalls = 0

                    override suspend fun push() = Unit

                    override suspend fun pull() = Unit

                    override suspend fun syncNow() {
                        syncNowCalls++
                        gate.await()
                    }
                }
            snapshotSync = FakeSnapshotSync()
            journalBackend =
                FakeJournalBackend().apply {
                    listPeerJournalsResult =
                        Result.success(
                            listOf(
                                RemoteJournalFile(fileId = "file-a", deviceId = "device-a", modifiedAtEpochMs = 222L),
                            ),
                        )
                }
            journalSyncConfig =
                FakeJournalSyncConfigStore(folderId = "shared-folder").apply {
                    seedPeerHighWater("file-a", 222L)
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")
            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = blockingJournalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            viewModel.state.test {
                assertFalse(awaitItem().isSyncing)

                viewModel.onEvent(CloudSyncEvent.SyncNowClicked())
                runCurrent()
                assertTrue(awaitItem().isSyncing)

                gate.complete(Unit)

                val settled = awaitItem()
                assertFalse(settled.isSyncing)
                assertNull(settled.errorBannerRes)
                assertEquals(
                    listOf(
                        PeerJournalState(
                            deviceId = "device-a",
                            modifiedAtMs = 222L,
                            pulledThroughMs = 222L,
                        ),
                    ),
                    settled.peerStatuses,
                )
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(1, blockingJournalSync.syncNowCalls)
            assertEquals(2, journalBackend.listPeerJournalCalls.count { it == "shared-folder" })
        }

    @Test
    fun `sync now failure maps the sync error to the matching string resource`() =
        runTest {
            val viewModel = buildViewModel(initialFolderId = "shared-folder")
            journalSync.syncNowError = SyncException(SyncError.Server)

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked())
            runCurrent()

            assertEquals(R.string.sync_err_server, viewModel.state.value.errorBannerRes)
            assertFalse(viewModel.state.value.isSyncing)
        }

    @Test
    fun `sync now cancellation clears refreshing without showing error banner`() =
        runTest {
            val viewModel = buildViewModel(initialFolderId = "shared-folder")
            journalSync.syncNowError = CancellationException("cancelled")

            viewModel.onEvent(CloudSyncEvent.SyncNowClicked())
            runCurrent()

            assertNull(viewModel.state.value.errorBannerRes)
            assertFalse(viewModel.state.value.isSyncing)
        }

    @Test
    fun `journal backend error while loading peers maps the banner`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            journalSync = FakeJournalSync()
            journalBackend =
                FakeJournalBackend().apply {
                    listPeerJournalsResult = Result.failure(SyncException(SyncError.Network))
                }
            journalSyncConfig = FakeJournalSyncConfigStore(folderId = "shared-folder")
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")
            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertEquals(R.string.sync_err_network, viewModel.state.value.errorBannerRes)
            assertTrue(
                viewModel.state.value.peerStatuses
                    .isEmpty(),
            )
        }

    @Test
    fun `device id error while loading peers maps the banner and skips backend`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            journalSync = FakeJournalSync()
            journalBackend =
                FakeJournalBackend().apply {
                    listPeerJournalsResult =
                        Result.success(
                            listOf(
                                RemoteJournalFile(fileId = "file-self", deviceId = "device-self", modifiedAtEpochMs = 300L),
                            ),
                        )
                }
            journalSyncConfig = FakeJournalSyncConfigStore(folderId = "shared-folder")
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self", SyncException(SyncError.Auth))

            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertEquals(R.string.sync_err_auth, viewModel.state.value.errorBannerRes)
            assertTrue(
                viewModel.state.value.peerStatuses
                    .isEmpty(),
            )
            assertTrue(
                journalBackend.listPeerJournalCalls
                    .isEmpty(),
            )
        }

    @Test
    fun `stored folder id error while loading status maps the banner`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            journalSync = FakeJournalSync()
            journalBackend = FakeJournalBackend()
            journalSyncConfig =
                FakeJournalSyncConfigStore().apply {
                    folderIdError = SyncException(SyncError.Quota)
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")

            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertEquals(R.string.sync_err_quota, viewModel.state.value.errorBannerRes)
            assertTrue(
                viewModel.state.value.peerStatuses
                    .isEmpty(),
            )
        }

    @Test
    fun `folder id persistence error maps the banner and skips backend`() =
        runTest {
            val viewModel = buildViewModel()
            journalSyncConfig.setFolderIdError = SyncException(SyncError.Server)

            viewModel.onEvent(CloudSyncEvent.FolderIdChanged("shared-folder"))
            runCurrent()

            assertEquals(R.string.sync_err_server, viewModel.state.value.errorBannerRes)
            assertTrue(journalBackend.listPeerJournalCalls.isEmpty())
        }

    @Test
    fun `peer high water error while loading status maps the banner`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            journalSync = FakeJournalSync()
            journalBackend =
                FakeJournalBackend().apply {
                    listPeerJournalsResult =
                        Result.success(
                            listOf(
                                RemoteJournalFile(fileId = "file-a", deviceId = "device-a", modifiedAtEpochMs = 300L),
                            ),
                        )
                }
            journalSyncConfig =
                FakeJournalSyncConfigStore(folderId = "shared-folder").apply {
                    peerHighWaterError = SyncException(SyncError.Auth)
                }
            scheduler = FakeSyncScheduler()
            appSettings = FakeAppSettingsRepository(AppSettings())
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")
            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = appSettings,
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertEquals(R.string.sync_err_auth, viewModel.state.value.errorBannerRes)
            assertTrue(
                viewModel.state.value.peerStatuses
                    .isEmpty(),
            )
        }

    @Test
    fun `settings flow error maps the banner`() =
        runTest {
            snapshotSync = FakeSnapshotSync()
            journalSync = FakeJournalSync()
            journalBackend = FakeJournalBackend()
            journalSyncConfig = FakeJournalSyncConfigStore()
            scheduler = FakeSyncScheduler()
            remoteConfig = FakeRemoteConfigRepository()
            deviceIdProvider = FakeDeviceIdProvider("device-self")
            val viewModel =
                CloudSyncViewModel(
                    snapshotSync = snapshotSync,
                    journalSync = journalSync,
                    journalBackend = journalBackend,
                    journalSyncConfig = journalSyncConfig,
                    syncScheduler = scheduler,
                    appSettings = FailingAppSettingsRepository(SyncException(SyncError.Network)),
                    remoteConfig = remoteConfig,
                    deviceIdProvider = deviceIdProvider,
                )

            runCurrent()

            assertEquals(R.string.sync_err_network, viewModel.state.value.errorBannerRes)
        }

    @Test
    fun `auto sync toggled on persists the flag and enables periodic sync`() =
        runTest {
            val viewModel = buildViewModel(initialSettings = AppSettings(autoSyncEnabled = false))

            viewModel.onEvent(CloudSyncEvent.AutoSyncToggled(true))
            runCurrent()

            assertTrue(appSettings.settings.first().autoSyncEnabled)
            assertEquals(1, scheduler.enableCount)
            assertEquals(0, scheduler.disableCount)
        }

    @Test
    fun `connect on a disabled target sets the not configured banner`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = false)

            viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox))

            assertEquals(R.string.sync_not_configured, viewModel.state.value.errorBannerRes)
        }

    @Test
    fun `dismiss error clears an existing error banner`() =
        runTest {
            val viewModel = buildViewModel(dropboxEnabled = false)
            viewModel.onEvent(CloudSyncEvent.ConnectClicked(SyncTarget.Dropbox))

            viewModel.onEvent(CloudSyncEvent.DismissError)

            assertNull(viewModel.state.value.errorBannerRes)
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

    @Test
    fun `map error maps conflict to the unknown fallback string resource`() {
        val viewModel = buildViewModel()
        assertEquals(R.string.sync_err_unknown, viewModel.mapError(SyncError.Conflict))
    }

    private class FakeJournalSyncConfigStore(
        private var folderId: String = "",
    ) : JournalSyncConfigStore {
        private val peerHighWater = mutableMapOf<String, Long>()
        var folderIdError: Throwable? = null
        var setFolderIdError: Throwable? = null
        var peerHighWaterError: Throwable? = null

        fun seedPeerHighWater(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            peerHighWater[fileId] = modifiedAtMs
        }

        override suspend fun folderId(): String {
            folderIdError?.let { throw it }
            return folderId
        }

        override suspend fun setFolderId(folderId: String) {
            setFolderIdError?.let { throw it }
            this.folderId = folderId
        }

        override suspend fun peerHighWaterMs(fileId: String): Long {
            peerHighWaterError?.let { throw it }
            return peerHighWater[fileId] ?: 0L
        }

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) {
            peerHighWater[fileId] = modifiedAtMs
        }

        override suspend fun isBootstrapDone(): Boolean = true

        override suspend fun markBootstrapDone() = Unit
    }

    private class FakeJournalBackend : JournalBackend {
        var listPeerJournalsResult: Result<List<RemoteJournalFile>> = Result.success(emptyList())
        val listPeerJournalCalls = mutableListOf<String>()

        override suspend fun uploadJournal(
            folderId: String,
            deviceId: String,
            bytes: ByteArray,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun listPeerJournals(folderId: String): Result<List<RemoteJournalFile>> {
            listPeerJournalCalls += folderId
            return listPeerJournalsResult
        }

        override suspend fun downloadJournal(fileId: String): Result<ByteArray> = Result.success(byteArrayOf())
    }

    private class FakeDeviceIdProvider(
        private val value: String,
        private val error: Throwable? = null,
    ) : DeviceIdProvider {
        override suspend fun deviceId(): String {
            error?.let { throw it }
            return value
        }
    }

    private class FailingAppSettingsRepository(
        private val error: Throwable,
    ) : AppSettingsRepository {
        override val settings: Flow<AppSettings> = flow { throw error }

        override suspend fun update(transform: (AppSettings) -> AppSettings) = throw error
    }
}
