package com.kshavrin.mymoney.core.sync.shared

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.AppSettingsRepositoryImpl
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.domain.model.Account
import com.kshavrin.mymoney.core.domain.model.AccountType
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.sync.SharedOperation
import com.kshavrin.mymoney.core.network.shared.CreatedInvite
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedRealtime
import com.kshavrin.mymoney.core.network.shared.SharedRealtimeEvent
import com.kshavrin.mymoney.core.network.shared.SharedSession
import com.kshavrin.mymoney.core.network.shared.SharedUser
import com.kshavrin.mymoney.core.network.shared.SharedWorkspace
import com.kshavrin.mymoney.core.network.shared.SharedWorkspaceApi
import com.kshavrin.mymoney.core.network.shared.WorkspaceInvite
import com.kshavrin.mymoney.core.network.shared.WorkspaceBillingState
import com.kshavrin.mymoney.core.network.shared.WorkspaceMember
import com.kshavrin.mymoney.core.network.shared.WorkspaceRole
import com.kshavrin.mymoney.core.sync.SyncExecutionGate
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.testing.fake.FakeAppSettingsRepository
import com.kshavrin.mymoney.core.testing.fake.FakeSupporterSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SharedSyncCoordinatorImplTest {
    private val dispatcher = StandardTestDispatcher()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)
    private val json = Json { ignoreUnknownKeys = true }
    private val codec = SharedEntityCodec(json)
    private val executionGate = SyncExecutionGate()

    private lateinit var db: MoneyDatabase
    private lateinit var auth: FakeSharedAuth
    private lateinit var realtime: FakeSharedRealtime
    private lateinit var workspaceApi: FakeSharedWorkspaceApi
    private lateinit var journalRepository: FakeSharedJournalRepository
    private lateinit var backupRepository: FakeInternalBackupRepository
    private lateinit var appSettings: AppSettingsRepository
    private lateinit var configStore: FakeJournalSyncConfigStore
    private lateinit var sharedStore: FakeSharedSyncStore
    private lateinit var scheduler: FakeSyncScheduler
    private lateinit var accountRepository: FakeAccountRepository
    private lateinit var categoryRepository: FakeCategoryRepository
    private lateinit var transactionRepository: FakeTransactionRepository
    private lateinit var currencyRepository: FakeCurrencyRepository
    private lateinit var deviceIdProvider: DeviceIdProvider
    private lateinit var supporterSync: FakeSupporterSync

    private lateinit var coordinator: SharedSyncCoordinatorImpl

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MoneyDatabase::class.java,
                ).allowMainThreadQueries()
                .build()

        auth = FakeSharedAuth()
        realtime = FakeSharedRealtime()
        workspaceApi = FakeSharedWorkspaceApi()
        journalRepository = FakeSharedJournalRepository()
        backupRepository = FakeInternalBackupRepository()
        appSettings = FakeAppSettingsRepository()
        configStore = FakeJournalSyncConfigStore()
        sharedStore = FakeSharedSyncStore()
        scheduler = FakeSyncScheduler()
        accountRepository = FakeAccountRepository()
        categoryRepository = FakeCategoryRepository()
        transactionRepository = FakeTransactionRepository()
        currencyRepository = FakeCurrencyRepository()
        supporterSync = FakeSupporterSync()
        deviceIdProvider =
            object : DeviceIdProvider {
                override suspend fun deviceId() = "test-device"
            }

        coordinator = createCoordinator()
    }

    private fun createCoordinator() =
        SharedSyncCoordinatorImpl(
            auth = auth,
            realtime = realtime,
            workspaceApi = workspaceApi,
            journalRepository = journalRepository,
            backupRepository = backupRepository,
            appSettings = appSettings,
            configStore = configStore,
            sharedStore = sharedStore,
            syncScheduler = scheduler,
            executionGate = executionGate,
            deviceIdProvider = deviceIdProvider,
            transactionRepository = transactionRepository,
            accountRepository = accountRepository,
            categoryRepository = categoryRepository,
            currencyRepository = currencyRepository,
            codec = codec,
            database = db,
            clock = clock,
            dispatcher = dispatcher,
            supporterSync = supporterSync,
        )

    @After
    fun tearDown() {
        db.close()
    }

    // ── isSignedIn / accountEmail ──────────────────────────────────────────

    @Test
    fun `isSignedIn returns false when no session`() {
        auth.session = null
        assertTrue(!coordinator.isSignedIn())
    }

    @Test
    fun `isSignedIn returns true when session present`() {
        auth.session = fakeSession()
        assertTrue(coordinator.isSignedIn())
    }

    @Test
    fun `accountEmail returns null when signed out`() {
        auth.session = null
        assertNull(coordinator.accountEmail())
    }

    @Test
    fun `accountEmail returns email from session`() {
        auth.session = fakeSession(email = "user@example.com")
        assertEquals("user@example.com", coordinator.accountEmail())
    }

    @Test
    fun `signIn delegates the Google token and raw nonce to auth`() =
        runTest(dispatcher) {
            auth.session = fakeSession(email = "signed-in@example.com")

            val result = coordinator.signIn("google-id-token", "raw-nonce")

            assertTrue(result.isSuccess)
            assertEquals("google-id-token" to "raw-nonce", auth.lastSignIn)
        }

    @Test
    fun `signIn propagates authentication failure`() =
        runTest(dispatcher) {
            auth.signInFailure = SyncException(SyncError.Auth)

            val result = coordinator.signIn("google-id-token", "raw-nonce")

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
        }

    @Test
    fun `signIn propagates supporter restore failure after authentication succeeds`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            val restoreFailure = SyncException(SyncError.Server)
            supporterSync.restoreResult = Result.failure(restoreFailure)

            val result = coordinator.signIn("google-id-token", "raw-nonce")

            assertTrue(result.isFailure)
            assertEquals(restoreFailure, result.exceptionOrNull())
        }

    @Test
    fun `signOut delegates to auth and removes the cached session`() =
        runTest(dispatcher) {
            auth.session = fakeSession()

            val result = coordinator.signOut()

            assertTrue(result.isSuccess)
            assertEquals(1, auth.signOutCalls)
            assertNull(auth.session)
        }

    // ── createWorkspace ────────────────────────────────────────────────────

    @Test
    fun `createWorkspace no-import path creates backup clears DB pulls and activates binding`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.createResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.createWorkspace("My Budget", importLocalData = false)

            assertTrue(result.isSuccess)
            assertEquals("ws-1", result.getOrThrow().id)
            assertEquals(1, backupRepository.internalBackupCalls)
            assertEquals(1, backupRepository.clearDatabaseCalls)
            assertEquals(InitialDataSeeder.defaultCurrencyCatalog(), currencyRepository.lastUpsertAll)
            assertTrue(sharedStore.membershipActive)
            assertNotNull(configStore.current)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertEquals("ws-1", configStore.current?.stableAccountId)
        }

    @Test
    fun `createWorkspace import path publishes local accounts and categories before pulling`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.createResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))

            // Seed local data that should be published
            accountRepository.accounts = listOf(sampleAccount(id = 1L))
            accountRepository.uuids = mapOf(1L to "account-uuid-local")
            currencyRepository.currencies = mapOf(5L to fakeCurrency(code = "USD"))

            coordinator.createWorkspace("Budget", importLocalData = true)

            // push was called for the account
            assertTrue(
                "Expected at least one journal push for local account publishing",
                journalRepository.pushCalls > 0,
            )
            assertEquals(0, backupRepository.clearDatabaseCalls)
        }

    @Test
    fun `createWorkspace fails when not signed in`() =
        runTest(dispatcher) {
            auth.session = null
            val result = coordinator.createWorkspace("X", importLocalData = false)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? SyncException
            assertEquals(SyncError.Auth, ex?.syncError)
        }

    @Test
    fun `createWorkspace fails when an active binding already exists`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Dropbox, "id", "a@b.com")
            val result = coordinator.createWorkspace("X", importLocalData = false)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? SyncException
            assertEquals(SyncError.Conflict, ex?.syncError)
        }

    @Test
    fun `discoverRemoteWorkspace reads server membership without changing local state`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())

            val result = coordinator.discoverRemoteWorkspace()

            assertEquals(SharedWorkspaceSummary("ws-1", "Budget"), result.getOrThrow())
            assertNull(configStore.current)
            assertEquals(0, backupRepository.internalBackupCalls)
            assertEquals(0, backupRepository.clearDatabaseCalls)
        }

    @Test
    fun `recoverRemoteWorkspace clears local data only after the explicit recovery call`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.recoverRemoteWorkspace(importLocalData = false)

            assertTrue(result.isSuccess)
            assertEquals(1, backupRepository.internalBackupCalls)
            assertEquals(1, backupRepository.clearDatabaseCalls)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
        }

    @Test
    fun `recoverRemoteWorkspace enables periodic sync after live adoption when auto sync is enabled`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))
            appSettings.update { it.copy(autoSyncEnabled = true) }

            val result = coordinator.recoverRemoteWorkspace(importLocalData = false)

            assertTrue(result.isSuccess)
            assertEquals(1, scheduler.enableCalls)
        }

    @Test
    fun `active workspace access propagates server Grace state and deadline`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            val graceEndsAt = clock.instant().plusSeconds(86_400)
            workspaceApi.currentWorkspaceResult =
                Result.success(
                    fakeWorkspace(
                        billingState = WorkspaceBillingState.Grace,
                        billingStateUntil = graceEndsAt,
                    ),
                )

            val access = coordinator.activeWorkspaceAccess().getOrThrow()

            assertEquals(SharedWorkspaceBillingState.Grace, access.billingState)
            assertEquals(graceEndsAt, access.billingStateUntil)
            assertTrue(access.isReadOnly)
        }

    @Test
    fun `active workspace access propagates cancellation`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.failure(CancellationException("screen closed"))

            val cancellation =
                try {
                    coordinator.activeWorkspaceAccess()
                    error("access cancellation should propagate")
                } catch (failure: CancellationException) {
                    failure
                }

            assertEquals("screen closed", cancellation.message)
        }

    // ── joinWorkspace ──────────────────────────────────────────────────────

    @Test
    fun `joinWorkspace no-import path does not call clearDatabase before binding activates`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.joinResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))

            coordinator.joinWorkspace("invite-token", importLocalData = false)

            // no-import still clears DB
            assertEquals(1, backupRepository.clearDatabaseCalls)
            assertTrue(sharedStore.membershipActive)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
        }

    @Test
    fun `joinWorkspace membership is activated before binding is written`() =
        runTest(dispatcher) {
            // The ordering guard: membership must be set BEFORE binding appears, so a concurrent
            // syncNow cannot see a binding with inactive membership and wrongly evict the member.
            val activationOrder = mutableListOf<String>()
            auth.session = fakeSession()
            workspaceApi.joinResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))
            sharedStore.onSetMembershipActive = { activationOrder += "membership" }
            configStore.onSetBinding = { activationOrder += "binding" }

            coordinator.joinWorkspace("token", importLocalData = false)

            assertEquals(listOf("membership", "binding"), activationOrder)
        }

    @Test
    fun `createInvite returns the one-time token for the active Shared workspace`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            workspaceApi.createInviteResult = Result.success(fakeCreatedInvite("invite-token"))

            val result = coordinator.createInvite()

            assertTrue(result.isSuccess)
            assertEquals("invite-token", result.getOrThrow().token)
            assertEquals(1, workspaceApi.createInviteCalls)
            assertEquals("ws-1", workspaceApi.lastCreateInviteWorkspaceId)
        }

    @Test
    fun `createInvite exposes no token when the provider rejects the request`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            workspaceApi.createInviteResult = Result.failure(SyncException(SyncError.Network))

            val result = coordinator.createInvite()

            assertTrue(result.isFailure)
            assertNull(result.getOrNull())
            assertEquals(SyncError.Network, (result.exceptionOrNull() as SyncException).syncError)
        }

    @Test
    fun `createInvite does not call the provider without an active Shared workspace`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Dropbox, "dropbox-id", "dropbox@example.com")

            val result = coordinator.createInvite()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Conflict, (result.exceptionOrNull() as SyncException).syncError)
            assertEquals(0, workspaceApi.createInviteCalls)
        }

    @Test
    fun `createInvite rejects inactive membership and revokes the local session`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = false
            workspaceApi.createInviteResult = Result.success(fakeCreatedInvite("invite-token"))

            val result = coordinator.createInvite()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as SyncException).syncError)
            assertEquals(0, workspaceApi.createInviteCalls)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
            assertNull(auth.session)
        }

    // ── leaveWorkspace ─────────────────────────────────────────────────────

    @Test
    fun `leaveWorkspace cuts remote access and clears local state even when backup fails`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            backupRepository.internalBackupFailure = RuntimeException("disk full")

            val result = coordinator.leaveWorkspace()

            // Binding must be cleared regardless of backup failure
            assertNull(configStore.current)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
            // Result carries the backup failure
            assertTrue(result.isFailure)
            assertEquals("disk full", result.exceptionOrNull()?.message)
        }

    @Test
    fun `leaveWorkspace signs out and clears local state even when server leave fails`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            workspaceApi.leaveFailure = RuntimeException("network error")

            val result = coordinator.leaveWorkspace()

            assertNull(configStore.current)
            assertEquals(1, auth.signOutCalls)
            assertTrue(result.isFailure)
            assertEquals("network error", result.exceptionOrNull()?.message)
        }

    @Test
    fun `leaveWorkspace when both backup and server fail propagates server error with backup suppressed`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            backupRepository.internalBackupFailure = RuntimeException("disk full")
            workspaceApi.leaveFailure = RuntimeException("network error")

            val result = coordinator.leaveWorkspace()

            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull()!!
            // Server error is the primary; backup error is suppressed
            assertEquals("network error", ex.message)
            assertTrue(ex.suppressed.any { it.message == "disk full" })
        }

    @Test
    fun `leaveWorkspace with no active Shared binding fails immediately`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = null
            val result = coordinator.leaveWorkspace()
            assertTrue(result.isFailure)
        }

    @Test
    fun `disconnectFromDevice keeps local binding when final sync fails`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.failure(SyncException(SyncError.Network)))

            val result = coordinator.disconnectFromDevice()

            assertTrue(result.isFailure)
            assertNotNull(configStore.current)
            assertEquals(0, scheduler.disableCalls)
            assertEquals(0, auth.signOutCalls)
        }

    @Test
    fun `disconnectFromDevice clears local state without leaving or deleting workspace`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.disconnectFromDevice()

            assertTrue(result.isSuccess)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
            assertEquals(1, auth.clearLocalSessionCalls)
            assertEquals(0, workspaceApi.leaveCalls)
            assertEquals(0, workspaceApi.deleteCalls)
        }

    // ── syncNow forced-removal guard ───────────────────────────────────────

    @Test
    fun `syncNow clears binding and throws Auth error when membership is no longer active`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = false

            val result = coordinator.syncNow()

            assertNull("binding must be cleared on forced removal", configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertTrue(result.isFailure)
            val ex = result.exceptionOrNull() as? SyncException
            assertEquals(SyncError.Auth, ex?.syncError)
        }

    @Test
    fun `syncNow terminal auth failure clears binding cancels work and signs out`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.failure(SyncException(SyncError.Auth)))

            val result = coordinator.syncNow()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
            assertNull(auth.session)
        }

    @Test
    fun `syncNow proceeds normally when membership is active`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.syncNow()

            assertTrue(result.isSuccess)
            // binding remains intact
            assertNotNull(configStore.current)
        }

    @Test
    fun `successful Shared sync persists completion time across a fresh settings store`() =
        runTest(dispatcher) {
            val settingsFile =
                File(
                    System.getProperty("java.io.tmpdir"),
                    "shared-sync-${UUID.randomUUID()}.preferences_pb",
                )
            val writeJob = Job()
            appSettings =
                AppSettingsRepositoryImpl(
                    PreferenceDataStoreFactory.create(
                        scope = CoroutineScope(writeJob + Dispatchers.IO),
                        produceFile = { settingsFile },
                    ),
                )
            coordinator = createCoordinator()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.success(emptyList()))

            try {
                assertTrue(coordinator.syncNow().isSuccess)
                writeJob.cancelAndJoin()

                val readJob = Job()
                try {
                    val coldStartSettings =
                        AppSettingsRepositoryImpl(
                            PreferenceDataStoreFactory.create(
                                scope = CoroutineScope(readJob + Dispatchers.IO),
                                produceFile = { settingsFile },
                            ),
                        ).settings.first()

                    assertEquals(clock.millis(), coldStartSettings.lastSyncAt)
                } finally {
                    readJob.cancelAndJoin()
                }
            } finally {
                writeJob.cancel()
                settingsFile.delete()
            }
        }

    @Test
    fun `failed or cancelled Shared sync does not advance last sync time`() =
        runTest(dispatcher) {
            appSettings.update { it.copy(lastSyncAt = 1_600_000_000_000L) }
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.failure(SyncException(SyncError.Network)))

            assertTrue(coordinator.syncNow().isFailure)
            assertEquals(1_600_000_000_000L, appSettings.settings.first().lastSyncAt)

            journalRepository.pullResults.add(Result.failure(CancellationException()))

            assertTrue(coordinator.syncNow().isFailure)
            assertEquals(1_600_000_000_000L, appSettings.settings.first().lastSyncAt)
        }

    // ── pullAndApply per-operation skip-and-continue ───────────────────────

    @Test
    fun `pullAndApply advances cursor past malformed non-currency op and applies subsequent valid op`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true

            // A malformed category payload is still a proven-invalid operation and may be skipped.
            // Currency integrity failures are covered below and must remain retryable instead.
            val malformedOp = fakeOperation(serverSequence = 1L, entityKind = EntityKind.Category, payload = "{}")
            // Op 2: tombstone account (no ref resolution needed) → applied
            val validOp =
                fakeOperation(
                    serverSequence = 2L,
                    entityKind = EntityKind.Account,
                    tombstone = true,
                    payload = null,
                )
            journalRepository.pullResults.add(Result.success(listOf(malformedOp, validOp)))

            coordinator.syncNow()

            // Cursor must have advanced to sequence 2 (past the malformed op)
            assertEquals(2L, sharedStore.cursor)
            // applySharedArchive was called for the tombstone op
            assertTrue(accountRepository.archivedUuids.contains(validOp.entityId))
        }

    @Test
    fun `pullAndApply retains missing canonical currency and does not advance cursor`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            val payload = codec.encodeAccount(sampleAccount(currencyId = 77L), "account-uuid", "XYZ")
            journalRepository.pullResults.add(
                Result.success(
                    listOf(fakeOperation(serverSequence = 1L, payload = payload)),
                ),
            )

            val result = coordinator.syncNow()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Conflict, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertEquals(0L, sharedStore.cursor)
            assertTrue(accountRepository.upsertCalls.isEmpty())
        }

    @Test
    fun `pullAndApply retains malformed currency payload and does not advance cursor`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            val malformedPayload =
                """{"uuid":"account-uuid","id":1,"name":"Acct","currencyCode":"XYZ","currency":{"code":"XYZ"}}"""
            journalRepository.pullResults.add(
                Result.success(
                    listOf(fakeOperation(serverSequence = 1L, payload = malformedPayload)),
                ),
            )

            val result = coordinator.syncNow()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Conflict, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertEquals(0L, sharedStore.cursor)
            assertTrue(accountRepository.upsertCalls.isEmpty())
        }

    @Test
    fun `pullAndApply materializes portable custom currency before dependent account and transaction`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            accountRepository.uuids = mapOf(10L to "account-uuid")
            val customCurrency = fakeCurrency(code = "XYZ").copy(id = 0L, symbol = "¤", name = "Test currency")
            val account = sampleAccount(id = 10L, currencyId = 0L)
            val transaction = sampleTransaction(currencyId = 0L, accountId = 10L)
            val order = mutableListOf<String>()
            currencyRepository.onUpsert = { order += "currency" }
            accountRepository.onApply = { order += "account" }
            transactionRepository.onApply = { order += "transaction" }
            journalRepository.pullResults.add(
                Result.success(
                    listOf(
                        fakeOperation(
                            serverSequence = 1L,
                            entityKind = EntityKind.Account,
                            entityId = "account-uuid",
                            payload = codec.encodeAccount(account, "account-uuid", customCurrency),
                        ),
                        fakeOperation(
                            serverSequence = 2L,
                            entityKind = EntityKind.Transaction,
                            entityId = "transaction-uuid",
                            payload =
                                codec.encodeTransaction(
                                    transaction,
                                    "transaction-uuid",
                                    customCurrency,
                                    "account-uuid",
                                    null,
                                    null,
                                ),
                        ),
                    ),
                ),
            )

            val result = coordinator.syncNow()

            assertTrue(result.isSuccess)
            assertEquals(listOf("currency", "account", "currency", "transaction"), order)
            assertEquals("XYZ", currencyRepository.findByCode("XYZ")?.code)
            assertEquals(
                currencyRepository.findByCode("XYZ")?.id,
                accountRepository.upsertCalls
                    .single()
                    .first.currencyId,
            )
            assertEquals(
                currencyRepository.findByCode("XYZ")?.id,
                transactionRepository.upsertCalls
                    .single()
                    .first.currencyId,
            )
        }

    @Test
    fun `listConflicts auth cleanup waits for the shared execution gate`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            journalRepository.conflictsResult = Result.failure(SyncException(SyncError.Auth))
            val gateEntered = CompletableDeferred<Unit>()
            val releaseGate = CompletableDeferred<Unit>()
            val holder =
                launch {
                    executionGate.withExclusive {
                        gateEntered.complete(Unit)
                        releaseGate.await()
                    }
                }
            gateEntered.await()

            val listJob = async { coordinator.listConflicts() }
            runCurrent()
            assertNotNull(configStore.current)

            releaseGate.complete(Unit)
            assertTrue(listJob.await().isFailure)
            holder.join()
            assertNull(configStore.current)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
        }

    @Test
    fun `restoreInternalBackup waits for worker gate and detaches binding before importing`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            val gateEntered = CompletableDeferred<Unit>()
            val releaseGate = CompletableDeferred<Unit>()
            val holder =
                launch {
                    executionGate.withExclusive {
                        gateEntered.complete(Unit)
                        releaseGate.await()
                    }
                }
            gateEntered.await()

            val restoreJob = async { coordinator.restoreInternalBackup("/internal/backup-1.db") }
            runCurrent()
            assertTrue(backupRepository.importPaths.isEmpty())

            releaseGate.complete(Unit)
            assertTrue(restoreJob.await().isSuccess)
            holder.join()
            assertEquals(1, scheduler.cancelAllCalls)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, auth.clearLocalSessionCalls)
            assertEquals(listOf("/internal/backup-1.db"), backupRepository.importPaths)
        }

    @Test
    fun `foreground realtime stops its lifecycle collection when stopped`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true

            assertTrue(coordinator.startForegroundRealtime().isSuccess)
            assertEquals(SharedRealtimeStatus.Starting, coordinator.foregroundRealtimeStatus.value)
            realtime.awaitActiveSubscriptions(1)
            assertEquals(1, realtime.eventsCalls)
            assertEquals(1, realtime.activeSubscriptions)

            coordinator.stopForegroundRealtime()
            realtime.awaitActiveSubscriptions(0)

            assertEquals(SharedRealtimeStatus.Inactive, coordinator.foregroundRealtimeStatus.value)
            assertEquals(0, realtime.activeSubscriptions)
        }

    @Test
    fun `foreground realtime reconciles every operation hint through the durable sync path`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            realtime.eventsFlow =
                flow {
                    emit(SharedRealtimeEvent.Connected)
                    emit(SharedRealtimeEvent.OperationAvailable)
                    emit(SharedRealtimeEvent.OperationAvailable)
                    awaitCancellation()
                }

            assertTrue(coordinator.startForegroundRealtime().isSuccess)

            assertEquals(
                SharedRealtimeStatus.Connected,
                coordinator.foregroundRealtimeStatus.first { it == SharedRealtimeStatus.Connected },
            )
            journalRepository.awaitPullCalls(3)
            assertEquals(1, realtime.eventsCalls)
            assertTrue("each hint must reconcile through syncNow", journalRepository.pullCalls >= 3)

            coordinator.stopForegroundRealtime()
            realtime.awaitActiveSubscriptions(0)
            assertEquals(0, realtime.activeSubscriptions)
        }

    @Test
    fun `server disconnect exposes sleeping status before bounded retry`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            realtime.eventsFlow = flow { emit(SharedRealtimeEvent.Disconnected(SyncException(SyncError.Server))) }

            assertTrue(coordinator.startForegroundRealtime().isSuccess)

            assertEquals(
                SharedRealtimeStatus.Sleeping(1),
                coordinator.foregroundRealtimeStatus.first { it == SharedRealtimeStatus.Sleeping(1) },
            )
            assertEquals(1, realtime.eventsCalls)
            coordinator.stopForegroundRealtime()
        }

    @Test
    fun `auth disconnect revokes the binding and ends realtime without retry`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            realtime.eventsFlow = flow { emit(SharedRealtimeEvent.Disconnected(SyncException(SyncError.Auth))) }

            assertTrue(coordinator.startForegroundRealtime().isSuccess)
            auth.awaitSignOutCalls(1)

            assertNull(configStore.current)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
            assertEquals(SharedRealtimeStatus.Inactive, coordinator.foregroundRealtimeStatus.value)
            assertEquals(1, realtime.eventsCalls)
        }

    @Test
    fun `entitlement rejection ends realtime without detaching the shared workspace`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            realtime.eventsFlow =
                flow {
                    emit(SharedRealtimeEvent.Disconnected(SyncException(SyncError.EntitlementRequired)))
                }

            assertTrue(coordinator.startForegroundRealtime().isSuccess)

            assertEquals(
                SharedRealtimeStatus.EntitlementRequired,
                coordinator.foregroundRealtimeStatus.first { it == SharedRealtimeStatus.EntitlementRequired },
            )
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertEquals(0, scheduler.disableCalls)
            assertEquals(1, realtime.eventsCalls)
        }

    @Test
    fun `realtime transient failures stop after bounded retries with error status`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            realtime.eventsFlow = flow { emit(SharedRealtimeEvent.Disconnected(IllegalStateException("socket lost"))) }

            assertTrue(coordinator.startForegroundRealtime().isSuccess)

            assertEquals(
                SharedRealtimeStatus.Error,
                coordinator.foregroundRealtimeStatus.first { it == SharedRealtimeStatus.Error },
            )
            assertEquals(6, realtime.eventsCalls)
        }

    // ── resolveConflict ────────────────────────────────────────────────────

    @Test
    fun `resolveConflict delegates to journalRepository then pulls and applies`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.resolveConflict("conflict-1", "op-winner")

            assertEquals(1, journalRepository.resolveCalls)
            assertEquals("conflict-1" to "op-winner", journalRepository.lastResolve)
            assertTrue(result.isSuccess)
        }

    // ── helpers / fakes ────────────────────────────────────────────────────

    private fun fakeSession(email: String = "test@example.com") =
        SharedSession(user = SharedUser(id = "user-1", email = email), accessToken = "token")

    private fun fakeWorkspace(
        id: String = "ws-1",
        name: String = "Budget",
        billingState: WorkspaceBillingState = WorkspaceBillingState.Active,
        billingStateUntil: Instant? = null,
    ) =
        SharedWorkspace(
            id = id,
            name = name,
            ownerId = "user-1",
            createdAt = clock.instant(),
            billingState = billingState,
            billingStateUntil = billingStateUntil,
        )

    private fun fakeCreatedInvite(token: String) =
        CreatedInvite(
            invite =
                WorkspaceInvite(
                    id = "invite-1",
                    workspaceId = "ws-1",
                    role = WorkspaceRole.Editor,
                    expiresAt = clock.instant().plusSeconds(3_600),
                ),
            token = token,
        )

    private fun sampleAccount(
        id: Long = 1L,
        currencyId: Long = 5L,
    ) =
        Account(
            id = id,
            name = "Cash",
            currencyId = currencyId,
            initialBalance = BigDecimal.ZERO,
            type = AccountType.Cash,
            colorHex = "#4CAF50",
            iconKey = "ic_cash",
            isDefault = false,
            sortOrder = 0,
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            isArchived = false,
        )

    private fun fakeCurrency(code: String = "USD") =
        Currency(
            id = 5L,
            code = code,
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun sampleTransaction(
        currencyId: Long = 5L,
        accountId: Long = 10L,
    ) =
        Transaction(
            id = 42L,
            kind = TransactionKind.Expense,
            amount = BigDecimal("99.99"),
            currencyId = currencyId,
            accountId = accountId,
            categoryId = null,
            note = "lunch",
            occurredAt = clock.instant(),
            createdAt = clock.instant(),
            updatedAt = clock.instant(),
            isDeleted = false,
            toAccountId = null,
            toAmount = null,
            exchangeRate = null,
        )

    private fun fakeOperation(
        serverSequence: Long,
        entityKind: EntityKind = EntityKind.Account,
        entityId: String = "entity-uuid-$serverSequence",
        tombstone: Boolean = false,
        payload: String? = null,
    ) = SharedOperation(
        id = "op-$serverSequence",
        workspaceId = "ws-1",
        idempotencyKey = "key-$serverSequence",
        serverSequence = serverSequence,
        baseSequence = 0L,
        deviceId = "other-device",
        entityKind = entityKind,
        entityId = entityId,
        payload = payload,
        tombstone = tombstone,
        createdAt = clock.instant(),
    )

    // ── local fakes ────────────────────────────────────────────────────────

    private inner class FakeSharedAuth : SharedAuth {
        var session: SharedSession? = null
        var signOutCalls = 0
        private val signOutCallCount = MutableStateFlow(0)
        var clearLocalSessionCalls = 0
        var lastSignIn: Pair<String, String>? = null
        var signInFailure: Throwable? = null

        override fun currentSession() = session

        override suspend fun signInWithGoogle(
            googleIdToken: String,
            nonce: String,
        ): Result<SharedSession> {
            lastSignIn = googleIdToken to nonce
            signInFailure?.let { return Result.failure(it) }
            return Result.success(session ?: fakeSession())
        }

        override suspend fun signOut(): Result<Unit> {
            signOutCalls++
            signOutCallCount.value = signOutCalls
            session = null
            return Result.success(Unit)
        }

        suspend fun awaitSignOutCalls(expected: Int) {
            signOutCallCount.first { it >= expected }
        }

        override suspend fun clearLocalSession() {
            clearLocalSessionCalls++
            session = null
        }
    }

    private class FakeSharedRealtime : SharedRealtime {
        var eventsCalls = 0
        var activeSubscriptions = 0
        private val activeSubscriptionCount = MutableStateFlow(0)
        var workspaceId: String? = null
        var accessToken: String? = null
        var eventsFlow: Flow<SharedRealtimeEvent> = flow { awaitCancellation() }

        override fun events(
            workspaceId: String,
            accessToken: String,
        ): Flow<SharedRealtimeEvent> =
            flow {
                eventsCalls++
                this@FakeSharedRealtime.workspaceId = workspaceId
                this@FakeSharedRealtime.accessToken = accessToken
                activeSubscriptions++
                activeSubscriptionCount.value = activeSubscriptions
                try {
                    emitAll(eventsFlow)
                } finally {
                    activeSubscriptions--
                    activeSubscriptionCount.value = activeSubscriptions
                }
            }

        suspend fun awaitActiveSubscriptions(expected: Int) {
            activeSubscriptionCount.first { it == expected }
        }
    }

    private inner class FakeSharedWorkspaceApi : SharedWorkspaceApi {
        var createResult: Result<SharedWorkspace> = Result.failure(RuntimeException("not set"))
        var joinResult: Result<SharedWorkspace> = Result.failure(RuntimeException("not set"))
        var createInviteResult: Result<CreatedInvite> = Result.failure(RuntimeException("unused"))
        var createInviteCalls = 0
        var lastCreateInviteWorkspaceId: String? = null
        var leaveCalls = 0
        var deleteCalls = 0
        var leaveFailure: Throwable? = null
        var currentWorkspaceResult: Result<SharedWorkspace?> = Result.success(null)

        override suspend fun createWorkspace(name: String) = createResult

        override suspend fun joinWorkspace(token: String) = joinResult

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> {
            leaveCalls++
            leaveFailure?.let { return Result.failure(it) }
            return Result.success(Unit)
        }

        override suspend fun currentWorkspace() = currentWorkspaceResult

        override suspend fun listMembers(workspaceId: String) = Result.success(emptyList<WorkspaceMember>())

        override suspend fun createInvite(workspaceId: String): Result<CreatedInvite> {
            createInviteCalls++
            lastCreateInviteWorkspaceId = workspaceId
            return createInviteResult
        }

        override suspend fun revokeInvite(inviteId: String) = Result.success(Unit)

        override suspend fun deleteWorkspace(workspaceId: String): Result<Unit> {
            deleteCalls++
            return Result.success(Unit)
        }
    }

    private inner class FakeSharedJournalRepository : SharedJournalRepository {
        val pullResults = ArrayDeque<Result<List<SharedOperation>>>()
        var pullCalls = 0
        private val pullCallCount = MutableStateFlow(0)
        var pushCalls = 0
        var resolveCalls = 0
        var lastResolve: Pair<String, String>? = null
        var conflictsResult: Result<List<SharedConflict>> = Result.success(emptyList())

        override suspend fun push(
            workspaceId: String,
            idempotencyKey: String,
            baseSequence: Long,
            deviceId: String,
            entityKind: EntityKind,
            entityId: String,
            payload: String?,
            tombstone: Boolean,
        ): Result<SharedOperation> {
            pushCalls++
            return Result.success(fakeOperation(serverSequence = pushCalls.toLong(), entityKind = entityKind))
        }

        override suspend fun pull(
            workspaceId: String,
            afterSequence: Long,
            limit: Int,
        ): Result<List<SharedOperation>> {
            pullCalls++
            pullCallCount.value = pullCalls
            return pullResults.removeFirstOrNull()
                ?: Result.success(emptyList())
        }

        suspend fun awaitPullCalls(expected: Int) {
            pullCallCount.first { it >= expected }
        }

        override suspend fun listPendingConflicts(workspaceId: String) = conflictsResult

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<SharedOperation> {
            resolveCalls++
            lastResolve = conflictId to winnerOperationId
            return Result.success(fakeOperation(serverSequence = 99L))
        }
    }

    private inner class FakeInternalBackupRepository : BackupRepository {
        var internalBackupCalls = 0
        var internalBackupFailure: Throwable? = null
        var clearDatabaseCalls = 0
        var importPaths = mutableListOf<String>()

        override suspend fun createInternalBackup(): Result<String> {
            internalBackupCalls++
            internalBackupFailure?.let { return Result.failure(it) }
            return Result.success("/internal/backup.db")
        }

        override suspend fun clearDatabase(): Result<Unit> {
            clearDatabaseCalls++
            return Result.success(Unit)
        }

        override suspend fun exportDb(treeUriString: String) = Result.success(Unit)

        override suspend fun importDb(documentUriString: String) = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String) = emptyList<com.kshavrin.mymoney.core.domain.model.BackupFile>()

        override suspend fun rotateBackups(treeUriString: String) = Result.success(Unit)

        override suspend fun exportToFile(destAbsolutePath: String) = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> {
            importPaths += srcAbsolutePath
            return Result.success(Unit)
        }
    }

    private inner class FakeJournalSyncConfigStore : JournalSyncConfigStore {
        var current: CloudBinding? = null
        var onSetBinding: ((CloudBinding) -> Unit)? = null

        override suspend fun binding() = current

        override suspend fun setBinding(binding: CloudBinding) {
            current = binding
            onSetBinding?.invoke(binding)
        }

        override suspend fun clearBinding() {
            current = null
        }

        override suspend fun peerHighWaterMs(fileId: String) = 0L

        override suspend fun setPeerHighWaterMs(
            fileId: String,
            modifiedAtMs: Long,
        ) = Unit

        override suspend fun isBootstrapDone() = true

        override suspend fun markBootstrapDone() = Unit

        override suspend fun clear() {
            current = null
        }
    }

    private inner class FakeSharedSyncStore : SharedSyncStore {
        var cursor = 0L
        var membershipActive = true
        var cursorIsCleared = false
        var onSetMembershipActive: ((Boolean) -> Unit)? = null

        override suspend fun cursor() = cursor

        override suspend fun setCursor(sequence: Long) {
            cursor = sequence
        }

        override suspend fun isMembershipActive() = membershipActive

        override suspend fun setMembershipActive(active: Boolean) {
            membershipActive = active
            onSetMembershipActive?.invoke(active)
        }

        override suspend fun clear() {
            cursor = 0L
            membershipActive = false
            cursorIsCleared = true
        }
    }

    private class FakeSyncScheduler : SyncScheduler {
        var enableCalls = 0
        var disableCalls = 0
        var cancelAllCalls = 0

        override fun enablePeriodicSync() {
            enableCalls++
        }

        override fun disablePeriodicSync() {
            disableCalls++
        }

        override suspend fun cancelAllSync(): Result<Unit> {
            cancelAllCalls++
            return Result.success(Unit)
        }

        override fun syncNow(target: com.kshavrin.mymoney.core.sync.SyncTarget?) = Unit
    }

    private inner class FakeAccountRepository : AccountRepository {
        var accounts: List<Account> = emptyList()
        var uuids: Map<Long, String> = emptyMap()
        val archivedUuids = mutableListOf<String>()
        val upsertCalls = mutableListOf<Triple<Account, String, String>>()
        var onApply: (() -> Unit)? = null

        override fun observeActive(): Flow<List<Account>> = flowOf(accounts)

        override suspend fun listAllIncludingArchived() = accounts

        override suspend fun findById(id: Long) = accounts.firstOrNull { it.id == id }

        override suspend fun findDefault() = accounts.firstOrNull { it.isDefault }

        override suspend fun computeBalance(accountId: Long) = BigDecimal.ZERO

        override suspend fun upsert(account: Account) = account.id

        override suspend fun uuidForId(id: Long) = uuids[id]

        override suspend fun idForUuid(uuid: String) = uuids.entries.firstOrNull { it.value == uuid }?.key

        override suspend fun applySharedUpsert(
            account: Account,
            uuid: String,
            deviceId: String,
        ) {
            upsertCalls += Triple(account, uuid, deviceId)
            onApply?.invoke()
        }

        override suspend fun applySharedArchive(uuid: String) {
            archivedUuids += uuid
        }

        override suspend fun archive(id: Long) = Unit

        override suspend fun setDefault(id: Long) = Unit

        override suspend fun countByCurrency(currencyId: Long) = 0
    }

    private inner class FakeCategoryRepository : CategoryRepository {
        var categories: List<Category> = emptyList()
        var uuids: Map<Long, String> = emptyMap()

        override fun observeByKind(kind: CategoryKind): Flow<List<Category>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<Category>> = flowOf(categories)

        override suspend fun findById(id: Long) = categories.firstOrNull { it.id == id }

        override suspend fun upsert(category: Category) = category.id

        override suspend fun upsertAll(categories: List<Category>) = Unit

        override suspend fun uuidForId(id: Long) = uuids[id]

        override suspend fun idForUuid(uuid: String) = uuids.entries.firstOrNull { it.value == uuid }?.key

        override suspend fun applySharedUpsert(
            category: Category,
            uuid: String,
            deviceId: String,
        ) = Unit

        override suspend fun applySharedArchive(uuid: String) = Unit

        override suspend fun archive(id: Long) = Unit
    }

    private inner class FakeTransactionRepository : TransactionRepository {
        var transactions: List<Transaction> = emptyList()
        var uuids: Map<Long, String> = emptyMap()
        val deletedUuids = mutableListOf<String>()
        val upsertCalls = mutableListOf<Triple<Transaction, String, String>>()
        var onApply: (() -> Unit)? = null

        override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<Transaction>> = flowOf(transactions)

        override fun paged(
            accountId: Long,
            categoryId: Long?,
            from: Instant,
            to: Instant,
        ) =
            error("unused")

        override suspend fun findById(id: Long) = null

        override suspend fun findByPeriod(
            accountId: Long,
            period: com.kshavrin.mymoney.core.domain.model.Period,
        ) = emptyList<Transaction>()

        override suspend fun getCategorySummary(
            accountId: Long,
            period: com.kshavrin.mymoney.core.domain.model.Period,
            kind: TransactionKind,
        ) = emptyList<CategorySummary>()

        override suspend fun getCategoryGroups(
            accountId: Long,
            period: com.kshavrin.mymoney.core.domain.model.Period,
        ) = emptyList<CategoryGroup>()

        override suspend fun searchByNote(
            query: String,
            limit: Int,
        ) = emptyList<Transaction>()

        override suspend fun upsert(transaction: Transaction) = transaction.id

        override suspend fun uuidForId(id: Long) = uuids[id]

        override suspend fun applySharedUpsert(
            transaction: Transaction,
            uuid: String,
            deviceId: String,
        ) {
            upsertCalls += Triple(transaction, uuid, deviceId)
            onApply?.invoke()
        }

        override suspend fun applySharedDelete(
            uuid: String,
            now: Instant,
        ) {
            deletedUuids += uuid
        }

        override suspend fun softDelete(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun restore(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun pruneDeleted(before: Instant) = Unit

        override suspend fun countByAccount(id: Long) = 0

        override suspend fun countByCategory(id: Long) = 0

        override suspend fun countByCurrency(id: Long) = 0
    }

    private inner class FakeCurrencyRepository : CurrencyRepository {
        var currencies: Map<Long, Currency> = emptyMap()
        private val byCode: Map<String, Currency> get() = currencies.values.associateBy { it.code }
        var lastUpsertAll: List<Currency> = emptyList()
        var onUpsert: (() -> Unit)? = null

        override fun observeActive(): Flow<List<Currency>> = flowOf(currencies.values.toList())

        override fun observeAll(): Flow<List<Currency>> = flowOf(currencies.values.toList())

        override suspend fun findById(id: Long) = currencies[id]

        override suspend fun findByCode(code: String) = byCode[code]

        override suspend fun upsert(currency: Currency): Long {
            val id = if (currency.id == 0L) (currencies.keys.maxOrNull() ?: 0L) + 1L else currency.id
            currencies = currencies + (id to currency.copy(id = id))
            onUpsert?.invoke()
            return id
        }

        override suspend fun upsertAll(currencies: List<Currency>) {
            lastUpsertAll = currencies
            currencies.forEach { upsert(it) }
        }

        override suspend fun setActive(
            id: Long,
            active: Boolean,
        ) = Unit
    }
}
