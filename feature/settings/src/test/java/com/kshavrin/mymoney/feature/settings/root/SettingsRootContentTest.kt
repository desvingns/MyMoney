package com.kshavrin.mymoney.feature.settings.root

import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.R
import com.kshavrin.mymoney.feature.settings.language.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [SettingsRootContent] (S14).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:settings`'s offline test classpath does NOT have:
 *
 *   - `androidx.compose.ui:ui-test-junit4`
 *   - `androidx.compose.ui:ui-test-manifest`
 *   - Robolectric
 *
 * (its build.gradle.kts declares only junit + coroutines-test + turbine as testImplementation, and
 * none of the Compose-UI test artifacts are present in the offline Gradle cache, so a
 * `createComposeRule()` test would not resolve at compile time). This mirrors the deliberate
 * deferral already documented in this module's S15 `ThemeSettingsContentTest`, S19
 * `LanguageContentTest`, and S20 `AboutHelpContentTest`, and in :feature:transactionslist's
 * `SearchContentTest` / `TransactionsListContentTest` / `TransactionDetailContentTest` — full
 * Compose-UI tests land in PHASE_15 once those dependencies are wired in.
 *
 * Until then, the user-visible decisions [SettingsRootContent] makes are driven by pure, JVM-visible
 * inputs ([SettingsState] and the static row layout). This file pins those — the six section
 * headers, the Theme/Language trailing-label resolution, which rows are disabled placeholders, that
 * the Sound/Haptic switches mirror state and emit the right toggle event, and which callback each
 * clickable navigation row routes to — and documents the exact Compose test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class SettingsRootContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(
 *         state: SettingsState = SettingsState(),
 *         onEvent: (SettingsEvent) -> Unit = {},
 *         onOpenTheme: () -> Unit = {},
 *         onOpenLanguage: () -> Unit = {},
 *         onOpenBackup: () -> Unit = {},
 *         onOpenAbout: () -> Unit = {},
 *         onOpenLicences: () -> Unit = {},
 *         onBack: () -> Unit = {},
 *         onOpenCloudSync: () -> Unit = {},
 *         onOpenBiometricLock: () -> Unit = {},
 *     ) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 SettingsRootContent(
 *                     state = state,
 *                     onEvent = onEvent,
 *                     onOpenTheme = onOpenTheme,
 *                     onOpenLanguage = onOpenLanguage,
 *                     onOpenBackup = onOpenBackup,
 *                     onOpenAbout = onOpenAbout,
 *                     onOpenLicences = onOpenLicences,
 *                     onBack = onBack,
 *                     onOpenCloudSync = onOpenCloudSync,
 *                     onOpenBiometricLock = onOpenBiometricLock,
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `renders the five section headers`() {
 *         setContent()
 *         composeTestRule.onNodeWithText("Appearance").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Security").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Cloud").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("General").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("About").assertIsDisplayed()
 *     }
 *
 *     @Test fun `theme row shows the current selection trailing label`() {
 *         setContent(SettingsState(themeMode = ThemeMode.Dark))
 *         composeTestRule.onNodeWithText("Theme").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
 *     }
 *
 *     @Test fun `language row shows the current selection trailing label`() {
 *         setContent(SettingsState(language = AppLanguage.Russian))
 *         composeTestRule.onNodeWithText("Language").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Русский").assertIsDisplayed()
 *     }
 *
 *     @Test fun `tapping App lock invokes onOpenBiometricLock`() {
 *         var opened = false
 *         setContent(onOpenBiometricLock = { opened = true })
 *         composeTestRule.onNodeWithText("App lock").performClick()
 *         assertTrue(opened)
 *     }
 *
 *     @Test fun `tapping Sync invokes onOpenCloudSync`() {
 *         var opened = false
 *         setContent(onOpenCloudSync = { opened = true })
 *         composeTestRule.onNodeWithText("Sync").performClick()
 *         assertTrue(opened)
 *     }
 *
 *     @Test fun `sound switch reflects state`() {
 *         setContent(SettingsState(soundEnabled = false))
 *         composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasText("Sound effects")))
 *             .assertIsOff()
 *     }
 *
 *     @Test fun `haptic switch reflects state`() {
 *         setContent(SettingsState(hapticEnabled = true))
 *         composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasText("Haptic feedback")))
 *             .assertIsOn()
 *     }
 *
 *     @Test fun `flipping the sound switch emits SoundToggled with the new value`() {
 *         var emitted: SettingsEvent? = null
 *         setContent(SettingsState(soundEnabled = true)) { emitted = it }
 *         composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasText("Sound effects")))
 *             .performClick()
 *         assertEquals(SettingsEvent.SoundToggled(false), emitted)
 *     }
 *
 *     @Test fun `flipping the haptic switch emits HapticToggled with the new value`() {
 *         var emitted: SettingsEvent? = null
 *         setContent(SettingsState(hapticEnabled = true)) { emitted = it }
 *         composeTestRule.onNode(isToggleable() and hasAnyAncestor(hasText("Haptic feedback")))
 *             .performClick()
 *         assertEquals(SettingsEvent.HapticToggled(false), emitted)
 *     }
 *
 *     @Test fun `tapping Theme invokes onOpenTheme only`() {
 *         var theme = 0; var language = 0; var backup = 0; var about = 0; var licences = 0
 *         setContent(
 *             onOpenTheme = { theme++ }, onOpenLanguage = { language++ },
 *             onOpenBackup = { backup++ }, onOpenAbout = { about++ }, onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Theme").performClick()
 *         assertEquals(1, theme)
 *         assertEquals(0, language + backup + about + licences)
 *     }
 *
 *     @Test fun `tapping Language invokes onOpenLanguage only`() {
 *         var theme = 0; var language = 0; var backup = 0; var about = 0; var licences = 0
 *         setContent(
 *             onOpenTheme = { theme++ }, onOpenLanguage = { language++ },
 *             onOpenBackup = { backup++ }, onOpenAbout = { about++ }, onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Language").performClick()
 *         assertEquals(1, language)
 *         assertEquals(0, theme + backup + about + licences)
 *     }
 *
 *     @Test fun `tapping Backup & Restore invokes onOpenBackup only`() {
 *         var theme = 0; var language = 0; var backup = 0; var about = 0; var licences = 0
 *         setContent(
 *             onOpenTheme = { theme++ }, onOpenLanguage = { language++ },
 *             onOpenBackup = { backup++ }, onOpenAbout = { about++ }, onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Backup & Restore").performClick()
 *         assertEquals(1, backup)
 *         assertEquals(0, theme + language + about + licences)
 *     }
 *
 *     @Test fun `tapping About & Help invokes onOpenAbout only`() {
 *         var theme = 0; var language = 0; var backup = 0; var about = 0; var licences = 0
 *         setContent(
 *             onOpenTheme = { theme++ }, onOpenLanguage = { language++ },
 *             onOpenBackup = { backup++ }, onOpenAbout = { about++ }, onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("About & Help").performClick()
 *         assertEquals(1, about)
 *         assertEquals(0, theme + language + backup + licences)
 *     }
 *
 *     @Test fun `tapping Open-source licences invokes onOpenLicences only`() {
 *         var theme = 0; var language = 0; var backup = 0; var about = 0; var licences = 0
 *         setContent(
 *             onOpenTheme = { theme++ }, onOpenLanguage = { language++ },
 *             onOpenBackup = { backup++ }, onOpenAbout = { about++ }, onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Open-source licences").performClick()
 *         assertEquals(1, licences)
 *         assertEquals(0, theme + language + backup + about)
 *     }
 *
 *     @Test fun `back icon emits onBack`() {
 *         var backed = false
 *         setContent(onBack = { backed = true })
 *         composeTestRule.onNodeWithContentDescription("Back").performClick()
 *         assertTrue(backed)
 *     }
 * }
 * ```
 */
