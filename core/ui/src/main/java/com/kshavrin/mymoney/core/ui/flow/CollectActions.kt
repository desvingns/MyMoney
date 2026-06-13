package com.kshavrin.mymoney.core.ui.flow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a one-shot action [Flow] only while the host is at least [Lifecycle.State.STARTED],
 * re-subscribing across configuration changes so events emitted between STOP and START are not
 * dropped by a bare [LaunchedEffect] collector. Events emitted while fully paused remain a known
 * limitation of a replay = 0 SharedFlow.
 */
@Composable
fun <T> CollectActions(
    flow: Flow<T>,
    key: Any?,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
    onAction: suspend (T) -> Unit,
) {
    LaunchedEffect(flow, lifecycleOwner, key) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { onAction(it) }
        }
    }
}
