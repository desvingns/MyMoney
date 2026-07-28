package com.kshavrin.mymoney.core.sync.shared

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.CloudBinding
import com.kshavrin.mymoney.core.datastore.CloudProvider
import com.kshavrin.mymoney.core.datastore.JournalSyncConfigStore
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.sync.SharedOperation
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedWorkspace
import com.kshavrin.mymoney.core.network.shared.SharedWorkspaceApi
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
        private val codec: SharedEntityCodec,
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
                    backupRepository.createInternalBackup().getOrThrow()
                    runCatching { workspaceApi.leaveWorkspace(workspaceId).getOrThrow() }
                    configStore.clearBinding()
                    sharedStore.clear()
                }
            }

        private suspend fun adoptWorkspace(
            workspace: SharedWorkspace,
            importLocalData: Boolean,
        ) {
            backupRepository.createInternalBackup().getOrThrow()
            sharedStore.clear()
            if (importLocalData) {
                pullAndApply(workspace.id)
                publishLocalData(workspace.id)
            } else {
                backupRepository.clearDatabase().getOrThrow()
                pullAndApply(workspace.id)
            }
            configStore.setBinding(
                CloudBinding(
                    provider = CloudProvider.Shared,
                    stableAccountId = workspace.id,
                    accountLabel = workspace.name,
                ),
            )
            sharedStore.setMembershipActive(true)
        }

        private suspend fun pullAndApply(workspaceId: String) {
            var after = sharedStore.cursor()
            while (true) {
                val operations = journalRepository.pull(workspaceId, after, PAGE_SIZE).getOrThrow()
                if (operations.isEmpty()) break
                operations.forEach { applyOperation(it) }
                after = operations.maxOf { it.serverSequence }
                sharedStore.setCursor(after)
                if (operations.size < PAGE_SIZE) break
            }
        }

        private suspend fun applyOperation(operation: SharedOperation) {
            val now = clock.instant()
            when (operation.entityKind) {
                EntityKind.Transaction ->
                    if (operation.tombstone) {
                        operation.entityId.toLongOrNull()?.let { transactionRepository.softDelete(it, now) }
                    } else {
                        operation.payload?.let { transactionRepository.upsert(codec.decodeTransaction(it)) }
                    }
                EntityKind.Account ->
                    if (operation.tombstone) {
                        operation.entityId.toLongOrNull()?.let { accountRepository.archive(it) }
                    } else {
                        operation.payload?.let { accountRepository.upsert(codec.decodeAccount(it)) }
                    }
                EntityKind.Category ->
                    if (operation.tombstone) {
                        operation.entityId.toLongOrNull()?.let { categoryRepository.archive(it) }
                    } else {
                        operation.payload?.let { categoryRepository.upsert(codec.decodeCategory(it)) }
                    }
            }
        }

        private suspend fun publishLocalData(workspaceId: String) {
            val deviceId = deviceIdProvider.deviceId()
            val baseSequence = sharedStore.cursor()
            accountRepository.observeActive().first().forEach { account ->
                push(workspaceId, deviceId, baseSequence, EntityKind.Account, codec.entityId(account), codec.encodeAccount(account))
            }
            categoryRepository.observeAll().first().forEach { category ->
                push(workspaceId, deviceId, baseSequence, EntityKind.Category, codec.entityId(category), codec.encodeCategory(category))
            }
            transactionRepository.observeAll().first().forEach { transaction ->
                push(
                    workspaceId,
                    deviceId,
                    baseSequence,
                    EntityKind.Transaction,
                    codec.entityId(transaction),
                    codec.encodeTransaction(transaction),
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
