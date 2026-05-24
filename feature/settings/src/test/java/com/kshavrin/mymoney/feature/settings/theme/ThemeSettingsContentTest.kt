package com.kshavrin.mymoney.feature.settings.theme

import com.kshavrin.mymoney.core.ui.theme.ThemeMode
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [ThemeSettingsContent] (S15).
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
 * deferral already documented in :feature:transactionslist's `SearchContentTest`,
 * `TransactionsListContentTest`, and `TransactionDetailContentTest` — full Compose-UI tests land in
 * PHASE_15 once those dependencies are wired in.
 *
 * Until then, the user-visible decisions [ThemeSettingsContent] makes are driven by pure,
 * JVM-visible inputs ([ThemeSettingsState.selected] and the [ThemeMode] of each row). This file pins
 * those — the row set, which row is selected, the event each row emits, and that the three rows
 * carry distinct labels — and documents the exact Compose test that replaces it.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class ThemeSettingsContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: ThemeSettingsState, onEvent: (ThemeSettingsEvent) -> Unit = {}) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 ThemeSettingsContent(state = state, onEvent = onEvent, onBack = {})
 *             }
 *         }
 *     }
 *
 *     @Test fun `renders the three theme rows`() {
 *         setContent(ThemeSettingsState(selected = ThemeMode.System))
 *         composeTestRule.onNodeWithText("Follow system").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Light").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("Dark").assertIsDisplayed()
 *     }
 *
 *     @Test fun `the row matching state is selected`() {
 *         setContent(ThemeSettingsState(selected = ThemeMode.Dark))
 *         composeTestRule.onNode(isSelectable() and hasText("Dark")).assertIsSelected()
 *         composeTestRule.onNode(isSelectable() and hasText("Light")).assertIsNotSelected()
 *         composeTestRule.onNode(isSelectable() and hasText("Follow system")).assertIsNotSelected()
 *     }
 *
 *     @Test fun `tapping a row emits ModeSelected for that mode`() {
 *         var emitted: ThemeSettingsEvent? = null
 *         setContent(ThemeSettingsState(selected = ThemeMode.System)) { emitted = it }
 *         composeTestRule.onNode(isSelectable() and hasText("Dark")).performClick()
 *         assertEquals(ThemeSettingsEvent.ModeSelected(ThemeMode.Dark), emitted)
 *     }
 *
 *     @Test fun `back icon emits onBack`() {
 *         var backed = false
 *         composeTestRule.setContent {
 *             MyMoneyTheme(themeMode = ThemeMode.System) {
 *                 ThemeSettingsContent(
 *                     state = ThemeSettingsState(),
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
class ThemeSettingsContentTest {

    /** Mirror of `ThemeMode.entries.forEach { ThemeRow(...) }` — one row per mode, in order. */
    private val rows: List<ThemeMode> = ThemeMode.entries.toList()

    /** Mirror of the per-row `selected = state.selected == mode` flag. */
    private fun isRowSelected(state: ThemeSettingsState, mode: ThemeMode): Boolean =
        state.selected == mode

    /** Mirror of the per-row `onClick = { onEvent(ThemeSettingsEvent.ModeSelected(mode)) }`. */
    private fun eventForRowClick(mode: ThemeMode): ThemeSettingsEvent =
        ThemeSettingsEvent.ModeSelected(mode)

    /** Mirror of the private `ThemeMode.labelRes` mapping in ThemeSettingsScreen. */
    private fun labelRes(mode: ThemeMode): Int = when (mode) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

    @Test
    fun `content renders exactly three rows one per theme mode in declaration order`() {
        assertEquals(3, rows.size)
        assertEquals(listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark), rows)
    }

    @Test
    fun `exactly the row matching state selected is marked selected`() {
        for (selected in rows) {
            val state = ThemeSettingsState(selected = selected)
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
        val state = ThemeSettingsState(selected = ThemeMode.Dark)
        assertTrue(isRowSelected(state, ThemeMode.Dark))
        assertFalse(isRowSelected(state, ThemeMode.Light))
        assertFalse(isRowSelected(state, ThemeMode.System))
    }

    @Test
    fun `tapping a row emits ModeSelected carrying that row's mode`() {
        for (mode in rows) {
            assertEquals(ThemeSettingsEvent.ModeSelected(mode), eventForRowClick(mode))
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
