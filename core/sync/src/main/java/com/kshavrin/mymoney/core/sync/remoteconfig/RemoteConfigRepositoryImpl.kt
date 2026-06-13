package com.kshavrin.mymoney.core.sync.remoteconfig

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.kshavrin.mymoney.core.domain.repository.RemoteConfigRepository
import com.kshavrin.mymoney.core.sync.BuildConfig
import com.kshavrin.mymoney.core.sync.R
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemoteConfigRepositoryImpl
    @Inject
    constructor() : RemoteConfigRepository {
        // HAS_FIREBASE is false when no google-services.json / firebase.enabled property is set;
        // in that build no Firebase class may be touched (FirebaseApp is never initialised).
        private val config: FirebaseRemoteConfig? by lazy {
            if (!BuildConfig.HAS_FIREBASE) {
                null
            } else {
                FirebaseRemoteConfig.getInstance().apply {
                    setConfigSettingsAsync(
                        remoteConfigSettings {
                            minimumFetchIntervalInSeconds =
                                if (BuildConfig.DEBUG) 0L else PROD_FETCH_INTERVAL_SECONDS
                        },
                    )
                    setDefaultsAsync(R.xml.remote_config_defaults)
                }
            }
        }

        override suspend fun refresh(): Result<Unit> {
            val rc = config ?: return Result.success(Unit)
            return runCatching { rc.fetchAndActivate().await() }
                .fold(
                    onSuccess = { Result.success(Unit) },
                    onFailure = { Result.success(Unit) },
                )
        }

        override fun recurringTemplatesEnabled(): Boolean =
            config?.getBoolean(KEY_RECURRING_TEMPLATES) ?: DEFAULT_RECURRING_TEMPLATES

        override fun budgetModeEnabled(): Boolean =
            config?.getBoolean(KEY_BUDGET_MODE) ?: DEFAULT_BUDGET_MODE

        override fun dropboxSyncEnabled(): Boolean =
            config?.getBoolean(KEY_DROPBOX_SYNC) ?: DEFAULT_DROPBOX_SYNC

        override fun gdriveSyncEnabled(): Boolean =
            config?.getBoolean(KEY_GDRIVE_SYNC) ?: DEFAULT_GDRIVE_SYNC

        override fun minSupportedVersionCode(): Long =
            config?.getLong(KEY_MIN_SUPPORTED_VERSION_CODE) ?: DEFAULT_MIN_SUPPORTED_VERSION_CODE

        override fun aestheticSoundPack(): String =
            config?.getString(KEY_AESTHETIC_SOUND_PACK)?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_AESTHETIC_SOUND_PACK

        private companion object {
            const val PROD_FETCH_INTERVAL_SECONDS = 43_200L

            const val KEY_RECURRING_TEMPLATES = "feature_recurring_templates_enabled"
            const val KEY_BUDGET_MODE = "feature_budget_mode_enabled"
            const val KEY_DROPBOX_SYNC = "dropbox_sync_enabled"
            const val KEY_GDRIVE_SYNC = "gdrive_sync_enabled"
            const val KEY_MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
            const val KEY_AESTHETIC_SOUND_PACK = "aesthetic_sound_pack"

            const val DEFAULT_RECURRING_TEMPLATES = true
            const val DEFAULT_BUDGET_MODE = true
            const val DEFAULT_DROPBOX_SYNC = false
            const val DEFAULT_GDRIVE_SYNC = false
            const val DEFAULT_MIN_SUPPORTED_VERSION_CODE = 1L
            const val DEFAULT_AESTHETIC_SOUND_PACK = "default"
        }
    }
