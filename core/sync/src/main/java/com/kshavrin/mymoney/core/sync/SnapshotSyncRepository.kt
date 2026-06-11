package com.kshavrin.mymoney.core.sync

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.domain.model.SyncLogEntry
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.SyncLogRepository
import com.kshavrin.mymoney.core.sync.dropbox.snapshotFileName
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnapshotSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backends: Set<@JvmSuppressWildcards CloudSyncBackend>,
    private val backup: BackupRepository,
    private val syncLog: SyncLogRepository,
    private val settings: AppSettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SnapshotSync {

    private fun backend(target: SyncTarget): CloudSyncBackend =
        backends.first { it.target == target }

    override fun isConnected(target: SyncTarget): Boolean = backend(target).isConnected()

    override fun connectedTargets(): List<SyncTarget> =
        backends.filter { it.isConnected() }.map { it.target }

    override fun connect(target: SyncTarget, payload: String) = backend(target).connect(payload)

    override fun disconnect(target: SyncTarget) = backend(target).disconnect()

    override suspend fun accountLabel(target: SyncTarget): Result<String> =
        backend(target).accountLabel()

    override suspend fun syncNow(target: SyncTarget): Result<SyncOutcome> = withContext(ioDispatcher) {
        breadcrumb("syncNow target=$target")
        runCatching {
            val backend = backend(target)
            val remoteNewest = backend.listSnapshots().orThrow().firstOrNull()
            val localLastSync = lastSyncMillis()
            if (remoteNewest != null && remoteNewest.modifiedAtEpochMs > localLastSync + CONFLICT_WINDOW_MS) {
                return@runCatching SyncOutcome.ConflictDetected(
                    remoteModifiedMs = remoteNewest.modifiedAtEpochMs,
                    localLastSyncMs = localLastSync,
                )
            }
            doPush(target)
        }.recoverFailure(target, EVENT_PUSH)
    }

    override suspend fun push(target: SyncTarget): Result<SyncOutcome> = withContext(ioDispatcher) {
        breadcrumb("push target=$target")
        runCatching { doPush(target) }.recoverFailure(target, EVENT_PUSH)
    }

    override suspend fun keepLocal(target: SyncTarget): Result<SyncOutcome> = push(target)

    override suspend fun keepRemote(target: SyncTarget): Result<SyncOutcome> = withContext(ioDispatcher) {
        breadcrumb("keepRemote target=$target")
        runCatching {
            val backend = backend(target)
            val safety = cacheFile("monefy_safety_${Instant.now().toEpochMilli()}.db")
            backup.exportToFile(safety.absolutePath)

            val temp = cacheFile("monefy_pull_${Instant.now().toEpochMilli()}.db")
            try {
                val remote = backend.downloadNewest(temp).orThrow()
                    ?: return@runCatching SyncOutcome.UpToDate
                backup.importFromFile(temp.absolutePath).orThrow()
                settings.update { it.copy(lastSyncAt = remote.modifiedAtEpochMs) }
                writeLog(target, EVENT_PULL, STATUS_SUCCESS)
                SyncOutcome.Pulled
            } finally {
                temp.delete()
                safety.delete()
            }
        }.recoverFailure(target, EVENT_PULL)
    }

    override suspend fun autoSyncConnected(): Result<Unit> = withContext(ioDispatcher) {
        var retryable: SyncException? = null
        for (target in connectedTargets()) {
            push(target).onFailure { t ->
                if (retryable == null && t is SyncException && t.syncError.isRetryable()) {
                    retryable = t
                }
            }
        }
        retryable?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private suspend fun doPush(target: SyncTarget): SyncOutcome {
        val backend = backend(target)
        val now = Instant.now()
        val temp = cacheFile("monefy_sync_${now.toEpochMilli()}.db")
        try {
            backup.exportToFile(temp.absolutePath).orThrow()
            backend.upload(temp, snapshotFileName(now)).orThrow()
            backend.prune(KEEP_SNAPSHOTS).orThrow()
            settings.update { it.copy(lastSyncAt = now.toEpochMilli()) }
            writeLog(target, EVENT_PUSH, STATUS_SUCCESS)
            return SyncOutcome.Pushed
        } finally {
            temp.delete()
        }
    }

    private suspend fun lastSyncMillis(): Long = settings.settings.first().lastSyncAt ?: 0L

    private fun cacheFile(name: String): File = File(context.cacheDir, name)

    private suspend fun <T> Result<T>.recoverFailure(
        target: SyncTarget,
        event: String,
    ): Result<T> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { cause ->
            if (cause is CancellationException) throw cause
            val error = (cause as? SyncException)?.syncError ?: SyncError.Unknown
            try {
                writeLog(target, event, STATUS_FAILURE, errorMessage = error.name)
                sentryLevelFor(error)?.let { level ->
                    Sentry.withScope { scope ->
                        scope.level = level
                        Sentry.captureException(SyncException(error))
                    }
                }
                Result.failure(SyncException(error))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Result.failure(t)
            }
        },
    )

    private suspend fun writeLog(
        target: SyncTarget,
        event: String,
        status: String,
        errorMessage: String? = null,
    ): Long = syncLog.insert(
        SyncLogEntry(
            id = 0,
            target = target.name,
            event = event,
            entityKind = null,
            entityId = null,
            performedAt = Instant.now(),
            status = status,
            payloadHash = null,
            errorMessage = errorMessage,
        ),
    ).also { syncLog.pruneOld(target.name) }

    private fun breadcrumb(message: String) {
        Sentry.addBreadcrumb(Breadcrumb().apply {
            category = "sync"
            this.message = message
        })
    }

    private companion object {
        const val CONFLICT_WINDOW_MS = 60_000L
        const val KEEP_SNAPSHOTS = 3
        const val EVENT_PUSH = "push"
        const val EVENT_PULL = "pull"
        const val STATUS_SUCCESS = "success"
        const val STATUS_FAILURE = "failure"
    }
}

internal fun sentryLevelFor(error: SyncError): SentryLevel? = when (error) {
    SyncError.Auth -> SentryLevel.WARNING
    SyncError.Quota -> SentryLevel.WARNING
    SyncError.Server -> SentryLevel.ERROR
    SyncError.Conflict -> SentryLevel.INFO
    SyncError.Network -> null
    SyncError.Unknown -> SentryLevel.ERROR
}

private fun SyncError.isRetryable(): Boolean =
    this == SyncError.Network || this == SyncError.Server

private fun <T> Result<T>.orThrow(): T = getOrElse { cause ->
    if (cause is CancellationException) throw cause
    throw if (cause is SyncException) cause else SyncException(SyncError.Unknown)
}
