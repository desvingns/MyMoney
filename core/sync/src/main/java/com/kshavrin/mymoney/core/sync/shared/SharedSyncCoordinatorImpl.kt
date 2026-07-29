package com.kshavrin.mymoney.core.sync.shared

import androidx.room.withTransaction
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.database.entity.SharedEntityStateEntity
import com.kshavrin.mymoney.core.database.entity.SharedPendingOperationEntity
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.sync.SyncExecutionGate
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.sync.SharedOperation
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedWorkspace
import com.kshavrin.mymoney.core.network.shared.SharedWorkspaceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedSyncCoordinatorImpl
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val workspaceApi: SharedWorkspaceApi,
        private val journalRepository: SharedJournalRepository,
        private val backupRepository: BackupRepository,
        private val appSettings: AppSettingsRepository,
        private val configStore: JournalSyncConfigStore,
        private val sharedStore: SharedSyncStore,
        private val syncScheduler: SyncScheduler,
        private val executionGate: SyncExecutionGate,
        private val deviceIdProvider: DeviceIdProvider,
        private val transactionRepository: TransactionRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository,
        private val currencyRepository: CurrencyRepository,
        private val codec: SharedEntityCodec,
        private val database: MoneyDatabase,
        private val clock: Clock,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SharedSyncCoordinator {
        private val operationMutex = Mutex()

        override fun isSignedIn(): Boolean = auth.currentSession() != null

        override fun accountEmail(): String? = auth.currentSession()?.user?.email

        override suspend fun signIn(
            googleIdToken: String,
            nonce: String,
        ): Result<Unit> =
            withContext(dispatcher) { auth.signInWithGoogle(googleIdToken, nonce).map { } }

        override suspend fun signOut(): Result<Unit> = withContext(dispatcher) { auth.signOut() }

        override suspend fun activeWorkspace(): SharedWorkspaceSummary? =
            withContext(dispatcher) {
                configStore
                    .binding()
                    ?.takeIf { it.provider == CloudProvider.Shared }
                    ?.let { SharedWorkspaceSummary(id = it.stableAccountId, name = it.accountLabel) }
            }

        override suspend fun createWorkspace(
            name: String,
            importLocalData: Boolean,
        ): Result<SharedWorkspaceSummary> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    runCatching {
                        ensureSignedIn()
                        ensureNoActiveBinding()
                        val workspace = workspaceApi.createWorkspace(name).getOrThrow()
                        adoptWorkspace(workspace, importLocalData)
                        SharedWorkspaceSummary(workspace.id, workspace.name)
                    }
                }
            }

        override suspend fun joinWorkspace(
            inviteToken: String,
            importLocalData: Boolean,
        ): Result<SharedWorkspaceSummary> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    runCatching {
                        ensureSignedIn()
                        ensureNoActiveBinding()
                        val workspace = workspaceApi.joinWorkspace(inviteToken).getOrThrow()
                        adoptWorkspace(workspace, importLocalData)
                        SharedWorkspaceSummary(workspace.id, workspace.name)
                    }
                }
            }

        override suspend fun createInvite(): Result<SharedWorkspaceInvite> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    val result =
                        runCatching {
                            ensureSignedIn()
                            val workspaceId = requireActiveWorkspaceId()
                            if (!sharedStore.isMembershipActive()) {
                                throw SyncException(SyncError.Auth)
                            }
                            SharedWorkspaceInvite(workspaceApi.createInvite(workspaceId).getOrThrow().token)
                        }
                    clearSharedStateOnAuthFailure(result)
                }
            }

        override suspend fun syncNow(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    val result =
                        runCatching {
                            val workspaceId = requireActiveWorkspaceId()
                            // Forced-removal guard: if this device's membership is no longer active, detach the
                            // shared binding (mirroring leave's local cleanup) so background sync stops and the
                            // UI can surface an access-denied state, keeping the shared data as a personal copy.
                            if (!sharedStore.isMembershipActive()) {
                                throw SyncException(SyncError.Auth)
                            }
                            enqueueLocalChanges(workspaceId)
                            publishPendingOperations(workspaceId)
                            pullAndApply(workspaceId)
                            appSettings.update { it.copy(lastSyncAt = clock.millis()) }
                        }
                    clearSharedStateOnAuthFailure(result)
                }
            }

        override suspend fun listConflicts(): Result<List<SharedConflict>> =
            withContext(dispatcher) {
                clearSharedStateOnAuthFailure(
                    runCatching { journalRepository.listPendingConflicts(requireActiveWorkspaceId()).getOrThrow() },
                )
            }

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    clearSharedStateOnAuthFailure(
                        runCatching {
                            journalRepository.resolveConflict(conflictId, winnerOperationId).getOrThrow()
                            pullAndApply(requireActiveWorkspaceId())
                        },
                    )
                }
            }

        override suspend fun restoreInternalBackup(backupPath: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    syncScheduler.cancelAllSync().getOrThrow()
                    executionGate.withExclusive {
                        operationMutex.withLock {
                            detachForLocalRestoreLocked()
                            backupRepository.importFromFile(backupPath).getOrThrow()
                        }
                    }
                }
            }

        override suspend fun leaveWorkspace(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    runCatching {
                        val workspaceId = requireActiveWorkspaceId()
                        // A failed safety backup must NOT keep remote access alive: capture its result but
                        // always proceed to cut remote access, then propagate the backup failure at the end.
                        val backup = backupRepository.createInternalBackup()
                        val serverLeave = runCatching { workspaceApi.leaveWorkspace(workspaceId).getOrThrow() }
                        clearSharedLocalState()
                        // The shared data stays as a personal local copy. Whether or not the server-side
                        // membership row was removed, always drop the auth session so a stale token cannot
                        // keep remote access alive if the server leave did not land.
                        runCatching { auth.signOut().getOrThrow() }
                        val serverError = serverLeave.exceptionOrNull()
                        val backupError = backup.exceptionOrNull()
                        if (serverError != null) {
                            backupError?.let(serverError::addSuppressed)
                            throw serverError
                        }
                        backupError?.let { throw it }
                        Unit
                    }
                }
            }

        private suspend fun adoptWorkspace(
            workspace: SharedWorkspace,
            importLocalData: Boolean,
        ) {
            backupRepository.createInternalBackup().getOrThrow()
            sharedStore.clear()
            clearSharedOutbox()
            if (importLocalData) {
                // Publish the ORIGINAL local rows before pulling remote state; applying remote
                // operations first could overwrite a local row (IDs are still local Room ids) and
                // then publish the overwritten remote data instead of the user's own data.
                enqueueLocalChanges(workspace.id)
                publishPendingOperations(workspace.id)
                pullAndApply(workspace.id)
            } else {
                backupRepository.clearDatabase().getOrThrow()
                currencyRepository.upsertAll(InitialDataSeeder.defaultCurrencyCatalog())
                pullAndApply(workspace.id)
            }
            // Mark membership active BEFORE the binding becomes visible, so a concurrent syncNow()
            // landing between these two writes cannot see the binding with an inactive-membership flag
            // and wrongly evict a legitimately-joining member.
            sharedStore.setMembershipActive(true)
            configStore.setBinding(
                CloudBinding(
                    provider = CloudProvider.Shared,
                    stableAccountId = workspace.id,
                    accountLabel = workspace.name,
                ),
            )
        }

        private suspend fun detachForLocalRestoreLocked() {
            configStore.clearBinding()
            sharedStore.clear()
            runCatching { clearSharedOutbox() }.onFailure(Throwable::reportToSentry)
            auth.clearLocalSession()
        }

        private suspend fun pullAndApply(workspaceId: String) {
            var after = sharedStore.cursor()
            while (true) {
                val operations =
                    journalRepository
                        .pull(workspaceId, after, PAGE_SIZE)
                        .getOrThrow()
                        .sortedBy { it.serverSequence }
                if (operations.isEmpty()) break
                for (operation in operations) {
                    // A single malformed/unknown operation must not stall sync forever: log and skip
                    // it, but still advance the cursor past it so the next pull moves on. Each op is
                    // applied in its own Room transaction so a process kill cannot leave a half-applied
                    // batch behind.
                    try {
                        database.withTransaction {
                            applyOperation(operation)
                            if (operation.tombstone || operation.payload != null) {
                                database.sharedOutboxDao().upsertState(operation.toState())
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        t.reportToSentry()
                    }
                    after = operation.serverSequence
                    sharedStore.setCursor(after)
                }
                if (operations.size < PAGE_SIZE) break
            }
        }

        private suspend fun applyOperation(operation: SharedOperation) {
            // entityId carries the stable cross-device uuid. Apply keyed by
            // uuid via the non-journaling repository methods so a remote row can never overwrite a
            // local row by Room-id collision, and pulled edits never leak into the private cloud.
            val uuid = operation.entityId
            val deviceId = operation.deviceId
            val now = clock.instant()
            when (operation.entityKind) {
                EntityKind.Transaction ->
                    if (operation.tombstone) {
                        transactionRepository.applySharedDelete(uuid, now)
                    } else {
                        operation.payload?.let { payload ->
                            val decoded = codec.decodeTransaction(payload)
                            val refs = codec.decodeTransactionRefs(payload)
                            // Remap portable references to LOCAL ids. A null lookup (referenced
                            // currency/account/category not present yet) throws, so the per-operation
                            // catch logs and skips this op; the missing row arrives on a later pull.
                            val currencyId =
                                currencyRepository.findByCode(refs.currencyCode)?.id
                                    ?: error("shared transaction references unknown currency ${refs.currencyCode}")
                            val accountId =
                                accountRepository.idForUuid(refs.accountUuid)
                                    ?: error("shared transaction references unknown account ${refs.accountUuid}")
                            val categoryId =
                                refs.categoryUuid?.let {
                                    categoryRepository.idForUuid(it)
                                        ?: error("shared transaction references unknown category $it")
                                }
                            val toAccountId =
                                refs.toAccountUuid?.let {
                                    accountRepository.idForUuid(it)
                                        ?: error("shared transaction references unknown account $it")
                                }
                            transactionRepository.applySharedUpsert(
                                decoded.copy(
                                    currencyId = currencyId,
                                    accountId = accountId,
                                    categoryId = categoryId,
                                    toAccountId = toAccountId,
                                ),
                                uuid,
                                deviceId,
                            )
                        }
                    }
                EntityKind.Account ->
                    if (operation.tombstone) {
                        accountRepository.applySharedArchive(uuid)
                    } else {
                        operation.payload?.let { payload ->
                            val decoded = codec.decodeAccount(payload)
                            val currencyCode = codec.decodeAccountCurrencyCode(payload)
                            val currencyId =
                                currencyRepository.findByCode(currencyCode)?.id
                                    ?: error("shared account references unknown currency $currencyCode")
                            accountRepository.applySharedUpsert(decoded.copy(currencyId = currencyId), uuid, deviceId)
                        }
                    }
                EntityKind.Category ->
                    if (operation.tombstone) {
                        categoryRepository.applySharedArchive(uuid)
                    } else {
                        operation.payload?.let {
                            categoryRepository.applySharedUpsert(codec.decodeCategory(it), uuid, deviceId)
                        }
                    }
            }
        }

        private suspend fun enqueueLocalChanges(workspaceId: String) {
            val snapshots = localSnapshots()
            val deviceId = deviceIdProvider.deviceId()
            val baseSequence = sharedStore.cursor()
            database.withTransaction {
                val outbox = database.sharedOutboxDao()
                snapshots.forEach { snapshot ->
                    val pending =
                        outbox.pendingForEntity(
                            workspaceId = workspaceId,
                            entityKind = snapshot.entityKind.name,
                            entityId = snapshot.entityId,
                        )
                    val state =
                        outbox.stateForEntity(
                            workspaceId = workspaceId,
                            entityKind = snapshot.entityKind.name,
                            entityId = snapshot.entityId,
                        )
                    if (pending.isEmpty() && state.matches(snapshot)) return@forEach
                    if (pending.any { it.matches(snapshot) }) return@forEach
                    outbox.insertPending(
                        SharedPendingOperationEntity(
                            idempotencyKey = UUID.randomUUID().toString(),
                            workspaceId = workspaceId,
                            baseSequence = baseSequence,
                            deviceId = deviceId,
                            entityKind = snapshot.entityKind.name,
                            entityId = snapshot.entityId,
                            payload = snapshot.payload,
                            tombstone = snapshot.tombstone,
                            createdAt = clock.millis(),
                        ),
                    )
                }
            }
        }

        private suspend fun publishPendingOperations(workspaceId: String) {
            database.sharedOutboxDao().pendingForWorkspace(workspaceId).forEach { operation ->
                journalRepository
                    .push(
                        workspaceId = operation.workspaceId,
                        idempotencyKey = operation.idempotencyKey,
                        baseSequence = operation.baseSequence,
                        deviceId = operation.deviceId,
                        entityKind = EntityKind.valueOf(operation.entityKind),
                        entityId = operation.entityId,
                        payload = operation.payload,
                        tombstone = operation.tombstone,
                    ).getOrThrow()
                database.withTransaction {
                    database.sharedOutboxDao().upsertState(operation.toState())
                    database.sharedOutboxDao().deletePending(operation.idempotencyKey)
                }
            }
        }

        private suspend fun localSnapshots(): List<LocalSnapshot> =
            buildList {
                accountRepository.listAllIncludingArchived().forEach { account ->
                    val uuid = accountRepository.uuidForId(account.id)
                    val currencyCode = currencyRepository.findById(account.currencyId)?.code
                    if (uuid != null && currencyCode != null) {
                        add(
                            LocalSnapshot(
                                entityKind = EntityKind.Account,
                                entityId = uuid,
                                payload = codec.encodeAccount(account, uuid, currencyCode),
                                tombstone = false,
                            ),
                        )
                    }
                }
                categoryRepository.observeAll().first().forEach { category ->
                    val uuid = categoryRepository.uuidForId(category.id)
                    if (uuid != null) {
                        add(
                            LocalSnapshot(
                                entityKind = EntityKind.Category,
                                entityId = uuid,
                                payload = codec.encodeCategory(category, uuid),
                                tombstone = false,
                            ),
                        )
                    }
                }
                transactionRepository.listAllIncludingDeleted().forEach { transaction ->
                    val uuid = transactionRepository.uuidForId(transaction.id) ?: return@forEach
                    if (transaction.isDeleted) {
                        add(LocalSnapshot(EntityKind.Transaction, uuid, payload = null, tombstone = true))
                        return@forEach
                    }
                    val currencyCode = currencyRepository.findById(transaction.currencyId)?.code ?: return@forEach
                    val accountUuid = accountRepository.uuidForId(transaction.accountId) ?: return@forEach
                    val categoryUuid =
                        transaction.categoryId?.let { categoryRepository.uuidForId(it) ?: return@forEach }
                    val toAccountUuid =
                        transaction.toAccountId?.let { accountRepository.uuidForId(it) ?: return@forEach }
                    add(
                        LocalSnapshot(
                            entityKind = EntityKind.Transaction,
                            entityId = uuid,
                            payload =
                                codec.encodeTransaction(
                                    transaction,
                                    uuid,
                                    currencyCode,
                                    accountUuid,
                                    categoryUuid,
                                    toAccountUuid,
                                ),
                            tombstone = false,
                        ),
                    )
                }
            }

        private suspend fun clearSharedOutbox() {
            database.withTransaction {
                database.sharedOutboxDao().clearPending()
                database.sharedOutboxDao().clearStates()
            }
        }

        private suspend fun clearSharedLocalState() {
            syncScheduler.disablePeriodicSync()
            configStore.clearBinding()
            sharedStore.clear()
            runCatching { clearSharedOutbox() }.onFailure(Throwable::reportToSentry)
        }

        private data class LocalSnapshot(
            val entityKind: EntityKind,
            val entityId: String,
            val payload: String?,
            val tombstone: Boolean,
        )

        private fun SharedEntityStateEntity?.matches(snapshot: LocalSnapshot): Boolean =
            this?.let { it.payload == snapshot.statePayload && it.tombstone == snapshot.tombstone } ?: false

        private fun SharedPendingOperationEntity.matches(snapshot: LocalSnapshot): Boolean =
            payload?.let(codec::canonicalPayload) == snapshot.statePayload && tombstone == snapshot.tombstone

        private val LocalSnapshot.statePayload: String?
            get() = payload?.let(codec::canonicalPayload)

        private fun SharedOperation.toState(): SharedEntityStateEntity =
            SharedEntityStateEntity(
                workspaceId = workspaceId,
                entityKind = entityKind.name,
                entityId = entityId,
                payload = payload?.let(codec::canonicalPayload),
                tombstone = tombstone,
            )

        private fun SharedPendingOperationEntity.toState(): SharedEntityStateEntity =
            SharedEntityStateEntity(
                workspaceId = workspaceId,
                entityKind = entityKind,
                entityId = entityId,
                payload = payload?.let(codec::canonicalPayload),
                tombstone = tombstone,
            )

        private fun ensureSignedIn() {
            if (auth.currentSession() == null) throw SyncException(SyncError.Auth)
        }

        private suspend fun ensureNoActiveBinding() {
            if (configStore.binding() != null) throw SyncException(SyncError.Conflict)
        }

        private suspend fun requireActiveWorkspaceId(): String =
            configStore
                .binding()
                ?.takeIf { it.provider == CloudProvider.Shared }
                ?.stableAccountId
                ?: throw SyncException(SyncError.Conflict)

        private fun Result<*>.isAuthFailure(): Boolean =
            (exceptionOrNull() as? SyncException)?.syncError == SyncError.Auth

        private suspend fun <T> clearSharedStateOnAuthFailure(result: Result<T>): Result<T> {
            if (result.isAuthFailure()) {
                clearSharedLocalState()
                runCatching { auth.signOut().getOrThrow() }.onFailure(Throwable::reportToSentry)
            }
            return result
        }

        private companion object {
            const val PAGE_SIZE = 100
        }
    }
