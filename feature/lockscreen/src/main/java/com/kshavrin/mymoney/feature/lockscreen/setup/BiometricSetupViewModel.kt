package com.kshavrin.mymoney.feature.lockscreen.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BiometricSetupViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val secureStorage: SecureStorage,
    private val availabilityChecker: BiometricAvailabilityChecker,
    private val pinHasher: PinHasher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(BiometricSetupState())
    val state: StateFlow<BiometricSetupState> = _state.asStateFlow()

    private val _actions = MutableSharedFlow<BiometricSetupAction>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val actions: SharedFlow<BiometricSetupAction> = _actions.asSharedFlow()

    private var pinSetupGeneration = 0
    private var dismissedPinSetupGeneration = -1
    private var pinSetupJob: Job? = null

    init {
        _state.value = _state.value.copy(availability = availabilityChecker.availability())
        viewModelScope.launch {
            appSettingsRepository.settings.collect { settings ->
                _state.value = _state.value.copy(
                    enabled = settings.biometricLockEnabled,
                    idleTimeoutSec = settings.biometricIdleTimeoutSec,
                )
            }
        }
    }

    fun onEvent(event: BiometricSetupEvent) {
        when (event) {
            is BiometricSetupEvent.ToggleChanged -> onToggleChanged(event.enabled)
            BiometricSetupEvent.BiometricAuthSucceeded -> onBiometricAuthSucceeded()
            BiometricSetupEvent.BiometricAuthFailed -> Unit
            is BiometricSetupEvent.IdleTimeoutSelected -> onIdleTimeoutSelected(event.seconds)
            is BiometricSetupEvent.PinEntered -> onPinEntered(event.pin)
            BiometricSetupEvent.PinSetupDismissed -> onPinSetupDismissed()
        }
    }

    private fun onToggleChanged(requestedEnabled: Boolean) {
        if (_state.value.availability != BiometricAvailability.Available) return
        if (requestedEnabled) {
            viewModelScope.launch { _actions.emit(BiometricSetupAction.LaunchBiometricPrompt) }
        } else {
            viewModelScope.launch {
                appSettingsRepository.update { it.copy(biometricLockEnabled = false) }
            }
        }
    }

    private fun onBiometricAuthSucceeded() {
        viewModelScope.launch {
            if (hasPin()) {
                appSettingsRepository.update { it.copy(biometricLockEnabled = true) }
            } else {
                pinSetupGeneration += 1
                _state.value = _state.value.copy(pinSetupVisible = true)
            }
        }
    }

    private fun onIdleTimeoutSelected(seconds: Int) {
        viewModelScope.launch {
            appSettingsRepository.update { it.copy(biometricIdleTimeoutSec = seconds) }
        }
    }

    private fun onPinEntered(pin: String) {
        if (pin.length != PIN_LENGTH) return
        val generation = pinSetupGeneration
        pinSetupJob?.cancel()
        pinSetupJob = viewModelScope.launch {
            withContext(ioDispatcher) { secureStorage.writePinHash(pinHasher.hash(pin)) }
            if (generation != pinSetupGeneration || generation == dismissedPinSetupGeneration) return@launch
            appSettingsRepository.update { it.copy(biometricLockEnabled = true) }
            _state.value = _state.value.copy(pinSetupVisible = false)
        }
    }

    private fun onPinSetupDismissed() {
        dismissedPinSetupGeneration = pinSetupGeneration
        pinSetupJob?.cancel()
        pinSetupJob = null
        _state.value = _state.value.copy(pinSetupVisible = false)
        viewModelScope.launch {
            appSettingsRepository.update { it.copy(biometricLockEnabled = false) }
        }
    }

    private suspend fun hasPin(): Boolean =
        withContext(ioDispatcher) { secureStorage.read().pinHash != null }

    private companion object {
        const val PIN_LENGTH = 4
    }
}

data class BiometricSetupState(
    val availability: BiometricAvailability = BiometricAvailability.Available,
    val enabled: Boolean = false,
    val idleTimeoutSec: Int = 60,
    val pinSetupVisible: Boolean = false,
) {
    val toggleEnabled: Boolean get() = availability == BiometricAvailability.Available
}

sealed interface BiometricSetupEvent {
    data class ToggleChanged(val enabled: Boolean) : BiometricSetupEvent
    data object BiometricAuthSucceeded : BiometricSetupEvent
    data object BiometricAuthFailed : BiometricSetupEvent
    data class IdleTimeoutSelected(val seconds: Int) : BiometricSetupEvent
    data class PinEntered(val pin: String) : BiometricSetupEvent
    data object PinSetupDismissed : BiometricSetupEvent
}

sealed interface BiometricSetupAction {
    data object LaunchBiometricPrompt : BiometricSetupAction
    data object OpenSystemBiometricSettings : BiometricSetupAction
}
