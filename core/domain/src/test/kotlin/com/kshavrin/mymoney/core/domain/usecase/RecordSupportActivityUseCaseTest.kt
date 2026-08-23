package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordSupportActivityUseCaseTest {
    @Test
    fun `records support activity through the repository`() =
        runTest {
            val repository = FakeSupporterRepository()

            val result = RecordSupportActivityUseCase(repository)()

            assertTrue(result.isSuccess)
            assertEquals(1, repository.recordSupportActivityCalls)
        }

    @Test
    fun `propagates a repository failure`() =
        runTest {
            val failure = IllegalStateException("write failed")
            val repository = FakeSupporterRepository().apply { recordResult = Result.failure(failure) }

            val result = RecordSupportActivityUseCase(repository)()

            assertTrue(result.isFailure)
            assertSame(failure, result.exceptionOrNull())
        }

    private class FakeSupporterRepository : SupporterRepository {
        private val mutableState = MutableStateFlow(SupporterState(badgeEarned = false, purchaseCount = 0))

        var recordSupportActivityCalls = 0
        var recordResult: Result<Unit> = Result.success(Unit)

        override fun state(): Flow<SupporterState> = mutableState.asStateFlow()

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            Result.success(Unit)

        override suspend fun recordSupportActivity(): Result<Unit> {
            recordSupportActivityCalls++
            return recordResult
        }

        override suspend fun mergeRemote(
            remoteCount: Int,
            remoteBadge: Boolean,
        ): Result<Unit> = Result.success(Unit)
    }
}
