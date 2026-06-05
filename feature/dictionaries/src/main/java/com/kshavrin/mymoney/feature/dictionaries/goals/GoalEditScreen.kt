package com.kshavrin.mymoney.feature.dictionaries.goals

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.kshavrin.mymoney.feature.dictionaries.R
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GoalEditViewModel @Inject constructor() : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditRoute(
    onBack: () -> Unit,
    viewModel: GoalEditViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.goals_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.dictionaries_back),
                        )
                    }
                },
            )
        },
    ) { _ -> }
}