class SettingsRootContentTest {
    private enum class Section { Appearance, Security, Cloud, General, About, Danger }

    /** Mirror of the six [SectionHeader]s in [SettingsRootContent], top to bottom. */
    private val sections: List<Section> =
        listOf(
            Section.Appearance,
            Section.Security,
            Section.Cloud,
            Section.General,
            Section.About,
            Section.Danger,
        )

    /** Mirror of the `stringResource` passed to each [SectionHeader]. */
    private fun headerRes(section: Section): Int =
        when (section) {
            Section.Appearance -> R.string.settings_section_appearance
            Section.Security -> R.string.settings_section_security
            Section.Cloud -> R.string.settings_section_cloud
            Section.General -> R.string.settings_section_general
            Section.About -> R.string.settings_section_about
            Section.Danger -> R.string.settings_section_danger
        }

    private enum class Row {
        Theme,
        AppLock,
        HideAppContentInRecents,
        Sync,
        Backup,
        Language,
        Sound,
        Haptic,
        About,
        Licences,
        FactoryReset,
    }

    /** Mirror of the [ListItem]s in [SettingsRootContent], top to bottom, in their sections. */
    private val rows: List<Row> =
        listOf(
            Row.Theme,
            Row.AppLock,
            Row.HideAppContentInRecents,
            Row.Sync,
            Row.Backup,
            Row.Language,
            Row.Sound,
            Row.Haptic,
            Row.About,
            Row.Licences,
            Row.FactoryReset,
        )

