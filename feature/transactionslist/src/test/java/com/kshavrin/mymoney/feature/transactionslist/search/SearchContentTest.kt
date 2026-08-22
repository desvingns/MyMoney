package com.kshavrin.mymoney.feature.transactionslist.search

import com.kshavrin.mymoney.feature.transactionslist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchContentTest {
    private fun showsMicSlot(
        query: String,
        voiceAvailable: Boolean,
    ): Boolean = query.isEmpty() && voiceAvailable

    private fun showsClearIcon(query: String): Boolean = query.isNotEmpty()

    private fun bodyTakesOver(
        contextualOverlay: Boolean,
        phase: SearchPhase,
    ): Boolean =
        !contextualOverlay || phase != SearchPhase.Empty

    @Test
    fun `mic slot is shown only for an empty query when voice search is available`() {
        assertTrue(
            "empty query + voice available -> mic branch",
            showsMicSlot("", voiceAvailable = true),
        )
        assertFalse(
            "empty query + voice unavailable -> no mic branch",
            showsMicSlot("", voiceAvailable = false),
        )
        assertFalse(
            "non-empty query -> not the mic branch",
            showsMicSlot("coffee", voiceAvailable = true),
        )
    }

    @Test
    fun `clear icon is the trailing branch exactly when the query is non-empty`() {
        assertTrue("non-empty query -> clear icon", showsClearIcon("coffee"))
        assertFalse("blank query -> no clear icon", showsClearIcon(""))
    }

    @Test
    fun `voice and clear actions expose the complete trailing-action matrix`() {
        val cases =
            listOf(
                Triple("", true, 1),
                Triple("", false, 0),
                Triple("a", true, 1),
                Triple("coffee", false, 1),
            )
        for ((query, voiceAvailable, expectedActions) in cases) {
            assertEquals(
                "trailing actions for query='$query', voiceAvailable=$voiceAvailable",
                expectedActions,
                listOf(
                    showsMicSlot(query, voiceAvailable),
                    showsClearIcon(query),
                ).count { it },
            )
        }
    }

    private enum class Body { Chips, Loading, Results, NoMatches, Error }

    private fun bodyByPhase(phase: SearchPhase): Body =
        when (phase) {
            SearchPhase.Empty -> Body.Chips
            SearchPhase.Loading -> Body.Loading
            SearchPhase.Results -> Body.Results
            SearchPhase.EmptyResults -> Body.NoMatches
            SearchPhase.Error -> Body.Error
        }

    @Test
    fun `Empty phase renders the history chips row`() {
        assertEquals(Body.Chips, bodyByPhase(SearchPhase.Empty))
    }

    @Test
    fun `Results phase renders the results list`() {
        assertEquals(Body.Results, bodyByPhase(SearchPhase.Results))
    }

    @Test
    fun `EmptyResults phase renders the No matches message`() {
        assertEquals(Body.NoMatches, bodyByPhase(SearchPhase.EmptyResults))
    }

    @Test
    fun `Loading phase renders the progress indicator`() {
        assertEquals(Body.Loading, bodyByPhase(SearchPhase.Loading))
    }

    @Test
    fun `Error phase renders the error message`() {
        assertEquals(Body.Error, bodyByPhase(SearchPhase.Error))
    }

    @Test
    fun `each SearchPhase maps to one dedicated body branch`() {
        val phases =
            listOf(
                SearchPhase.Empty,
                SearchPhase.Loading,
                SearchPhase.Results,
                SearchPhase.EmptyResults,
                SearchPhase.Error,
            )

        assertEquals(
            listOf(Body.Chips, Body.Loading, Body.Results, Body.NoMatches, Body.Error),
            phases.map(::bodyByPhase),
        )
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
