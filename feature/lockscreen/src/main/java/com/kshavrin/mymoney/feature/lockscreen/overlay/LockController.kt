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
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

data class AppContentSecurityState(
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

        @Volatile
        private var firstSettingsSeen = false

        private var pausedAt: Long? = null
        private val activityStartLock = Any()
        private val activityStartId = AtomicLong()
        private var latestObservedSettings: AppSettings? = null
        private var pendingActivityStartId: Long? = null
        private var pendingActivityLockEnabled: Boolean? = null

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
                    settings = latest
                    val activityStartToResolve =
                        synchronized(activityStartLock) {
                            latestObservedSettings = latest
                            val pendingStartId = pendingActivityStartId
                            if (
                                pendingStartId == activityStartId.get() &&
                                pendingActivityLockEnabled == latest.biometricLockEnabled
                            ) {
                                pendingActivityStartId = null
                                pendingActivityLockEnabled = null
                                pendingStartId
                            } else {
                                if (pendingStartId != null && pendingStartId != activityStartId.get()) {
                                    pendingActivityStartId = null
                                    pendingActivityLockEnabled = null
                                }
                                null
                            }
                        }
                    _appContentSecure.value = latest.hideAppContentInRecents
                    _appContentSecurityState.value =
                        AppContentSecurityState(shouldSecure = latest.hideAppContentInRecents)
                    if (!latest.biometricLockEnabled) {
                        _shouldShowLock.value = false
                    }
                    if (!firstSettingsSeen) {
                        firstSettingsSeen = true
                        if (latest.biometricLockEnabled) _shouldShowLock.value = true
                        _isResolved.value = true
                    }
                    if (activityStartToResolve != null) {
                        resolveActivityLock(activityStartToResolve, latest)
                    }
                }
            }
        }

        fun observeProcessLifecycle() {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        }

        fun onMainActivityCreated() {
            val startId = activityStartId.incrementAndGet()
            _isActivityLockResolved.value = false
            synchronized(activityStartLock) {
                pendingActivityStartId = null
                pendingActivityLockEnabled = null
            }
            scope.launch {
                val activitySettings = appSettingsRepository.settings.first()
                val resolveImmediately =
                    synchronized(activityStartLock) {
                        if (activityStartId.get() != startId) {
                            false
                        } else if (latestObservedSettings == activitySettings) {
                            true
                        } else {
                            pendingActivityStartId = startId
                            pendingActivityLockEnabled = activitySettings.biometricLockEnabled
                            false
                        }
                    }
                if (resolveImmediately) resolveActivityLock(startId, activitySettings)
            }
        }

        private fun resolveActivityLock(
            startId: Long,
            activitySettings: AppSettings,
        ) {
            if (activityStartId.get() != startId) return
            _shouldShowLock.value = activitySettings.biometricLockEnabled
            _isActivityLockResolved.value = true
        }

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
