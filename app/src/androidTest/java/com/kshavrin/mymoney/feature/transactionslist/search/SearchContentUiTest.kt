package com.kshavrin.mymoney.feature.transactionslist.search

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transactionslist.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class SearchContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button emits search back event`() {
        val capturedEvents = mutableListOf<SearchEvent>()

        setContent(
            state = SearchState(query = "coffee"),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.transactions_list_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SearchEvent.BackClicked), capturedEvents)
        }
    }

    @Test
    fun `query input emits text change event`() {
        val capturedEvents = mutableListOf<SearchEvent>()

        setContent(onEvent = { event -> capturedEvents += event })

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("coffee")

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(SearchEvent.QueryChanged("coffee")))
        }
    }

    @Test
    fun `search field requests focus when opened`() {
        setContent()

        composeTestRule.waitForIdle()

        composeTestRule
            .onNode(hasSetTextAction())
            .assertIsFocused()
    }

    @Test
    fun `hero bar keeps back input and voice controls aligned in one row`() {
        setContent(voiceSearchAvailable = true)

        val backBounds =
            composeTestRule
                .onNodeWithContentDescription(
                    targetString(R.string.transactions_list_back),
                ).fetchSemanticsNode()
                .boundsInRoot
        val inputBounds =
            composeTestRule
                .onNode(hasSetTextAction())
                .fetchSemanticsNode()
                .boundsInRoot
        val voiceBounds =
            composeTestRule
                .onNodeWithContentDescription(
                    targetString(R.string.search_voice_cd),
                ).fetchSemanticsNode()
                .boundsInRoot

        assertTrue(abs(backBounds.center.y - inputBounds.center.y) <= 1f)
        assertTrue(abs(inputBounds.center.y - voiceBounds.center.y) <= 1f)
    }

    @Test
    fun `hero bar leaves the empty body below the hero app bar spacing token`() {
        setContent(
            state = SearchState(history = listOf("rent"), phase = SearchPhase.Empty),
        )

        val historyBounds =
            composeTestRule
                .onNodeWithText("rent")
                .fetchSemanticsNode()
                .boundsInRoot
        val minimumBodyTop =
            with(composeTestRule.density) {
                (Spacing.heroAppBarHeight + Spacing.s).toPx()
            }

        assertTrue(
            "history content must begin below the hero app bar",
            historyBounds.top >= minimumBodyTop,
        )
    }

    @Test
    fun `empty query shows hint and voice action instead of clear action`() {
        setContent(voiceSearchAvailable = true)

        composeTestRule
            .onNodeWithText(targetString(R.string.search_records_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.search_voice_cd))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.search_clear_cd))
            .assertDoesNotExist()
    }

    @Test
    fun `empty query hides both trailing actions when voice search is unavailable`() {
        setContent(voiceSearchAvailable = false)

        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.search_voice_cd),
            ).assertDoesNotExist()
        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.search_clear_cd),
            ).assertDoesNotExist()
    }

    @Test
    fun `drawer overlay keeps the same hero bar controls as the list route`() {
        setContent(
            contextualOverlay = true,
            voiceSearchAvailable = true,
        )

        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.transactions_list_back),
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.search_records_hint))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.search_voice_cd),
            ).assertIsDisplayed()
    }

    @Test
    fun `non blank query shows clear action instead of voice action`() {
        setContent(
            state = SearchState(query = "coffee"),
            voiceSearchAvailable = true,
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.search_clear_cd))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.search_voice_cd))
            .assertDoesNotExist()
    }

    @Test
    fun `search ime action emits query submitted event`() {
        val capturedEvents = mutableListOf<SearchEvent>()

        setContent(
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNode(hasSetTextAction())
            .performTextInput("coffee")
            .performImeAction()

        composeTestRule.runOnIdle {
            assertTrue(capturedEvents.contains(SearchEvent.QueryChanged("coffee")))
            assertTrue(capturedEvents.contains(SearchEvent.QuerySubmitted))
        }
    }

    @Test
    fun `clear button emits query cleared event`() {
        val capturedEvents = mutableListOf<SearchEvent>()

        setContent(
            state = SearchState(query = "coffee"),
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.search_clear_cd))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SearchEvent.QueryCleared), capturedEvents)
        }
    }

    @Test
    fun `clear button restores the voice action after the query becomes empty`() {
        composeTestRule.setContent {
            var query by remember { mutableStateOf("coffee") }

            MyMoneyTheme {
                SearchContent(
                    state = SearchState(query = query),
                    snackbarHostState = remember { SnackbarHostState() },
                    onEvent = { event ->
                        when (event) {
                            is SearchEvent.QueryChanged -> query = event.text
                            SearchEvent.QueryCleared -> query = ""
                            else -> Unit
                        }
                    },
                    onLaunchVoice = {},
                    voiceSearchAvailable = true,
                )
            }
        }

        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.search_clear_cd),
            ).performClick()

        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.search_voice_cd),
            ).assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(
                targetString(R.string.search_clear_cd),
            ).assertDoesNotExist()
    }

    @Test
    fun `loading state shows the progress indicator below the hero bar`() {
        setContent(
            state = SearchState(query = "coffee", phase = SearchPhase.Loading),
        )

        composeTestRule
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
            .assertIsDisplayed()
    }

    @Test
    fun `voice button launches voice search when available`() {
        var launches = 0

        setContent(
            voiceSearchAvailable = true,
            onLaunchVoice = { launches += 1 },
        )

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.search_voice_cd))
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(1, launches)
        }
    }

    @Test
    fun `history chip emits suggestion clicked event`() {
        val capturedEvents = mutableListOf<SearchEvent>()

        setContent(
            state = SearchState(history = listOf("rent"), phase = SearchPhase.Empty),
            contextualOverlay = true,
            onEvent = { event -> capturedEvents += event },
        )

        composeTestRule
            .onNodeWithText("rent")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SearchEvent.SuggestionClicked("rent")), capturedEvents)
        }
    }

    @Test
    fun `result row keeps note and emits open detail event`() {
        val capturedEvents = mutableListOf<SearchEvent>()

        setContent(
            state =
                SearchState(
                    query = "coffee",
                    phase = SearchPhase.Results,
                    results = listOf(searchRow(id = 42L, note = "Coffee")),
                    currencies = listOf(currency(id = 1L)),
                ),
            contextualOverlay = true,
            onEvent = { event -> capturedEvents += event },
        )

        val row = searchRow(id = 42L, note = "Coffee")
        val rowDescription = searchRowDescription(row, currency(id = 1L))
        composeTestRule
            .onAllNodesWithContentDescription(rowDescription)
            .assertCountEquals(1)
        composeTestRule
            .onNodeWithContentDescription(rowDescription)
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf(SearchEvent.ResultClicked(42L)), capturedEvents)
        }
    }

    @Test
    fun `empty results state shows no matches message`() {
        setContent(
            state =
                SearchState(
                    query = "missing",
                    phase = SearchPhase.EmptyResults,
                ),
            contextualOverlay = true,
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.search_no_matches))
            .assertIsDisplayed()
    }

    @Test
    fun `error state shows search error message`() {
        setContent(
            state =
                SearchState(
                    query = "coffee",
                    phase = SearchPhase.Error,
                ),
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.search_error))
            .assertIsDisplayed()
    }

    private fun setContent(
        state: SearchState = SearchState(),
        voiceSearchAvailable: Boolean = false,
        contextualOverlay: Boolean = false,
        onEvent: (SearchEvent) -> Unit = {},
        onLaunchVoice: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                SearchContent(
                    state = state,
                    snackbarHostState = remember { SnackbarHostState() },
                    onEvent = onEvent,
                    onLaunchVoice = onLaunchVoice,
                    voiceSearchAvailable = voiceSearchAvailable,
                    contextualOverlay = contextualOverlay,
                )
            }
        }
    }

    private fun searchRow(
        id: Long,
        note: String,
    ): SearchRow =
        SearchRow(
            id = id,
            kind = TransactionKind.Expense,
            amount = BigDecimal("12.34"),
            currencyId = 1L,
            categoryId = 10L,
            note = note,
            occurredAt = Instant.parse("2026-05-28T00:00:00Z"),
        )

    private fun currency(id: Long): Currency =
        Currency(
            id = id,
            code = "USD",
            symbol = "$",
            name = "US Dollar",
            decimalDigits = 2,
            isActive = true,
            sortOrder = 0,
        )

    private fun searchRowDescription(
        row: SearchRow,
        currency: Currency,
    ): String {
        val formattedAmount =
            "-" +
                MoneyFormatter.format(
                    amount = row.amount,
                    currencySymbol = currency.symbol,
                    decimalDigits = currency.decimalDigits,
                    locale = Locale.getDefault(),
                )
        val formattedDate =
            DateTimeFormatter
                .ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .format(row.occurredAt.atZone(ZoneId.systemDefault()))
        return targetString(
            R.string.transactions_list_search_record_cd,
            formattedAmount,
            row.note.orEmpty(),
            formattedDate,
        )
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resourceId, *formatArgs)
}
