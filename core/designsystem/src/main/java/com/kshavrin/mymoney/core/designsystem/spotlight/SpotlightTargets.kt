package com.kshavrin.mymoney.core.designsystem.spotlight

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned

@Stable
class SpotlightTargetRegistry {
    private val _bounds = mutableStateMapOf<Any, Rect>()

    operator fun get(key: Any): Rect? = _bounds[key]

    internal fun put(key: Any, rect: Rect) {
        _bounds[key] = rect
    }

    internal fun remove(key: Any) {
        _bounds.remove(key)
    }
}

@Composable
fun rememberSpotlightRegistry(): SpotlightTargetRegistry = remember { SpotlightTargetRegistry() }

fun Modifier.spotlightTarget(
    registry: SpotlightTargetRegistry?,
    key: Any,
): Modifier {
    if (registry == null) return this
    return composed {
        DisposableEffect(registry, key) {
            onDispose { registry.remove(key) }
        }
        onGloballyPositioned { coordinates ->
            registry.put(key, coordinates.boundsInRoot())
        }
    }
}
