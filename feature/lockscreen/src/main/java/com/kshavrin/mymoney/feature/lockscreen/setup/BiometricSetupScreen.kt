package com.kshavrin.mymoney.feature.lockscreen.setup

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.lockscreen.R
import com.kshavrin.mymoney.feature.lockscreen.overlay.PIN_LENGTH
import com.kshavrin.mymoney.feature.lockscreen.overlay.PinKeypad

private val IDLE_TIMEOUT_OPTIONS = listOf(30, 60, 120, 300)
private val BIOMETRIC_ERROR_COLOR = Color(0xFFFF5722)
private const val LOCK_SETUP_ENABLE_TAG = "lock_setup_enable"
private const val LOCK_SETUP_IDLE_TIMEOUT_TAG = "lock_setup_idle_timeout"

@Composable
fun BiometricSetupRoute(
    onBack: () -> Unit,
    viewModel: BiometricSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                BiometricSetupAction.LaunchBiometricPrompt -> {
                    val activity = context as? FragmentActivity ?: return@collect
                    launchBiometricPrompt(
                        activity = activity,
                        title = context.getString(R.string.biometric_prompt_title),
                        cancel = context.getString(R.string.biometric_prompt_cancel),
                        onSuccess = { viewModel.onEvent(BiometricSetupEvent.BiometricAuthSucceeded) },
                        onFailure = { viewModel.onEvent(BiometricSetupEvent.BiometricAuthFailed) },
                    )
                }

                BiometricSetupAction.OpenSystemBiometricSettings ->
                    context.startActivity(systemBiometricSettingsIntent())
            }
        }
    }

    BiometricSetupContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSystemSettings = {
            context.startActivity(systemBiometricSettingsIntent())
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricSetupContent(
    state: BiometricSetupState,
    onEvent: (BiometricSetupEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSystemSettings: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.biometric_section_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.lock_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val biometricToggleLabel = stringResource(R.string.biometric_enable_toggle)
            ListItem(
                headlineContent = { Text(biometricToggleLabel) },
                trailingContent = {
                    Switch(
                        modifier =
                            Modifier
                                .testTag(LOCK_SETUP_ENABLE_TAG)
                                .semantics { contentDescription = biometricToggleLabel },
                        checked = state.enabled,
                        enabled = state.toggleEnabled,
                        onCheckedChange = { onEvent(BiometricSetupEvent.ToggleChanged(it)) },
                    )
                },
            )

            UnavailabilityNotice(
                availability = state.availability,
                onOpenSystemSettings = onOpenSystemSettings,
            )

            if (state.enabled && state.toggleEnabled) {
                IdleTimeoutRow(
                    selectedSeconds = state.idleTimeoutSec,
                    onSelect = { onEvent(BiometricSetupEvent.IdleTimeoutSelected(it)) },
                )
            }
        }
    }

    if (state.pinSetupVisible) {
        PinSetupDialog(
            onConfirm = { onEvent(BiometricSetupEvent.PinEntered(it)) },
            onDismiss = { onEvent(BiometricSetupEvent.PinSetupDismissed) },
        )
    }
}

@Composable
private fun UnavailabilityNotice(
    availability: BiometricAvailability,
    onOpenSystemSettings: () -> Unit,
) {
    when (availability) {
        BiometricAvailability.Available -> Unit
        BiometricAvailability.NoHardware ->
            Text(
                text = stringResource(R.string.biometric_unavailable),
                color = BIOMETRIC_ERROR_COLOR,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
            )

        BiometricAvailability.NotEnrolled -> {
            val openSettingsDescription = stringResource(R.string.biometric_open_system_settings)
            Text(
                text = stringResource(R.string.biometric_enrol_required),
                color = BIOMETRIC_ERROR_COLOR,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .clickable(onClick = onOpenSystemSettings)
                        .semantics { contentDescription = openSettingsDescription }
                        .padding(horizontal = Spacing.l, vertical = Spacing.s),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleTimeoutRow(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTimeoutLabel = stringResource(idleTimeoutLabelRes(selectedSeconds))
    val timeoutDescription = stringResource(R.string.biometric_idle_timeout_cd, selectedTimeoutLabel)
    Box {
        ListItem(
            headlineContent = { Text(stringResource(R.string.biometric_idle_timeout_label)) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedTimeoutLabel)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
            },
            modifier =
                Modifier
                    .testTag(LOCK_SETUP_IDLE_TIMEOUT_TAG)
                    .clickable { expanded = true }
                    .semantics { contentDescription = timeoutDescription },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            IDLE_TIMEOUT_OPTIONS.forEach { seconds ->
                DropdownMenuItem(
                    text = { Text(stringResource(idleTimeoutLabelRes(seconds))) },
                    onClick = {
                        expanded = false
                        onSelect(seconds)
                    },
                )
            }
        }
    }
}

@Composable
private fun PinSetupDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var firstPin by remember { mutableStateOf<String?>(null) }
    var entered by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    val onDigit: (Int) -> Unit = { digit ->
        if (entered.length < PIN_LENGTH) {
            mismatch = false
            entered += digit.toString()
            if (entered.length == PIN_LENGTH) {
                val current = firstPin
                if (current == null) {
                    firstPin = entered
                    entered = ""
                } else if (current == entered) {
                    onConfirm(entered)
                } else {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    mismatch = true
                    firstPin = null
                    entered = ""
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.lock_back))
            }
        },
        title = { Text(stringResource(R.string.pin_setup_title)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(
                    text =
                        stringResource(
                            if (firstPin == null) R.string.pin_setup_enter else R.string.pin_setup_confirm,
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mismatch) {
                    Text(
                        text = stringResource(R.string.pin_setup_mismatch),
                        color = BIOMETRIC_ERROR_COLOR,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                PinKeypad(
                    entered = entered,
                    onDigit = onDigit,
                    onBackspace = { if (entered.isNotEmpty()) entered = entered.dropLast(1) },
                )
            }
        },
    )
}

private fun idleTimeoutLabelRes(seconds: Int): Int =
    when (seconds) {
        30 -> R.string.biometric_idle_timeout_30s
        60 -> R.string.biometric_idle_timeout_1m
        120 -> R.string.biometric_idle_timeout_2m
        else -> R.string.biometric_idle_timeout_5m
    }

private fun systemBiometricSettingsIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL)
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }

private fun launchBiometricPrompt(
    activity: FragmentActivity,
    title: String,
    cancel: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt =
        BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence,
                ) {
                    onFailure()
                }
            },
        )
    val info =
        BiometricPrompt.PromptInfo
            .Builder()
            .setTitle(title)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()
    prompt.authenticate(info)
}
