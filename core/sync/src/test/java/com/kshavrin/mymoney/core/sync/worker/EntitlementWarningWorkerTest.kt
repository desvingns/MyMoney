package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import android.content.ContextWrapper
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
import com.kshavrin.mymoney.core.datastore.EntitlementWarningStore
import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.EntitlementWarning
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.notification.EntitlementNotifier
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Executor
import kotlin.coroutines.EmptyCoroutineContext

class EntitlementWarningWorkerTest {
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
    fun `refresh failure returns retry before max retries`() =
        runTest {
            val repo = FakeEntitlementRepository(refreshResult = Result.failure(RuntimeException("network")))
            assertEquals(ListenableWorker.Result.retry(), createWorker(repo = repo, runAttemptCount = 0).doWork())
        }

    @Test
    fun `refresh failure returns failure at max retries`() =
        runTest {
            val repo = FakeEntitlementRepository(refreshResult = Result.failure(RuntimeException("network")))
            assertEquals(
                ListenableWorker.Result.failure(),
                createWorker(repo = repo, runAttemptCount = EntitlementWarningWorker.MAX_RETRIES).doWork(),
            )
        }

    @Test
    fun `cancellation exception from refresh is rethrown and not swallowed as retry`() =
        runTest {
            val repo = FakeEntitlementRepository(refreshResult = Result.failure(CancellationException("cancelled")))
            try {
                createWorker(repo = repo).doWork()
                fail("Expected CancellationException")
            } catch (_: CancellationException) {
            }
        }

    @Test
    fun `returns success when entitlement produces no warnings`() =
        runTest {
            val repo = FakeEntitlementRepository()
            assertEquals(ListenableWorker.Result.success(), createWorker(repo = repo).doWork())
        }

    @Test
    fun `trial ending notification is sent for subscription trial expiring within three days`() =
        runTest {
            val expiresAt = Instant.now().plusDays(2)
            val repo = FakeEntitlementRepository()
            repo.seed(subscriptionTrialEntitlement(expiresAt))
            val notifier = FakeEntitlementNotifier()

            createWorker(repo = repo, notifier = notifier).doWork()

            assertEquals(listOf(EntitlementWarning.TRIAL_ENDING_3D), notifier.notifyCalls)
        }

    @Test
    fun `dedup prevents duplicate notification for the same warning and expiresAt pair`() =
        runTest {
            val expiresAt = Instant.now().plusDays(2)
            val repo = FakeEntitlementRepository()
            repo.seed(subscriptionTrialEntitlement(expiresAt))
            val store = FakeEntitlementWarningStore()
            val notifier = FakeEntitlementNotifier()

            createWorker(repo = repo, store = store, notifier = notifier).doWork()
            createWorker(repo = repo, store = store, notifier = notifier).doWork()

            assertEquals(1, notifier.notifyCalls.size)
            assertEquals(1, store.markNotifiedCalls.size)
        }

    @Test
    fun `markNotified is called before notify ensuring at-most-once delivery`() =
        runTest {
            val expiresAt = Instant.now().plusDays(2)
            val repo = FakeEntitlementRepository()
            repo.seed(subscriptionTrialEntitlement(expiresAt))
            val callSequence = mutableListOf<String>()
            val store =
                object : FakeEntitlementWarningStore() {
                    override suspend fun markNotified(
                        warning: EntitlementWarning,
                        expiresAt: Instant?,
                    ) {
                        callSequence += "markNotified:${warning.name}"
                        super.markNotified(warning, expiresAt)
                    }
                }
            val notifier =
                object : EntitlementNotifier {
                    override fun notify(warning: EntitlementWarning) {
                        callSequence += "notify:${warning.name}"
                    }
                }

            createWorker(repo = repo, store = store, notifier = notifier).doWork()

            assertEquals(
                listOf("markNotified:TRIAL_ENDING_3D", "notify:TRIAL_ENDING_3D"),
                callSequence,
            )
        }

    @Test
    fun `at-most-once semantics prevent re-notification when notify throws after markNotified`() =
        runTest {
            val expiresAt = Instant.now().plusDays(2)
            val repo = FakeEntitlementRepository()
            repo.seed(subscriptionTrialEntitlement(expiresAt))
            val store = FakeEntitlementWarningStore()
            var notifyAttempts = 0
            val throwingNotifier =
                object : EntitlementNotifier {
                    override fun notify(warning: EntitlementWarning) {
                        notifyAttempts++
                        throw RuntimeException("delivery failed")
                    }
                }

            assertEquals(
                ListenableWorker.Result.retry(),
                createWorker(repo = repo, store = store, notifier = throwingNotifier).doWork(),
            )
            assertEquals(1, notifyAttempts)
            assertEquals(1, store.markNotifiedCalls.size)

            val silentNotifier = FakeEntitlementNotifier()
            assertEquals(
                ListenableWorker.Result.success(),
                createWorker(repo = repo, store = store, notifier = silentNotifier).doWork(),
            )
            assertEquals(0, silentNotifier.notifyCalls.size)
        }

