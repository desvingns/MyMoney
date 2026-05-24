package com.kshavrin.mymoney.feature.cloudsync.fake

import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository

class FakeRemoteConfigRepository(
    private var dropboxEnabled: Boolean = true,
    private var gdriveEnabled: Boolean = true,
) : RemoteConfigRepository {

    var refreshCount = 0
        private set

    override suspend fun refresh(): Result<Unit> {
        refreshCount++
        return Result.success(Unit)
    }

    override fun recurringTemplatesEnabled(): Boolean = true

    override fun budgetModeEnabled(): Boolean = true

    override fun dropboxSyncEnabled(): Boolean = dropboxEnabled

    override fun gdriveSyncEnabled(): Boolean = gdriveEnabled

    override fun minSupportedVersionCode(): Long = 0L

    override fun aestheticSoundPack(): String = "default"
}
