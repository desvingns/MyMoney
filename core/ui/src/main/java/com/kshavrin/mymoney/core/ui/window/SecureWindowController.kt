package com.kshavrin.mymoney.core.ui.window

import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.staticCompositionLocalOf

class SecureWindowController(
    private val window: Window,
) {
    private val secureSources = mutableSetOf<SecureWindowSource>()

    fun setSecure(
        source: SecureWindowSource,
        enabled: Boolean,
    ) {
        if (enabled) {
            secureSources += source
        } else {
            secureSources -= source
        }
        if (secureSources.isEmpty()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

enum class SecureWindowSource {
    AppContent,
    LockOverlay,
    BiometricSetup,
}

val LocalSecureWindowController =
    staticCompositionLocalOf<SecureWindowController> {
        error("A SecureWindowController must be provided by the activity host.")
    }
