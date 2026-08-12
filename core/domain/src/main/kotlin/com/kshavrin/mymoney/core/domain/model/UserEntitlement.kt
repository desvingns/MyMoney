package com.kshavrin.mymoney.core.domain.model

import java.time.Instant

sealed interface UserEntitlement {
    data object Free : UserEntitlement

    data class Plus(
        val source: EntitlementSource,
        val state: EntitlementState,
        val startsAt: Instant,
        val expiresAt: Instant?,
        val graceEndsAt: Instant?,
    ) : UserEntitlement
}

enum class EntitlementSource {
    SUBSCRIPTION_MONTHLY,
    SUBSCRIPTION_YEARLY,
    AD_REWARD,
    WHITELIST,
}

enum class EntitlementState {
    NONE,
    TRIAL,
    ACTIVE,
    GRACE,
    EXPIRED,
    LOCAL_ONLY,
}