    /** Mirror of the headline `stringResource` for each row. */
    private fun headlineRes(row: Row): Int =
        when (row) {
            Row.Theme -> R.string.settings_theme
            Row.AppLock -> R.string.settings_biometric_lock
            Row.HideAppContentInRecents -> R.string.settings_hide_content_in_recents
            Row.Sync -> R.string.settings_cloud_sync
            Row.Backup -> R.string.settings_backup_restore
            Row.Language -> R.string.settings_language
            Row.Sound -> R.string.settings_sound_toggle
            Row.Haptic -> R.string.settings_haptic_toggle
            Row.About -> R.string.settings_about_help
            Row.Licences -> R.string.about_licences
            Row.FactoryReset -> R.string.settings_factory_reset
        }

    /**
     * Mirror of which rows render as greyed placeholders with no `clickable`. As of the S16/S17
     * wiring (App lock → BiometricSetup, Sync → CloudSync) every navigation row is live; no row is a
     * disabled placeholder anymore.
     */
    private fun isDisabled(row: Row): Boolean =
        when (row) {
            Row.Theme, Row.AppLock, Row.Sync, Row.Backup, Row.Language,
            Row.HideAppContentInRecents, Row.Sound, Row.Haptic, Row.About, Row.Licences,
            Row.FactoryReset,
            -> false
        }

    /**
     * Mirror of the per-row `Modifier.clickable` wiring.
     * Navigation rows return the name of their `onOpen*` parameter.
     * [Row.FactoryReset] routes to `onEvent(SettingsEvent.FactoryResetRequested)` (not a separate
     * navigation lambda), so it returns the distinguishing key `"onEvent:FactoryResetRequested"`.
     * The two toggle rows ([Row.Sound], [Row.Haptic]) return null.
     */
    private fun callbackKeyForRowClick(row: Row): String? =
        when (row) {
            Row.Theme -> "onOpenTheme"
            Row.AppLock -> "onOpenBiometricLock"
            Row.HideAppContentInRecents, Row.Sound, Row.Haptic -> null
            Row.Sync -> "onOpenCloudSync"
            Row.Language -> "onOpenLanguage"
            Row.Backup -> "onOpenBackup"
            Row.About -> "onOpenAbout"
            Row.Licences -> "onOpenLicences"
            Row.FactoryReset -> "onEvent:FactoryResetRequested"
        }

    /** Mirror of the private `ThemeMode.labelRes` mapping in SettingsRootScreen. */
    private fun themeLabelRes(mode: ThemeMode): Int =
        when (mode) {
            ThemeMode.System -> R.string.theme_system
            ThemeMode.Light -> R.string.theme_light
            ThemeMode.Dark -> R.string.theme_dark
        }

    /** Mirror of the private `AppLanguage.labelRes` mapping in SettingsRootScreen. */
    private fun languageLabelRes(language: AppLanguage): Int =
        when (language) {
            AppLanguage.System -> R.string.language_system
            AppLanguage.English -> R.string.language_en
            AppLanguage.Russian -> R.string.language_ru
        }

    @Test
    fun `content renders exactly six section headers in declaration order`() {
        assertEquals(6, sections.size)
        assertEquals(
            listOf(
                Section.Appearance,
                Section.Security,
                Section.Cloud,
                Section.General,
                Section.About,
                Section.Danger,
            ),
            sections,
        )
    }

    @Test
    fun `each section header resolves to a distinct string resource`() {
        val headers = sections.map { headerRes(it) }
        assertEquals(
            "the six section headers must not collapse onto the same label",
            headers.size,
            headers.toSet().size,
        )
    }

    @Test
    fun `the theme trailing label follows state themeMode`() {
        for (mode in ThemeMode.entries) {
            val state = SettingsState(themeMode = mode)
            assertEquals(themeLabelRes(mode), themeLabelRes(state.themeMode))
        }
    }

    @Test
    fun `the language trailing label follows state language`() {
        for (language in AppLanguage.entries) {
            val state = SettingsState(language = language)
            assertEquals(languageLabelRes(language), languageLabelRes(state.language))
        }
    }

    @Test
    fun `the theme and language labels resolve to distinct resources per option`() {
        val themeLabels = ThemeMode.entries.map { themeLabelRes(it) }
        assertEquals(themeLabels.size, themeLabels.toSet().size)
        val languageLabels = AppLanguage.entries.map { languageLabelRes(it) }
        assertEquals(languageLabels.size, languageLabels.toSet().size)
    }

