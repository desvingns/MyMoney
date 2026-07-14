package com.kshavrin.mymoney.feature.settings.root

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.language.AppLanguage

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
        }
    }
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