    @Test
    fun `grace entered notification fires on active-to-grace transition but not on subsequent grace runs`() =
        runTest {
            val now = Instant.now()
            val expiresAt = now.minusDays(1)
            val graceEntitlement =
                UserEntitlement.Plus(
                    source = EntitlementSource.SUBSCRIPTION_MONTHLY,
                    state = EntitlementState.GRACE,
                    startsAt = now.minusDays(35),
                    expiresAt = expiresAt,
                    graceEndsAt = expiresAt.plusDays(7),
                )
            val repo = FakeEntitlementRepository()
            repo.seed(graceEntitlement)
            val store = FakeEntitlementWarningStore()
            store.setPreviousState(EntitlementState.ACTIVE)
            val notifier = FakeEntitlementNotifier()

            createWorker(repo = repo, store = store, notifier = notifier).doWork()
            assertEquals(1, notifier.notifyCalls.count { it == EntitlementWarning.GRACE_ENTERED })

            createWorker(repo = repo, store = store, notifier = notifier).doWork()
            assertEquals(
                "GRACE_ENTERED must not fire again when previous state is already GRACE",
                1,
                notifier.notifyCalls.count { it == EntitlementWarning.GRACE_ENTERED },
            )
        }

    @Test
    fun `ad reward entitlement in trial state produces no warning notifications`() =
        runTest {
            val expiresAt = Instant.now().plusDays(2)
            val repo = FakeEntitlementRepository()
            repo.seed(
                UserEntitlement.Plus(
                    source = EntitlementSource.AD_REWARD,
                    state = EntitlementState.TRIAL,
                    startsAt = Instant.now().minusDays(5),
                    expiresAt = expiresAt,
                    graceEndsAt = expiresAt,
                ),
            )
            val notifier = FakeEntitlementNotifier()

            createWorker(repo = repo, notifier = notifier).doWork()

            assertTrue(
                "Expected no warnings for AD_REWARD source but got: ${notifier.notifyCalls}",
                notifier.notifyCalls.isEmpty(),
            )
        }

    private fun subscriptionTrialEntitlement(expiresAt: Instant) =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_YEARLY,
            state = EntitlementState.TRIAL,
            startsAt = Instant.now().minusDays(5),
            expiresAt = expiresAt,
            graceEndsAt = expiresAt.plusDays(7),
        )

    private fun createWorker(
        repo: FakeEntitlementRepository = FakeEntitlementRepository(),
        store: EntitlementWarningStore = FakeEntitlementWarningStore(),
        notifier: EntitlementNotifier = FakeEntitlementNotifier(),
        runAttemptCount: Int = 0,
    ) = EntitlementWarningWorker(
        appContext = appContext,
        params = workerParameters(runAttemptCount = runAttemptCount),
        entitlementRepository = repo,
        entitlementWarningStore = store,
        entitlementNotifier = notifier,
    )

    private fun workerParameters(runAttemptCount: Int = 0): WorkerParameters =
        WorkerParameters(
            UUID.randomUUID(),
            Data.EMPTY,
            emptyList<String>(),
            WorkerParameters.RuntimeExtras(),
            runAttemptCount,
            0,
            executor,
            EmptyCoroutineContext,
            taskExecutor,
            workerFactory,
            progressUpdater,
            foregroundUpdater,
        )

    private fun completedFuture(): ListenableFuture<Void> =
        SettableFuture.create<Void>().apply { set(null) }

    private class ImmediateTaskExecutor(
        private val executor: Executor,
    ) : TaskExecutor {
        private val serialExecutor: SerialExecutor = SerialExecutorImpl(executor)

        override fun getMainThreadExecutor(): Executor = executor

        override fun getSerialTaskExecutor(): SerialExecutor = serialExecutor
    }

    open class FakeEntitlementWarningStore : EntitlementWarningStore {
        private var previousStateValue: EntitlementState? = null
        private val notifiedPairs = mutableSetOf<Pair<EntitlementWarning, Instant?>>()
        val markNotifiedCalls = mutableListOf<Pair<EntitlementWarning, Instant?>>()

        override suspend fun previousState(): EntitlementState? = previousStateValue

        override suspend fun setPreviousState(state: EntitlementState) {
            previousStateValue = state
        }

        override suspend fun wasNotified(
            warning: EntitlementWarning,
            expiresAt: Instant?,
        ): Boolean = (warning to expiresAt) in notifiedPairs

        override suspend fun markNotified(
            warning: EntitlementWarning,
            expiresAt: Instant?,
        ) {
            markNotifiedCalls += warning to expiresAt
            notifiedPairs += warning to expiresAt
        }
    }

    private class FakeEntitlementNotifier : EntitlementNotifier {
        val notifyCalls = mutableListOf<EntitlementWarning>()

        override fun notify(warning: EntitlementWarning) {
            notifyCalls += warning
        }
    }

    private class FakeEntitlementRepository(
        private val refreshResult: Result<Unit> = Result.success(Unit),
    ) : EntitlementRepository {
        private val entitlementState = MutableStateFlow<UserEntitlement>(UserEntitlement.Free)

        override val entitlement: StateFlow<UserEntitlement> = entitlementState.asStateFlow()

        override suspend fun refresh(): Result<Unit> = refreshResult

        fun seed(entitlement: UserEntitlement) {
            entitlementState.value = entitlement
        }
    }
}

private fun Instant.plusDays(days: Long): Instant = plus(days, ChronoUnit.DAYS)

private fun Instant.minusDays(days: Long): Instant = minus(days, ChronoUnit.DAYS)
