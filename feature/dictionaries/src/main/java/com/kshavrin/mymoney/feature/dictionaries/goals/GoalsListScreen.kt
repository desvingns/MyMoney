package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.common.money.MoneyFormatter
import com.kshavrin.mymoney.core.designsystem.icon.goalIcon
import com.kshavrin.mymoney.core.domain.model.Goal
import com.kshavrin.mymoney.core.domain.model.GoalVariant
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.goalCreditChipContainer
import com.kshavrin.mymoney.core.ui.theme.goalCreditChipContent
import com.kshavrin.mymoney.core.ui.theme.goalIconCircleContent
import com.kshavrin.mymoney.core.ui.theme.goalListEmptyTitle
import com.kshavrin.mymoney.core.ui.theme.goalListRowAmount
import com.kshavrin.mymoney.core.ui.theme.goalSavingsChipContainer
import com.kshavrin.mymoney.core.ui.theme.goalSavingsChipContent
import com.kshavrin.mymoney.feature.dictionaries.R
import com.kshavrin.mymoney.feature.dictionaries.common.parseHexColor
import java.util.Locale

@Composable
fun GoalsListRoute(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: GoalsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.actions.collect { action ->
            when (action) {
                is GoalsListAction.NavigateEdit -> onEdit(action.id)
                GoalsListAction.NavigateAdd -> onAdd()
                GoalsListAction.NavigateBack -> onBack()
            }
        }
    }
    GoalsListContent(state = state, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsListContent(
    state: GoalsListState,
    onEvent: (GoalsListEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(GoalsListEvent.BackClicked) }) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEvent(GoalsListEvent.AddClicked) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.goals_add))
            }
        },
    ) { innerPadding ->
        if (state.rows.isEmpty()) {
            GoalsEmptyState(modifier = Modifier.fillMaxSize().padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(state.rows, key = { it.id }) { goal ->
                    GoalRowItem(
                        goal = goal,
                        onClick = { onEvent(GoalsListEvent.ItemClicked(goal.id)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GoalsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(Spacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Flag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Spacing.xxl),
        )
        Text(
            text = stringResource(R.string.goals_empty),
            style = MaterialTheme.typography.goalListEmptyTitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

@Composable
private fun GoalRowItem(goal: Goal, onClick: () -> Unit) {
    val amountText = MoneyFormatter.format(
        amount = goal.targetAmount,
        currencySymbol = "",
        decimalDigits = 2,
        locale = Locale.getDefault(),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Box(
            modifier = Modifier
                .size(Spacing.goalListIconCircleSize)
                .clip(CircleShape)
                .background(parseHexColor(goal.colorHex)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = goalIcon(goal.iconKey),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.goalIconCircleContent,
                modifier = Modifier.size(Spacing.goalListIconSize),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = goal.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = amountText,
                style = MaterialTheme.typography.goalListRowAmount,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GoalVariantChip(variant = goal.variant)
    }
}

@Composable
private fun GoalVariantChip(variant: GoalVariant) {
    val labelRes = when (variant) {
        GoalVariant.SAVINGS -> R.string.goals_variant_savings
        GoalVariant.CREDIT -> R.string.goals_variant_credit
    }
    val containerColor = when (variant) {
        GoalVariant.SAVINGS -> MaterialTheme.colorScheme.goalSavingsChipContainer
        GoalVariant.CREDIT -> MaterialTheme.colorScheme.goalCreditChipContainer
    }
    val contentColor = when (variant) {
        GoalVariant.SAVINGS -> MaterialTheme.colorScheme.goalSavingsChipContent
        GoalVariant.CREDIT -> MaterialTheme.colorScheme.goalCreditChipContent
    }
    AssistChip(
        onClick = {},
        label = { Text(stringResource(labelRes)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = contentColor,
        ),
    )
}
