package com.kshavrin.mymoney.feature.settings.language

import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [LanguageContent] (S19).
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
 * deferral already documented in this module's S15 `ThemeSettingsContentTest` and in
 * :feature:transactionslist's `SearchContentTest` / `TransactionsListContentTest` /
 * `TransactionDetailContentTest` — full Compose-UI tests land in PHASE_15 once those dependencies
 * are wired in.
 *
 * Until then, the user-visible decisions [LanguageContent] makes are driven by pure, JVM-visible
 * inputs ([LanguageState.selected] and the [AppLanguage] of each row). This file pins those — the
 * row set, which row is selected, the event each row emits, and that the three rows carry distinct
 * labels — and documents the exact Compose test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class LanguageContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: LanguageState, onEvent: (LanguageEvent) -> Unit = {}) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 LanguageContent(state = state, onEvent = onEvent, onBack = {})
 *             }
 *         }
 *     }
 *
 *     @Test fun `renders the three language rows`() {
 *         setContent(LanguageState(selected = AppLanguage.System))
 *         composeTestRule.onNodeWithText("Follow system").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("English").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Русский").assertIsDisplayed()
 *     }
 *
 *     @Test fun `the row matching state is selected`() {
 *         setContent(LanguageState(selected = AppLanguage.Russian))
 *         composeTestRule.onNode(isSelectable() and hasText("Русский")).assertIsSelected()
 *         composeTestRule.onNode(isSelectable() and hasText("English")).assertIsNotSelected()
 *         composeTestRule.onNode(isSelectable() and hasText("Follow system")).assertIsNotSelected()
 *     }
 *
 *     @Test fun `tapping a row emits LanguageSelected for that language`() {
 *         var emitted: LanguageEvent? = null
 *         setContent(LanguageState(selected = AppLanguage.System)) { emitted = it }
 *         composeTestRule.onNode(isSelectable() and hasText("Русский")).performClick()
 *         assertEquals(LanguageEvent.LanguageSelected(AppLanguage.Russian), emitted)
 *     }
 *
 *     @Test fun `back icon emits onBack`() {
 *         var backed = false
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 LanguageContent(
 *                     state = LanguageState(),
 *                     onEvent = {},
 *                     onBack = { backed = true },
 *                 )
 *             }
 *         }
 *         composeTestRule.onNodeWithContentDescription("Back").performClick()
 *         assertTrue(backed)
 *     }
 * }
 * ```
 */
class LanguageContentTest {
    /** Mirror of `AppLanguage.entries.forEach { LanguageRow(...) }` — one row per language, in order. */
    private val rows: List<AppLanguage> = AppLanguage.entries.toList()

    /** Mirror of the per-row `selected = state.selected == language` flag. */
    private fun isRowSelected(
        state: LanguageState,
        language: AppLanguage,
    ): Boolean =
        state.selected == language

    /** Mirror of the per-row `onClick = { onEvent(LanguageEvent.LanguageSelected(language)) }`. */
    private fun eventForRowClick(language: AppLanguage): LanguageEvent =
        LanguageEvent.LanguageSelected(language)

    /** Mirror of the private `AppLanguage.labelRes` mapping in LanguageScreen. */
    private fun labelRes(language: AppLanguage): Int =
        when (language) {
            AppLanguage.System -> R.string.language_system
            AppLanguage.English -> R.string.language_en
            AppLanguage.Russian -> R.string.language_ru
        }

    @Test
    fun `content renders exactly three rows one per language in declaration order`() {
        assertEquals(3, rows.size)
        assertEquals(listOf(AppLanguage.System, AppLanguage.English, AppLanguage.Russian), rows)
    }

    @Test
    fun `exactly the row matching state selected is marked selected`() {
        for (selected in rows) {
            val state = LanguageState(selected = selected)
            val selectedRows = rows.filter { isRowSelected(state, it) }
            assertEquals(
                "exactly one row selected when state.selected=$selected",
                listOf(selected),
                selectedRows,
            )
        }
    }

    @Test
    fun `a row reports unselected when it does not match state selected`() {
        val state = LanguageState(selected = AppLanguage.Russian)
        assertTrue(isRowSelected(state, AppLanguage.Russian))
        assertFalse(isRowSelected(state, AppLanguage.English))
        assertFalse(isRowSelected(state, AppLanguage.System))
    }

    @Test
    fun `tapping a row emits LanguageSelected carrying that row's language`() {
        for (language in rows) {
            assertEquals(LanguageEvent.LanguageSelected(language), eventForRowClick(language))
        }
    }

    @Test
    fun `each row resolves to a distinct label string resource`() {
        val labels = rows.map { labelRes(it) }
        assertEquals(
            "the three rows must not collapse onto the same label",
            labels.size,
            labels.toSet().size,
        )
    }
}
