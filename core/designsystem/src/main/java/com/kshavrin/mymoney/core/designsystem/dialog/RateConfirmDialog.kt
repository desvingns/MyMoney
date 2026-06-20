package com.kshavrin.mymoney.core.designsystem.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.rateStaleContainer
import com.kshavrin.mymoney.core.ui.theme.rateStaleContent
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * A single conversion pair shown in [RateConfirmDialog].
 *
 * [fromCode] / [toCode] are display labels (currency codes such as "EUR", "USD") — the
 * dialog lives in :core:designsystem and must not pull in the :core:domain Currency model,
 * so the caller passes the codes it already holds.
 *
 * [displayRate] is the cross-rate from→to that the user sees and may edit (NOT the stored
 * EUR→X base rate); the dialog renders it verbatim and pre-fills the edit field with it.
 */
data class RateRow(
    val fromCode: String,
    val toCode: String,
    val lastUpdated: LocalDate?,
    val displayRate: BigDecimal?,
    val stale: Boolean,
    val missing: Boolean,
)

const val RATE_CONFIRM_DIALOG_TAG = "rate_confirm_dialog"
const val RATE_CONFIRM_BUTTON_TAG = "rate_confirm_button"
const val RATE_DISMISS_BUTTON_TAG = "rate_dismiss_button"
const val RATE_ROW_DATE_TAG_PREFIX = "rate_row_date_"
const val RATE_ROW_FIELD_TAG_PREFIX = "rate_row_field_"

private const val RATE_SCALE = 2

/**
 * Production rate-confirmation dialog. Shown every time the app touches a conversion rate.
 *
 * Thin wrapper that places [RateConfirmDialogContent] inside an OS-level dialog window so
 * real callers get the scrim/window behaviour. The windowless body lives in
 * [RateConfirmDialogContent] and is what UI tests render directly (mirrors the project's
 * `<Name>Content` convention), avoiding dialog sub-windows leaking across `createComposeRule`
 * test runs.
 *
 * @param rows source of truth for what is displayed; this composable never loads or saves it.
 * @param onRateEdited optional notification fired as the user types a valid value in a row.
 * @param onConfirm receives the resolved value for every row (manual input if valid,
 *   otherwise the pre-filled [RateRow.displayRate]), keyed by row index.
 * @param onDismiss user dismissed the dialog without confirming.
 */
