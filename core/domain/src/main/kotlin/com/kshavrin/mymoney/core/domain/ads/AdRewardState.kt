package com.kshavrin.mymoney.core.domain.ads

import java.time.Instant

data class AdRewardState(
    val progress: Int,
    val required: Int,
    val frozen: Boolean,
    val frozenReason: FrozenReason?,
    val plusActive: Boolean,
    val plusProvider: String?,
    val plusExpiresAt: Instant?,
    val totalWatched: Int = 0,
)

sealed interface FrozenReason {
    data class PlusActive(
        val provider: String,
    ) : FrozenReason

    data object Unknown : FrozenReason
}