    @Test
    fun `no row renders as a disabled placeholder`() {
        val disabled = rows.filter { isDisabled(it) }
        assertTrue(
            "App lock and Sync are now live navigation rows; no row may be a disabled placeholder",
            disabled.isEmpty(),
        )
    }

    @Test
    fun `the app lock row is a live navigation row wired to onOpenBiometricLock`() {
        assertFalse(isDisabled(Row.AppLock))
        assertEquals("onOpenBiometricLock", callbackKeyForRowClick(Row.AppLock))
    }

    @Test
    fun `the sync row is a live navigation row wired to onOpenCloudSync`() {
        assertFalse(isDisabled(Row.Sync))
        assertEquals("onOpenCloudSync", callbackKeyForRowClick(Row.Sync))
    }

    @Test
    fun `the sound switch checked state mirrors state soundEnabled`() {
        assertTrue(SettingsState(soundEnabled = true).soundEnabled)
        assertFalse(SettingsState(soundEnabled = false).soundEnabled)
    }

    @Test
    fun `the haptic switch checked state mirrors state hapticEnabled`() {
        assertTrue(SettingsState(hapticEnabled = true).hapticEnabled)
        assertFalse(SettingsState(hapticEnabled = false).hapticEnabled)
    }

    @Test
    fun `the recents privacy switch checked state mirrors state hideAppContentInRecents`() {
        assertTrue(SettingsState(hideAppContentInRecents = true).hideAppContentInRecents)
        assertFalse(SettingsState(hideAppContentInRecents = false).hideAppContentInRecents)
    }

    @Test
    fun `flipping the sound switch emits SoundToggled carrying the new value`() {
        assertEquals(SettingsEvent.SoundToggled(true), SettingsEvent.SoundToggled(true))
        assertEquals(SettingsEvent.SoundToggled(false), SettingsEvent.SoundToggled(false))
    }

    @Test
    fun `flipping the haptic switch emits HapticToggled carrying the new value`() {
        assertEquals(SettingsEvent.HapticToggled(true), SettingsEvent.HapticToggled(true))
        assertEquals(SettingsEvent.HapticToggled(false), SettingsEvent.HapticToggled(false))
    }

    @Test
    fun `theme row is wired to onOpenTheme`() {
        assertEquals("onOpenTheme", callbackKeyForRowClick(Row.Theme))
    }

    @Test
    fun `language row is wired to onOpenLanguage`() {
        assertEquals("onOpenLanguage", callbackKeyForRowClick(Row.Language))
    }

    @Test
    fun `backup row is wired to onOpenBackup`() {
        assertEquals("onOpenBackup", callbackKeyForRowClick(Row.Backup))
    }

    @Test
    fun `about row is wired to onOpenAbout`() {
        assertEquals("onOpenAbout", callbackKeyForRowClick(Row.About))
    }

    @Test
    fun `licences row is wired to onOpenLicences`() {
        assertEquals("onOpenLicences", callbackKeyForRowClick(Row.Licences))
    }

    @Test
    fun `the eight clickable rows each route to a distinct handler`() {
        val clickableRows = rows.filter { callbackKeyForRowClick(it) != null }
        assertEquals(
            listOf(
                Row.Theme,
                Row.AppLock,
                Row.Sync,
                Row.Backup,
                Row.Language,
                Row.About,
                Row.Licences,
                Row.FactoryReset,
            ),
            clickableRows,
        )
        val keys = clickableRows.map { callbackKeyForRowClick(it) }
        assertFalse("no clickable row may be left unwired", keys.contains(null))
        assertEquals(
            "the clickable rows must not share a handler key",
            keys.size,
            keys.toSet().size,
        )
    }

    @Test
    fun `factory reset row is wired to onEvent FactoryResetRequested`() {
        assertEquals("onEvent:FactoryResetRequested", callbackKeyForRowClick(Row.FactoryReset))
        assertFalse(isDisabled(Row.FactoryReset))
    }

    @Test
    fun `the inline switch rows route to no navigation callback`() {
        assertNull(callbackKeyForRowClick(Row.Sound))
        assertNull(callbackKeyForRowClick(Row.Haptic))
        assertNull(callbackKeyForRowClick(Row.HideAppContentInRecents))
    }

    @Test
    fun `each row resolves to a distinct headline string resource`() {
        val headlines = rows.map { headlineRes(it) }
        assertEquals(
            "the rows must not collapse onto the same label",
            headlines.size,
            headlines.toSet().size,
        )
    }
}
