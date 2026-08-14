package com.kshavrin.mymoney.core.common.exception

open class SyncException(
    val syncError: SyncError,
) : Exception()

enum class SyncError {
    Network,
    Auth,
    EntitlementRequired,
    Quota,
    Conflict,
    Server,
    Unknown,
}
