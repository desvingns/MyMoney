package com.kshavrin.mymoney.core.sync.shared

import androidx.room.withTransaction
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.common.exception.reportToSentry
import com.kshavrin.mymoney.core.database.MoneyDatabase
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
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
        private val configStore: JournalSyncConfigStore,
        private val sharedStore: SharedSyncStore,
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
        override fun isSignedIn(): Boolean = auth.currentSession() != null

        override fun accountEmail(): String? = auth.currentSession()?.user?.email

        override suspend fun signIn(googleIdToken: String): Result<Unit> =
            withContext(dispatcher) { auth.signInWithGoogle(googleIdToken).map { } }

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
                runCatching {
                    ensureSignedIn()
                    ensureNoActiveBinding()
                    val workspace = workspaceApi.createWorkspace(name).getOrThrow()
                    adoptWorkspace(workspace, importLocalData)
                    SharedWorkspaceSummary(workspace.id, workspace.name)
                }
            }

        override suspend fun joinWorkspace(
            inviteToken: String,
            importLocalData: Boolean,
        ): Result<SharedWorkspaceSummary> =
            withContext(dispatcher) {
                runCatching {
                    ensureSignedIn()
                    ensureNoActiveBinding()
                    val workspace = workspaceApi.joinWorkspace(inviteToken).getOrThrow()
                    adoptWorkspace(workspace, importLocalData)
                    SharedWorkspaceSummary(workspace.id, workspace.name)
                }
            }

        override suspend fun syncNow(): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val workspaceId = requireActiveWorkspaceId()
                    // Forced-removal guard: if this device's membership is no longer active, detach the
                    // shared binding (mirroring leave's local cleanup) so background sync stops and the
                    // UI can surface an access-denied state, keeping the shared data as a personal copy.
                    if (!sharedStore.isMembershipActive()) {
                        clearSharedLocalState()
                        throw SyncException(SyncError.Auth)
                    }
                    pullAndApply(workspaceId)
                }
            }

        override suspend fun listConflicts(): Result<List<SharedConflict>> =
            withContext(dispatcher) {
                runCatching { journalRepository.listPendingConflicts(requireActiveWorkspaceId()).getOrThrow() }
            }

        override suspend fun resolveConflict(
            conflictId: String,
            winnerOperationId: String,
        ): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    journalRepository.resolveConflict(conflictId, winnerOperationId).getOrThrow()
                    pullAndApply(requireActiveWorkspaceId())
                }
            }

        override suspend fun leaveWorkspace(): Result<Unit> =
            withContext(dispatcher) {
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

        private suspend fun adoptWorkspace(
            workspace: SharedWorkspace,
            importLocalData: Boolean,
        ) {
            backupRepository.createInternalBackup().getOrThrow()
            sharedStore.clear()
            if (importLocalData) {
                // Publish the ORIGINAL local rows before pulling remote state; applying remote
                // operations first could overwrite a local row (IDs are still local Room ids) and
                // then publish the overwritten remote data instead of the user's own data.
                publishLocalData(workspace.id)
                pullAndApply(workspace.id)
            } else {
                backupRepository.clearDatabase().getOrThrow()
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
                        database.withTransaction { applyOperation(operation) }
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
            // entityId carries the stable cross-device uuid (see publishLocalData). Apply keyed by
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

        private suspend fun publishLocalData(workspaceId: String) {
            val deviceId = deviceIdProvider.deviceId()
            val baseSequence = sharedStore.cursor()
            accountRepository.listAllIncludingArchived().forEach { account ->
                val uuid = accountRepository.uuidForId(account.id) ?: return@forEach
                val currencyCode = currencyRepository.findById(account.currencyId)?.code ?: return@forEach
                push(
                    workspaceId,
                    deviceId,
                    baseSequence,
                    EntityKind.Account,
                    uuid,
                    codec.encodeAccount(account, uuid, currencyCode),
                )
            }
            categoryRepository.observeAll().first().forEach { category ->
                val uuid = categoryRepository.uuidForId(category.id) ?: return@forEach
                push(workspaceId, deviceId, baseSequence, EntityKind.Category, uuid, codec.encodeCategory(category, uuid))
            }
            transactionRepository.observeAll().first().forEach { transaction ->
                val uuid = transactionRepository.uuidForId(transaction.id) ?: return@forEach
                // Publish portable references. If any referenced row lacks a uuid/code it cannot be
                // shared portably yet, so skip the whole transaction rather than emit a broken ref.
                val currencyCode = currencyRepository.findById(transaction.currencyId)?.code ?: return@forEach
                val accountUuid = accountRepository.uuidForId(transaction.accountId) ?: return@forEach
                val categoryUuid =
                    transaction.categoryId?.let { categoryRepository.uuidForId(it) ?: return@forEach }
                val toAccountUuid =
                    transaction.toAccountId?.let { accountRepository.uuidForId(it) ?: return@forEach }
                push(
                    workspaceId,
                    deviceId,
                    baseSequence,
                    EntityKind.Transaction,
                    uuid,
                    codec.encodeTransaction(transaction, uuid, currencyCode, accountUuid, categoryUuid, toAccountUuid),
                )
            }
        }

        private suspend fun push(
            workspaceId: String,
            deviceId: String,
            baseSequence: Long,
            entityKind: EntityKind,
            entityId: String,
            payload: String,
        ) {
            journalRepository
                .push(
                    workspaceId = workspaceId,
                    idempotencyKey = UUID.randomUUID().toString(),
                    baseSequence = baseSequence,
                    deviceId = deviceId,
                    entityKind = entityKind,
                    entityId = entityId,
                    payload = payload,
                    tombstone = false,
                ).getOrThrow()
        }

        private suspend fun clearSharedLocalState() {
            configStore.clearBinding()
            sharedStore.clear()
        }

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

        private companion object {
            const val PAGE_SIZE = 100
        }
    }
