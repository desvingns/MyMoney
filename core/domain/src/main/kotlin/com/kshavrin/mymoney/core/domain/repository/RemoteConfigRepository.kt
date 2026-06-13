package com.kshavrin.mymoney.core.domain.repository

interface RemoteConfigRepository {
    suspend fun refresh(): Result<Unit>

    fun recurringTemplatesEnabled(): Boolean

    fun budgetModeEnabled(): Boolean

    fun dropboxSyncEnabled(): Boolean

    fun gdriveSyncEnabled(): Boolean

    fun minSupportedVersionCode(): Long

    fun aestheticSoundPack(): String
}
