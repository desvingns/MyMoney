package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.domain.csv.ExistingCategorySummary
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.wizardStrategyCard
import com.kshavrin.mymoney.feature.settings.R

@Composable
fun ManualMergeStep(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.import_wizard_merge_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.import_wizard_merge_subheading),
        style = MaterialTheme.typography.bodyMedium,
    )
    state.mergeRows.forEach { row ->
        MergeRowCard(row = row, onEvent = onEvent)
    }
}

@Composable
private fun MergeRowCard(
    row: MergeRow,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.wizardStrategyCard,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = row.importCategoryName,
                style = MaterialTheme.typography.titleSmall,
            )
            MergeActionDropdown(row = row, onEvent = onEvent)
            if (row.isMergeInto) {
                OutlinedTextField(
                    value = row.resultName,
                    onValueChange = {
                        onEvent(
                            ImportWizardEvent.MergeResultNameChanged(
                                importCategoryName = row.importCategoryName,
                                resultName = it,
                            ),
                        )
                    },
                    label = { Text(stringResource(R.string.import_wizard_merge_result_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MergeActionDropdown(
    row: MergeRow,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val createNewLabel = stringResource(R.string.import_wizard_merge_create_new)
    val selectedLabel =
        if (row.isMergeInto) {
            row.candidates.firstOrNull { it.id == row.targetId }?.name ?: createNewLabel
        } else {
            createNewLabel
        }
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text =
                    if (row.isMergeInto) {
                        stringResource(R.string.import_wizard_merge_into, selectedLabel)
                    } else {
                        selectedLabel
                    },
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text(createNewLabel) },
                onClick = {
                    expanded = false
                    onEvent(
                        ImportWizardEvent.MergeActionSelected(
                            importCategoryName = row.importCategoryName,
                            target = null,
                        ),
                    )
                },
            )
            row.candidates.forEach { candidate: ExistingCategorySummary ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.import_wizard_merge_into, candidate.name)) },
                    onClick = {
                        expanded = false
                        onEvent(
                            ImportWizardEvent.MergeActionSelected(
                                importCategoryName = row.importCategoryName,
                                target = candidate,
                            ),
                        )
                    },
                )
            }
        }
    }
}
