package com.kshavrin.mymoney.core.common.exception

open class SyncException(
    val syncError: SyncError,
) : Exception()

enum class SyncError {
    Network,
    Auth,
    Quota,
    Conflict,
    Server,
    Unknown,
}
