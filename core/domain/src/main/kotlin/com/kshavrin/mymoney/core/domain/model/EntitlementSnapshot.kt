package com.kshavrin.mymoney.core.domain.model

import java.time.Instant

data class EntitlementSnapshot(
    val source: EntitlementSource,
    val startsAt: Instant,
    val expiresAt: Instant?,
    val inTrial: Boolean,
    val revokedAt: Instant?,
)
