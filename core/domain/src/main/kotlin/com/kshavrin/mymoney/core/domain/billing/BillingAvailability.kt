package com.kshavrin.mymoney.core.domain.billing

sealed interface BillingAvailability {
    data object Available : BillingAvailability

    data object UnavailableOnDevice : BillingAvailability

    data object ServiceUnavailable : BillingAvailability

    data object NetworkUnavailable : BillingAvailability

    data object UnavailableInRegion : BillingAvailability

    data class UnknownFailure(
        val responseCode: Int,
    ) : BillingAvailability

    data object DisabledInBuild : BillingAvailability
}
