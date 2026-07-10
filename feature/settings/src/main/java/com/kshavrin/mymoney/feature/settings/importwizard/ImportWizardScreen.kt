package com.kshavrin.mymoney.feature.settings.importwizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.domain.csv.ImportCategoryStrategy
import com.kshavrin.mymoney.core.domain.csv.ImportDataStrategy
import com.kshavrin.mymoney.core.domain.csv.OrphanDecision
import com.kshavrin.mymoney.core.ui.flow.CollectActions
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.wizardOrphanWarningContainer
import com.kshavrin.mymoney.core.ui.theme.wizardOrphanWarningContent
import com.kshavrin.mymoney.core.ui.theme.wizardStrategyCard
import com.kshavrin.mymoney.core.ui.theme.wizardStrategyCardSelectedBorder
import com.kshavrin.mymoney.core.ui.theme.wizardStrategyCardSelectedContainer
import com.kshavrin.mymoney.core.ui.theme.wizardStrategyCardSelectedContent
import com.kshavrin.mymoney.feature.settings.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun ImportWizardRoute(
    navController: NavController,
    viewModel: ImportWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val importedMessage = stringResource(R.string.import_wizard_completed)

    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            ImportWizardAction.Cancel -> navController.popBackStack()
            ImportWizardAction.CommitSucceeded -> snackbarHostState.showSnackbar(importedMessage)
            ImportWizardAction.Finished -> navController.popBackStack()
        }
    }

    ImportWizardContent(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportWizardContent(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            val postCommitStep =
                state.step == ImportWizardStep.ConfigGate || state.step == ImportWizardStep.CategoryConfig
            TopAppBar(
                title = { Text(stringResource(R.string.import_wizard_title)) },
                navigationIcon = {
                    if (postCommitStep) {
                        IconButton(onClick = { onEvent(ImportWizardEvent.CloseClicked) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.import_wizard_close),
                            )
                        }
                    } else {
                        IconButton(onClick = { onEvent(ImportWizardEvent.BackClicked) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.import_wizard_back),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                when (state.step) {
                    ImportWizardStep.Preview -> PreviewStep(state)
                    ImportWizardStep.DataStrategy -> DataStrategyStep(state, onEvent)
                    ImportWizardStep.CategoryStrategy -> CategoryStrategyStep(state, onEvent)
                    ImportWizardStep.OrphanDecisions -> OrphanDecisionsStep(state, onEvent)
                    ImportWizardStep.ManualMerge -> ManualMergeStep(state, onEvent)
                    ImportWizardStep.Confirm -> ConfirmStep(state)
                    ImportWizardStep.ConfigGate -> ConfigGateStep(onEvent)
                    ImportWizardStep.CategoryConfig -> CategoryConfigStep(state, onEvent)
                }

                state.errorBannerRes?.let { res ->
                    Text(
                        text = stringResource(res),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (state.step != ImportWizardStep.OrphanDecisions &&
                    state.step != ImportWizardStep.ConfigGate &&
                    state.step != ImportWizardStep.CategoryConfig
                ) {
                    WizardNavButton(state = state, onEvent = onEvent)
                }
            }

            if (state.inProgress) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }

    if (state.destructiveConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { onEvent(ImportWizardEvent.DestructiveDismissed) },
            title = { Text(stringResource(R.string.import_wizard_destructive_title)) },
            text = { Text(stringResource(R.string.import_wizard_destructive_message)) },
            confirmButton = {
                Button(
                    onClick = { onEvent(ImportWizardEvent.DestructiveConfirmed) },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.import_wizard_destructive_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ImportWizardEvent.DestructiveDismissed) }) {
                    Text(stringResource(R.string.import_wizard_destructive_cancel))
                }
            },
        )
    }
}

@Composable
private fun WizardNavButton(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    if (state.step == ImportWizardStep.Confirm) {
        Button(
            onClick = {
                if (state.isDestructive) {
                    onEvent(ImportWizardEvent.DestructiveConfirmRequested)
                } else {
                    onEvent(ImportWizardEvent.DestructiveConfirmed)
                }
            },
            enabled = !state.inProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_wizard_finish))
        }
    } else {
        Button(
            onClick = { onEvent(ImportWizardEvent.NextClicked) },
            enabled = !state.inProgress && state.preview != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_wizard_next))
        }
    }
}

