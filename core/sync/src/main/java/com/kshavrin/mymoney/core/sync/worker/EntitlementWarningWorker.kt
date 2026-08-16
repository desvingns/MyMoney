package com.kshavrin.mymoney.core.sync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kshavrin.mymoney.core.datastore.EntitlementWarningStore
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.notification.EntitlementNotifier
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.core.domain.usecase.EntitlementStateMachine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.time.Instant

@HiltWorker
class EntitlementWarningWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val entitlementRepository: EntitlementRepository,
        private val entitlementWarningStore: EntitlementWarningStore,
        private val entitlementNotifier: EntitlementNotifier,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            runCatching { evaluate(Instant.now()) }.fold(
                onSuccess = { Result.success() },
                onFailure = {
                    if (it is CancellationException) throw it
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
                },
            )

        private suspend fun evaluate(now: Instant) {
            entitlementRepository.refresh().getOrThrow()
            val current = entitlementRepository.entitlement.value
            val previous = entitlementWarningStore.previousState()

            val warnings = EntitlementStateMachine.warnings(previous, current, now)
            val expiresAt = (current as? UserEntitlement.Plus)?.expiresAt
            warnings.forEach { warning ->
                if (!entitlementWarningStore.wasNotified(warning, expiresAt)) {
                    entitlementWarningStore.markNotified(warning, expiresAt)
                    entitlementNotifier.notify(warning)
                }
            }

            entitlementWarningStore.setPreviousState(current.stateOrNone())
        }

        private fun UserEntitlement.stateOrNone() =
            when (this) {
                is UserEntitlement.Plus -> state
                UserEntitlement.Free -> EntitlementState.NONE
            }

        companion object {
            const val MAX_RETRIES = 3
            const val UNIQUE_PERIODIC = "entitlement_warning"
        }
    }
