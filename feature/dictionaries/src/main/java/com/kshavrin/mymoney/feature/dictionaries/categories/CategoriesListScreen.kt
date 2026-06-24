package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIcon
import com.kshavrin.mymoney.core.designsystem.icon.NeonCategoryIconDefaults
import com.kshavrin.mymoney.core.domain.model.Category
import com.kshavrin.mymoney.core.domain.model.CategoryKind
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.parseHexColor

@Composable
fun CategoriesListRoute(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: CategoriesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is CategoriesListAction.NavigateEdit -> onEdit(action.id)
                CategoriesListAction.NavigateAdd -> onAdd()
                CategoriesListAction.NavigateBack -> onBack()
            }
        }
    }
    CategoriesListContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesListContent(
    state: CategoriesListState,
    onEvent: (CategoriesListEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionaries_categories_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(CategoriesListEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(CategoriesListEvent.AddClicked) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.dictionaries_add))
            }
        },
    ) { innerPadding ->
        val expenseTitle = stringResource(R.string.dictionaries_section_expense)
        val incomeTitle = stringResource(R.string.dictionaries_section_income)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
        ) {
            sectionHeader(expenseTitle)
            categorySection(
                items = state.expense,
                kind = CategoryKind.Expense,
                onClick = { onEvent(CategoriesListEvent.ItemClicked(it)) },
                onReordered = { newOrder ->
                    onEvent(CategoriesListEvent.Reordered(CategoryKind.Expense, newOrder))
                },
            )

            sectionHeader(incomeTitle)
            categorySection(
                items = state.income,
                kind = CategoryKind.Income,
                onClick = { onEvent(CategoriesListEvent.ItemClicked(it)) },
                onReordered = { newOrder ->
                    onEvent(CategoriesListEvent.Reordered(CategoryKind.Income, newOrder))
                },
            )
        }
    }
}

private fun LazyGridScope.sectionHeader(title: String) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

private fun LazyGridScope.categorySection(
    items: List<Category>,
    kind: CategoryKind,
    onClick: (Long) -> Unit,
    onReordered: (List<Category>) -> Unit,
) {
    item(span = { GridItemSpan(maxLineSpan) }, key = "section_$kind") {
        CategorySectionGrid(
            items = items,
            kind = kind,
            onClick = onClick,
            onReordered = onReordered,
        )
    }
}

@Composable
private fun CategorySectionGrid(
    items: List<Category>,
    kind: CategoryKind,
    onClick: (Long) -> Unit,
    onReordered: (List<Category>) -> Unit,
) {
    var localItems by remember(items) { mutableStateOf(items) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var pointerWindow by remember { mutableStateOf(Offset.Zero) }
    val itemBounds = remember(kind) { mutableStateMapOf<Long, Rect>() }

    val rows = localItems.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { cat ->
                    val isDragged = cat.id == draggedId
                    val currentCenter = itemBounds[cat.id]?.center ?: Offset.Zero
                    val visualOffset =
                        if (isDragged) {
                            Offset(pointerWindow.x - currentCenter.x, pointerWindow.y - currentCenter.y)
                        } else {
                            Offset.Zero
                        }
                    CategoryCard(
                        category = cat,
                        isDragged = isDragged,
                        dragOffset = visualOffset,
                        modifier =
                            Modifier
                                .weight(1f)
                                .onGloballyPositioned { coords ->
                                    itemBounds[cat.id] = coords.boundsInWindow()
                                }.pointerInput(cat.id, kind) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = cat.id
                                            pointerWindow = itemBounds[cat.id]?.center ?: Offset.Zero
                                        },
                                        onDragEnd = {
                                            val finalOrder = localItems
                                            draggedId = null
                                            pointerWindow = Offset.Zero
                                            onReordered(finalOrder)
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            pointerWindow = Offset.Zero
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            pointerWindow += dragAmount
                                            val targetId =
                                                itemBounds.entries
                                                    .firstOrNull { (id, bounds) ->
                                                        id != cat.id && bounds.contains(pointerWindow)
                                                    }?.key
                                            if (targetId != null) {
                                                val fromIdx = localItems.indexOfFirst { it.id == cat.id }
                                                val toIdx = localItems.indexOfFirst { it.id == targetId }
                                                if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                                    val mutable = localItems.toMutableList()
                                                    val moved = mutable.removeAt(fromIdx)
                                                    mutable.add(toIdx, moved)
                                                    localItems = mutable
                                                }
                                            }
                                        },
                                    )
                                },
                        onClick = { onClick(cat.id) },
                    )
                }
                repeat(3 - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    isDragged: Boolean,
    dragOffset: Offset,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cardModifier =
        modifier
            .then(
                if (isDragged) {
                    Modifier
                        .graphicsLayer {
                            translationX = dragOffset.x
                            translationY = dragOffset.y
                            alpha = 0.8f
                        }.shadow(elevation = 8.dp)
                } else {
                    Modifier
                },
            ).clickable(enabled = !isDragged, onClick = onClick)
            .padding(8.dp)

    Column(
        modifier = cardModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            NeonCategoryIcon(
                iconKey = category.iconKey,
                accent = parseHexColor(category.colorHex),
                containerSize = NeonCategoryIconDefaults.ContainerSize,
                iconSize = NeonCategoryIconDefaults.ListIconSize,
            )
        }
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
