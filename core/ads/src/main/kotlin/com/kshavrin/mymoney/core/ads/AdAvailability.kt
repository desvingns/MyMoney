package com.kshavrin.mymoney.core.ads

sealed interface AdAvailability {
    data object Available : AdAvailability

    data object Loading : AdAvailability

    data object NoFill : AdAvailability

    data object RegionUnavailable : AdAvailability

    data object Offline : AdAvailability

    data object ConsentRequired : AdAvailability

    data object Disabled : AdAvailability
}
