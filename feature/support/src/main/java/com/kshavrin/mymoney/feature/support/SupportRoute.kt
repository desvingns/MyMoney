package com.kshavrin.mymoney.feature.support

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.kshavrin.mymoney.core.ui.flow.CollectActions

@Composable
fun SupportRoute(
    onBack: () -> Unit,
    adSlot: @Composable () -> Unit = {},
    plusSlot: @Composable () -> Unit = {},
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            SupportAction.NavigateBack -> onBack()
        }
    }
    SupportScreen(
        state = state,
        onEvent = viewModel::onEvent,
        adSlot = adSlot,
        plusSlot = plusSlot,
    )
}
