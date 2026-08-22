package com.kshavrin.mymoney.feature.support

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kshavrin.mymoney.core.ui.flow.CollectActions

@Composable
fun SupportRoute(
    onBack: () -> Unit,
    adSlot: @Composable () -> Unit = {},
    plusSlot: @Composable () -> Unit = {},
    viewModel: SupportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        viewModel.onScreenResumed()
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.onScreenResumed()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CollectActions(flow = viewModel.actions, key = viewModel) { action ->
        when (action) {
            SupportAction.NavigateBack -> onBack()
        }
    }
    SupportContent(
        state = state,
        onEvent = viewModel::onEvent,
        adSlot = adSlot,
        plusSlot = plusSlot,
    )
}
