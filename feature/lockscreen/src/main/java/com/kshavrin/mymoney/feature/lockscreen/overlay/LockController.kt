package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
import com.kshavrin.mymoney.core.datastore.model.VersionedAppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class AppContentSecurityState(
    val activityStartId: Long,
    val shouldSecure: Boolean,
)

@Singleton
class LockController
    @Inject
    constructor(
        private val appSettingsRepository: AppSettingsRepository,
        @ApplicationScope private val scope: CoroutineScope,
    ) : DefaultLifecycleObserver {
        internal var now: () -> Long = { System.currentTimeMillis() }

        @Volatile
        private var settings: AppSettings = AppSettings()
        private var settingsRevision = 0L

        private var firstSettingsSeen = false

        private var pausedAt: Long? = null
        private val activityStartLock = Any()
        private var activityStartId = 0L
        private var resolvedActivityStartId: Long? = null
        private var observedSettingsGeneration = 0L
        private val activityStartSettingsGenerations = mutableMapOf<Long, Long>()
        private val activityLockStates = mutableMapOf<Long, MutableStateFlow<Boolean>>()
        private val activityLockResolutionStates = mutableMapOf<Long, MutableStateFlow<Boolean>>()
        private val resolvedActivityStartIds = mutableSetOf<Long>()
        private val provisionalActivityStartIds = mutableSetOf<Long>()

        private val _shouldShowLock = MutableStateFlow(false)
        val shouldShowLock: StateFlow<Boolean> = _shouldShowLock.asStateFlow()

        private val _isResolved = MutableStateFlow(false)
        val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()

        private val _isActivityLockResolved = MutableStateFlow(false)
        val isActivityLockResolved: StateFlow<Boolean> = _isActivityLockResolved.asStateFlow()

        private val _appContentSecure = MutableStateFlow(false)
        val appContentSecure: StateFlow<Boolean> = _appContentSecure.asStateFlow()

        private val _appContentSecurityState = MutableStateFlow<AppContentSecurityState?>(null)
        val appContentSecurityState: StateFlow<AppContentSecurityState?> =
            _appContentSecurityState.asStateFlow()

        init {
            scope.launch {
                appSettingsRepository.versionedSettings.collect { latest ->
                    synchronized(activityStartLock) {
                        onSettingsChanged(latest)
                    }
                }
            }
        }

        private fun onSettingsChanged(latest: VersionedAppSettings) {
            if (latest.revision < settingsRevision) return
            val biometricWasEnabled = settings.biometricLockEnabled
            observedSettingsGeneration += 1
            settings = latest.settings
            settingsRevision = latest.revision
            _appContentSecure.value = latest.settings.hideAppContentInRecents
            if (!latest.settings.biometricLockEnabled) {
                clearLiveActivityLocks()
            } else if (!biometricWasEnabled) {
                _shouldShowLock.value = true
                activityLockStates.values.forEach { it.value = true }
            }
            if (!firstSettingsSeen) {
                firstSettingsSeen = true
                if (latest.settings.biometricLockEnabled) _shouldShowLock.value = true
                _isResolved.value = true
            }
            publishCurrentActivitySecurityStateLocked(latest.settings)
        }

        private fun clearLiveActivityLocks() {
            _shouldShowLock.value = false
            activityLockStates.values.forEach { it.value = false }
        }

        fun observeProcessLifecycle() {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        fun onMainActivityCreated(): Long {
            val startId =
                synchronized(activityStartLock) {
                    activityStartId += 1
                    activityLockStates[activityStartId] = MutableStateFlow(false)
                    activityLockResolutionStates[activityStartId] = MutableStateFlow(false)
                    _isActivityLockResolved.value = false
                    activityStartSettingsGenerations[activityStartId] = observedSettingsGeneration
                    activityStartId
                }
            scope.launch {
                val activitySettings = appSettingsRepository.versionedSettings.first()
                resolveActivityLock(startId, activitySettings)
            }
            return startId
        }

        fun lockStateFor(activityStartId: Long): StateFlow<Boolean> =
            synchronized(activityStartLock) {
                requireNotNull(activityLockStates[activityStartId])
            }

        fun isActivityLockResolvedFor(activityStartId: Long): StateFlow<Boolean> =
            synchronized(activityStartLock) {
                requireNotNull(activityLockResolutionStates[activityStartId])
            }

        fun onMainActivityDestroyed(activityStartId: Long) {
            synchronized(activityStartLock) {
                val wasLive = activityLockStates.remove(activityStartId) != null
                activityLockResolutionStates.remove(activityStartId)
                activityStartSettingsGenerations.remove(activityStartId)
                resolvedActivityStartIds.remove(activityStartId)
                provisionalActivityStartIds.remove(activityStartId)
                _shouldShowLock.value = activityLockStates.values.any { it.value }
                val wasResolved = resolvedActivityStartId == activityStartId
                val wasNewest = this.activityStartId == activityStartId
                if (wasLive && (wasResolved || wasNewest)) {
                    val electedStartId = activityLockStates.keys.maxOrNull()
                    resolvedActivityStartId =
                        electedStartId?.takeIf { it in resolvedActivityStartIds }
                    if (resolvedActivityStartId == null && electedStartId != null) {
                        resolveActivityLockLocked(
                            startId = electedStartId,
                            activitySettings =
                                VersionedAppSettings(
                                    settings = settings,
                                    revision = settingsRevision,
                                ),
                            provisional = true,
                        )
                    } else {
                        _isActivityLockResolved.value = resolvedActivityStartId != null
                        resolvedActivityStartId?.let {
                            publishCurrentActivitySecurityStateLocked(settings)
                        }
                    }
                }
            }
        }

        private fun resolveActivityLock(
            startId: Long,
            activitySettings: VersionedAppSettings,
        ) {
            synchronized(activityStartLock) {
                resolveActivityLockLocked(startId, activitySettings)
            }
        }

        private fun resolveActivityLockLocked(
            startId: Long,
            activitySettings: VersionedAppSettings,
            provisional: Boolean = false,
        ) {
            if (startId in resolvedActivityStartIds && startId !in provisionalActivityStartIds) return
            if (startId !in activityLockStates) {
                return
            }
            val activityStartSettingsGeneration =
                activityStartSettingsGenerations[startId] ?: return
            val collectorObservedNewerSettings =
                observedSettingsGeneration > activityStartSettingsGeneration
            val resolvedSettings =
                if (activitySettings.revision > 0L || settingsRevision > 0L) {
                    if (settingsRevision > activitySettings.revision) {
                        VersionedAppSettings(settings = settings, revision = settingsRevision)
                    } else {
                        activitySettings
                    }
                } else if (collectorObservedNewerSettings) {
                    VersionedAppSettings(settings = settings, revision = settingsRevision)
                } else {
                    activitySettings
                }
            activityLockStates[startId]?.value = resolvedSettings.settings.biometricLockEnabled
            _shouldShowLock.value = activityLockStates.values.any { it.value }
            activityLockResolutionStates[startId]?.value = true
            resolvedActivityStartIds += startId
            if (provisional) {
                provisionalActivityStartIds += startId
            } else {
                provisionalActivityStartIds -= startId
            }
            if (activityLockStates.keys.maxOrNull() != startId) return
            settings = resolvedSettings.settings
            settingsRevision = resolvedSettings.revision
            observedSettingsGeneration += 1
            _appContentSecure.value = resolvedSettings.settings.hideAppContentInRecents
            _isActivityLockResolved.value = true
            resolvedActivityStartId = startId
            _appContentSecurityState.value =
                AppContentSecurityState(
                    activityStartId = startId,
                    shouldSecure = resolvedSettings.settings.hideAppContentInRecents,
                )
        }

        private fun publishCurrentActivitySecurityStateLocked(latest: AppSettings) {
            val currentStartId = resolvedActivityStartId ?: return
            if (currentStartId !in activityLockStates || currentStartId !in resolvedActivityStartIds) {
                return
            }
            _appContentSecurityState.value =
                AppContentSecurityState(
                    activityStartId = currentStartId,
                    shouldSecure = latest.hideAppContentInRecents,
                )
        }

        override fun onPause(owner: LifecycleOwner) {
            pausedAt = now()
        }

        override fun onResume(owner: LifecycleOwner) {
            if (shouldLockAfterIdle(pausedAt, now(), settings)) lockNow()
        }

        fun lockNow() {
            synchronized(activityStartLock) {
                _shouldShowLock.value = true
                activityLockStates.values.forEach { it.value = true }
            }
        }

        fun markUnlocked() {
            pausedAt = null
            _shouldShowLock.value = false
        }

        fun markUnlocked(activityStartId: Long) {
            pausedAt = null
            synchronized(activityStartLock) {
                activityLockStates[activityStartId]?.value = false
                _shouldShowLock.value = activityLockStates.values.any { it.value }
            }
        }

        internal fun shouldLockAfterIdle(
            pausedAt: Long?,
            now: Long,
            settings: AppSettings,
        ): Boolean {
            if (!settings.biometricLockEnabled) return false
            val since = pausedAt ?: return false
            val idleMillis = now - since
            return idleMillis >= settings.biometricIdleTimeoutSec * MILLIS_PER_SECOND
        }

        private companion object {
            const val MILLIS_PER_SECOND = 1_000L
        }
    }
