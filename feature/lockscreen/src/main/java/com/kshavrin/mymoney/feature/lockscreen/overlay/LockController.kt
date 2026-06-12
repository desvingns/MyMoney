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
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockController @Inject constructor(
    appSettingsRepository: AppSettingsRepository,
    @ApplicationScope scope: CoroutineScope,
) : DefaultLifecycleObserver {

    internal var now: () -> Long = { System.currentTimeMillis() }

    @Volatile
    private var settings: AppSettings = AppSettings()

    @Volatile
    private var firstSettingsSeen = false

    private var pausedAt: Long? = null

    private val _shouldShowLock = MutableStateFlow(false)
    val shouldShowLock: StateFlow<Boolean> = _shouldShowLock.asStateFlow()

    private val _isResolved = MutableStateFlow(false)
    val isResolved: StateFlow<Boolean> = _isResolved.asStateFlow()

    private val _biometricLockEnabled = MutableStateFlow(false)
    val biometricLockEnabled: StateFlow<Boolean> = _biometricLockEnabled.asStateFlow()

    init {
        scope.launch {
            appSettingsRepository.settings.collect { latest ->
                settings = latest
                _biometricLockEnabled.value = latest.biometricLockEnabled
                if (!latest.biometricLockEnabled) {
                    _shouldShowLock.value = false
                }
                if (!firstSettingsSeen) {
                    firstSettingsSeen = true
                    if (latest.biometricLockEnabled) _shouldShowLock.value = true
                    _isResolved.value = true
                }
            }
        }
    }

    fun observeProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
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

    internal fun shouldLockAfterIdle(pausedAt: Long?, now: Long, settings: AppSettings): Boolean {
        if (!settings.biometricLockEnabled) return false
        val since = pausedAt ?: return false
        val idleMillis = now - since
        return idleMillis >= settings.biometricIdleTimeoutSec * MILLIS_PER_SECOND
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}
