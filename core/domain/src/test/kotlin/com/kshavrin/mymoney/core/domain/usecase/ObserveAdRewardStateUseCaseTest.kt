package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.ads.AdRewardRepository
import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveAdRewardStateUseCaseTest {
    private val rewardState =
        AdRewardState(
            progress = 2,
            required = 5,
            frozen = false,
            frozenReason = null,
            plusActive = false,
            plusProvider = null,
            plusExpiresAt = null,
        )

    @Test
    fun `exposes repository reward state flow`() {
        val repository = FakeAdRewardRepository()
        val useCase = ObserveAdRewardStateUseCase(repository)

        assertSame(repository.state, useCase.state)

        repository.seed(rewardState)

        assertEquals(rewardState, useCase.state.value)
    }

    @Test
    fun `refresh delegates and returns the repository result`() = runTest {
        val repository = FakeAdRewardRepository().apply { refreshResult = Result.success(rewardState) }
        val useCase = ObserveAdRewardStateUseCase(repository)

        val outcome = useCase.refresh()

        assertTrue(outcome.isSuccess)
        assertSame(rewardState, outcome.getOrNull())
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `awaitConfirmation delegates and returns the repository outcome`() = runTest {
        val confirmation = ConfirmationOutcome.ProgressIncreased(rewardState)
        val repository = FakeAdRewardRepository().apply { confirmationOutcome = confirmation }
        val useCase = ObserveAdRewardStateUseCase(repository)

        val outcome = useCase.awaitConfirmation(rewardState)

        assertSame(confirmation, outcome)
        assertSame(rewardState, repository.lastConfirmationPrevious)
    }

    private class FakeAdRewardRepository : AdRewardRepository {
        private val mutableState = MutableStateFlow<AdRewardState?>(null)

        override val state: StateFlow<AdRewardState?> = mutableState.asStateFlow()

        var refreshResult: Result<AdRewardState> =
            Result.failure(IllegalStateException("No ad reward state seeded."))
        var refreshCalls: Int = 0
        var confirmationOutcome: ConfirmationOutcome = ConfirmationOutcome.PendingConfirmation
        var lastConfirmationPrevious: AdRewardState? = null

        fun seed(value: AdRewardState?) {
            mutableState.value = value
        }

        override fun invalidateSession() {
            mutableState.value = null
        }

        override suspend fun refresh(): Result<AdRewardState> {
            refreshCalls++
            return refreshResult
        }

        override suspend fun awaitConfirmation(previous: AdRewardState): ConfirmationOutcome {
            lastConfirmationPrevious = previous
            return confirmationOutcome
        }
    }
}
