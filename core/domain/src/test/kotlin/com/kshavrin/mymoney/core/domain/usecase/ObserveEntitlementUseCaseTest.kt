package com.kshavrin.mymoney.core.domain.usecase

import com.kshavrin.mymoney.core.domain.model.EntitlementSource
import com.kshavrin.mymoney.core.domain.model.EntitlementState
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ObserveEntitlementUseCaseTest {
    private val plusEntitlement =
        UserEntitlement.Plus(
            source = EntitlementSource.SUBSCRIPTION_MONTHLY,
            state = EntitlementState.ACTIVE,
            startsAt = Instant.parse("2026-08-01T00:00:00Z"),
            expiresAt = Instant.parse("2026-09-01T00:00:00Z"),
            graceEndsAt = null,
        )

    @Test
    fun `exposes repository entitlement state flow`() = runTest {
        val repository = FakeEntitlementRepository()
        val useCase = ObserveEntitlementUseCase(repository)

        assertSame(repository.entitlement, useCase.entitlement)

        repository.seed(plusEntitlement)

        assertEquals(plusEntitlement, useCase.entitlement.value)
    }

    @Test
    fun `refresh delegates a successful result`() = runTest {
        val repository = FakeEntitlementRepository().apply {
            refreshResult = Result.success(Unit)
        }
        val useCase = ObserveEntitlementUseCase(repository)

        val result = useCase.refresh()

        assertTrue(result.isSuccess)
        assertEquals(Unit, result.getOrNull())
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `refresh delegates a failure result`() = runTest {
        val failure = IllegalStateException("refresh failed")
        val repository = FakeEntitlementRepository().apply {
            refreshResult = Result.failure(failure)
        }
        val useCase = ObserveEntitlementUseCase(repository)

        val result = useCase.refresh()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, repository.refreshCalls)
    }

    private class FakeEntitlementRepository : EntitlementRepository {
        private val mutableEntitlement = MutableStateFlow<UserEntitlement>(UserEntitlement.Free)

        override val entitlement: StateFlow<UserEntitlement> = mutableEntitlement.asStateFlow()

        var refreshResult: Result<Unit> = Result.success(Unit)
        var refreshCalls: Int = 0

        fun seed(value: UserEntitlement) {
            mutableEntitlement.value = value
        }

        override suspend fun refresh(): Result<Unit> {
            refreshCalls++
            return refreshResult
        }
    }
}
