package com.kshavrin.mymoney.core.common.exception

class SyncException(val syncError: SyncError) : Exception()

enum class SyncError {
    Network,
    Auth,
    Quota,
    Conflict,
    Server,
    Unknown,
}
