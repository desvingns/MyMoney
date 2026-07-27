package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.kshavrin.mymoney.core.common.scope.ApplicationScope
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.model.AppSettings
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

        private var firstSettingsSeen = false

        private var pausedAt: Long? = null
        private val activityStartLock = Any()
        private var activityStartId = 0L
        private var resolvedActivityStartId: Long? = null
        private var resolvedActivityLockEnabled = false
        private var observedSettingsGeneration = 0L
        private var activityStartSettingsGeneration = 0L

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
                appSettingsRepository.settings.collect { latest ->
                    synchronized(activityStartLock) {
                        onSettingsChanged(latest)
                    }
                }
            }
        }

        private fun onSettingsChanged(latest: AppSettings) {
            observedSettingsGeneration += 1
            settings = latest
            _appContentSecure.value = latest.hideAppContentInRecents
            if (!latest.biometricLockEnabled && !isActivityStartLockActiveLocked()) {
                _shouldShowLock.value = false
            }
            if (!firstSettingsSeen) {
                firstSettingsSeen = true
                if (latest.biometricLockEnabled) _shouldShowLock.value = true
                _isResolved.value = true
            }
            publishCurrentActivitySecurityStateLocked(latest)
        }

        fun observeProcessLifecycle() {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        fun onMainActivityCreated(): Long {
            val startId =
                synchronized(activityStartLock) {
                    activityStartId += 1
                    resolvedActivityStartId = null
                    resolvedActivityLockEnabled = false
                    _isActivityLockResolved.value = false
                    activityStartSettingsGeneration = observedSettingsGeneration
                    activityStartId
                }
            scope.launch {
                val activitySettings = appSettingsRepository.settings.first()
                resolveActivityLock(startId, activitySettings)
            }
            return startId
        }

        private fun resolveActivityLock(
            startId: Long,
            activitySettings: AppSettings,
        ) {
            synchronized(activityStartLock) {
                if (activityStartId != startId) return
                val collectorObservedNewerSettings =
                    observedSettingsGeneration > activityStartSettingsGeneration
                val resolvedSettings =
                    if (collectorObservedNewerSettings) {
                        settings
                    } else {
                        activitySettings
                    }
                settings = resolvedSettings
                _appContentSecure.value = resolvedSettings.hideAppContentInRecents
                _shouldShowLock.value = resolvedSettings.biometricLockEnabled
                _isActivityLockResolved.value = true
                resolvedActivityStartId = startId
                resolvedActivityLockEnabled = resolvedSettings.biometricLockEnabled
                _appContentSecurityState.value =
                    AppContentSecurityState(
                        activityStartId = startId,
                        shouldSecure = resolvedSettings.hideAppContentInRecents,
                    )
            }
        }

        private fun publishCurrentActivitySecurityStateLocked(latest: AppSettings) {
            if (resolvedActivityStartId != activityStartId) return
            _appContentSecurityState.value =
                AppContentSecurityState(
                    activityStartId = activityStartId,
                    shouldSecure = latest.hideAppContentInRecents,
                )
        }

        private fun isActivityStartLockActiveLocked(): Boolean =
            resolvedActivityStartId == activityStartId && resolvedActivityLockEnabled

        override fun onPause(owner: LifecycleOwner) {
            pausedAt = now()
        }

        override fun onResume(owner: LifecycleOwner) {
            if (shouldLockAfterIdle(pausedAt, now(), settings)) _shouldShowLock.value = true
        }

        fun lockNow() {
            _shouldShowLock.value = true
        }

        fun markUnlocked() {
            pausedAt = null
            synchronized(activityStartLock) {
                resolvedActivityLockEnabled = false
            }
            _shouldShowLock.value = false
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
