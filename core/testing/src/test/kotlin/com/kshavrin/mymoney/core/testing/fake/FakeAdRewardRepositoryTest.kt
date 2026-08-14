package com.kshavrin.mymoney.core.testing.fake

import com.kshavrin.mymoney.core.domain.ads.AdRewardState
import com.kshavrin.mymoney.core.domain.ads.ConfirmationOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAdRewardRepositoryTest {
    @Test
    fun `refresh exposes the seeded state through the StateFlow`() =
        runTest {
            val state = AdRewardState(2, 5, false, null, false, null, null)
            val repository = FakeAdRewardRepository(state)

            assertEquals(state, repository.state.value)
            assertEquals(Result.success(state), repository.refresh())
            assertEquals(state, repository.state.value)
        }

    @Test
    fun `invalidateSession clears state and requires a new seed`() =
        runTest {
            val firstState = AdRewardState(2, 5, false, null, false, null, null)
            val secondState = AdRewardState(4, 5, false, null, false, null, null)
            val repository = FakeAdRewardRepository(firstState)

            repository.invalidateSession()

            assertNull(repository.state.value)
            assertTrue(repository.refresh().isFailure)
            assertEquals(ConfirmationOutcome.PendingConfirmation, repository.awaitConfirmation(firstState))

            repository.seedState(secondState)

            assertEquals(secondState, repository.refresh().getOrThrow())
            assertEquals(secondState, repository.state.value)
        }

    @Test
    fun `confirmation outcome is returned only for the current state and session`() =
        runTest {
            val previous = AdRewardState(2, 5, false, null, false, null, null)
            val updated = AdRewardState(3, 5, false, null, false, null, null)
            val repository = FakeAdRewardRepository(previous)
            repository.seedConfirmationOutcome(ConfirmationOutcome.ProgressIncreased(updated))

            assertEquals(
                ConfirmationOutcome.ProgressIncreased(updated),
                repository.awaitConfirmation(previous),
            )

            repository.seedState(updated)

            assertEquals(ConfirmationOutcome.PendingConfirmation, repository.awaitConfirmation(previous))
        }

    @Test
    fun `seedRefreshResult controls failures without replacing the cached state`() =
        runTest {
            val state = AdRewardState(2, 5, false, null, false, null, null)
            val failure = IllegalStateException("refresh failed")
            val repository = FakeAdRewardRepository(state)
            repository.seedRefreshResult(Result.failure(failure))

            val result = repository.refresh()

            assertEquals(failure, result.exceptionOrNull())
            assertEquals(state, repository.state.value)
        }
}
