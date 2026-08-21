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
import com.kshavrin.mymoney.core.datastore.SharedLocalOnlyState
import com.kshavrin.mymoney.core.datastore.SharedSyncStore
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsEvent
import com.kshavrin.mymoney.core.domain.analytics.AnalyticsGateway
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.repository.AccountRepository
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryRepository
import com.kshavrin.mymoney.core.domain.repository.CurrencyRepository
import com.kshavrin.mymoney.core.domain.repository.SharedJournalRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.seed.InitialDataSeeder
import com.kshavrin.mymoney.core.domain.supporter.SupporterSync
import com.kshavrin.mymoney.core.domain.sync.DeviceIdProvider
import com.kshavrin.mymoney.core.domain.sync.EntityKind
import com.kshavrin.mymoney.core.domain.sync.SharedConflict
import com.kshavrin.mymoney.core.domain.sync.SharedOperation
import com.kshavrin.mymoney.core.network.shared.SharedAuth
import com.kshavrin.mymoney.core.network.shared.SharedRealtime
import com.kshavrin.mymoney.core.network.shared.SharedRealtimeEvent
import com.kshavrin.mymoney.core.network.shared.SharedWorkspace
import com.kshavrin.mymoney.core.network.shared.SharedWorkspaceApi
import com.kshavrin.mymoney.core.network.shared.WorkspaceBillingState
import com.kshavrin.mymoney.core.network.shared.WorkspaceRole
import com.kshavrin.mymoney.core.sync.SyncExecutionGate
import com.kshavrin.mymoney.core.sync.SyncScheduler
import com.kshavrin.mymoney.core.sync.analytics.NoOpAnalyticsGateway
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SharedSyncCoordinatorImpl
    @Inject
    constructor(
        private val auth: SharedAuth,
        private val realtime: SharedRealtime,
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
        private val supporterSync: SupporterSync = NoOpSupporterSync,
        private val analytics: AnalyticsGateway = NoOpAnalyticsGateway(),
    ) : SharedSyncCoordinator {
        private val operationMutex = Mutex()
        private val restartRequiredAfterAdoptionRecovery = AtomicBoolean(false)
        private val foregroundRealtimeScope = CoroutineScope(SupervisorJob() + dispatcher)
        private val foregroundRealtimeLock = Any()
        private val _foregroundRealtimeStatus = MutableStateFlow<SharedRealtimeStatus>(SharedRealtimeStatus.Inactive)
        private var foregroundRealtimeGeneration = 0L
        private var foregroundRealtimeJob: Job? = null

        override val foregroundRealtimeStatus: StateFlow<SharedRealtimeStatus> = _foregroundRealtimeStatus

        override fun isSignedIn(): Boolean = auth.currentSession() != null

        override fun accountEmail(): String? = auth.currentSession()?.user?.email

        override suspend fun signIn(
            googleIdToken: String,
            nonce: String,
        ): Result<Unit> =
            withContext(dispatcher) {
                val signInResult = auth.signInWithGoogle(googleIdToken, nonce)
                signInResult.fold(
                    onSuccess = { supporterSync.restore() },
                    onFailure = Result.Companion::failure,
                )
            }

        override suspend fun signOut(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    if (configStore.binding()?.provider == CloudProvider.Shared) {
                        val cleanupFailures = revokeSharedLocalAccess()
                        cleanupFailures.forEach(Throwable::reportToSentry)
                        cleanupFailures.toResult()
                    } else {
                        stopForegroundRealtimeAndJoin()
                        auth.signOut()
                    }
                }
            }

        override suspend fun activeWorkspace(): SharedWorkspaceSummary? =
            withContext(dispatcher) {
                configStore
                    .binding()
                    ?.takeIf { it.provider == CloudProvider.Shared }
                    ?.let { SharedWorkspaceSummary(id = it.stableAccountId, name = it.accountLabel) }
            }

        override suspend fun discoverRemoteWorkspace(): Result<SharedWorkspaceSummary?> =
            withContext(dispatcher) {
                runCatching {
                    ensureSignedIn()
                    workspaceApi.currentWorkspace().getOrThrow()?.let {
                        SharedWorkspaceSummary(id = it.id, name = it.name)
                    }
                }
            }

        override suspend fun recoverRemoteWorkspace(
            importLocalData: Boolean,
        ): Result<SharedWorkspaceSummary> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    runCatching {
                        ensureSignedIn()
                        ensureNoActiveBinding()
                        val workspace =
                            workspaceApi.currentWorkspace().getOrThrow()
                                ?: throw SyncException(SyncError.Auth)
                        val backupPath = backupRepository.createInternalBackup().getOrThrow()
                        adoptWorkspaceWithRecovery(
                            workspace = workspace,
                            importLocalData = importLocalData,
                            backupPath = backupPath,
                            remoteCompensation = {},
                        )
                        SharedWorkspaceSummary(workspace.id, workspace.name)
                    }
                }
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
                        val backupPath = backupRepository.createInternalBackup().getOrThrow()
                        val workspace = workspaceApi.createWorkspace(name).getOrThrow()
                        adoptWorkspaceWithRecovery(
                            workspace = workspace,
                            importLocalData = importLocalData,
                            backupPath = backupPath,
                            remoteCompensation = { workspaceApi.deleteWorkspace(workspace.id).getOrThrow() },
                        )
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
                        val backupPath = backupRepository.createInternalBackup().getOrThrow()
                        val workspace = workspaceApi.joinWorkspace(inviteToken).getOrThrow()
                        adoptWorkspaceWithRecovery(
                            workspace = workspace,
                            importLocalData = importLocalData,
                            backupPath = backupPath,
                            remoteCompensation = { workspaceApi.leaveWorkspace(workspace.id).getOrThrow() },
                        )
                        SharedWorkspaceSummary(workspace.id, workspace.name)
                    }
                }
            }

        override suspend fun createInvite(): Result<SharedWorkspaceInvite> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    if (sharedStore.localOnlyState() != null) {
                        return@withLock Result.failure(SyncException(SyncError.EntitlementRequired))
                    }
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
                    clearSharedStateOnAuthFailure(syncNowLocked())
                }
            }

        override suspend fun disconnectFromDevice(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    val finalSync = syncNowLocked()
                    if (finalSync.isFailure) return@withLock clearSharedStateOnAuthFailure(finalSync)
                    disconnectFromDeviceLocked()
                }
            }

        override suspend fun detachToLocalOnly(reason: LocalOnlyReason): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    if (sharedStore.localOnlyState() != null) return@withLock Result.success(Unit)
                    if (configStore.binding()?.provider != CloudProvider.Shared) return@withLock Result.success(Unit)
                    try {
                        backupRepository.createInternalBackup().getOrThrow()
                        currentCoroutineContext().ensureActive()
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        return@withLock Result.failure(failure)
                    }
                    try {
                        withContext(NonCancellable) {
                            val teardownFailures = mutableListOf<Throwable>()
                            val accessContext = sharedStore.workspaceAccessContext()
                            sharedStore.setLocalOnly(
                                reason = reason.name,
                                sinceEpochMs = clock.millis(),
                                workspaceBillingState = accessContext?.billingState,
                                isWorkspaceOwner = accessContext?.isWorkspaceOwner,
                            )
                            analytics.log(AnalyticsEvent.SharedDetached(reason.name))
                            collectCleanupFailure(teardownFailures) { stopForegroundRealtimeAndJoin() }
                            collectCleanupFailure(teardownFailures) { syncScheduler.cancelAllSync().getOrThrow() }
                            teardownFailures.toResult().getOrThrow()
                        }
                        Result.success(Unit)
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        Result.failure(failure)
                    }
                }
            }

        override suspend fun reattachAfterEntitlementRestored(): Result<Unit> =
            withContext(dispatcher) {
                val reattachResult =
                    operationMutex.withLock {
                        if (sharedStore.localOnlyState() == null) return@withLock Result.success(Unit)
                        try {
                            ensureSignedIn()
                            requireActiveWorkspaceId()
                            if (!sharedStore.isMembershipActive()) throw SyncException(SyncError.Auth)
                            val workspace = workspaceApi.currentWorkspace().getOrThrow() ?: throw SyncException(SyncError.Auth)
                            if (workspace.billingState != WorkspaceBillingState.Active) {
                                throw SyncException(SyncError.EntitlementRequired)
                            }
                            // A freshly authenticated, server-verified active membership is required before
                            // LocalOnly may lift. An Unknown role (absent/unverifiable self-membership) must
                            // never push/pull or clear LocalOnly/outbox — fail closed and keep accumulating.
                            if (activeWorkspaceOwnershipResult().getOrThrow().role == SharedWorkspaceRole.Unknown) {
                                throw SyncException(SyncError.Auth)
                            }
                            val accessContext = sharedStore.workspaceAccessContext()
                            sharedStore.setWorkspaceAccessContext(
                                billingState = workspace.billingState.name,
                                isWorkspaceOwner = accessContext?.isWorkspaceOwner,
                            )
                            val shouldEnablePeriodicSync = appSettings.settings.first().autoSyncEnabled
                            syncNowLocked(allowLocalOnly = true).getOrThrow()
                            currentCoroutineContext().ensureActive()
                            withContext(NonCancellable) {
                                if (shouldEnablePeriodicSync) {
                                    syncScheduler.enablePeriodicSync()
                                }
                                sharedStore.clearLocalOnly()
                            }
                            Result.success(Unit)
                        } catch (failure: Throwable) {
                            if (failure is CancellationException) throw failure
                            Result.failure(failure)
                        }
                    }
                if (reattachResult.isFailure) reattachResult else startForegroundRealtime()
            }

        override suspend fun startForegroundRealtime(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    if (sharedStore.localOnlyState() != null) {
                        return@withLock Result.failure(SyncException(SyncError.EntitlementRequired))
                    }
                    val (generation, previousJob) = beginForegroundRealtime()
                    try {
                        previousJob?.cancelAndJoin()
                        val workspaceId = requireActiveWorkspaceId()
                        if (!sharedStore.isMembershipActive()) throw SyncException(SyncError.Auth)
                        if (!updateForegroundRealtimeStatus(generation, SharedRealtimeStatus.Starting)) {
                            return@withLock Result.success(Unit)
                        }
                        val realtimeJob =
                            foregroundRealtimeScope.launch(start = CoroutineStart.LAZY) {
                                superviseForegroundRealtime(workspaceId, generation)
                            }
                        if (!installForegroundRealtimeJob(generation, realtimeJob)) {
                            realtimeJob.cancel()
                            return@withLock Result.success(Unit)
                        }
                        realtimeJob.start()
                        Result.success(Unit)
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        val result = Result.failure<Unit>(failure)
                        if (result.isAuthFailure()) {
                            clearSharedStateOnAuthFailure(result)
                        } else {
                            updateForegroundRealtimeStatus(generation, SharedRealtimeStatus.Error)
                            result
                        }
                    }
                }
            }

        override fun stopForegroundRealtime() {
            invalidateForegroundRealtime()?.cancel()
        }

        override suspend fun listConflicts(): Result<List<SharedConflict>> =
            withContext(dispatcher) {
                val result = runCatching { journalRepository.listPendingConflicts(requireActiveWorkspaceId()).getOrThrow() }
                if (result.isAuthFailure()) {
                    executionGate.withExclusive {
                        operationMutex.withLock { clearSharedStateOnAuthFailure(result) }
                    }
                } else {
                    result
                }
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
                try {
                    executionGate.withExclusive {
                        operationMutex.withLock {
                            ensureInternalBackupRestoreAllowed()
                            detachForLocalRestoreLocked()
                            backupRepository.importFromFile(backupPath).getOrThrow()
                        }
                    }
                    Result.success(Unit)
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    Result.failure(failure)
                }
            }

        override suspend fun leaveWorkspace(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    runCatching {
                        val workspaceId = requireActiveWorkspaceId()
                        val backup = backupRepository.createInternalBackup()
                        val serverLeave = runCatching { workspaceApi.leaveWorkspace(workspaceId).getOrThrow() }
                        completeRemoteWorkspaceExit(backup, serverLeave)
                    }
                }
            }

        override suspend fun activeWorkspaceOwnership(): Result<SharedWorkspaceOwnership> =
            withContext(dispatcher) {
                val result = activeWorkspaceOwnershipResult()
                result.getOrNull()?.let { ownership ->
                    val accessContext = sharedStore.workspaceAccessContext()
                    sharedStore.setWorkspaceAccessContext(
                        billingState = accessContext?.billingState,
                        isWorkspaceOwner = ownership.isOwner,
                    )
                }
                if (result.isAuthFailure()) {
                    executionGate.withExclusive {
                        operationMutex.withLock { clearSharedStateOnAuthFailure(result) }
                    }
                } else {
                    result
                }
            }

        override suspend fun activeWorkspaceAccess(): Result<SharedWorkspaceAccess> =
            withContext(dispatcher) {
                val result =
                    try {
                        currentLocalOnlyState()?.let { localOnly ->
                            return@withContext Result.success(
                                SharedWorkspaceAccess(
                                    billingState =
                                        localOnly.workspaceBillingState
                                            ?: SharedWorkspaceBillingState.Expired,
                                    localOnly = localOnly,
                                ),
                            )
                        }
                        ensureSignedIn()
                        if (!sharedStore.isMembershipActive()) throw SyncException(SyncError.Auth)
                        val workspace = workspaceApi.currentWorkspace().getOrThrow() ?: throw SyncException(SyncError.Auth)
                        val accessContext = sharedStore.workspaceAccessContext()
                        sharedStore.setWorkspaceAccessContext(
                            billingState = workspace.billingState.name,
                            isWorkspaceOwner = accessContext?.isWorkspaceOwner,
                        )
                        Result.success(
                            SharedWorkspaceAccess(
                                billingState = workspace.billingState.toSharedWorkspaceBillingState(),
                                billingStateUntil = workspace.billingStateUntil,
                            ),
                        )
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        Result.failure(failure)
                    }
                if (result.isAuthFailure()) {
                    executionGate.withExclusive {
                        operationMutex.withLock { clearSharedStateOnAuthFailure(result) }
                    }
                } else {
                    result
                }
            }

        override suspend fun currentLocalOnlyState(): LocalOnlyState? =
            withContext(dispatcher) {
                sharedStore.localOnlyState()?.toLocalOnlyState()
            }

        override fun consumeRestartRequiredAfterAdoptionRecovery(): Boolean =
            restartRequiredAfterAdoptionRecovery.getAndSet(false)

        override suspend fun deleteWorkspace(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    val result =
                        runCatching {
                            val workspaceId = requireActiveWorkspaceId()
                            val ownership = activeWorkspaceOwnershipResult().getOrThrow()
                            if (!ownership.isSoleOwner) throw SyncException(SyncError.Conflict)
                            val backup = backupRepository.createInternalBackup()
                            val serverDelete = runCatching { workspaceApi.deleteWorkspace(workspaceId).getOrThrow() }
                            completeRemoteWorkspaceExit(backup, serverDelete)
                        }
                    result.exceptionOrNull()?.let { failure ->
                        if (failure is CancellationException) throw failure
                    }
                    clearSharedStateOnAuthFailure(
                        result,
                    )
                }
            }

        override suspend fun deleteAccount(): Result<Unit> =
            withContext(dispatcher) {
                operationMutex.withLock {
                    runCatching {
                        ensureSignedIn()
                        workspaceApi.deleteAccount().getOrThrow()
                        clearDeletedAccountLocalState()
                    }
                }
            }

        private suspend fun adoptWorkspace(
            workspace: SharedWorkspace,
            importLocalData: Boolean,
        ) {
            sharedStore.clear()
            clearSharedOutbox()
            if (importLocalData) {
                // Enqueue snapshots before pulling remote state. The durable outbox preserves the
                // user's original payloads even if a remote operation subsequently changes a row.
                enqueueLocalChanges(workspace.id)
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
            if (appSettings.settings.first().autoSyncEnabled) {
                syncScheduler.enablePeriodicSync()
            }
        }

        private suspend fun adoptWorkspaceWithRecovery(
            workspace: SharedWorkspace,
            importLocalData: Boolean,
            backupPath: String,
            remoteCompensation: suspend () -> Unit,
        ) {
            try {
                adoptWorkspace(workspace, importLocalData)
                if (importLocalData) {
                    currentCoroutineContext().ensureActive()
                    // The local binding is committed before delivery. Shielding publication prevents a
                    // cancellation from leaving a partial join import that a remote leave cannot retract.
                    withContext(NonCancellable) {
                        try {
                            publishPendingOperations(workspace.id)
                        } catch (failure: Throwable) {
                            if (failure is CancellationException) throw failure
                            failure.reportToSentry()
                            throw failure
                        }
                    }
                    currentCoroutineContext().ensureActive()
                }
            } catch (failure: Throwable) {
                recoverFailedAdoption(backupPath, remoteCompensation, failure)
                throw failure
            }
        }

        private suspend fun recoverFailedAdoption(
            backupPath: String,
            remoteCompensation: suspend () -> Unit,
            failure: Throwable,
        ) =
            withContext(NonCancellable) {
                val recoveryFailures = mutableListOf<Throwable>()
                collectRecoveryFailure(recoveryFailures) { remoteCompensation() }
                collectRecoveryFailure(recoveryFailures) { stopForegroundRealtimeAndJoin() }
                collectRecoveryFailure(recoveryFailures) { syncScheduler.disablePeriodicSync() }
                collectRecoveryFailure(recoveryFailures) { configStore.clearBinding() }
                collectRecoveryFailure(recoveryFailures) { sharedStore.clear() }
                collectRecoveryFailure(recoveryFailures) { clearSharedOutbox() }
                restartRequiredAfterAdoptionRecovery.set(true)
                collectRecoveryFailure(recoveryFailures) {
                    backupRepository.importFromFile(backupPath).getOrThrow()
                }
                recoveryFailures.forEach { recoveryFailure ->
                    recoveryFailure.reportToSentry()
                    failure.addSuppressed(recoveryFailure)
                }
            }

        private suspend fun collectRecoveryFailure(
            failures: MutableList<Throwable>,
            recovery: suspend () -> Unit,
        ) {
            try {
                recovery()
            } catch (failure: Throwable) {
                failures += failure
            }
        }

        private suspend fun detachForLocalRestoreLocked() {
            val cleanupFailures = mutableListOf<Throwable>()
            withContext(NonCancellable) {
                collectCleanupFailure(cleanupFailures) { stopForegroundRealtimeAndJoin() }
                collectCleanupFailure(cleanupFailures) { syncScheduler.cancelAllSync().getOrThrow() }
                collectCleanupFailure(cleanupFailures) { configStore.clearBinding() }
                collectCleanupFailure(cleanupFailures) { sharedStore.clear() }
                collectCleanupFailure(cleanupFailures) { clearSharedOutbox() }
                collectCleanupFailure(cleanupFailures) { auth.clearLocalSession() }
            }
            currentCoroutineContext().ensureActive()
            throwCleanupFailures(cleanupFailures)
        }

        private suspend fun syncNowLocked(allowLocalOnly: Boolean = false): Result<Unit> =
            runCatching {
                if (!allowLocalOnly) ensureNotInLocalOnlyMode()
                val workspaceId = requireActiveWorkspaceId()
                if (!sharedStore.isMembershipActive()) {
                    throw SyncException(SyncError.Auth)
                }
                enqueueLocalChanges(workspaceId)
                publishPendingOperations(workspaceId)
                pullAndApply(workspaceId)
                appSettings.update { it.copy(lastSyncAt = clock.millis()) }
            }

        private suspend fun disconnectFromDeviceLocked(): Result<Unit> =
            withContext(NonCancellable) {
                val cleanupFailures = mutableListOf<Throwable>()
                collectCleanupFailure(cleanupFailures) { stopForegroundRealtimeAndJoin() }
                collectCleanupFailure(cleanupFailures) { syncScheduler.disablePeriodicSync() }
                collectCleanupFailure(cleanupFailures) { configStore.clearBinding() }
                collectCleanupFailure(cleanupFailures) { sharedStore.clear() }
                collectCleanupFailure(cleanupFailures) { clearSharedOutbox() }
                collectCleanupFailure(cleanupFailures) { auth.signOut().getOrThrow() }
                collectCleanupFailure(cleanupFailures) { auth.clearLocalSession() }
                cleanupFailures.toResult()
            }

        private suspend fun ensureInternalBackupRestoreAllowed() {
            val binding = configStore.binding()
            if (binding != null && binding.provider != CloudProvider.Shared) {
                throw SyncException(SyncError.Conflict)
            }
        }

        private suspend fun pullAndApply(workspaceId: String) {
            var durableAfter = sharedStore.cursor()
            var fetchAfter = durableAfter
            val fetchedOperations = mutableListOf<SharedOperation>()
            val deferredOperations = mutableListOf<SharedOperation>()
            val completedSequences = mutableSetOf<Long>()
            var nextCursorOperationIndex = 0
            while (true) {
                val operations =
                    journalRepository
                        .pull(workspaceId, fetchAfter, PAGE_SIZE)
                        .getOrThrow()
                        .sortedBy { it.serverSequence }
                if (operations.isEmpty()) break

                val nextFetchAfter = operations.last().serverSequence
                if (nextFetchAfter <= fetchAfter) throw SyncException(SyncError.Conflict)

                for (operation in operations) {
                    fetchedOperations += operation
                    when (applyPulledOperation(operation)) {
                        PullOperationResult.Completed -> completedSequences += operation.serverSequence
                        PullOperationResult.Deferred -> deferredOperations += operation
                    }
                }

                var retryMadeProgress = true
                while (deferredOperations.isNotEmpty() && retryMadeProgress) {
                    retryMadeProgress = false
                    val iterator = deferredOperations.iterator()
                    while (iterator.hasNext()) {
                        val operation = iterator.next()
                        when (applyPulledOperation(operation)) {
                            PullOperationResult.Completed -> {
                                completedSequences += operation.serverSequence
                                iterator.remove()
                                retryMadeProgress = true
                            }

                            PullOperationResult.Deferred -> Unit
                        }
                    }
                }

                val cursorBeforeCheckpoint = durableAfter
                while (nextCursorOperationIndex < fetchedOperations.size) {
                    val operation = fetchedOperations[nextCursorOperationIndex]
                    if (operation.serverSequence !in completedSequences) break
                    durableAfter = operation.serverSequence
                    nextCursorOperationIndex += 1
                }
                if (durableAfter > cursorBeforeCheckpoint) {
                    sharedStore.setCursor(durableAfter)
                }

                if (operations.size < PAGE_SIZE) break
                fetchAfter = nextFetchAfter
            }

            if (deferredOperations.isNotEmpty()) throw SyncException(SyncError.Conflict)
        }

        private suspend fun superviseForegroundRealtime(
            workspaceId: String,
            generation: Long,
        ) {
            var retryAttempt = 0
            while (currentCoroutineContext().isActive) {
                if (
                    !updateForegroundRealtimeStatus(
                        generation,
                        if (retryAttempt == 0) {
                            SharedRealtimeStatus.Starting
                        } else {
                            SharedRealtimeStatus.Retrying(retryAttempt)
                        },
                    )
                ) {
                    return
                }
                try {
                    syncNow().getOrThrow()
                    val accessToken = auth.accessToken().getOrThrow()
                    realtime.events(workspaceId, accessToken).collect { event ->
                        if (!isForegroundRealtimeCurrent(generation)) throw CancellationException()
                        when (event) {
                            SharedRealtimeEvent.Connected -> {
                                retryAttempt = 0
                                if (!updateForegroundRealtimeStatus(generation, SharedRealtimeStatus.Connected)) {
                                    throw CancellationException()
                                }
                            }

                            SharedRealtimeEvent.OperationAvailable -> syncNow().getOrThrow()
                            is SharedRealtimeEvent.Disconnected -> throw event.cause
                        }
                    }
                    throw SharedRealtimeStreamFinishedException()
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    if (failure.isEntitlementRequiredFailure()) {
                        updateForegroundRealtimeStatus(generation, SharedRealtimeStatus.EntitlementRequired)
                        return
                    }
                    if (failure.isRealtimeTerminalFailure()) {
                        // Serialize the terminal-failure cleanup under operationMutex so the LocalOnly read
                        // inside clearSharedStateOnAuthFailure and the destructive-vs-non-destructive branch
                        // are atomic against a concurrent detachToLocalOnly() DataStore commit. Every other
                        // caller already holds this lock; without it a real IoDispatcher thread could read
                        // localOnly==null, then have the commit land, then still wipe binding/cursor/outbox.
                        operationMutex.withLock {
                            clearSharedStateOnAuthFailure(Result.failure<Unit>(failure))
                        }
                        if ((failure as? SyncException)?.syncError != SyncError.Auth) {
                            updateForegroundRealtimeStatus(generation, SharedRealtimeStatus.Error)
                        }
                        return
                    }
                    retryAttempt += 1
                    if (retryAttempt > FOREGROUND_REALTIME_MAX_RETRIES) {
                        updateForegroundRealtimeStatus(generation, SharedRealtimeStatus.Error)
                        return
                    }
                    if (
                        !updateForegroundRealtimeStatus(
                            generation,
                            if (failure.isServerFailure()) {
                                SharedRealtimeStatus.Sleeping(retryAttempt)
                            } else {
                                SharedRealtimeStatus.Retrying(retryAttempt)
                            },
                        )
                    ) {
                        return
                    }
                    delay(foregroundRealtimeBackoffMillis(retryAttempt))
                }
            }
        }

        private suspend fun applyPulledOperation(operation: SharedOperation): PullOperationResult =
            try {
                database.withTransaction {
                    applyOperation(operation)
                    if (operation.tombstone || operation.payload != null) {
                        database.sharedOutboxDao().upsertState(operation.toState())
                    }
                }
                PullOperationResult.Completed
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                t.reportToSentry()
                if (t is SharedIntegrityException) PullOperationResult.Deferred else throw t
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
                            val currencyReference =
                                decodeCurrencyReference {
                                    codec.decodeTransactionCurrencyReference(payload)
                                }
                            val decoded = codec.decodeTransaction(payload)
                            val refs = codec.decodeTransactionRefs(payload)
                            // Remap portable references to LOCAL ids. A null lookup (referenced
                            // currency/account/category not present yet) throws, so the per-operation
                            // catch logs and skips this op; the missing row arrives on a later pull.
                            val currencyId =
                                materializeCurrency(currencyReference.code, currencyReference.currency).id
                            val accountId =
                                accountRepository.idForUuid(refs.accountUuid)
                                    ?: throw SharedForeignKeyIntegrityException(
                                        "shared transaction references unknown account ${refs.accountUuid}",
                                    )
                            val categoryId =
                                refs.categoryUuid?.let {
                                    categoryRepository.idForUuid(it)
                                        ?: throw SharedForeignKeyIntegrityException(
                                            "shared transaction references unknown category $it",
                                        )
                                }
                            val toAccountId =
                                refs.toAccountUuid?.let {
                                    accountRepository.idForUuid(it)
                                        ?: throw SharedForeignKeyIntegrityException(
                                            "shared transaction references unknown account $it",
                                        )
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
                            val currencyReference =
                                decodeCurrencyReference {
                                    codec.decodeAccountCurrencyReference(payload)
                                }
                            val decoded = codec.decodeAccount(payload)
                            val currencyId =
                                materializeCurrency(currencyReference.code, currencyReference.currency).id
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
                    val currency = currencyRepository.findById(account.currencyId)
                    if (uuid != null && currency != null) {
                        add(
                            LocalSnapshot(
                                entityKind = EntityKind.Account,
                                entityId = uuid,
                                payload = codec.encodeAccount(account, uuid, currency),
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
                    val currency = currencyRepository.findById(transaction.currencyId) ?: return@forEach
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
                                    currency,
                                    accountUuid,
                                    categoryUuid,
                                    toAccountUuid,
                                ),
                            tombstone = false,
                        ),
                    )
                }
            }

        private suspend fun materializeCurrency(
            code: String,
            payloadCurrency: Currency?,
        ): Currency =
            try {
                require(code.matches(CURRENCY_CODE_REGEX)) { "shared currency code is invalid" }
                payloadCurrency?.let { currency ->
                    require(currency.code == code) { "shared currency code does not match its reference" }
                    require(currency.symbol.isNotBlank() && currency.symbol.length <= 4) {
                        "shared currency symbol is invalid"
                    }
                    require(currency.name.isNotBlank()) { "shared currency name is invalid" }
                    require(currency.decimalDigits in 0..8) { "shared currency decimal digits are invalid" }
                }
                val existing = currencyRepository.findByCode(code)
                if (payloadCurrency == null) {
                    existing
                        ?: throw SharedCurrencyIntegrityException(
                            "shared operation references currency $code without canonical currency data",
                        )
                } else {
                    currencyRepository.upsert(payloadCurrency.copy(id = existing?.id ?: 0L))
                    checkNotNull(currencyRepository.findByCode(code)) {
                        "shared currency $code was not persisted"
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (t is SharedCurrencyIntegrityException) throw t
                throw SharedCurrencyIntegrityException("shared currency $code could not be materialized", t)
            }

        private fun decodeCurrencyReference(
            block: () -> SharedEntityCodec.CurrencyReference,
        ): SharedEntityCodec.CurrencyReference =
            try {
                block()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (t is SharedCurrencyIntegrityException) throw t
                throw SharedCurrencyIntegrityException("shared currency payload is invalid", t)
            }

        private suspend fun clearSharedOutbox() {
            database.withTransaction {
                database.sharedOutboxDao().clearPending()
                database.sharedOutboxDao().clearStates()
            }
        }

        private suspend fun completeRemoteWorkspaceExit(
            backup: Result<String>,
            remoteExit: Result<Unit>,
        ) {
            val remoteError = remoteExit.exceptionOrNull()
            val localCleanupFailures = revokeSharedLocalAccess()
            localCleanupFailures.forEach(Throwable::reportToSentry)
            if (remoteError != null) {
                remoteError.reportToSentry()
                localCleanupFailures.forEach(remoteError::addSuppressed)
                backup.exceptionOrNull()?.let(remoteError::addSuppressed)
                throw remoteError
            }
            backup.exceptionOrNull()?.let { backupFailure ->
                localCleanupFailures.forEach(backupFailure::addSuppressed)
                throw backupFailure
            }
            throwCleanupFailures(localCleanupFailures)
        }

        private suspend fun revokeSharedLocalAccess(): List<Throwable> =
            revokeSharedLocalAccess(currentCoroutineContext()[Job])

        private suspend fun clearDeletedAccountLocalState() {
            val failures =
                withContext(NonCancellable) {
                    val cleanupFailures = mutableListOf<Throwable>()
                    collectCleanupFailure(cleanupFailures) { stopForegroundRealtimeAndJoin() }
                    if (configStore.binding()?.provider == CloudProvider.Shared) {
                        collectSharedLocalCleanupFailures(cleanupFailures)
                    }
                    collectCleanupFailure(cleanupFailures) { auth.clearLocalSession() }
                    cleanupFailures
                }
            failures.forEach(Throwable::reportToSentry)
            throwCleanupFailures(failures)
        }

        private suspend fun revokeSharedLocalAccess(currentJob: Job?): List<Throwable> =
            withContext(NonCancellable) {
                val failures = mutableListOf<Throwable>()
                collectCleanupFailure(failures) { stopForegroundRealtimeAndJoin(currentJob) }
                collectSharedLocalCleanupFailures(failures)
                collectCleanupFailure(failures) { auth.signOut().getOrThrow() }
                collectCleanupFailure(failures) { auth.clearLocalSession() }
                failures
            }

        private fun beginForegroundRealtime(): Pair<Long, Job?> =
            synchronized(foregroundRealtimeLock) {
                foregroundRealtimeGeneration += 1
                foregroundRealtimeGeneration to foregroundRealtimeJob.also { foregroundRealtimeJob = null }
            }

        private fun installForegroundRealtimeJob(
            generation: Long,
            job: Job,
        ): Boolean =
            synchronized(foregroundRealtimeLock) {
                if (foregroundRealtimeGeneration != generation) {
                    false
                } else {
                    foregroundRealtimeJob = job
                    true
                }
            }

        private fun invalidateForegroundRealtime(): Job? =
            synchronized(foregroundRealtimeLock) {
                foregroundRealtimeGeneration += 1
                _foregroundRealtimeStatus.value = SharedRealtimeStatus.Inactive
                foregroundRealtimeJob.also { foregroundRealtimeJob = null }
            }

        private suspend fun stopForegroundRealtimeAndJoin() {
            stopForegroundRealtimeAndJoin(currentCoroutineContext()[Job])
        }

        private suspend fun stopForegroundRealtimeAndJoin(currentJob: Job?) {
            val realtimeJob = invalidateForegroundRealtime() ?: return
            realtimeJob.cancel()
            if (realtimeJob != currentJob) realtimeJob.join()
        }

        private fun isForegroundRealtimeCurrent(generation: Long): Boolean =
            synchronized(foregroundRealtimeLock) { foregroundRealtimeGeneration == generation }

        private fun updateForegroundRealtimeStatus(
            generation: Long,
            status: SharedRealtimeStatus,
        ): Boolean =
            synchronized(foregroundRealtimeLock) {
                if (foregroundRealtimeGeneration != generation) {
                    false
                } else {
                    _foregroundRealtimeStatus.value = status
                    true
                }
            }

        private fun List<Throwable>.toResult(): Result<Unit> {
            val primaryFailure = firstOrNull() ?: return Result.success(Unit)
            drop(1).forEach(primaryFailure::addSuppressed)
            return Result.failure(primaryFailure)
        }

        private suspend fun collectSharedLocalCleanupFailures(failures: MutableList<Throwable>) {
            collectCleanupFailure(failures) { syncScheduler.disablePeriodicSync() }
            collectCleanupFailure(failures) { configStore.clearBinding() }
            collectCleanupFailure(failures) { sharedStore.clear() }
            collectCleanupFailure(failures) { clearSharedOutbox() }
        }

        private suspend fun collectCleanupFailure(
            failures: MutableList<Throwable>,
            cleanup: suspend () -> Unit,
        ) {
            try {
                cleanup()
            } catch (failure: Throwable) {
                failures += failure
            }
        }

        private fun throwCleanupFailures(failures: List<Throwable>) {
            val primaryFailure = failures.firstOrNull() ?: return
            failures.drop(1).forEach(primaryFailure::addSuppressed)
            throw primaryFailure
        }

        private enum class PullOperationResult {
            Completed,
            Deferred,
        }

        private data class LocalSnapshot(
            val entityKind: EntityKind,
            val entityId: String,
            val payload: String?,
            val tombstone: Boolean,
        )

        private open class SharedIntegrityException(
            message: String,
            cause: Throwable? = null,
        ) : IllegalStateException(message, cause)

        private class SharedCurrencyIntegrityException(
            message: String,
            cause: Throwable? = null,
        ) : SharedIntegrityException(message, cause)

        private class SharedForeignKeyIntegrityException(
            message: String,
        ) : SharedIntegrityException(message)

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

        private suspend fun ensureNotInLocalOnlyMode() {
            if (sharedStore.localOnlyState() != null) throw SyncException(SyncError.EntitlementRequired)
        }

        private suspend fun ensureNoActiveBinding() {
            if (configStore.binding() != null) throw SyncException(SyncError.Conflict)
        }

        private suspend fun activeWorkspaceOwnershipResult(): Result<SharedWorkspaceOwnership> =
            try {
                val workspaceId = requireActiveWorkspaceId()
                val userId = auth.currentSession()?.user?.id ?: throw SyncException(SyncError.Auth)
                val members = workspaceApi.listMembers(workspaceId).getOrThrow().filter { it.active }
                val ownMembership = members.firstOrNull { it.userId == userId }
                val role =
                    when {
                        ownMembership == null -> SharedWorkspaceRole.Unknown
                        ownMembership.role == WorkspaceRole.Owner -> SharedWorkspaceRole.VerifiedOwner
                        else -> SharedWorkspaceRole.VerifiedParticipant
                    }
                val isOwner = role == SharedWorkspaceRole.VerifiedOwner
                Result.success(
                    SharedWorkspaceOwnership(
                        isOwner = isOwner,
                        isSoleOwner = isOwner && members.size == 1,
                        role = role,
                    ),
                )
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                Result.failure(failure)
            }

        private suspend fun requireActiveWorkspaceId(): String =
            configStore
                .binding()
                ?.takeIf { it.provider == CloudProvider.Shared }
                ?.stableAccountId
                ?: throw SyncException(SyncError.Conflict)

        private fun Result<*>.isAuthFailure(): Boolean =
            (exceptionOrNull() as? SyncException)?.syncError == SyncError.Auth

        private fun Throwable.isServerFailure(): Boolean =
            (this as? SyncException)?.syncError == SyncError.Server

        private fun Throwable.isRealtimeTerminalFailure(): Boolean =
            (this as? SyncException)?.syncError in setOf(SyncError.Auth, SyncError.Conflict)

        private fun Throwable.isEntitlementRequiredFailure(): Boolean =
            (this as? SyncException)?.syncError == SyncError.EntitlementRequired

        private fun SharedLocalOnlyState.toLocalOnlyState(): LocalOnlyState =
            LocalOnlyState(
                reason = LocalOnlyReason.entries.firstOrNull { it.name == reason } ?: LocalOnlyReason.RemoteKillswitch,
                since = java.time.Instant.ofEpochMilli(sinceEpochMs),
                workspaceBillingState =
                    workspaceBillingState
                        ?.let { value -> SharedWorkspaceBillingState.entries.firstOrNull { it.name == value } },
                isWorkspaceOwner = isWorkspaceOwner,
            )

        private fun foregroundRealtimeBackoffMillis(retryAttempt: Int): Long =
            FOREGROUND_REALTIME_BASE_BACKOFF_MILLIS shl (retryAttempt - 1)

        private suspend fun <T> clearSharedStateOnAuthFailure(result: Result<T>): Result<T> {
            if (result.isAuthFailure()) {
                val cleanupFailures =
                    if (sharedStore.localOnlyState() != null) {
                        // Fail-closed while LocalOnly: an auth failure or an unverifiable role must NEVER
                        // destroy the workspace binding, the sync cursor, or the accumulated outbox — those
                        // are exactly the data LocalOnly exists to protect until the subscription returns.
                        // Only stop transport/jobs and invalidate the local auth token.
                        stopTransportOnAuthFailureUnderLocalOnly()
                    } else {
                        revokeSharedLocalAccess()
                    }
                cleanupFailures.forEach(Throwable::reportToSentry)
                result.exceptionOrNull()?.let { authFailure ->
                    cleanupFailures.forEach(authFailure::addSuppressed)
                }
            }
            return result
        }

        private suspend fun stopTransportOnAuthFailureUnderLocalOnly(): List<Throwable> {
            val currentJob = currentCoroutineContext()[Job]
            return withContext(NonCancellable) {
                val failures = mutableListOf<Throwable>()
                collectCleanupFailure(failures) { stopForegroundRealtimeAndJoin(currentJob) }
                collectCleanupFailure(failures) { syncScheduler.cancelAllSync().getOrThrow() }
                collectCleanupFailure(failures) { auth.clearLocalSession() }
                failures
            }
        }

        private companion object {
            const val PAGE_SIZE = 100
            const val FOREGROUND_REALTIME_MAX_RETRIES = 5
            const val FOREGROUND_REALTIME_BASE_BACKOFF_MILLIS = 1_000L
            val CURRENCY_CODE_REGEX = Regex("^[A-Z]{3}$")
        }
    }

private class SharedRealtimeStreamFinishedException : IllegalStateException()

private fun WorkspaceBillingState.toSharedWorkspaceBillingState(): SharedWorkspaceBillingState =
    when (this) {
        WorkspaceBillingState.Active -> SharedWorkspaceBillingState.Active
        WorkspaceBillingState.Grace -> SharedWorkspaceBillingState.Grace
        WorkspaceBillingState.Expired -> SharedWorkspaceBillingState.Expired
    }

private object NoOpSupporterSync : SupporterSync {
    override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> = Result.success(Unit)

    override suspend fun syncPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> = Result.success(Unit)

    override suspend fun restore(): Result<Unit> = Result.success(Unit)
}
