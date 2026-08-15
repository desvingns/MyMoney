package com.kshavrin.mymoney.core.sync.usecase

import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncSettingsUseCaseTest {
    @Test
    fun `availability reflects remote config flags`() {
        val useCase =
            CloudSyncSettingsUseCase(
                appSettingsRepository = FakeAppSettingsRepository(),
                remoteConfigRepository =
                    FakeRemoteConfigRepository(
                        dropboxEnabled = true,
                        gdriveEnabled = false,
                        sharedEnabled = true,
                    ),
            )

        assertEquals(
            CloudSyncAvailability(
                dropboxEnabled = true,
                gdriveEnabled = false,
                sharedEnabled = true,
            ),
            useCase.availability(),
        )
    }

    @Test
    fun `observeLastSyncAt maps settings flow to last sync timestamp`() = runTest {
        val appSettings = FakeAppSettingsRepository(AppSettings(lastSyncAt = 42L))
        val useCase = CloudSyncSettingsUseCase(appSettings, FakeRemoteConfigRepository())
        val lastSyncAt = useCase.observeLastSyncAt()

        assertEquals(42L, lastSyncAt.first())

        appSettings.seed(AppSettings(lastSyncAt = null))

        assertNull(lastSyncAt.first())
    }

    @Test
    fun `isAutoSyncEnabled reads the current settings value`() = runTest {
        val appSettings = FakeAppSettingsRepository(AppSettings(autoSyncEnabled = false))
        val useCase = CloudSyncSettingsUseCase(appSettings, FakeRemoteConfigRepository())

        assertFalse(useCase.isAutoSyncEnabled())

        appSettings.seed(AppSettings(autoSyncEnabled = true))

        assertTrue(useCase.isAutoSyncEnabled())
    }

    private class FakeAppSettingsRepository(
        initial: AppSettings = AppSettings(),
    ) : AppSettingsRepository {
        private val mutableSettings = MutableStateFlow(initial)

        override val settings: Flow<AppSettings> = mutableSettings.asStateFlow()

        fun seed(value: AppSettings) {
            mutableSettings.value = value
        }

        override suspend fun update(transform: (AppSettings) -> AppSettings) {
            mutableSettings.value = transform(mutableSettings.value)
        }
    }

    private class FakeRemoteConfigRepository(
        private val dropboxEnabled: Boolean = false,
        private val gdriveEnabled: Boolean = false,
        private val sharedEnabled: Boolean = false,
    ) : RemoteConfigRepository {
        override suspend fun refresh(): Result<Unit> = Result.success(Unit)

        override fun recurringTemplatesEnabled(): Boolean = true

        override fun budgetModeEnabled(): Boolean = true

        override fun dropboxSyncEnabled(): Boolean = dropboxEnabled

        override fun gdriveSyncEnabled(): Boolean = gdriveEnabled

        override fun sharedSyncEnabled(): Boolean = sharedEnabled

        override fun minSupportedVersionCode(): Long = 1L

        override fun aestheticSoundPack(): String = "default"
    }
}
