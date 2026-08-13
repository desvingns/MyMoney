package com.kshavrin.mymoney.core.billing

import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.EntitlementCache
import com.kshavrin.mymoney.core.domain.model.UserEntitlement
import com.kshavrin.mymoney.core.domain.repository.EntitlementRepository
import com.kshavrin.mymoney.core.domain.usecase.EntitlementStateMachine
import com.kshavrin.mymoney.core.network.shared.SupabaseEntitlementApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntitlementRepositoryImpl
    @Inject
    constructor(
        private val api: SupabaseEntitlementApi,
        private val cache: EntitlementCache,
        private val clock: Clock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : EntitlementRepository {
        override val entitlement: StateFlow<UserEntitlement> =
            cache.cachedEntitlement
                .map { cached -> EntitlementStateMachine.resolve(cached.snapshot, clock.instant()) }
                .stateIn(
                    scope = applicationScope,
                    started = SharingStarted.Eagerly,
                    initialValue = UserEntitlement.Free,
                )

        override suspend fun refresh(): Result<Unit> =
            withContext(ioDispatcher) {
                api
                    .getMyEntitlement()
                    .mapCatching { snapshot ->
                        cache.update(snapshot = snapshot, lastValidatedAt = clock.instant())
                    }
            }
    }
