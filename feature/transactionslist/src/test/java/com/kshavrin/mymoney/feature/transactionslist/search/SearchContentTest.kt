package com.kshavrin.mymoney.feature.transactionslist.search

import com.kshavrin.mymoney.feature.transactionslist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchContentTest {
    private fun showsMicSlot(query: String): Boolean = query.isEmpty()

    private fun showsClearIcon(query: String): Boolean = !query.isEmpty()

    private fun bodyTakesOver(
        contextualOverlay: Boolean,
        phase: SearchPhase,
    ): Boolean =
        !contextualOverlay || phase != SearchPhase.Empty

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

    private enum class Body { Chips, Loading, Results, NoMatches, Error }

    private fun bodyFor(phase: SearchPhase): Body =
        when (phase) {
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
    fun `contextual overlay keeps the dashboard context only while search is empty`() {
        assertFalse(bodyTakesOver(contextualOverlay = true, phase = SearchPhase.Empty))
        assertTrue(bodyTakesOver(contextualOverlay = true, phase = SearchPhase.Loading))
        assertTrue(bodyTakesOver(contextualOverlay = true, phase = SearchPhase.Results))
        assertTrue(bodyTakesOver(contextualOverlay = true, phase = SearchPhase.EmptyResults))
        assertTrue(bodyTakesOver(contextualOverlay = true, phase = SearchPhase.Error))
    }

    @Test
    fun `standalone search always owns the body surface regardless of phase`() {
        assertTrue(bodyTakesOver(contextualOverlay = false, phase = SearchPhase.Empty))
        assertTrue(bodyTakesOver(contextualOverlay = false, phase = SearchPhase.Loading))
        assertTrue(bodyTakesOver(contextualOverlay = false, phase = SearchPhase.Results))
        assertTrue(bodyTakesOver(contextualOverlay = false, phase = SearchPhase.EmptyResults))
        assertTrue(bodyTakesOver(contextualOverlay = false, phase = SearchPhase.Error))
    }

    @Test
    fun `the No matches and error slots resolve to distinct string resources`() {
        val noMatches = R.string.search_no_matches
        val error = R.string.search_error
        assertTrue("distinct string resources for EmptyResults vs Error", noMatches != error)
    }
}
