package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import android.content.ContextWrapper
import androidx.paging.PagingData
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.ProgressUpdater
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SerialExecutorImpl
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.impl.utils.taskexecutor.SerialExecutor
import androidx.work.impl.utils.taskexecutor.TaskExecutor
import com.google.common.util.concurrent.ListenableFuture
import com.kshavrin.mymoney.core.common.exception.SyncError
import com.kshavrin.mymoney.core.common.exception.SyncException
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.model.BackupFile
import com.kshavrin.mymoney.core.domain.model.Period
import com.kshavrin.mymoney.core.domain.model.RecurringTemplate
import com.kshavrin.mymoney.core.domain.model.Transaction
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.domain.repository.BackupRepository
import com.kshavrin.mymoney.core.domain.repository.CategoryGroup
import com.kshavrin.mymoney.core.domain.repository.CategorySummary
import com.kshavrin.mymoney.core.domain.repository.RecurringTemplateRepository
import com.kshavrin.mymoney.core.domain.repository.TransactionRepository
import com.kshavrin.mymoney.core.domain.transaction.TransactionRunner
import com.kshavrin.mymoney.core.domain.usecase.GenerateDueRecurringUseCase
import com.kshavrin.mymoney.core.domain.usecase.RecurringScheduler
import com.kshavrin.mymoney.core.sync.SnapshotSync
import com.kshavrin.mymoney.core.sync.SyncOutcome
import com.kshavrin.mymoney.core.sync.SyncTarget
import com.kshavrin.mymoney.core.sync.fake.FakeAppSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerCancellationBehaviorTest {
    private val executor: Executor = Executor { it.run() }
    private val taskExecutor = ImmediateTaskExecutor(executor)
    private val workerFactory =
        object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker? = null
        }
    private val progressUpdater =
        object : ProgressUpdater {
            override fun updateProgress(
                context: Context,
                id: UUID,
                data: Data,
            ): ListenableFuture<Void> = completedFuture()
        }
    private val foregroundUpdater =
        object : ForegroundUpdater {
            override fun setForegroundAsync(
                context: Context,
                id: UUID,
                foregroundInfo: ForegroundInfo,
            ): ListenableFuture<Void> = completedFuture()
        }
    private val cacheDir: File =
        File.createTempFile("worker_cache", "").let { tmp ->
            tmp.delete()
            tmp.mkdirs()
            tmp.deleteOnExit()
            tmp
        }
    private val appContext: Context =
        object : ContextWrapper(null) {
            override fun getCacheDir(): File = cacheDir
        }

    @Test
    fun `backup rotation cancellation is rethrown instead of becoming retry`() =
        runTest {
            val repository = RotationBackupRepository(Result.failure(CancellationException("cancelled")))
            val worker =
                BackupRotationWorker(
                    appContext = appContext,
                    params =
                        workerParameters(
                            inputData =
                                Data
                                    .Builder()
                                    .putString(BackupRotationWorker.KEY_TREE_URI, "content://tree/backup")
                                    .build(),
                        ),
                    backupRepository = repository,
                )

            try {
                worker.doWork()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
            }

            assertEquals(listOf("content://tree/backup"), repository.rotateCalls)
        }

    @Test
    fun `backup rotation ordinary failure keeps retry mapping before max attempts`() =
        runTest {
            val worker =
                BackupRotationWorker(
                    appContext = appContext,
                    params =
                        workerParameters(
                            inputData =
                                Data
                                    .Builder()
                                    .putString(BackupRotationWorker.KEY_TREE_URI, "content://tree/backup")
                                    .build(),
                        ),
                    backupRepository = RotationBackupRepository(Result.failure(IllegalStateException("boom"))),
                )

            assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        }

    @Test
    fun `prune deleted cancellation is rethrown instead of becoming retry`() =
        runTest {
            val worker =
                PruneDeletedWorker(
                    appContext = appContext,
                    params = workerParameters(),
                    transactionRepository = PruneTransactionRepository(CancellationException("cancelled")),
                )

            try {
                worker.doWork()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
            }
        }

    @Test
    fun `prune deleted ordinary failure keeps retry mapping before max attempts`() =
        runTest {
            val worker =
                PruneDeletedWorker(
                    appContext = appContext,
                    params = workerParameters(),
                    transactionRepository = PruneTransactionRepository(IllegalStateException("boom")),
                )

            assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        }

    @Test
    fun `recurring cancellation is rethrown instead of becoming retry`() =
        runTest {
            val worker =
                RecurringWorker(
                    appContext = appContext,
                    params = workerParameters(),
                    generateDueRecurring = generateDueRecurringUseCase(CancellationException("cancelled")),
                )

            try {
                worker.doWork()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
            }
        }

    @Test
    fun `recurring ordinary failure keeps retry mapping before max attempts`() =
        runTest {
            val worker =
                RecurringWorker(
                    appContext = appContext,
                    params = workerParameters(),
                    generateDueRecurring = generateDueRecurringUseCase(IllegalStateException("boom")),
                )

            assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        }

    @Test
    fun `sync cancellation is rethrown instead of becoming retry`() =
        runTest {
            val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true))
            val worker =
                SyncWorker(
                    appContext = appContext,
                    params =
                        workerParameters(
                            inputData =
                                Data
                                    .Builder()
                                    .putString(SyncWorker.KEY_TARGET, SyncTarget.Dropbox.name)
                                    .build(),
                        ),
                    orchestrator = FakeSnapshotSync(pushResult = Result.failure(CancellationException("cancelled"))),
                    settings = settings,
                )

            try {
                worker.doWork()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
            }
        }

    @Test
    fun `sync ordinary failure keeps retry mapping before max attempts`() =
        runTest {
            val settings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = true))
            val worker =
                SyncWorker(
                    appContext = appContext,
                    params =
                        workerParameters(
                            inputData =
                                Data
                                    .Builder()
                                    .putString(SyncWorker.KEY_TARGET, SyncTarget.Dropbox.name)
                                    .build(),
                        ),
                    orchestrator =
                        FakeSnapshotSync(
                            pushResult = Result.failure(SyncException(SyncError.Network)),
                        ),
                    settings = settings,
                )

            assertEquals(ListenableWorker.Result.retry(), worker.doWork())
        }

    private fun workerParameters(
        inputData: Data = Data.EMPTY,
        runAttemptCount: Int = 0,
        workerContext: CoroutineContext = EmptyCoroutineContext,
    ): WorkerParameters =
        WorkerParameters(
            UUID.randomUUID(),
            inputData,
            emptyList<String>(),
            WorkerParameters.RuntimeExtras(),
            runAttemptCount,
            0,
            executor,
            workerContext,
            taskExecutor,
            workerFactory,
            progressUpdater,
            foregroundUpdater,
        )

    private fun generateDueRecurringUseCase(throwable: Throwable): GenerateDueRecurringUseCase =
        GenerateDueRecurringUseCase(
            recurringTemplateRepository = ThrowingRecurringTemplateRepository(throwable),
            transactionRepository = NoOpTransactionRepository(),
            recurringScheduler = RecurringScheduler(),
            transactionRunner =
                object : TransactionRunner {
                    override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
                },
            defaultDispatcher = UnconfinedTestDispatcher(),
        )

    private fun completedFuture(): ListenableFuture<Void> =
        SettableFuture.create<Void>().apply { set(null) }

    private class FakeSnapshotSync(
        private val pushResult: Result<SyncOutcome> = Result.success(SyncOutcome.Pushed),
        private val autoSyncResult: Result<Unit> = Result.success(Unit),
    ) : SnapshotSync {
        override fun isConnected(target: SyncTarget): Boolean = true

        override fun connectedTargets(): List<SyncTarget> = listOf(SyncTarget.Dropbox)

        override suspend fun syncNow(target: SyncTarget): Result<SyncOutcome> = pushResult

        override suspend fun push(target: SyncTarget): Result<SyncOutcome> = pushResult

        override suspend fun keepLocal(target: SyncTarget): Result<SyncOutcome> = pushResult

        override suspend fun keepRemote(target: SyncTarget): Result<SyncOutcome> = pushResult

        override fun connect(
            target: SyncTarget,
            payload: String,
        ) = Unit

        override fun disconnect(target: SyncTarget) = Unit

        override suspend fun accountLabel(target: SyncTarget): Result<String> = Result.success(target.name)

        override suspend fun autoSyncConnected(): Result<Unit> = autoSyncResult
    }

    private class ImmediateTaskExecutor(
        private val executor: Executor,
    ) : TaskExecutor {
        private val serialExecutor: SerialExecutor = SerialExecutorImpl(executor)

        override fun getMainThreadExecutor(): Executor = executor

        override fun getSerialTaskExecutor(): SerialExecutor = serialExecutor
    }

    private class RotationBackupRepository(
        private val rotateResult: Result<Unit>,
    ) : BackupRepository {
        val rotateCalls: MutableList<String> = mutableListOf()

        override suspend fun exportDb(treeUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun importDb(documentUriString: String): Result<Unit> = Result.success(Unit)

        override suspend fun listLocalBackups(treeUriString: String): List<BackupFile> = emptyList()

        override suspend fun rotateBackups(treeUriString: String): Result<Unit> {
            rotateCalls += treeUriString
            return rotateResult
        }

        override suspend fun exportToFile(destAbsolutePath: String): Result<Unit> = Result.success(Unit)

        override suspend fun importFromFile(srcAbsolutePath: String): Result<Unit> = Result.success(Unit)
    }

    private class PruneTransactionRepository(
        private val throwable: Throwable,
    ) : NoOpTransactionRepository() {
        override suspend fun pruneDeleted(before: Instant): Unit = throw throwable
    }

    private class ThrowingRecurringTemplateRepository(
        private val throwable: Throwable,
    ) : RecurringTemplateRepository {
        override suspend fun findDue(now: Instant): List<RecurringTemplate> = throw throwable

        override fun observeAll(): Flow<List<RecurringTemplate>> = flowOf(emptyList())

        override suspend fun upsert(template: RecurringTemplate): Long = 0L

        override suspend fun updateNextRun(
            id: Long,
            nextRunAt: Instant,
        ) = Unit

        override suspend fun deactivate(id: Long) = Unit
    }

    private open class NoOpTransactionRepository : TransactionRepository {
        override fun observeRecent(limit: Int): Flow<List<Transaction>> = flowOf(emptyList())

        override fun observeAll(): Flow<List<Transaction>> = flowOf(emptyList())

        override fun paged(
            accountId: Long,
            categoryId: Long?,
            from: Instant,
            to: Instant,
        ): Flow<PagingData<Transaction>> = flowOf(PagingData.empty())

        override suspend fun findById(id: Long): Transaction? = null

        override suspend fun findByPeriod(
            accountId: Long,
            period: Period,
        ): List<Transaction> = emptyList()

        override suspend fun getCategorySummary(
            accountId: Long,
            period: Period,
            kind: TransactionKind,
        ): List<CategorySummary> = emptyList()

        override suspend fun getCategoryGroups(
            accountId: Long,
            period: Period,
        ): List<CategoryGroup> = emptyList()

        override suspend fun searchByNote(
            query: String,
            limit: Int,
        ): List<Transaction> = emptyList()

        override suspend fun upsert(transaction: Transaction): Long = 0L

        override suspend fun softDelete(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun restore(
            id: Long,
            now: Instant,
        ) = Unit

        override suspend fun pruneDeleted(before: Instant) = Unit

        override suspend fun countByAccount(id: Long): Int = 0

        override suspend fun countByCategory(id: Long): Int = 0

        override suspend fun countByCurrency(id: Long): Int = 0
    }
}
