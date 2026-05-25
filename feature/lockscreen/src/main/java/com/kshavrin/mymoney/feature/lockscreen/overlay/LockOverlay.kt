package com.kshavrin.mymoney.feature.lockscreen.overlay

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT
import androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val LOCK_ERROR_COLOR = Color(0xFFFF5722)

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

    var pinFallback by remember { mutableStateOf(false) }
    var entered by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    BackHandler {}

    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity ?: return@LaunchedEffect
        launchBiometricPrompt(
            activity = activity,
            title = activity.getString(R.string.lock_prompt_title),
            subtitle = activity.getString(R.string.lock_prompt_subtitle),
            cancel = activity.getString(R.string.lock_prompt_cancel),
            onSuccess = onUnlocked,
            onLockout = { pinFallback = true },
        )
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
                    text = stringResource(R.string.lock_pin_prompt),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (pinError) {
                    Text(
                        text = stringResource(R.string.lock_pin_wrong),
                        color = LOCK_ERROR_COLOR,
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
                    text = stringResource(R.string.lock_locked),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
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
                if (errorCode == ERROR_LOCKOUT || errorCode == ERROR_LOCKOUT_PERMANENT) onLockout()
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
