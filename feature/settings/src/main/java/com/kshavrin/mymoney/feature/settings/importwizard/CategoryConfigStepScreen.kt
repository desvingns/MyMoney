package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.designsystem.picker.CATEGORY_EXPENSE_ICON_KEYS
import com.kshavrin.mymoney.core.designsystem.picker.CATEGORY_INCOME_ICON_KEYS
import com.kshavrin.mymoney.core.designsystem.picker.ColorPickerGrid
import com.kshavrin.mymoney.core.designsystem.picker.IconPickerGrid
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.wizardStepProgressContainer
import com.kshavrin.mymoney.core.ui.theme.wizardStepProgressContent
import com.kshavrin.mymoney.feature.settings.R

/**
 * D7 gate: after import commit, asks whether to customize each resulting category now or later.
 */
@Composable
fun ConfigGateStep(onEvent: (ImportWizardEvent) -> Unit) {
    Text(
        text = stringResource(R.string.import_wizard_config_gate_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.import_wizard_config_gate_message),
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(
        onClick = { onEvent(ImportWizardEvent.ConfigureNowClicked) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.import_wizard_config_gate_now))
    }
    OutlinedButton(
        onClick = { onEvent(ImportWizardEvent.ConfigureLaterClicked) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.import_wizard_config_gate_later))
    }
}

/**
 * Embedded per-category config step (D7): edits name/icon/color of one resulting category, with
 * Back / Next, and "Done" on the last category. Saving each edit goes through
 * [com.kshavrin.mymoney.core.domain.repository.CategoryRepository] (the VM handles persistence).
 */
@Composable
fun CategoryConfigStep(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.import_wizard_config_heading),
            style = MaterialTheme.typography.titleMedium,
        )
        Surface(
            color = MaterialTheme.colorScheme.wizardStepProgressContainer,
            contentColor = MaterialTheme.colorScheme.wizardStepProgressContent,
            shape = RoundedCornerShape(percent = 50),
        ) {
            Text(
                text = stringResource(R.string.import_wizard_config_progress, state.configPosition, state.configTotal),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
            )
        }
    }

    OutlinedTextField(
        value = state.configName,
        onValueChange = { onEvent(ImportWizardEvent.ConfigNameChanged(it)) },
        label = { Text(stringResource(R.string.import_wizard_config_field_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Text(
        text = stringResource(R.string.import_wizard_config_field_icon),
        style = MaterialTheme.typography.titleSmall,
    )
    val iconKeys =
        if (state.configCurrentKind == CategoryKind.Income) {
            CATEGORY_INCOME_ICON_KEYS
        } else {
            CATEGORY_EXPENSE_ICON_KEYS
        }
    IconPickerGrid(
        iconKeys = iconKeys,
        selectedIconKey = state.configIconKey,
        iconFor = { categoryIcon(it) },
        onIconSelected = { onEvent(ImportWizardEvent.ConfigIconChanged(it)) },
    )

    Text(
        text = stringResource(R.string.import_wizard_config_field_color),
        style = MaterialTheme.typography.titleSmall,
    )
    ColorPickerGrid(
        selectedHex = state.configColorHex,
        onColorSelected = { onEvent(ImportWizardEvent.ConfigColorChanged(it)) },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        OutlinedButton(
            onClick = { onEvent(ImportWizardEvent.ConfigBackClicked) },
            enabled = !state.inProgress,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.import_wizard_config_back))
        }
        Button(
            onClick = { onEvent(ImportWizardEvent.ConfigNextClicked) },
            enabled = !state.inProgress,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(
                    if (state.isLastConfigStep) {
                        R.string.import_wizard_config_done
                    } else {
                        R.string.import_wizard_config_next
                    },
                ),
            )
        }
    }
}
