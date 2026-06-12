package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT
import androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
import androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
import androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS
import androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.kshavrin.mymoney.core.common.di.IoDispatcher
import com.kshavrin.mymoney.core.datastore.AppSettingsRepository
import com.kshavrin.mymoney.core.datastore.SecureStorage
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.lockscreen.R
import com.kshavrin.mymoney.feature.lockscreen.setup.PinHasher
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LockOverlayEntryPoint {
    fun secureStorage(): SecureStorage
    fun appSettingsRepository(): AppSettingsRepository
    @IoDispatcher fun ioDispatcher(): CoroutineDispatcher
}

@Composable
fun LockOverlay(
    onUnlocked: () -> Unit,
    launchBiometric: (
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancel: String,
        onSuccess: () -> Unit,
        onLockout: () -> Unit,
        onPinFallback: () -> Unit,
    ) -> Unit = ::launchBiometricPrompt,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val dependencies = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, LockOverlayEntryPoint::class.java)
    }

    var pinFallback by rememberSaveable { mutableStateOf(false) }
    var pinAvailable by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var entered by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var lockoutDeadlineEpochMs by rememberSaveable { mutableStateOf<Long?>(null) }
    var nowEpochMs by remember { mutableStateOf(System.currentTimeMillis()) }

    BackHandler {}

    fun showPinFallback() {
        pinFallback = true
        entered = ""
        pinError = false
    }

    fun launchPrompt(activity: FragmentActivity) {
        pinFallback = false
        launchBiometric(
            activity,
            activity.getString(R.string.lock_prompt_title),
            activity.getString(R.string.lock_prompt_subtitle),
            activity.getString(R.string.lock_enter_pin),
            onUnlocked,
            ::showPinFallback,
            ::showPinFallback,
        )
    }

    LaunchedEffect(Unit) {
        val restoredPinFallback = pinFallback
        pinAvailable = hasPin(dependencies)
        lockoutDeadlineEpochMs = currentLockoutDeadlineEpochMs(dependencies)
        if (restoredPinFallback) return@LaunchedEffect

        val activity = context as? FragmentActivity
        if (activity == null) {
            showPinFallback()
        } else {
            launchPrompt(activity)
        }
    }

    LaunchedEffect(lockoutDeadlineEpochMs) {
        while (lockoutDeadlineEpochMs != null) {
            nowEpochMs = System.currentTimeMillis()
            if (remainingLockoutSeconds(lockoutDeadlineEpochMs, nowEpochMs) <= 0) {
                lockoutDeadlineEpochMs = currentLockoutDeadlineEpochMs(dependencies)
                break
            }
            delay(LOCKOUT_COUNTDOWN_TICK_MS)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (pinFallback) {
                Text(
                    text = stringResource(
                        if (pinAvailable == true) R.string.lock_pin_prompt else R.string.lock_locked,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pinAvailable == true) {
                    val lockoutSeconds = remainingLockoutSeconds(lockoutDeadlineEpochMs, nowEpochMs)
                    if (lockoutSeconds > 0) {
                        Text(
                            text = stringResource(R.string.lock_pin_retry_after, lockoutSeconds),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                    } else if (pinError) {
                        Text(
                            text = stringResource(R.string.lock_pin_wrong),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = Spacing.s),
                        )
                    }
                    PinKeypad(
                        entered = entered,
                        onDigit = { digit ->
                            val canEnterPin = remainingLockoutSeconds(lockoutDeadlineEpochMs, nowEpochMs) <= 0
                            if (canEnterPin && entered.length < PIN_LENGTH) {
                                pinError = false
                                entered += digit.toString()
                                if (entered.length == PIN_LENGTH) {
                                    val candidate = entered
                                    coroutineScope.launch {
                                        if (verifyPin(dependencies, candidate)) {
                                            lockoutDeadlineEpochMs = null
                                            onUnlocked()
                                        } else {
                                            if (hapticEnabled(dependencies)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            entered = ""
                                            lockoutDeadlineEpochMs = recordFailedPinAttempt(dependencies)
                                            pinError = lockoutDeadlineEpochMs == null
                                        }
                                    }
                                }
                            }
                        },
                        onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                        modifier = Modifier.padding(top = Spacing.l),
                        enabled = lockoutSeconds <= 0,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.lock_pin_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.s),
                    )
                    Button(
                        onClick = {
                            val activity = context as? FragmentActivity
                            if (activity != null) launchPrompt(activity)
                        },
                        modifier = Modifier.padding(top = Spacing.l),
                    ) {
                        Text(stringResource(R.string.lock_retry_biometric))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.lock_locked),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private suspend fun hasPin(dependencies: LockOverlayEntryPoint): Boolean =
    withContext(dependencies.ioDispatcher()) {
        dependencies.secureStorage().read().pinHash != null
    }

private suspend fun verifyPin(dependencies: LockOverlayEntryPoint, pin: String): Boolean =
    withContext(dependencies.ioDispatcher()) {
        val secureStorage = dependencies.secureStorage()
        val stored = secureStorage.read().pinHash ?: return@withContext false
        val hasher = PinHasher()
        val result = hasher.verifyDetailed(pin, stored)
        if (result.verified) {
            if (result.needsRehash) {
                secureStorage.writePinHash(hasher.hash(pin))
            }
            secureStorage.clearPinLockout()
        }
        result.verified
    }

private suspend fun currentLockoutDeadlineEpochMs(dependencies: LockOverlayEntryPoint): Long? =
    withContext(dependencies.ioDispatcher()) {
        val secureStorage = dependencies.secureStorage()
        val settings = secureStorage.read()
        val deadline = settings.pinLockoutDeadlineEpochMs ?: return@withContext null
        if (deadline > System.currentTimeMillis()) {
            deadline
        } else {
            secureStorage.writePinLockout(settings.failedPinAttempts, deadlineEpochMs = null)
            null
        }
    }

private suspend fun recordFailedPinAttempt(dependencies: LockOverlayEntryPoint): Long? =
    withContext(dependencies.ioDispatcher()) {
        val secureStorage = dependencies.secureStorage()
        val settings = secureStorage.read()
        val failedPinAttempts = settings.failedPinAttempts + 1
        val deadline =
            if (failedPinAttempts >= LOCKOUT_ATTEMPT_STEP && failedPinAttempts % LOCKOUT_ATTEMPT_STEP == 0) {
                System.currentTimeMillis() + lockoutDelayMs(failedPinAttempts)
            } else {
                null
            }
        secureStorage.writePinLockout(failedPinAttempts, deadline)
        deadline
    }

private fun lockoutDelayMs(failedPinAttempts: Int): Long {
    val lockoutLevel = failedPinAttempts / LOCKOUT_ATTEMPT_STEP - 1
    var delayMs = INITIAL_LOCKOUT_MS
    repeat(lockoutLevel.coerceAtLeast(0)) {
        delayMs = (delayMs * 2).coerceAtMost(MAX_LOCKOUT_MS)
    }
    return delayMs
}

private fun remainingLockoutSeconds(deadlineEpochMs: Long?, nowEpochMs: Long): Long =
    deadlineEpochMs?.let { ((it - nowEpochMs + 999L) / 1_000L).coerceAtLeast(0L) } ?: 0L

private suspend fun hapticEnabled(dependencies: LockOverlayEntryPoint): Boolean =
    dependencies.appSettingsRepository().settings.first().hapticEnabled

private fun launchBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    cancel: String,
    onSuccess: () -> Unit,
    onLockout: () -> Unit,
    onPinFallback: () -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    ERROR_LOCKOUT,
                    ERROR_LOCKOUT_PERMANENT,
                    -> onLockout()

                    ERROR_NEGATIVE_BUTTON,
                    ERROR_USER_CANCELED,
                    ERROR_NO_BIOMETRICS,
                    ERROR_HW_UNAVAILABLE,
                    ERROR_HW_NOT_PRESENT,
                    -> onPinFallback()

                    else -> onPinFallback()
                }
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setNegativeButtonText(cancel)
        .setAllowedAuthenticators(BIOMETRIC_STRONG)
        .build()
    activity.runOnUiThread {
        prompt.authenticate(info)
    }
}

private const val LOCKOUT_ATTEMPT_STEP = 5
private const val INITIAL_LOCKOUT_MS = 30_000L
private const val MAX_LOCKOUT_MS = 30L * 60L * 1_000L
private const val LOCKOUT_COUNTDOWN_TICK_MS = 1_000L