@Composable
private fun PreviewStep(state: ImportWizardState) {
    Text(
        text = stringResource(R.string.import_wizard_preview_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    val preview = state.preview
    if (preview == null) {
        if (!state.inProgress && state.errorBannerRes == null) {
            Text(
                text = stringResource(R.string.import_wizard_preview_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }
    Text(
        text = stringResource(R.string.import_wizard_preview_rows, preview.rowCount),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.import_wizard_preview_categories, preview.categories.size),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.import_wizard_preview_accounts, preview.accounts.size),
        style = MaterialTheme.typography.bodyMedium,
    )
    preview.dateRange?.let { range ->
        Text(
            text =
                stringResource(
                    R.string.import_wizard_preview_date_range,
                    formatImportDate(range.start),
                    formatImportDate(range.end),
                ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun formatImportDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()))

@Composable
private fun DataStrategyStep(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.import_wizard_data_strategy_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    StrategyCard(
        title = stringResource(R.string.import_wizard_data_append),
        description = stringResource(R.string.import_wizard_data_append_desc),
        selected = state.dataStrategy == ImportDataStrategy.Append,
        onClick = { onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.Append)) },
    )
    StrategyCard(
        title = stringResource(R.string.import_wizard_data_append_dedup),
        description = stringResource(R.string.import_wizard_data_append_dedup_desc),
        selected = state.dataStrategy == ImportDataStrategy.AppendDedup,
        onClick = { onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.AppendDedup)) },
    )
    StrategyCard(
        title = stringResource(R.string.import_wizard_data_replace_all),
        description = stringResource(R.string.import_wizard_data_replace_all_desc),
        selected = state.dataStrategy == ImportDataStrategy.ReplaceAll,
        onClick = { onEvent(ImportWizardEvent.DataStrategySelected(ImportDataStrategy.ReplaceAll)) },
    )
}

@Composable
private fun CategoryStrategyStep(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    Text(
        text = stringResource(R.string.import_wizard_category_strategy_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    StrategyCard(
        title = stringResource(R.string.import_wizard_category_append),
        description = stringResource(R.string.import_wizard_category_append_desc),
        selected = state.categoryStrategy == ImportCategoryStrategy.Append,
        onClick = { onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.Append)) },
    )
    StrategyCard(
        title = stringResource(R.string.import_wizard_category_replace),
        description = stringResource(R.string.import_wizard_category_replace_desc),
        selected = state.categoryStrategy == ImportCategoryStrategy.ReplaceCurrent,
        onClick = { onEvent(ImportWizardEvent.CategoryStrategySelected(ImportCategoryStrategy.ReplaceCurrent)) },
    )
    StrategyCard(
        title = stringResource(R.string.import_wizard_category_manual_merge),
        description = stringResource(R.string.import_wizard_category_manual_merge_desc),
        selected = state.categoryStrategy is ImportCategoryStrategy.AppendManualMerge,
        onClick = {
            onEvent(
                ImportWizardEvent.CategoryStrategySelected(
                    ImportCategoryStrategy.AppendManualMerge(emptyList()),
                ),
            )
        },
    )
}

@Composable
private fun OrphanDecisionsStep(
    state: ImportWizardState,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    val next = state.orphanCategories.firstOrNull { it.name !in state.orphanDecisions.keys }
    Text(
        text = stringResource(R.string.import_wizard_orphan_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    if (next != null) {
        OrphanWarningDialog(category = next, onEvent = onEvent)
    }
}

@Composable
private fun OrphanWarningDialog(
    category: OrphanCategory,
    onEvent: (ImportWizardEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onEvent(ImportWizardEvent.OrphanDecided(category.name, OrphanDecision.KeepCategory))
        },
        containerColor = MaterialTheme.colorScheme.wizardOrphanWarningContainer,
        titleContentColor = MaterialTheme.colorScheme.wizardOrphanWarningContent,
        textContentColor = MaterialTheme.colorScheme.wizardOrphanWarningContent,
        title = { Text(stringResource(R.string.import_wizard_orphan_title)) },
        text = {
            Text(
                stringResource(
                    R.string.import_wizard_orphan_message,
                    category.name,
                    category.transactionCount,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onEvent(ImportWizardEvent.OrphanDecided(category.name, OrphanDecision.KeepCategory))
                },
            ) {
                Text(stringResource(R.string.import_wizard_orphan_keep))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onEvent(ImportWizardEvent.OrphanDecided(category.name, OrphanDecision.DeleteTransactions))
                },
            ) {
                Text(stringResource(R.string.import_wizard_orphan_delete))
            }
        },
    )
}

@Composable
private fun ConfirmStep(state: ImportWizardState) {
    Text(
        text = stringResource(R.string.import_wizard_confirm_heading),
        style = MaterialTheme.typography.titleMedium,
    )
    val dataLabel =
        when (state.dataStrategy) {
            ImportDataStrategy.Append -> R.string.import_wizard_data_append
            ImportDataStrategy.AppendDedup -> R.string.import_wizard_data_append_dedup
            ImportDataStrategy.ReplaceAll -> R.string.import_wizard_data_replace_all
        }
    Text(
        text = stringResource(R.string.import_wizard_confirm_data, stringResource(dataLabel)),
        style = MaterialTheme.typography.bodyMedium,
    )
    if (state.dataStrategy != ImportDataStrategy.ReplaceAll) {
        val categoryLabel =
            when (state.categoryStrategy) {
                ImportCategoryStrategy.Append -> R.string.import_wizard_category_append
                ImportCategoryStrategy.ReplaceCurrent -> R.string.import_wizard_category_replace
                is ImportCategoryStrategy.AppendManualMerge -> R.string.import_wizard_category_manual_merge
            }
        Text(
            text = stringResource(R.string.import_wizard_confirm_category, stringResource(categoryLabel)),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    if (state.isDestructive) {
        Text(
            text = stringResource(R.string.import_wizard_confirm_destructive_note),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.wizardStrategyCardSelectedContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.wizardStrategyCardSelectedContent
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.wizardStrategyCard,
        border =
            if (selected) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.wizardStrategyCardSelectedBorder)
            } else {
                null
            },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
