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
import androidx.compose.material3.ListItemDefaults
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(stringResource(R.string.settings_section_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                trailingContent = { Text(stringResource(state.themeMode.labelRes)) },
                modifier = Modifier.clickable(onClick = onOpenTheme),
            )

            SectionHeader(stringResource(R.string.settings_section_security))
            DisabledListItem(stringResource(R.string.settings_biometric_lock))

            SectionHeader(stringResource(R.string.settings_section_cloud))
            DisabledListItem(stringResource(R.string.settings_cloud_sync))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_backup_restore)) },
                modifier = Modifier.clickable(onClick = onOpenBackup),
            )

            SectionHeader(stringResource(R.string.settings_section_general))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_language)) },
                trailingContent = { Text(stringResource(state.language.labelRes)) },
                modifier = Modifier.clickable(onClick = onOpenLanguage),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_sound_toggle)) },
                trailingContent = {
                    Switch(
                        checked = state.soundEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.SoundToggled(it)) },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_haptic_toggle)) },
                trailingContent = {
                    Switch(
                        checked = state.hapticEnabled,
                        onCheckedChange = { onEvent(SettingsEvent.HapticToggled(it)) },
                    )
                },
            )

            SectionHeader(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_help)) },
                modifier = Modifier.clickable(onClick = onOpenAbout),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_licences)) },
                modifier = Modifier.clickable(onClick = onOpenLicences),
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
        modifier = Modifier.padding(
            start = Spacing.l,
            end = Spacing.l,
            top = Spacing.l,
            bottom = Spacing.s,
        ),
    )
}

@Composable
private fun DisabledListItem(title: String) {
    ListItem(
        headlineContent = { Text(title) },
        colors = ListItemDefaults.colors(
            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        ),
    )
}

private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.System -> R.string.language_system
        AppLanguage.English -> R.string.language_en
        AppLanguage.Russian -> R.string.language_ru
    }
