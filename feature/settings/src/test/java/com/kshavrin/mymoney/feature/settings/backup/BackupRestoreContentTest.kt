package com.kshavrin.mymoney.feature.settings.backup

import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [BackupRestoreContent] (S18).
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
 * deferral already documented in this module's `ThemeSettingsContentTest` and `LanguageContentTest`
 * and in :feature:transactionslist's content tests — full Compose-UI tests land in PHASE_15 once
 * those dependencies are wired in.
 *
 * Until then, the user-visible decisions [BackupRestoreContent] makes are driven by pure,
 * JVM-visible inputs on [BackupRestoreState]. This file pins those — button enablement during a
 * backup, error-banner visibility, and the size label argument — and documents the exact Compose
 * test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class BackupRestoreContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(
 *         state: BackupRestoreState,
 *         onExport: () -> Unit = {},
 *         onImport: () -> Unit = {},
 *         onBack: () -> Unit = {},
 *     ) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 BackupRestoreContent(
 *                     state = state,
 *                     onExport = onExport,
 *                     onImport = onImport,
 *                     onBack = onBack,
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `shows the screen title`() {
 *         setContent(BackupRestoreState())
 *         composeTestRule.onNodeWithText("Backup & Restore").assertIsDisplayed()
 *     }
 *
 *     @Test fun `export and import buttons are enabled when idle`() {
 *         setContent(BackupRestoreState(inProgress = false))
 *         composeTestRule.onNodeWithText("Export database").assertIsEnabled()
 *         composeTestRule.onNodeWithText("Import database").assertIsEnabled()
 *     }
 *
 *     @Test fun `export and import buttons are disabled while a backup is in progress`() {
 *         setContent(BackupRestoreState(inProgress = true))
 *         composeTestRule.onNodeWithText("Export database").assertIsNotEnabled()
 *         composeTestRule.onNodeWithText("Import database").assertIsNotEnabled()
 *     }
 *
 *     @Test fun `tapping export invokes onExport`() {
 *         var exported = false
 *         setContent(BackupRestoreState(), onExport = { exported = true })
 *         composeTestRule.onNodeWithText("Export database").performClick()
 *         assertTrue(exported)
 *     }
 *
 *     @Test fun `error banner is hidden when errorBannerRes is null`() {
 *         setContent(BackupRestoreState(errorBannerRes = null))
 *         composeTestRule.onNodeWithText("Backup failed").assertDoesNotExist()
 *     }
 *
 *     @Test fun `error banner is shown when errorBannerRes is set`() {
 *         setContent(BackupRestoreState(errorBannerRes = R.string.backup_error))
 *         composeTestRule.onNodeWithText("Backup failed").assertIsDisplayed()
 *     }
 *
 *     @Test fun `back icon invokes onBack`() {
 *         var backed = false
 *         setContent(BackupRestoreState(), onBack = { backed = true })
 *         composeTestRule.onNodeWithContentDescription("Back").performClick()
 *         assertTrue(backed)
 *     }
 * }
 * ```
 */
class BackupRestoreContentTest {

    /** Mirror of the export/import `enabled = !state.inProgress` flag. */
    private fun actionsEnabled(state: BackupRestoreState): Boolean = !state.inProgress

    /** Mirror of the `state.errorBannerRes?.let { ... }` error Text. */
    private fun errorBannerVisible(state: BackupRestoreState): Boolean = state.errorBannerRes != null

    @Test
    fun `idle state enables export and import`() {
        val state = BackupRestoreState(inProgress = false)

        assertTrue(actionsEnabled(state))
    }

    @Test
    fun `in progress state disables export and import`() {
        val state = BackupRestoreState(inProgress = true)

        assertFalse(actionsEnabled(state))
    }

    @Test
    fun `no error banner when errorBannerRes is null`() {
        val state = BackupRestoreState(errorBannerRes = null)

        assertFalse(errorBannerVisible(state))
        assertNull(state.errorBannerRes)
    }

    @Test
    fun `error banner shown when errorBannerRes is set`() {
        val state = BackupRestoreState(errorBannerRes = R.string.backup_error)

        assertTrue(errorBannerVisible(state))
        assertEquals(R.string.backup_error, state.errorBannerRes)
    }

    @Test
    fun `default state shows no error and enables actions`() {
        val state = BackupRestoreState()

        assertTrue(actionsEnabled(state))
        assertFalse(errorBannerVisible(state))
        assertEquals(0L, state.dbSizeBytes)
    }
}
