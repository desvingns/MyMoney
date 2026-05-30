package com.kshavrin.mymoney.feature.transaction.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.kshavrin.mymoney.core.designsystem.icon.categoryIcon
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.feature.transaction.R

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
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.categories, key = { it.id }) { cat ->
                        CategoryCell(
                            category = cat,
                            onClick = { onEvent(CategoryPickerEvent.CategoryClicked(cat.id)) },
                        )
                    }
                    item(key = ADD_CELL_KEY) {
                        AddCategoryCell(
                            onClick = { onEvent(CategoryPickerEvent.AddCategoryClicked) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = parseHexColor(category.colorHex)
    GridCard(
        contentDescription = category.name,
        icon = categoryIcon(category.iconKey),
        iconTint = tint,
        label = category.name,
        labelColor = tint,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun AddCategoryCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    GridCard(
        contentDescription = stringResource(R.string.add_category_cta),
        icon = Icons.Outlined.AddCircleOutline,
        iconTint = neutral,
        label = stringResource(R.string.add_category_cta),
        labelColor = neutral,
        onClick = onClick,
        modifier = modifier.testTag(ADD_CELL_TAG),
    )
}

@Composable
private fun GridCard(
    contentDescription: String,
    icon: ImageVector,
    iconTint: Color,
    label: String,
    labelColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = SolidColor(MaterialTheme.colorScheme.outlineVariant),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.m, horizontal = Spacing.s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

private const val ADD_CELL_KEY = "category_picker_add_cell"
private const val ADD_CELL_TAG = "category_picker_add_cell"

private fun parseHexColor(hex: String): Color = try {
    val cleaned = hex.removePrefix("#")
    val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
    Color(argb.toLong(16))
} catch (_: Exception) {
    Color.Gray
}
