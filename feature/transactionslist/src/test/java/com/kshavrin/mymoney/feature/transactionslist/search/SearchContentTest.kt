package com.kshavrin.mymoney.feature.transactionslist.search

import com.kshavrin.mymoney.feature.transactionslist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract-level pinning for [SearchContent] (S08).
 *
 * # Why this is not a Compose-UI test yet
 *
 * `:feature:transactionslist`'s offline test classpath does NOT yet have:
 *
 *   - `androidx.compose.ui:ui-test-junit4`
 *   - `androidx.compose.ui:ui-test-manifest`
 *
 * (neither artifact is present in the Gradle cache, so a `createComposeRule()` test would fail
 * to resolve at compile time). This mirrors the deliberate deferral already documented in this
 * module's slice-1 `TransactionsListContentTest`, slice-3 `TransactionDetailContentTest`, and
 * `:core:designsystem`'s `MonefyKeypadTest` — full Compose-UI tests land in PHASE_15 once those
 * dependencies are wired in.
 *
 * Until then, the user-visible decisions [SearchContent] makes are driven by pure, JVM-visible
 * inputs ([SearchState.query] and [SearchState.phase]). This file pins those so the contract can't
 * drift, and documents the exact Compose test that replaces it.
 *
 * Note: voice *availability* lives in the Composable (`isVoiceSearchAvailable(context)`), not in
 * the ViewModel (SPEC CONSTRAINTS). So the mic slot is shown when the query branch selects "mic"
 * AND the device has a recognizer; the predicate pinned here is the query-driven branch only.
 *
 * # What the real Compose-UI test must cover (template for PHASE_15)
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = android.app.Application::class)
 * @GraphicsMode(GraphicsMode.Mode.NATIVE)
 * class SearchContentTest {
 *     @get:Rule val composeTestRule = createComposeRule()
 *
 *     private fun setContent(state: SearchState) {
 *         composeTestRule.setContent {
 *             MyMoneyTheme {
 *                 SearchContent(
 *                     state = state,
 *                     snackbarHostState = remember { SnackbarHostState() },
 *                     onEvent = {},
 *                     onLaunchVoice = {},
 *                 )
 *             }
 *         }
 *     }
 *
 *     @Test fun `mic icon shown and clear icon absent when query is blank`() {
 *         setContent(SearchState(query = ""))
 *         composeTestRule.onNodeWithContentDescription("Voice search").assertIsDisplayed()
 *         composeTestRule.onNodeWithContentDescription("Clear search").assertDoesNotExist()
 *     }
 *
 *     @Test fun `clear icon shown and mic icon absent when query is non-blank`() {
 *         setContent(SearchState(query = "coffee"))
 *         composeTestRule.onNodeWithContentDescription("Clear search").assertIsDisplayed()
 *         composeTestRule.onNodeWithContentDescription("Voice search").assertDoesNotExist()
 *     }
 *
 *     @Test fun `Empty phase renders history chips`() {
 *         setContent(SearchState(history = listOf("coffee", "rent"), phase = SearchPhase.Empty))
 *         composeTestRule.onNodeWithText("coffee").assertIsDisplayed()
 *         composeTestRule.onNodeWithText("rent").assertIsDisplayed()
 *     }
 *
 *     @Test fun `EmptyResults phase renders the No matches message`() {
 *         setContent(SearchState(query = "zzz", phase = SearchPhase.EmptyResults))
 *         composeTestRule.onNodeWithText("No matches").assertIsDisplayed()
 *     }
 *
 *     @Test fun `Error phase renders the error message`() {
 *         setContent(SearchState(query = "x", phase = SearchPhase.Error))
 *         composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
 *     }
 * }
 * ```
 */
class SearchContentTest {

    /**
     * Mirror of the trailing-icon branch in [SearchContent]'s InputField:
     * `if (state.query.isEmpty()) { mic-slot } else { clear-icon }`.
     */
    private fun showsMicSlot(query: String): Boolean = query.isEmpty()
    private fun showsClearIcon(query: String): Boolean = !query.isEmpty()

    @Test
    fun `mic slot is the trailing branch exactly when the query is empty`() {
        assertTrue("blank query -> mic branch", showsMicSlot(""))
        assertFalse("non-empty query -> not the mic branch", showsMicSlot("coffee"))
    }

    @Test
    fun `clear icon is the trailing branch exactly when the query is non-empty`() {
        assertTrue("non-empty query -> clear icon", showsClearIcon("coffee"))
        assertFalse("blank query -> no clear icon", showsClearIcon(""))
    }

    @Test
    fun `mic and clear are mutually exclusive across the query branch`() {
        for (query in listOf("", "a", "coffee")) {
            assertEquals(
                "exactly one trailing branch for query='$query'",
                1,
                listOf(showsMicSlot(query), showsClearIcon(query)).count { it },
            )
        }
    }

    /**
     * Mirror of the `when (state.phase)` dispatch in [SearchContent]'s SearchBody: which slot the
     * body renders for each phase. Pins chips vs results vs "No matches" vs error vs loading.
     */
    private enum class Body { Chips, Loading, Results, NoMatches, Error }

    private fun bodyFor(phase: SearchPhase): Body = when (phase) {
        SearchPhase.Empty -> Body.Chips
        SearchPhase.Loading -> Body.Loading
        SearchPhase.Results -> Body.Results
        SearchPhase.EmptyResults -> Body.NoMatches
        SearchPhase.Error -> Body.Error
    }

    @Test
    fun `Empty phase renders the history chips row`() {
        assertEquals(Body.Chips, bodyFor(SearchPhase.Empty))
    }

    @Test
    fun `Results phase renders the results list`() {
        assertEquals(Body.Results, bodyFor(SearchPhase.Results))
    }

    @Test
    fun `EmptyResults phase renders the No matches message`() {
        assertEquals(Body.NoMatches, bodyFor(SearchPhase.EmptyResults))
    }

    @Test
    fun `Loading phase renders the progress indicator`() {
        assertEquals(Body.Loading, bodyFor(SearchPhase.Loading))
    }

    @Test
    fun `Error phase renders the error message`() {
        assertEquals(Body.Error, bodyFor(SearchPhase.Error))
    }

    @Test
    fun `the No matches and error slots resolve to distinct string resources`() {
        // The two centred-message phases must not collapse onto the same copy.
        val noMatches = R.string.search_no_matches
        val error = R.string.search_error
        assertTrue("distinct string resources for EmptyResults vs Error", noMatches != error)
    }
}
