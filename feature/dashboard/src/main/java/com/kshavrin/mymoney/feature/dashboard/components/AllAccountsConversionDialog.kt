package com.kshavrin.mymoney.feature.dashboard.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.kshavrin.mymoney.core.designsystem.dialog.RateConfirmDialog
import com.kshavrin.mymoney.core.designsystem.dialog.RateRow
import com.kshavrin.mymoney.core.domain.model.Currency
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.dashboard.R
import java.math.BigDecimal

/**
 * One-shot dialog states for the "All accounts" conversion flow. Driven by the ViewModel's
 * one-shot actions (replay = 0) and rendered by [AllAccountsConversionDialogHost]; only one is
 * ever active at a time.
 */
sealed interface AllAccountsConversionDialog {
    // Fork: fold everything into one currency, or show each currency separately.
    data object Mode : AllAccountsConversionDialog

    // Pick the currency every account folds into (asked every time — D7).
    data class TargetPicker(
        val currencies: List<Currency>,
    ) : AllAccountsConversionDialog

    // Confirm/edit cross-rates. [sourceCurrencyIds] runs parallel to [rows] so an edited row maps
    // back to its source currency id.
    data class RateConfirm(
        val rows: List<RateRow>,
        val sourceCurrencyIds: List<Long>,
    ) : AllAccountsConversionDialog
}

const val ALL_ACCOUNTS_MODE_DIALOG_TAG = "all_accounts_mode_dialog"
const val ALL_ACCOUNTS_MODE_CONVERT_TAG = "all_accounts_mode_convert"
const val ALL_ACCOUNTS_MODE_SEPARATE_TAG = "all_accounts_mode_separate"
const val ALL_ACCOUNTS_TARGET_DIALOG_TAG = "all_accounts_target_dialog"
const val ALL_ACCOUNTS_TARGET_OPTION_TAG_PREFIX = "all_accounts_target_option_"

@Composable
fun AllAccountsConversionDialogHost(
    dialog: AllAccountsConversionDialog?,
    onDismiss: () -> Unit,
    onConvertChosen: () -> Unit,
    onSeparateChosen: () -> Unit,
    onTargetChosen: (Long) -> Unit,
    onRatesConfirmed: (Map<Long, BigDecimal>) -> Unit,
) {
    when (dialog) {
        null -> Unit
        AllAccountsConversionDialog.Mode ->
            AllAccountsModeDialog(
                onDismiss = onDismiss,
                onConvertChosen = onConvertChosen,
                onSeparateChosen = onSeparateChosen,
            )
        is AllAccountsConversionDialog.TargetPicker ->
            AllAccountsTargetDialog(
                currencies = dialog.currencies,
                onDismiss = onDismiss,
                onTargetChosen = onTargetChosen,
            )
        is AllAccountsConversionDialog.RateConfirm ->
            RateConfirmDialog(
                rows = dialog.rows,
                onConfirm = { byIndex ->
                    onRatesConfirmed(byIndex.mapToSourceCurrencyIds(dialog.sourceCurrencyIds))
                },
                onDismiss = onDismiss,
            )
    }
}

private fun Map<Int, BigDecimal>.mapToSourceCurrencyIds(sourceCurrencyIds: List<Long>): Map<Long, BigDecimal> =
    buildMap {
        this@mapToSourceCurrencyIds.forEach { (index, rate) ->
            sourceCurrencyIds.getOrNull(index)?.let { put(it, rate) }
        }
    }

@Composable
private fun AllAccountsModeDialog(
    onDismiss: () -> Unit,
    onConvertChosen: () -> Unit,
    onSeparateChosen: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.testTag(ALL_ACCOUNTS_MODE_DIALOG_TAG),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.all_accounts_mode_title)) },
        text = { Text(stringResource(R.string.all_accounts_mode_message)) },
        confirmButton = {
            TextButton(
                onClick = onConvertChosen,
                modifier = Modifier.testTag(ALL_ACCOUNTS_MODE_CONVERT_TAG),
            ) {
                Text(stringResource(R.string.all_accounts_mode_convert))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSeparateChosen,
                modifier = Modifier.testTag(ALL_ACCOUNTS_MODE_SEPARATE_TAG),
            ) {
                Text(stringResource(R.string.all_accounts_mode_separate))
            }
        },
    )
}

@Composable
private fun AllAccountsTargetDialog(
    currencies: List<Currency>,
    onDismiss: () -> Unit,
    onTargetChosen: (Long) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.testTag(ALL_ACCOUNTS_TARGET_DIALOG_TAG),
            shape = RoundedCornerShape(Spacing.l),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = Spacing.xs,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(
                    text = stringResource(R.string.all_accounts_target_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                currencies.forEach { currency ->
                    Text(
                        text = "${currency.code} — ${currency.name}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onTargetChosen(currency.id) }
                                .testTag("$ALL_ACCOUNTS_TARGET_OPTION_TAG_PREFIX${currency.id}")
                                .padding(vertical = Spacing.s),
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.all_accounts_cancel))
                }
            }
        }
    }
}
