package com.kshavrin.mymoney.feature.transaction.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R
import com.kshavrin.mymoney.feature.transaction.categorygrid.CategoryGrid

@Composable
fun CategoryPickerRoute(
    navController: NavController,
    viewModel: CategoryPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val currentEntry = navController.currentBackStackEntry
    val createdIdFlow = currentEntry
        ?.savedStateHandle
        ?.getStateFlow(CategoryPickerViewModel.KEY_CREATED_CATEGORY_ID, -1L)

    // AS-4: forward the new id to S06/S07 and pop in the same frame so user perceives S22 -> S06 without seeing S09.
    if (createdIdFlow != null) {
        val createdId by createdIdFlow.collectAsState()
        LaunchedEffect(createdId) {
            if (createdId != -1L) {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(CategoryPickerViewModel.KEY_PICKED_CATEGORY_ID, createdId)
                currentEntry.savedStateHandle[CategoryPickerViewModel.KEY_CREATED_CATEGORY_ID] = -1L
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is CategoryPickerAction.ReturnWithId -> {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(CategoryPickerViewModel.KEY_PICKED_CATEGORY_ID, action.id)
                    navController.popBackStack()
                }
                is CategoryPickerAction.AddCategory -> {
                    navController.navigate(
                        "dictionaries/categories/edit/-1?kind=${action.kind.name}&fromPicker=true",
                    )
                }
                CategoryPickerAction.NavigateBack -> navController.popBackStack()
            }
        }
    }

    CategoryPickerContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerContent(
    state: CategoryPickerState,
    onEvent: (CategoryPickerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CategoryPickerEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.amountPreview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.s),
                    contentAlignment = Alignment.Center,
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(state.amountPreview) },
                    )
                }
            }

            if (state.categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.l),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.category_picker_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                CategoryGrid(
                    categories = state.categories,
                    onCategoryClick = { onEvent(CategoryPickerEvent.CategoryClicked(it)) },
                    onAddClick = { onEvent(CategoryPickerEvent.AddCategoryClicked) },
                )
            }
        }
    }
}
