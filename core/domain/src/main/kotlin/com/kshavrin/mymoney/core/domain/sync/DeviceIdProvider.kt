package com.kshavrin.mymoney.core.domain.sync

interface DeviceIdProvider {
    suspend fun deviceId(): String
}
