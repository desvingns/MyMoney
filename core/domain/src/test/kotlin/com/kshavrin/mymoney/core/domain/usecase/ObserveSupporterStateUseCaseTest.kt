package com.kshavrin.mymoney.core.domain.usecase

import app.cash.turbine.test
import com.kshavrin.mymoney.core.domain.billing.PurchaseOutcome
import com.kshavrin.mymoney.core.domain.supporter.SupporterRepository
import com.kshavrin.mymoney.core.domain.supporter.SupporterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ObserveSupporterStateUseCaseTest {
    @Test
    fun `returns and collects repository supporter state flow`() = runTest {
        val initialState =
            SupporterState(
                badgeEarned = true,
                purchaseCount = 5,
                smallCoffeeCount = 3,
                largeCoffeeCount = 2,
            )
        val updatedState = initialState.copy(smallCoffeeCount = 4, largeCoffeeCount = 5)
        val repository = FakeSupporterRepository(initialState)
        val useCase = ObserveSupporterStateUseCase(repository)

        assertSame(repository.stateFlow, useCase())

        useCase().test {
            val collectedInitialState = awaitItem()
            assertEquals(3, collectedInitialState.smallCoffeeCount)
            assertEquals(2, collectedInitialState.largeCoffeeCount)

            repository.emit(updatedState)

            assertEquals(updatedState, awaitItem())
        }
    }

    private class FakeSupporterRepository(initialState: SupporterState) : SupporterRepository {
        private val mutableState = MutableStateFlow(initialState)
        val stateFlow: StateFlow<SupporterState> = mutableState.asStateFlow()

        override fun state(): Flow<SupporterState> = stateFlow

        fun emit(state: SupporterState) {
            mutableState.value = state
        }

        override suspend fun recordPurchase(outcome: PurchaseOutcome.Purchased): Result<Unit> =
            Result.success(Unit)

        override suspend fun mergeRemote(
            remoteCount: Int,
            remoteBadge: Boolean,
        ): Result<Unit> = Result.success(Unit)
    }
}
