package com.kshavrin.mymoney.core.domain.billing

sealed interface BillingAvailability {
    data object Available : BillingAvailability

    data object UnavailableOnDevice : BillingAvailability

    data object UnavailableInRegion : BillingAvailability

    data object DisabledInBuild : BillingAvailability
}