@Composable
fun RateConfirmDialog(
    rows: List<RateRow>,
    onConfirm: (Map<Int, BigDecimal>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRateEdited: (Int, BigDecimal) -> Unit = { _, _ -> },
) {
    if (rows.isEmpty()) return
    Dialog(onDismissRequest = onDismiss) {
        RateConfirmDialogContent(
            rows = rows,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
            modifier = modifier,
            onRateEdited = onRateEdited,
        )
    }
}

/**
 * Windowless body of the rate-confirmation dialog: a themed [Surface] card with no OS
 * dialog window. Holds all of the dialog state and behaviour and is what tests render
 * directly.
 *
 * One row ⇒ single view (title with the from/to codes); more than one ⇒ a list with a
 * single confirm covering every row. Manual edits are one-shot: they are returned to the
 * caller via [onConfirm] keyed by row index and are NOT persisted by this component.
 *
 * @param rows source of truth for what is displayed; this composable never loads or saves it.
 * @param onRateEdited optional notification fired as the user types a valid value in a row.
 * @param onConfirm receives the resolved value for every row (manual input if valid,
 *   otherwise the pre-filled [RateRow.displayRate]), keyed by row index.
 * @param onDismiss user dismissed the dialog without confirming.
 */
@Composable
fun RateConfirmDialogContent(
    rows: List<RateRow>,
    onConfirm: (Map<Int, BigDecimal>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onRateEdited: (Int, BigDecimal) -> Unit = { _, _ -> },
) {
    if (rows.isEmpty()) return

    val inputs: SnapshotStateList<String> =
        rememberSaveable(
            rows.size,
            saver = listSaver,
        ) {
            mutableStateListOf<String>().apply {
                rows.forEach { add(it.displayRate.toInputText()) }
            }
        }

    val resolved = rows.mapIndexed { index, row -> row.resolve(inputs.getOrNull(index)) }
    val confirmEnabled = resolved.all { it != null }

    val onConfirmClick: () -> Unit = {
        if (confirmEnabled) {
            val result =
                buildMap {
                    resolved.forEachIndexed { index, value ->
                        if (value != null) put(index, value)
                    }
                }
            onConfirm(result)
        }
    }

    val onRowInput: (Int, String) -> Unit = { index, text ->
        if (index in inputs.indices) {
            inputs[index] = text
            text.parseRate()?.let { onRateEdited(index, it) }
        }
    }

    Surface(
        modifier = modifier.testTag(RATE_CONFIRM_DIALOG_TAG),
        shape = RoundedCornerShape(Spacing.l),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = Spacing.xs,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (rows.size == 1) {
                val row = rows.first()
                Text(
                    text = stringResource(R.string.rate_confirm_title, row.fromCode, row.toCode),
                    style = MaterialTheme.typography.titleMedium,
                )
                RateRowContent(
                    index = 0,
                    row = row,
                    input = inputs.getOrNull(0).orEmpty(),
                    onInputChanged = { onRowInput(0, it) },
                )
            } else {
                Text(
                    text = stringResource(R.string.rate_confirm_list_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = Spacing.dashboardTileHeight * 6),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    itemsIndexed(rows) { index, row ->
                        RateRowContent(
                            index = index,
                            row = row,
                            input = inputs.getOrNull(index).orEmpty(),
                            onInputChanged = { onRowInput(index, it) },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag(RATE_DISMISS_BUTTON_TAG),
                ) {
                    Text(stringResource(R.string.rate_dismiss_action))
                }
                TextButton(
                    onClick = onConfirmClick,
                    enabled = confirmEnabled,
                    modifier = Modifier.testTag(RATE_CONFIRM_BUTTON_TAG),
                ) {
                    Text(stringResource(R.string.rate_confirm_action))
                }
            }
        }
    }
}

@Composable
private fun RateRowContent(
    index: Int,
    row: RateRow,
    input: String,
    onInputChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(R.string.rate_pair_label, row.fromCode, row.toCode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            modifier = Modifier.testTag("$RATE_ROW_DATE_TAG_PREFIX$index"),
            text =
                row.lastUpdated?.let {
                    stringResource(R.string.rate_last_updated, it.toString())
                } ?: stringResource(R.string.rate_last_updated_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text =
                row.displayRate?.let {
                    stringResource(R.string.rate_value_on_date, it.toPlainString())
                } ?: stringResource(R.string.rate_value_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("$RATE_ROW_FIELD_TAG_PREFIX$index"),
            value = input,
            onValueChange = onInputChanged,
            singleLine = true,
            isError = row.missing && input.parseRate() == null,
            label = { Text(stringResource(R.string.rate_manual_input_label)) },
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                ),
        )
        when {
            row.missing ->
                RateBadge(
                    text = stringResource(R.string.rate_missing_hint),
                    container = MaterialTheme.colorScheme.error,
                    content = MaterialTheme.colorScheme.onError,
                )
            row.stale ->
                RateBadge(
                    text = stringResource(R.string.rate_stale_hint),
                    container = MaterialTheme.colorScheme.rateStaleContainer,
                    content = MaterialTheme.colorScheme.rateStaleContent,
                )
        }
    }
}

@Composable
private fun RateBadge(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(Spacing.s),
        color = container,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = Spacing.s, vertical = Spacing.xs),
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
    }
}

/**
 * Resolve the value a row contributes to [onConfirm]: a valid manual input wins; otherwise
 * the pre-filled [RateRow.displayRate]. A `missing` row with no valid input resolves to null,
 * which disables confirm — the user must enter a rate for it.
 */
private fun RateRow.resolve(input: String?): BigDecimal? {
    val parsed = input?.parseRate()
    if (parsed != null) return parsed
    return if (missing) null else displayRate?.setScale(RATE_SCALE, RoundingMode.HALF_UP)
}

private fun String.parseRate(): BigDecimal? =
    trim()
        .replace(',', '.')
        .toBigDecimalOrNull()
        ?.setScale(RATE_SCALE, RoundingMode.HALF_UP)
        ?.takeIf { it > BigDecimal.ZERO }

private fun BigDecimal?.toInputText(): String =
    this?.setScale(RATE_SCALE, RoundingMode.HALF_UP)?.toPlainString().orEmpty()

private val listSaver =
    androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<String>, String>(
        save = { it.toList() },
        restore = { mutableStateListOf<String>().apply { addAll(it) } },
    )
