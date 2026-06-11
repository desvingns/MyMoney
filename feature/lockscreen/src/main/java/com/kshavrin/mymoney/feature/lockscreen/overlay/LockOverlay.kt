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
fun LockOverlay(onUnlocked: () -> Unit) {
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

    BackHandler {}

    fun showPinFallback() {
        pinFallback = true
        entered = ""
        pinError = false
    }

    fun launchPrompt(activity: FragmentActivity) {
        pinFallback = false
        launchBiometricPrompt(
            activity = activity,
            title = activity.getString(R.string.lock_prompt_title),
            subtitle = activity.getString(R.string.lock_prompt_subtitle),
            cancel = activity.getString(R.string.lock_enter_pin),
            onSuccess = onUnlocked,
            onLockout = ::showPinFallback,
            onPinFallback = ::showPinFallback,
        )
    }

    LaunchedEffect(Unit) {
        val restoredPinFallback = pinFallback
        pinAvailable = hasPin(dependencies)
        if (restoredPinFallback) return@LaunchedEffect

        val activity = context as? FragmentActivity
        if (activity == null) {
            showPinFallback()
        } else {
            launchPrompt(activity)
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
                    if (pinError) {
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
                            if (entered.length < PIN_LENGTH) {
                                pinError = false
                                entered += digit.toString()
                                if (entered.length == PIN_LENGTH) {
                                    val candidate = entered
                                    coroutineScope.launch {
                                        if (verifyPin(dependencies, candidate)) {
                                            onUnlocked()
                                        } else {
                                            if (hapticEnabled(dependencies)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                            entered = ""
                                            pinError = true
                                        }
                                    }
                                }
                            }
                        },
                        onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                        modifier = Modifier.padding(top = Spacing.l),
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
        val stored = dependencies.secureStorage().read().pinHash ?: return@withContext false
        PinHasher().verify(pin, stored)
    }

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
    prompt.authenticate(info)
}
