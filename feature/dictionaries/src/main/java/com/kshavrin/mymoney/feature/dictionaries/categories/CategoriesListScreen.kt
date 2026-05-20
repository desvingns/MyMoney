package com.kshavrin.mymoney.feature.dictionaries.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.domain.model.Category
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.dictionaries_section_expense),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            CategoryGrid(items = state.expense, onClick = { onEvent(CategoriesListEvent.ItemClicked(it)) })

            Text(
                text = stringResource(R.string.dictionaries_section_income),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            CategoryGrid(items = state.income, onClick = { onEvent(CategoriesListEvent.ItemClicked(it)) })
        }
    }
}

@Composable
private fun CategoryGrid(items: List<Category>, onClick: (Long) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(items, key = { it.id }) { cat ->
            CategoryCard(category = cat, onClick = { onClick(cat.id) })
        }
    }
}

@Composable
private fun CategoryCard(category: Category, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(parseHexColor(category.colorHex)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = category.iconKey
                    .removePrefix("ic_cat_")
                    .removePrefix("ic_account_")
                    .take(3),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
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
