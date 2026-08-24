package com.kshavrin.mymoney.core.sync.shared

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.SharedPendingOperationEntity
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.AppSettingsRepositoryImpl
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
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
import com.kshavrin.mymoney.core.network.shared.WorkspaceBillingState
import com.kshavrin.mymoney.core.network.shared.WorkspaceInvite
import com.kshavrin.mymoney.core.network.shared.WorkspaceMember
import com.kshavrin.mymoney.core.network.shared.WorkspaceRole
import com.kshavrin.mymoney.core.sync.SyncExecutionGate
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.testing.fake.FakeAnalyticsGateway
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
    private lateinit var analytics: FakeAnalyticsGateway

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
        analytics = FakeAnalyticsGateway()
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
            analytics = analytics,
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
    fun `recoverRemoteWorkspace replace path materializes remote rows before binding`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            val operations = seedRemoteJournalForEmptyLocalDb()

            val result = coordinator.recoverRemoteWorkspace(importLocalData = false)

            assertTrue(result.isSuccess)
            assertEquals(1, backupRepository.internalBackupCalls)
            assertEquals(1, backupRepository.clearDatabaseCalls)
            assertEquals(1, journalRepository.pullCalls)
            assertEquals(listOf(0L), journalRepository.pullAfterSequences)
            assertMaterializedRemoteRows(operations)
            assertEquals(operations.last().serverSequence, sharedStore.cursor)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertEquals("ws-1", configStore.current?.stableAccountId)
        }

    @Test
    fun `recoverRemoteWorkspace replace probe failure preserves local rows without clearing or restart`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            val localAccount = sampleAccount(id = 1L)
            val localCategory =
                Category(
                    id = 2L,
                    name = "Local food",
                    kind = CategoryKind.Expense,
                    iconKey = "ic_food",
                    colorHex = "#FF5722",
                    textColor = "#FFFFFF",
                    sortOrder = 1,
                    isDefault = false,
                    isArchived = false,
                    createdAt = clock.instant(),
                )
            val localTransaction = sampleTransaction(accountId = localAccount.id)
            val localCurrency = fakeCurrency()
            accountRepository.accounts = listOf(localAccount)
            categoryRepository.categories = listOf(localCategory)
            transactionRepository.transactions = listOf(localTransaction)
            currencyRepository.currencies = mapOf(localCurrency.id to localCurrency)
            val probeFailure = SyncException(SyncError.EntitlementRequired)
            journalRepository.pullResults.add(Result.failure(probeFailure))

            val result = coordinator.recoverRemoteWorkspace(importLocalData = false)

            assertTrue(result.isFailure)
            assertEquals(probeFailure, result.exceptionOrNull())
            assertEquals(1, backupRepository.internalBackupCalls)
            assertEquals(1, journalRepository.pullCalls)
            assertEquals(listOf(0L), journalRepository.pullAfterSequences)
            assertEquals(0, backupRepository.clearDatabaseCalls)
            assertTrue(backupRepository.importPaths.isEmpty())
            assertTrue(!coordinator.consumeRestartRequiredAfterAdoptionRecovery())
            assertNull(configStore.current)
            assertEquals(listOf(localAccount), accountRepository.accounts)
            assertEquals(listOf(localCategory), categoryRepository.categories)
            assertEquals(listOf(localTransaction), transactionRepository.transactions)
            assertEquals(mapOf(localCurrency.id to localCurrency), currencyRepository.currencies)
        }

    @Test
    fun `recoverRemoteWorkspace replace path succeeds with empty remote journal at cursor zero`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.recoverRemoteWorkspace(importLocalData = false)

            assertTrue(result.isSuccess)
            assertEquals(1, backupRepository.internalBackupCalls)
            assertEquals(1, backupRepository.clearDatabaseCalls)
            assertEquals(1, journalRepository.pullCalls)
            assertEquals(listOf(0L), journalRepository.pullAfterSequences)
            assertEquals(0L, sharedStore.cursor)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertEquals("ws-1", configStore.current?.stableAccountId)
            assertTrue(accountRepository.upsertCalls.isEmpty())
            assertTrue(categoryRepository.upsertCalls.isEmpty())
            assertTrue(transactionRepository.upsertCalls.isEmpty())
        }

    @Test
    fun `recoverRemoteWorkspace import path materializes remote rows before binding`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            val operations = seedRemoteJournalForEmptyLocalDb()

            val result = coordinator.recoverRemoteWorkspace(importLocalData = true)

            assertTrue(result.isSuccess)
            assertEquals(1, backupRepository.internalBackupCalls)
            assertEquals(0, backupRepository.clearDatabaseCalls)
            assertMaterializedRemoteRows(operations)
            assertEquals(operations.last().serverSequence, sharedStore.cursor)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertEquals("ws-1", configStore.current?.stableAccountId)
        }

    @Test
    fun `recoverRemoteWorkspace import path preserves local rows when journal push fails`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            val localAccount = sampleAccount(id = 1L)
            accountRepository.accounts = listOf(localAccount)
            accountRepository.uuids = mapOf(1L to "account-uuid-local")
            val localCurrency = fakeCurrency(code = "USD")
            currencyRepository.currencies = mapOf(localCurrency.id to localCurrency)
            val pushFailure = SyncException(SyncError.Server)
            journalRepository.pushFailure = pushFailure
            journalRepository.pullResults.add(Result.success(emptyList()))

            val result = coordinator.recoverRemoteWorkspace(importLocalData = true)

            assertTrue(result.isFailure)
            assertEquals(pushFailure, result.exceptionOrNull())
            assertEquals(1, journalRepository.pushCalls)
            assertEquals(1, journalRepository.pullCalls)
            assertNull(configStore.current)
            assertTrue(!sharedStore.membershipActive)
            assertEquals(0L, sharedStore.cursor)
            assertTrue(backupRepository.importPaths.isEmpty())
            assertTrue(!coordinator.consumeRestartRequiredAfterAdoptionRecovery())
            assertEquals(listOf(localAccount), accountRepository.accounts)
            assertEquals(mapOf(localCurrency.id to localCurrency), currencyRepository.currencies)
        }

    @Test
    fun `recoverRemoteWorkspace does not report success after malformed remote operation`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            val malformedOperation =
                fakeOperation(
                    serverSequence = 1L,
                    entityKind = EntityKind.Category,
                    payload = "{}",
                )
            journalRepository.pullResults.add(Result.success(listOf(malformedOperation)))

            val result = coordinator.recoverRemoteWorkspace(importLocalData = false)

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
            assertEquals(0L, sharedStore.cursor)
            assertNull(configStore.current)
            assertTrue(accountRepository.upsertCalls.isEmpty())
            assertEquals(listOf("/internal/backup.db"), backupRepository.importPaths)
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
    fun `active workspace access rejects a missing server workspace`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            workspaceApi.currentWorkspaceResult = Result.success(null)

            val result = coordinator.activeWorkspaceAccess()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
        }

    @Test
    fun `active workspace access keeps local binding for a transient failure`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            workspaceApi.currentWorkspaceResult = Result.failure(SyncException(SyncError.Network))

            val result = coordinator.activeWorkspaceAccess()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Network, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNotNull(configStore.current)
            assertTrue(!sharedStore.cursorIsCleared)
            assertEquals(0, scheduler.disableCalls)
            assertEquals(0, auth.signOutCalls)
        }

    @Test
    fun `active workspace access revokes local state when membership is no longer active`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = false

            val result = coordinator.activeWorkspaceAccess()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
        }

    @Test
    fun `active workspace ownership revokes local state when auth is lost`() =
        runTest(dispatcher) {
            auth.session = null
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")

            val result = coordinator.activeWorkspaceOwnership()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
        }

    @Test
    fun `active workspace ownership revokes local state when membership lookup reports auth loss`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            workspaceApi.listMembersResult = Result.failure(SyncException(SyncError.Auth))

            val result = coordinator.activeWorkspaceOwnership()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
        }

    @Test
    fun `active workspace ownership retains local binding for transient network and server failures`() =
        runTest(dispatcher) {
            for (error in listOf(SyncError.Network, SyncError.Server)) {
                auth.session = fakeSession()
                configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
                sharedStore.cursorIsCleared = false
                workspaceApi.listMembersResult = Result.failure(SyncException(error))

                val result = coordinator.activeWorkspaceOwnership()

                assertTrue(result.isFailure)
                assertEquals(error, (result.exceptionOrNull() as? SyncException)?.syncError)
                assertNotNull(configStore.current)
                assertTrue(!sharedStore.cursorIsCleared)
                assertEquals(0, scheduler.disableCalls)
                assertEquals(0, auth.signOutCalls)
            }
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
    fun `disconnectFromDevice revokes local access when final sync is rejected for auth`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            journalRepository.pullResults.add(Result.failure(SyncException(SyncError.Auth)))

            val result = coordinator.disconnectFromDevice()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
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

    // ── pullAndApply per-operation failure and dependency ordering ──────────

    @Test
    fun `pullAndApply fails on malformed non-currency op and preserves cursor`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true

            val malformedOp = fakeOperation(serverSequence = 1L, entityKind = EntityKind.Category, payload = "{}")
            val validOp =
                fakeOperation(
                    serverSequence = 2L,
                    entityKind = EntityKind.Account,
                    tombstone = true,
                    payload = null,
                )
            journalRepository.pullResults.add(Result.success(listOf(malformedOp, validOp)))

            val result = coordinator.syncNow()

            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
            assertEquals(0L, sharedStore.cursor)
            assertTrue(accountRepository.archivedUuids.isEmpty())
            assertNotNull(configStore.current)
        }

    @Test
    fun `pullAndApply fails on Room persistence error and does not advance cursor`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            accountRepository.applyFailure = IllegalStateException("room write failed")
            val payload = codec.encodeAccount(sampleAccount(id = 10L, currencyId = 0L), "account-uuid", fakeCurrency())
            val operation = fakeOperation(serverSequence = 1L, payload = payload)
            journalRepository.pullResults.add(Result.success(listOf(operation)))

            val result = coordinator.syncNow()

            assertTrue(result.isFailure)
            assertEquals("room write failed", result.exceptionOrNull()?.message)
            assertEquals(0L, sharedStore.cursor)
            assertTrue(accountRepository.upsertCalls.isEmpty())
            assertNull(
                db.sharedOutboxDao().stateForEntity(
                    workspaceId = "ws-1",
                    entityKind = EntityKind.Account.name,
                    entityId = operation.entityId,
                ),
            )
            assertNotNull(configStore.current)
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
    fun `detach to local only joins realtime and cancels every sync path`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true

            assertTrue(coordinator.startForegroundRealtime().isSuccess)
            realtime.awaitActiveSubscriptions(1)

            assertTrue(coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired).isSuccess)

            realtime.awaitActiveSubscriptions(0)
            assertNotNull(sharedStore.localOnly)
            assertEquals(SharedRealtimeStatus.Inactive, coordinator.foregroundRealtimeStatus.value)
            assertEquals(1, scheduler.cancelAllCalls)
        }

    @Test
    fun `detach to local only logs SharedDetached with the triggering reason`() =
        runTest(dispatcher) {
            LocalOnlyReason.entries.forEach { reason ->
                sharedStore = FakeSharedSyncStore()
                configStore = FakeJournalSyncConfigStore()
                configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
                analytics = FakeAnalyticsGateway()
                coordinator = createCoordinator()

                assertTrue(coordinator.detachToLocalOnly(reason).isSuccess)

                assertEquals(
                    "SharedDetached must be logged once with reason ${reason.name}",
                    listOf(AnalyticsEvent.SharedDetached(reason.name)),
                    analytics.events,
                )
            }
        }

    @Test
    fun `detach to local only does not log SharedDetached when there is no Shared binding`() =
        runTest(dispatcher) {
            configStore.current = null

            assertTrue(coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired).isSuccess)

            assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
        }

    @Test
    fun `detach to local only propagates cancellation before persisting the gate`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            backupRepository.internalBackupFailure = CancellationException("screen closed")

            try {
                coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired)
                error("Expected CancellationException")
            } catch (_: CancellationException) {
            }

            assertNull(sharedStore.localOnly)
            assertEquals(0, scheduler.cancelAllCalls)
        }

    @Test
    fun `reattach keeps local only persisted until the ordered sync succeeds`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace())
            workspaceApi.listMembersResult = Result.success(listOf(fakeMember(role = WorkspaceRole.Owner)))
            sharedStore.localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = LocalOnlyReason.EntitlementExpired.name,
                    sinceEpochMs = clock.millis(),
                )
            journalRepository.pullResults.add(Result.failure(SyncException(SyncError.Network)))

            assertTrue(coordinator.reattachAfterEntitlementRestored().isFailure)
            assertNotNull(sharedStore.localOnly)
            assertEquals(0, scheduler.enableCalls)

            journalRepository.pullResults.add(Result.success(emptyList()))

            assertTrue(coordinator.reattachAfterEntitlementRestored().isSuccess)
            assertNull(sharedStore.localOnly)
            assertEquals(1, scheduler.enableCalls)
        }

    @Test
    fun `cold-start participant reattaches only after owner billing recovers`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            sharedStore.localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = LocalOnlyReason.EntitlementExpired.name,
                    sinceEpochMs = clock.millis(),
                    workspaceBillingState = WorkspaceBillingState.Expired.name,
                    isWorkspaceOwner = false,
                )
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace(billingState = WorkspaceBillingState.Expired))
            workspaceApi.listMembersResult = Result.success(listOf(fakeMember(role = WorkspaceRole.Editor)))

            val beforeOwnerRenewal = coordinator.reattachAfterEntitlementRestored()

            assertTrue(beforeOwnerRenewal.isFailure)
            assertEquals(SyncError.EntitlementRequired, (beforeOwnerRenewal.exceptionOrNull() as? SyncException)?.syncError)
            assertNotNull(sharedStore.localOnly)

            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace(billingState = WorkspaceBillingState.Active))
            workspaceApi.listMembersResult = Result.success(listOf(fakeMember(role = WorkspaceRole.Editor)))
            journalRepository.pullResults.add(Result.success(emptyList()))

            assertTrue(coordinator.reattachAfterEntitlementRestored().isSuccess)
            assertNull(sharedStore.localOnly)
        }

    @Test
    fun `auth failure while local only preserves reason binding cursor and every outbox row`() =
        runTest(dispatcher) {
            // No session → any auth-triggered cleanup path must fail closed while LocalOnly is durable.
            auth.session = null
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            sharedStore.cursor = 42L
            sharedStore.localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = LocalOnlyReason.EntitlementExpired.name,
                    sinceEpochMs = clock.millis(),
                )
            db.sharedOutboxDao().insertPending(pendingOutboxRow())

            val result = coordinator.activeWorkspaceOwnership()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            // Nothing destructive ran: LocalOnly, binding, cursor and the outbox are all preserved.
            assertNotNull(sharedStore.localOnly)
            assertEquals(LocalOnlyReason.EntitlementExpired.name, sharedStore.localOnly?.reason)
            assertNotNull(configStore.current)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertTrue(!sharedStore.cursorIsCleared)
            assertEquals(42L, sharedStore.cursor)
            assertEquals(1, db.sharedOutboxDao().pendingForWorkspace("ws-1").size)
            assertEquals(0, scheduler.disableCalls)
        }

    @Test
    fun `unknown role blocks reattach without touching local only outbox or the sync path`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            sharedStore.localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = LocalOnlyReason.EntitlementExpired.name,
                    sinceEpochMs = clock.millis(),
                )
            workspaceApi.currentWorkspaceResult = Result.success(fakeWorkspace(billingState = WorkspaceBillingState.Active))
            // Server lists an active membership for a different user only → own role is Unknown.
            workspaceApi.listMembersResult = Result.success(listOf(fakeMember(userId = "someone-else")))
            db.sharedOutboxDao().insertPending(pendingOutboxRow())

            val result = coordinator.reattachAfterEntitlementRestored()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNotNull(sharedStore.localOnly)
            assertEquals(1, db.sharedOutboxDao().pendingForWorkspace("ws-1").size)
            assertEquals(0, journalRepository.pushCalls)
            assertEquals(0, journalRepository.pullCalls)
            assertEquals(0, scheduler.enableCalls)
        }

    @Test
    fun `snapshot failure during detach leaves the attached realtime state fully intact`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true

            assertTrue(coordinator.startForegroundRealtime().isSuccess)
            realtime.awaitActiveSubscriptions(1)

            backupRepository.internalBackupFailure = RuntimeException("disk full")

            val result = coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired)

            assertTrue(result.isFailure)
            assertEquals("disk full", result.exceptionOrNull()?.message)
            // No durable commit, no teardown: LocalOnly absent, sync not cancelled, realtime still live.
            assertNull(sharedStore.localOnly)
            assertEquals(0, scheduler.cancelAllCalls)
            assertEquals(1, realtime.activeSubscriptions)
            assertTrue(coordinator.foregroundRealtimeStatus.value != SharedRealtimeStatus.Inactive)

            coordinator.stopForegroundRealtime()
            realtime.awaitActiveSubscriptions(0)
        }

    @Test
    fun `detachToLocalOnly is idempotent when already in local only mode`() =
        runTest(dispatcher) {
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = LocalOnlyReason.EntitlementExpired.name,
                    sinceEpochMs = clock.millis(),
                )

            val result = coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired)

            assertTrue(result.isSuccess)
            assertEquals(0, backupRepository.internalBackupCalls)
            assertEquals(0, scheduler.cancelAllCalls)
        }

    @Test
    fun `detachToLocalOnly returns success immediately when no Shared binding is active`() =
        runTest(dispatcher) {
            configStore.current = null

            val result = coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired)

            assertTrue(result.isSuccess)
            assertEquals(0, backupRepository.internalBackupCalls)
            assertNull(sharedStore.localOnly)
            assertEquals(0, scheduler.cancelAllCalls)
        }

    @Test
    fun `detachToLocalOnly commits local only flag before teardown and returns failure when teardown throws`() =
        runTest(dispatcher) {
            // Documents the known inconsistency window: setLocalOnly runs inside NonCancellable before
            // stopForegroundRealtimeAndJoin / cancelAllSync. A teardown failure after the commit
            // propagates as Result.failure — but the LocalOnly gate IS already durable. Callers must
            // surface the error without assuming LocalOnly was rolled back; the binding and outbox are
            // still protected by the committed gate.
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            scheduler.cancelAllFailure = RuntimeException("WorkManager cancel threw")

            val result = coordinator.detachToLocalOnly(LocalOnlyReason.EntitlementExpired)

            assertNotNull("LocalOnly must be committed even when teardown fails", sharedStore.localOnly)
            assertEquals(LocalOnlyReason.EntitlementExpired.name, sharedStore.localOnly?.reason)
            assertTrue("Method must surface the teardown failure", result.isFailure)
            assertEquals("WorkManager cancel threw", result.exceptionOrNull()?.message)
        }

    @Test
    fun `default constructed workspace ownership role is Unknown not a verified participant`() {
        val ownership = SharedWorkspaceOwnership()

        assertEquals(SharedWorkspaceRole.Unknown, ownership.role)
        assertTrue(!ownership.isRoleVerified)
    }

    @Test
    fun `realtime startup membership rejection revokes the shared binding`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = false

            val result = coordinator.startForegroundRealtime()

            assertTrue(result.isFailure)
            assertEquals(SyncError.Auth, (result.exceptionOrNull() as? SyncException)?.syncError)
            assertNull(configStore.current)
            assertTrue(sharedStore.cursorIsCleared)
            assertEquals(1, scheduler.disableCalls)
            assertEquals(1, auth.signOutCalls)
            assertEquals(SharedRealtimeStatus.Inactive, coordinator.foregroundRealtimeStatus.value)
            assertEquals(0, realtime.eventsCalls)
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
            assertNotNull(configStore.current)
            assertEquals(0, scheduler.disableCalls)
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
    fun `realtime auth failure racing a local only commit preserves binding cursor and outbox`() =
        runTest(dispatcher) {
            auth.session = fakeSession()
            configStore.current = CloudBinding(CloudProvider.Shared, "ws-1", "Budget")
            sharedStore.membershipActive = true
            sharedStore.cursor = 7L
            // The realtime supervisor connects, then reports a terminal Auth loss only AFTER a concurrent
            // detachToLocalOnly() commit has landed — the exact TOCTOU window. Its cleanup must observe the
            // committed LocalOnly under the lock and stay non-destructive.
            val localOnlyCommitted = CompletableDeferred<Unit>()
            realtime.eventsFlow =
                flow {
                    emit(SharedRealtimeEvent.Connected)
                    localOnlyCommitted.await()
                    emit(SharedRealtimeEvent.Disconnected(SyncException(SyncError.Auth)))
                }

            assertTrue(coordinator.startForegroundRealtime().isSuccess)
            coordinator.foregroundRealtimeStatus.first { it == SharedRealtimeStatus.Connected }

            db.sharedOutboxDao().insertPending(pendingOutboxRow())
            sharedStore.localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = LocalOnlyReason.EntitlementExpired.name,
                    sinceEpochMs = clock.millis(),
                )
            localOnlyCommitted.complete(Unit)

            coordinator.foregroundRealtimeStatus.first { it == SharedRealtimeStatus.Inactive }

            // Nothing destructive ran: LocalOnly, cursor, binding and every outbox row survive intact.
            assertNotNull(sharedStore.localOnly)
            assertEquals(LocalOnlyReason.EntitlementExpired.name, sharedStore.localOnly?.reason)
            assertEquals(7L, sharedStore.cursor)
            assertTrue(!sharedStore.cursorIsCleared)
            assertNotNull(configStore.current)
            assertEquals(CloudProvider.Shared, configStore.current?.provider)
            assertEquals(1, db.sharedOutboxDao().pendingForWorkspace("ws-1").size)
            assertEquals(0, scheduler.disableCalls)
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

    private fun fakeMember(
        userId: String = "user-1",
        role: WorkspaceRole = WorkspaceRole.Owner,
        active: Boolean = true,
    ) = WorkspaceMember(
        userId = userId,
        email = "test@example.com",
        role = role,
        joinedAt = clock.instant(),
        active = active,
    )

    private fun pendingOutboxRow(
        idempotencyKey: String = "pending-key-1",
        workspaceId: String = "ws-1",
    ) = SharedPendingOperationEntity(
        idempotencyKey = idempotencyKey,
        workspaceId = workspaceId,
        baseSequence = 0L,
        deviceId = "test-device",
        entityKind = EntityKind.Account.name,
        entityId = "pending-account-uuid",
        payload = "{}",
        tombstone = false,
        createdAt = clock.millis(),
    )

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

    private fun seedRemoteJournalForEmptyLocalDb(): List<SharedOperation> {
        val currency = fakeCurrency(code = "XYZ").copy(id = 0L, symbol = "¤", name = "Test currency")
        val account = sampleAccount(id = 10L, currencyId = 0L)
        val category =
            Category(
                id = 20L,
                name = "Food",
                kind = CategoryKind.Expense,
                iconKey = "ic_food",
                colorHex = "#FF5722",
                textColor = "#FFFFFF",
                sortOrder = 1,
                isDefault = false,
                isArchived = false,
                createdAt = clock.instant(),
            )
        val transaction = sampleTransaction(currencyId = 0L, accountId = account.id).copy(categoryId = category.id)
        val accountUuid = "remote-account-uuid"
        val categoryUuid = "remote-category-uuid"
        val transactionUuid = "remote-transaction-uuid"

        return listOf(
            fakeOperation(
                serverSequence = 1L,
                entityKind = EntityKind.Transaction,
                entityId = transactionUuid,
                payload = codec.encodeTransaction(transaction, transactionUuid, currency, accountUuid, categoryUuid, null),
            ),
            fakeOperation(
                serverSequence = 2L,
                entityKind = EntityKind.Account,
                entityId = accountUuid,
                payload = codec.encodeAccount(account, accountUuid, currency),
            ),
            fakeOperation(
                serverSequence = 3L,
                entityKind = EntityKind.Category,
                entityId = categoryUuid,
                payload = codec.encodeCategory(category, categoryUuid),
            ),
        ).also { operations -> journalRepository.pullResults.add(Result.success(operations)) }
    }

    private suspend fun assertMaterializedRemoteRows(operations: List<SharedOperation>) {
        assertEquals(1, accountRepository.upsertCalls.size)
        assertEquals(
            10L,
            accountRepository.upsertCalls
                .single()
                .first.id,
        )
        assertEquals(1, categoryRepository.upsertCalls.size)
        assertEquals(
            20L,
            categoryRepository.upsertCalls
                .single()
                .first.id,
        )
        assertEquals(1, transactionRepository.upsertCalls.size)
        assertEquals(
            42L,
            transactionRepository.upsertCalls
                .single()
                .first.id,
        )
        assertEquals(10L, accountRepository.idForUuid("remote-account-uuid"))
        assertEquals(20L, categoryRepository.idForUuid("remote-category-uuid"))
        assertEquals("XYZ", currencyRepository.findByCode("XYZ")?.code)
        operations.forEach { operation ->
            assertNotNull(
                db.sharedOutboxDao().stateForEntity(
                    workspaceId = operation.workspaceId,
                    entityKind = operation.entityKind.name,
                    entityId = operation.entityId,
                ),
            )
        }
    }

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
        var listMembersResult: Result<List<WorkspaceMember>> = Result.success(emptyList())

        override suspend fun createWorkspace(name: String) = createResult

        override suspend fun joinWorkspace(token: String) = joinResult

        override suspend fun leaveWorkspace(workspaceId: String): Result<Unit> {
            leaveCalls++
            leaveFailure?.let { return Result.failure(it) }
            return Result.success(Unit)
        }

        override suspend fun currentWorkspace() = currentWorkspaceResult

        override suspend fun listMembers(workspaceId: String) = listMembersResult

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
        val pullAfterSequences = mutableListOf<Long>()
        private val pullCallCount = MutableStateFlow(0)
        var pushCalls = 0
        var pushFailure: Throwable? = null
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
            pushFailure?.let { return Result.failure(it) }
            return Result.success(fakeOperation(serverSequence = pushCalls.toLong(), entityKind = entityKind))
        }

        override suspend fun pull(
            workspaceId: String,
            afterSequence: Long,
            limit: Int,
        ): Result<List<SharedOperation>> {
            pullCalls++
            pullAfterSequences += afterSequence
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
        var localOnly: com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState? = null
        var workspaceAccessContext: com.kshavrin.mymoney.core.datastore.SharedWorkspaceAccessContext? = null
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

        override suspend fun workspaceAccessContext() = workspaceAccessContext

        override suspend fun setWorkspaceAccessContext(
            billingState: String?,
            isWorkspaceOwner: Boolean?,
        ) {
            workspaceAccessContext =
                com.kshavrin.mymoney.core.datastore.SharedWorkspaceAccessContext(
                    billingState = billingState,
                    isWorkspaceOwner = isWorkspaceOwner,
                )
        }

        override suspend fun localOnlyState() = localOnly

        override suspend fun setLocalOnly(
            reason: String,
            sinceEpochMs: Long,
            workspaceBillingState: String?,
            isWorkspaceOwner: Boolean?,
        ) {
            localOnly =
                com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState(
                    reason = reason,
                    sinceEpochMs = sinceEpochMs,
                    workspaceBillingState = workspaceBillingState,
                    isWorkspaceOwner = isWorkspaceOwner,
                )
        }

        override suspend fun clearLocalOnly() {
            localOnly = null
        }

        override suspend fun clear() {
            cursor = 0L
            membershipActive = false
            localOnly = null
            cursorIsCleared = true
        }
    }

    private class FakeSyncScheduler : SyncScheduler {
        var enableCalls = 0
        var disableCalls = 0
        var cancelAllCalls = 0
        var cancelAllFailure: Throwable? = null

        override fun enablePeriodicSync() {
            enableCalls++
        }

        override fun disablePeriodicSync() {
            disableCalls++
        }

        override suspend fun cancelAllSync(): Result<Unit> {
            cancelAllCalls++
            cancelAllFailure?.let { return Result.failure(it) }
            return Result.success(Unit)
        }

        override fun syncNow(target: com.kshavrin.mymoney.core.sync.SyncTarget?) = Unit
    }

    private inner class FakeAccountRepository : AccountRepository {
        var accounts: List<Account> = emptyList()
        var uuids: Map<Long, String> = emptyMap()
        val archivedUuids = mutableListOf<String>()
        val upsertCalls = mutableListOf<Triple<Account, String, String>>()
        var applyFailure: Throwable? = null
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
            applyFailure?.let { throw it }
            upsertCalls += Triple(account, uuid, deviceId)
            uuids = uuids + (account.id to uuid)
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
        val upsertCalls = mutableListOf<Triple<Category, String, String>>()

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
        ) {
            upsertCalls += Triple(category, uuid, deviceId)
            uuids = uuids + (category.id to uuid)
        }

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
            uuids = uuids + (transaction.id to uuid)
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
