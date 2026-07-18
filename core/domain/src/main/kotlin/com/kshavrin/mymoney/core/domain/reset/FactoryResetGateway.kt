package com.kshavrin.mymoney.core.domain.reset

/**
 * Infrastructure port for [com.kshavrin.mymoney.core.domain.usecase.FactoryResetUseCase].
 *
 * Each primitive is a single destructive step; the use case owns the composition, ordering and
 * error aggregation. [detachCloudSync] is the explicit cloud detach — it empties the operation
 * journal and clears the journal-sync config so a re-linked cloud cannot replay-resurrect wiped
 * data.
 */
interface FactoryResetGateway {
    suspend fun wipeLocalData()

    suspend fun resetSettings()

    suspend fun clearSecrets()

    suspend fun detachCloudSync()
}
