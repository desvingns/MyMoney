package com.kshavrin.mymoney.feature.settings.root

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRootContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes settings back callback`() {
        var backed = false

        setContent(onBack = { backed = true })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(backed)
        }
    }

    @Test
    fun `destination rows invoke their settings callbacks`() {
        val opened = mutableListOf<String>()

        setContent(
            onOpenTheme = { opened += "theme" },
            onOpenChartSettings = { opened += "chart" },
            onOpenBiometricLock = { opened += "lock" },
            onOpenCloudSync = { opened += "cloud" },
            onOpenBackup = { opened += "backup" },
            onOpenLanguage = { opened += "language" },
            onOpenAbout = { opened += "about" },
            onOpenLicences = { opened += "licences" },
        )

        listOf(
            R.string.settings_theme to "theme",
            R.string.settings_chart_settings to "chart",
            R.string.settings_biometric_lock to "lock",
            R.string.settings_cloud_sync to "cloud",
            R.string.settings_backup_restore to "backup",
            R.string.settings_language to "language",
            R.string.settings_about_help to "about",
            R.string.about_licences to "licences",
        ).forEach { (labelRes, _) ->
            composeTestRule
                .onNodeWithText(targetString(labelRes))
                .performScrollTo()
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf("theme", "chart", "lock", "cloud", "backup", "language", "about", "licences"),
                opened,
            )
        }
    }

    @Test
    fun `current labels and inline switches reflect settings state`() {
        val emitted = mutableListOf<SettingsEvent>()

        setContent(
            state =
                SettingsState(
                    themeMode = ThemeMode.Dark,
                    language = AppLanguage.Russian,
                    soundEnabled = true,
                    hapticEnabled = false,
                ),
            onEvent = { event -> emitted += event },
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.theme_dark))
            .assertExists()
        composeTestRule
            .onNodeWithText(targetString(R.string.language_ru))
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_sound_toggle))
            .performScrollTo()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_sound_toggle))
            .assertExists()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_haptic_toggle))
            .performScrollTo()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_haptic_toggle))
            .assertExists()

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_sound_toggle))
            .assertIsOn()
            .performClick()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_haptic_toggle))
            .assertIsOff()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(
                listOf(
                    SettingsEvent.SoundToggled(false),
                    SettingsEvent.HapticToggled(true),
                ),
                emitted,
            )
        }
    }

    private fun setContent(
        state: SettingsState = SettingsState(),
        onEvent: (SettingsEvent) -> Unit = {},
        onOpenTheme: () -> Unit = {},
        onOpenChartSettings: () -> Unit = {},
        onOpenLanguage: () -> Unit = {},
        onOpenBackup: () -> Unit = {},
        onOpenAbout: () -> Unit = {},
        onOpenLicences: () -> Unit = {},
        onBack: () -> Unit = {},
        onOpenCloudSync: () -> Unit = {},
        onOpenBiometricLock: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                SettingsRootContent(
                    state = state,
                    onEvent = onEvent,
                    onOpenTheme = onOpenTheme,
                    onOpenChartSettings = onOpenChartSettings,
                    onOpenLanguage = onOpenLanguage,
                    onOpenBackup = onOpenBackup,
                    onOpenAbout = onOpenAbout,
                    onOpenLicences = onOpenLicences,
                    onBack = onBack,
                    onOpenCloudSync = onOpenCloudSync,
                    onOpenBiometricLock = onOpenBiometricLock,
                )
            }
        }
    }

    private fun targetString(resourceId: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId)
}
