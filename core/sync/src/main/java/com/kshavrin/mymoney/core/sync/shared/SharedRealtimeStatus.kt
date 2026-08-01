package com.kshavrin.mymoney.core.sync.shared

sealed interface SharedRealtimeStatus {
    data object Inactive : SharedRealtimeStatus

    data object Starting : SharedRealtimeStatus

    data object Connected : SharedRealtimeStatus

    data class Sleeping(
        val retryAttempt: Int,
    ) : SharedRealtimeStatus

    data class Retrying(
        val retryAttempt: Int,
    ) : SharedRealtimeStatus

    data object Error : SharedRealtimeStatus
}
