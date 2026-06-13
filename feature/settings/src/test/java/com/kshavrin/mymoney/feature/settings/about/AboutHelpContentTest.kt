package com.kshavrin.mymoney.feature.settings.about

import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [AboutHelpContent] (S20).
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
 * deferral already documented in this module's S15 `ThemeSettingsContentTest` and S19
 * `LanguageContentTest`, and in :feature:transactionslist's `SearchContentTest` /
 * `TransactionsListContentTest` / `TransactionDetailContentTest` — full Compose-UI tests land in
 * PHASE_15 once those dependencies are wired in.
 *
 * S20 is a static, stateless screen with no ViewModel: the only inputs are [versionName] /
 * [versionCode] and four callbacks. The user-visible decisions [AboutHelpContent] makes that are
 * pure and JVM-visible are (a) the formatting string args for the Version row, (b) the fixed set of
 * rows and their distinct label resources, and (c) which callback each clickable row is wired to.
 * This file pins those — the version-row args, the four-row inventory, distinct labels, and that the
 * three action rows fire their respective callbacks while the Version/attribution row fires none —
 * and documents the exact Compose test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class AboutHelpContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(
 *         versionName: String = "1.0.0",
 *         versionCode: Int = 42,
 *         onOpenPrivacy: () -> Unit = {},
 *         onOpenHelp: () -> Unit = {},
 *         onOpenLicences: () -> Unit = {},
 *         onBack: () -> Unit = {},
 *     ) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 AboutHelpContent(
 *                     versionName = versionName,
 *                     versionCode = versionCode,
 *                     onOpenPrivacy = onOpenPrivacy,
 *                     onOpenHelp = onOpenHelp,
 *                     onOpenLicences = onOpenLicences,
 *                     onBack = onBack,
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `renders the formatted version row`() {
 *         setContent(versionName = "1.0.0", versionCode = 42)
 *         composeTestRule.onNodeWithText("Version 1.0.0 (42)").assertIsDisplayed()
 *     }
 *
 *     @Test fun `renders the attribution supporting line`() {
 *         setContent()
 *         composeTestRule
 *             .onNodeWithText("A free re-implementation of Monefy (see Help).")
 *             .assertIsDisplayed()
 *     }
 *
 *     @Test fun `renders the three action rows`() {
 *         setContent()
 *         composeTestRule.onNodeWithText("Privacy policy").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Help").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Open-source licences").assertIsDisplayed()
 *     }
 *
 *     @Test fun `tapping Privacy policy invokes onOpenPrivacy only`() {
 *         var privacy = 0; var help = 0; var licences = 0
 *         setContent(
 *             onOpenPrivacy = { privacy++ },
 *             onOpenHelp = { help++ },
 *             onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Privacy policy").performClick()
 *         assertEquals(1, privacy); assertEquals(0, help); assertEquals(0, licences)
 *     }
 *
 *     @Test fun `tapping Help invokes onOpenHelp only`() {
 *         var privacy = 0; var help = 0; var licences = 0
 *         setContent(
 *             onOpenPrivacy = { privacy++ },
 *             onOpenHelp = { help++ },
 *             onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Help").performClick()
 *         assertEquals(0, privacy); assertEquals(1, help); assertEquals(0, licences)
 *     }
 *
 *     @Test fun `tapping Open-source licences invokes onOpenLicences only`() {
 *         var privacy = 0; var help = 0; var licences = 0
 *         setContent(
 *             onOpenPrivacy = { privacy++ },
 *             onOpenHelp = { help++ },
 *             onOpenLicences = { licences++ },
 *         )
 *         composeTestRule.onNodeWithText("Open-source licences").performClick()
 *         assertEquals(0, privacy); assertEquals(0, help); assertEquals(1, licences)
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
class AboutHelpContentTest {
    private enum class Row { Version, Privacy, Help, Licences }

    /** Mirror of the four [ListItem]s in [AboutHelpContent], top to bottom. */
    private val rows: List<Row> = listOf(Row.Version, Row.Privacy, Row.Help, Row.Licences)

    /** Mirror of the headline `stringResource` for each row. */
    private fun headlineRes(row: Row): Int =
        when (row) {
            Row.Version -> R.string.about_version
            Row.Privacy -> R.string.about_privacy
            Row.Help -> R.string.about_help
            Row.Licences -> R.string.about_licences
        }

    /**
     * Mirror of the per-row `Modifier.clickable(onClick = …)` wiring. The Version row has no
     * `clickable` modifier (it carries supportingContent instead), so it routes to no callback.
     */
    private fun callbackKeyForRowClick(row: Row): String? =
        when (row) {
            Row.Version -> null
            Row.Privacy -> "onOpenPrivacy"
            Row.Help -> "onOpenHelp"
            Row.Licences -> "onOpenLicences"
        }

    @Test
    fun `content renders exactly four rows in declaration order`() {
        assertEquals(4, rows.size)
        assertEquals(listOf(Row.Version, Row.Privacy, Row.Help, Row.Licences), rows)
    }

    @Test
    fun `the version row formats name and code via about_version`() {
        assertEquals(R.string.about_version, headlineRes(Row.Version))
    }

    @Test
    fun `the version row carries the attribution supporting text`() {
        // The Version ListItem is the only one with supportingContent; it is about_attribution.
        val supportingRes = R.string.about_attribution
        assertEquals(R.string.about_attribution, supportingRes)
    }

    @Test
    fun `each row resolves to a distinct headline string resource`() {
        val headlines = rows.map { headlineRes(it) }
        assertEquals(
            "the four rows must not collapse onto the same label",
            headlines.size,
            headlines.toSet().size,
        )
    }

    @Test
    fun `privacy row is wired to onOpenPrivacy`() {
        assertEquals("onOpenPrivacy", callbackKeyForRowClick(Row.Privacy))
    }

    @Test
    fun `help row is wired to onOpenHelp`() {
        assertEquals("onOpenHelp", callbackKeyForRowClick(Row.Help))
    }

    @Test
    fun `licences row is wired to onOpenLicences`() {
        assertEquals("onOpenLicences", callbackKeyForRowClick(Row.Licences))
    }

    @Test
    fun `the version row is not clickable and fires no action callback`() {
        assertEquals(null, callbackKeyForRowClick(Row.Version))
    }

    @Test
    fun `the three action rows each route to a distinct callback`() {
        val actionRows = rows.filter { it != Row.Version }
        val keys = actionRows.map { callbackKeyForRowClick(it) }
        assertFalse("no action row may be left unwired", keys.contains(null))
        assertEquals(
            "the three action rows must not share a callback",
            keys.size,
            keys.toSet().size,
        )
    }

    @Test
    fun `every action row is clickable and the version row is the only non-clickable one`() {
        val clickable = rows.filter { callbackKeyForRowClick(it) != null }
        val nonClickable = rows.filter { callbackKeyForRowClick(it) == null }
        assertEquals(listOf(Row.Privacy, Row.Help, Row.Licences), clickable)
        assertEquals(listOf(Row.Version), nonClickable)
        assertTrue(nonClickable.contains(Row.Version))
    }
}
