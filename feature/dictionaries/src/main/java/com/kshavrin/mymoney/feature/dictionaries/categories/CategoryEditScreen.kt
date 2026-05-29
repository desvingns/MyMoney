package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.BlockedDeleteDialog
import com.kshavrin.mymoney.feature.dictionaries.common.ColorPicker
import com.kshavrin.mymoney.feature.dictionaries.common.EXPENSE_ICON_KEYS
import com.kshavrin.mymoney.feature.dictionaries.common.INCOME_ICON_KEYS
import com.kshavrin.mymoney.feature.dictionaries.common.IconPickerSheet

@Composable
fun CategoryEditRoute(
    onBack: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CategoryEditAction.NavigateBack -> onBack()
                is CategoryEditAction.NavigateBackToPickerWithId -> onBack()
            }
        }
    }
    CategoryEditContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
fun CategoryEditRoute(
    navController: NavController,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                CategoryEditAction.NavigateBack -> navController.popBackStack()
                is CategoryEditAction.NavigateBackToPickerWithId -> {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("createdCategoryId", action.id)
                    navController.popBackStack()
                }
            }
        }
    }
    CategoryEditContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditContent(
    state: CategoryEditState,
    onEvent: (CategoryEditEvent) -> Unit,
) {
    var iconPickerVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isCreateMode) R.string.dictionaries_category_new
                            else R.string.dictionaries_category_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CategoryEditEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(CategoryEditEvent.NameChanged(it)) },
                label = { Text(stringResource(R.string.dictionaries_field_name)) },
                isError = state.errorMessage != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.errorMessage != null) {
                Text(
                    text = stringResource(R.string.dictionaries_error_name_required),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.dictionaries_field_kind),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.kind == CategoryKind.Expense,
                    onClick = { onEvent(CategoryEditEvent.KindChanged(CategoryKind.Expense)) },
                    label = { Text(stringResource(R.string.dictionaries_kind_expense)) },
                )
                FilterChip(
                    selected = state.kind == CategoryKind.Income,
                    onClick = { onEvent(CategoryEditEvent.KindChanged(CategoryKind.Income)) },
                    label = { Text(stringResource(R.string.dictionaries_kind_income)) },
                )
            }

            Text(
                text = stringResource(R.string.dictionaries_field_icon),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick = { iconPickerVisible = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(state.iconKey)
            }

            Text(
                text = stringResource(R.string.dictionaries_field_color),
                style = MaterialTheme.typography.titleSmall,
            )
            ColorPicker(
                selectedHex = state.colorHex,
                onColorSelected = { onEvent(CategoryEditEvent.ColorChanged(it)) },
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Button(
                    onClick = { onEvent(CategoryEditEvent.SaveClicked) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.dictionaries_save))
                }
                if (!state.isCreateMode) {
                    OutlinedButton(
                        onClick = { onEvent(CategoryEditEvent.DeleteClicked) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.dictionaries_delete))
                    }
                }
            }
        }
    }

    if (iconPickerVisible) {
        val iconKeys = if (state.kind == CategoryKind.Expense) EXPENSE_ICON_KEYS else INCOME_ICON_KEYS
        IconPickerSheet(
            iconKeys = iconKeys,
            selectedIconKey = state.iconKey,
            onIconSelected = {
                onEvent(CategoryEditEvent.IconChanged(it))
                iconPickerVisible = false
            },
            onDismiss = { iconPickerVisible = false },
        )
    }

    if (state.blockedDeleteCount != null) {
        BlockedDeleteDialog(
            entityName = state.name,
            transactionCount = state.blockedDeleteCount,
            onDismiss = { onEvent(CategoryEditEvent.BlockedDeleteDismissed) },
        )
    }
}
