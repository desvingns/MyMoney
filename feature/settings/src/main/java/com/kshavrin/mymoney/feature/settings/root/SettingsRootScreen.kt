package com.kshavrin.mymoney.feature.settings.root

import android.content.Intent
import android.os.Process
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kshavrin.mymoney.core.ui.flow.CollectActions
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.language.AppLanguage

const val FactoryResetConfirmFieldTag = "factory_reset_confirm_field"
const val FactoryResetConfirmButtonTag = "factory_reset_confirm_button"

@Composable
fun SettingsRootRoute(
    onOpenTheme: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCloudSync: () -> Unit,
    onOpenBiometricLock: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val resetMessage = stringResource(R.string.factory_reset_success)
    val resetErrorMessage = stringResource(R.string.factory_reset_error)
    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            is SettingsAction.RestartToOnboardingAfterReset -> {
                val message = if (action.hadFailures) resetErrorMessage else resetMessage
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                relaunchApplication(context)
            }
        }
    }
    SettingsRootContent(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenTheme = onOpenTheme,
        onOpenLanguage = onOpenLanguage,
        onOpenBackup = onOpenBackup,
        onOpenCloudSync = onOpenCloudSync,
        onOpenBiometricLock = onOpenBiometricLock,
        onOpenAbout = onOpenAbout,
        onOpenLicences = {
            context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
        },
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootContent(
    state: SettingsState,
    onEvent: (SettingsEvent) -> Unit,
    onOpenTheme: () -> Unit,
    onOpenLanguage: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
    onOpenCloudSync: () -> Unit = {},
    onOpenBiometricLock: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
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
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            val themeLabel = stringResource(R.string.settings_theme)
            ListItem(
                headlineContent = { Text(themeLabel) },
                trailingContent = { Text(stringResource(state.themeMode.labelRes)) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenTheme)
                        .semantics { contentDescription = themeLabel },
            )

            SectionHeader(stringResource(R.string.settings_section_security))
            val biometricLabel = stringResource(R.string.settings_biometric_lock)
            ListItem(
                headlineContent = { Text(biometricLabel) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenBiometricLock)
                        .semantics { contentDescription = biometricLabel },
            )

            SectionHeader(stringResource(R.string.settings_section_cloud))
            val cloudSyncLabel = stringResource(R.string.settings_cloud_sync)
            ListItem(
                headlineContent = { Text(cloudSyncLabel) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenCloudSync)
                        .semantics { contentDescription = cloudSyncLabel },
            )
            val backupLabel = stringResource(R.string.settings_backup_restore)
            ListItem(
                headlineContent = { Text(backupLabel) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenBackup)
                        .semantics { contentDescription = backupLabel },
            )

            SectionHeader(stringResource(R.string.settings_section_general))
            val languageLabel = stringResource(R.string.settings_language)
            ListItem(
                headlineContent = { Text(languageLabel) },
                trailingContent = { Text(stringResource(state.language.labelRes)) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenLanguage)
                        .semantics { contentDescription = languageLabel },
            )
            val soundLabel = stringResource(R.string.settings_sound_toggle)
            ListItem(
                headlineContent = {
                    Text(
                        text = soundLabel,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
                trailingContent = {
                    Switch(
                        modifier = Modifier.semantics { contentDescription = soundLabel },
                        checked = state.soundEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.SoundToggled(it)) },
                    )
                },
            )
            val hapticLabel = stringResource(R.string.settings_haptic_toggle)
            ListItem(
                headlineContent = {
                    Text(
                        text = hapticLabel,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                },
                trailingContent = {
                    Switch(
                        modifier = Modifier.semantics { contentDescription = hapticLabel },
                        checked = state.hapticEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.HapticToggled(it)) },
                    )
                },
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            val aboutLabel = stringResource(R.string.settings_about_help)
            ListItem(
                headlineContent = { Text(aboutLabel) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenAbout)
                        .semantics { contentDescription = aboutLabel },
            )
            val licencesLabel = stringResource(R.string.about_licences)
            ListItem(
                headlineContent = { Text(licencesLabel) },
                modifier =
                    Modifier
                        .clickable(onClick = onOpenLicences)
                        .semantics { contentDescription = licencesLabel },
            )

            SectionHeader(stringResource(R.string.settings_section_danger))
            val factoryResetLabel = stringResource(R.string.settings_factory_reset)
            ListItem(
                headlineContent = {
                    Text(
                        text = factoryResetLabel,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier =
                    Modifier
                        .clickable { onEvent(SettingsEvent.FactoryResetRequested) }
                        .semantics { contentDescription = factoryResetLabel },
            )
        }
    }

    FactoryResetDialogs(
        step = state.factoryResetStep,
        confirmText = state.factoryResetConfirmText,
        inProgress = state.factoryResetInProgress,
        onEvent = onEvent,
    )
}

@Composable
private fun FactoryResetDialogs(
    step: FactoryResetStep,
    confirmText: String,
    inProgress: Boolean,
    onEvent: (SettingsEvent) -> Unit,
) {
    when (step) {
        FactoryResetStep.Idle -> Unit
        FactoryResetStep.Confirm ->
            AlertDialog(
                onDismissRequest = { onEvent(SettingsEvent.FactoryResetDismissed) },
                title = { Text(stringResource(R.string.factory_reset_dialog_title)) },
                text = { Text(stringResource(R.string.factory_reset_dialog_body)) },
                confirmButton = {
                    Button(
                        onClick = { onEvent(SettingsEvent.FactoryResetContinued) },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                    ) {
                        Text(stringResource(R.string.factory_reset_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(SettingsEvent.FactoryResetDismissed) }) {
                        Text(stringResource(R.string.factory_reset_cancel))
                    }
                },
            )
        FactoryResetStep.TypeWord -> {
            val confirmWord = stringResource(R.string.factory_reset_confirm_word)
            AlertDialog(
                onDismissRequest = { onEvent(SettingsEvent.FactoryResetDismissed) },
                title = { Text(stringResource(R.string.factory_reset_type_title)) },
                text = {
                    Column {
                        Text(stringResource(R.string.factory_reset_type_prompt, confirmWord))
                        OutlinedTextField(
                            value = confirmText,
                            onValueChange = { onEvent(SettingsEvent.FactoryResetConfirmTextChanged(it)) },
                            singleLine = true,
                            enabled = !inProgress,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = Spacing.s)
                                    .testTag(FactoryResetConfirmFieldTag),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { onEvent(SettingsEvent.FactoryResetConfirmed) },
                        enabled = confirmText == confirmWord && !inProgress,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        modifier = Modifier.testTag(FactoryResetConfirmButtonTag),
                    ) {
                        Text(stringResource(R.string.factory_reset_cta))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(SettingsEvent.FactoryResetDismissed) }) {
                        Text(stringResource(R.string.factory_reset_cancel))
                    }
                },
            )
        }
    }
}

private fun relaunchApplication(context: android.content.Context) {
    val component =
        checkNotNull(
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.component,
        )
    context.startActivity(Intent.makeRestartActivityTask(component))
    Process.killProcess(Process.myPid())
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier.padding(
                start = Spacing.l,
                end = Spacing.l,
                top = Spacing.l,
                bottom = Spacing.s,
            ),
    )
}

private val ThemeMode.labelRes: Int
    get() =
        when (this) {
            ThemeMode.System -> R.string.theme_system
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.Dark -> R.string.theme_dark
        }

private val AppLanguage.labelRes: Int
    get() =
        when (this) {
            AppLanguage.System -> R.string.language_system
            AppLanguage.English -> R.string.language_en
            AppLanguage.Russian -> R.string.language_ru
        }
