package com.kshavrin.mymoney.feature.transactionslist.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.domain.model.TransactionKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transactionslist.R
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SearchRoute(
    onOpenDetail: (Long) -> Unit,
    onBack: () -> Unit,
    contextualOverlay: Boolean = false,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val voiceUnavailableMessage = stringResource(R.string.search_voice_unavailable)
    val voicePrompt = stringResource(R.string.search_voice_prompt)

    val voiceLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let { viewModel.onEvent(SearchEvent.VoiceResult(it)) }
            }
        }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is SearchAction.OpenDetail -> onOpenDetail(action.transactionId)
                SearchAction.NavigateBack -> onBack()
                SearchAction.ShowVoiceUnavailable ->
                    snackbarHostState.showSnackbar(voiceUnavailableMessage)
            }
        }
    }

    SearchContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
        onLaunchVoice = {
            val intent = voiceSearchIntent(voicePrompt)
            try {
                voiceLauncher.launch(intent)
            } catch (e: ActivityNotFoundException) {
                viewModel.onEvent(SearchEvent.VoiceUnavailable)
            }
        },
        contextualOverlay = contextualOverlay,
    )
}

@Composable
fun SearchContent(
    state: SearchState,
    snackbarHostState: SnackbarHostState,
    onEvent: (SearchEvent) -> Unit,
    onLaunchVoice: () -> Unit,
    modifier: Modifier = Modifier,
    voiceSearchAvailable: Boolean? = null,
    contextualOverlay: Boolean = false,
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        withFrameNanos { }
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val detectedVoiceAvailable = remember(context) { isVoiceSearchAvailable(context) }
    val voiceAvailable = voiceSearchAvailable ?: detectedVoiceAvailable
    val bodyTakesOver = !contextualOverlay || state.phase != SearchPhase.Empty

    BackHandler { onEvent(SearchEvent.BackClicked) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            MonefySearchTopBar(
                query = state.query,
                voiceAvailable = voiceAvailable,
                focusRequester = focusRequester,
                onQueryChange = { onEvent(SearchEvent.QueryChanged(it)) },
                onSearch = { onEvent(SearchEvent.QuerySubmitted) },
                onBack = { onEvent(SearchEvent.BackClicked) },
                onClear = { onEvent(SearchEvent.QueryCleared) },
                onLaunchVoice = onLaunchVoice,
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (bodyTakesOver) {
                                Modifier.background(MaterialTheme.colorScheme.surface)
                            } else {
                                Modifier
                            },
                        ),
            ) {
                SearchBody(state = state, onEvent = onEvent)
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun MonefySearchTopBar(
    query: String,
    voiceAvailable: Boolean,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onLaunchVoice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier =
                    Modifier
                        .height(64.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.transactions_list_back),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            color = MaterialTheme.colorScheme.onPrimary,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.search_records_hint),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isEmpty()) {
                    if (voiceAvailable) {
                        IconButton(onClick = onLaunchVoice) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = stringResource(R.string.search_voice_cd),
                            )
                        }
                    }
                } else {
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.search_clear_cd),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBody(
    state: SearchState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.phase) {
        SearchPhase.Empty -> SuggestionsRow(history = state.history, onEvent = onEvent, modifier = modifier)
        SearchPhase.Loading ->
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        SearchPhase.Results -> ResultsList(state = state, onEvent = onEvent, modifier = modifier)
        SearchPhase.EmptyResults ->
            CenteredMessage(
                text = stringResource(R.string.search_no_matches),
                modifier = modifier,
            )
        SearchPhase.Error ->
            CenteredMessage(
                text = stringResource(R.string.search_error),
                modifier = modifier,
            )
    }
}

@Composable
private fun SuggestionsRow(
    history: List<String>,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) return
    LazyRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        items(history, key = { it }) { query ->
            SuggestionChip(
                onClick = { onEvent(SearchEvent.SuggestionClicked(query)) },
                label = { Text(query) },
            )
        }
    }
}

@Composable
private fun ResultsList(
    state: SearchState,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(state.results, key = { it.id }) { row ->
            SearchResultRow(
                row = row,
                currency = state.currencyFor(row.currencyId),
                onClick = { onEvent(SearchEvent.ResultClicked(row.id)) },
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    row: SearchRow,
    currency: Currency?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val amountColor =
        when (row.kind) {
            TransactionKind.Expense -> MaterialTheme.colorScheme.tertiary
            TransactionKind.Income -> MaterialTheme.colorScheme.primary
            TransactionKind.Transfer -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val leadingIcon =
        when (row.kind) {
            TransactionKind.Transfer -> Icons.AutoMirrored.Filled.CompareArrows
            else -> Icons.Filled.Category
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = amountColor,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = formatSignedAmount(row.kind, row.amount, currency),
                style = MaterialTheme.typography.titleMedium,
                color = amountColor,
            )
            val note = row.note
            if (!note.isNullOrBlank()) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = formatDate(row.occurredAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onClick) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.transactions_list_open_detail),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(Spacing.l),
        )
    }
}

private fun voiceSearchIntent(prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)

private fun isVoiceSearchAvailable(context: Context): Boolean =
    context.packageManager.resolveActivity(voiceSearchIntent(""), 0) != null

private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())

private fun formatDate(instant: Instant): String =
    DATE_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private fun formatSignedAmount(
    kind: TransactionKind,
    amount: BigDecimal,
    currency: Currency?,
): String {
    val symbol = currency?.symbol ?: ""
    val digits = currency?.decimalDigits ?: 2
    val formatted =
        MoneyFormatter.format(
            amount = amount,
            currencySymbol = symbol,
            decimalDigits = digits,
            locale = Locale.getDefault(),
        )
    val sign =
        when (kind) {
            TransactionKind.Expense -> "-"
            TransactionKind.Income -> "+"
            TransactionKind.Transfer -> ""
        }
    return "$sign$formatted"
}
