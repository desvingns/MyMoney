package com.kshavrin.mymoney.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val Purple80 = Color(0xFFD0BCFF)
private val PurpleGrey80 = Color(0xFFCCC2DC)
private val Pink80 = Color(0xFFEFB8C8)

private val Purple40 = Color(0xFF6650A4)
private val PurpleGrey40 = Color(0xFF625B71)
private val Pink40 = Color(0xFF7D5260)

val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40,
    )

val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
    )

val ColorScheme.sharedSyncConnectedContainer: Color
    get() = primaryContainer

val ColorScheme.sharedSyncConnectedContent: Color
    get() = onPrimaryContainer

val ColorScheme.sharedSyncStartingContainer: Color
    get() = tertiaryContainer

val ColorScheme.sharedSyncStartingContent: Color
    get() = onTertiaryContainer

val ColorScheme.sharedSyncSleepingContainer: Color
    get() = secondaryContainer

val ColorScheme.sharedSyncSleepingContent: Color
    get() = onSecondaryContainer

val ColorScheme.sharedSyncRetryingContainer: Color
    get() = surfaceVariant

val ColorScheme.sharedSyncRetryingContent: Color
    get() = onSurfaceVariant

val ColorScheme.sharedSyncErrorContainer: Color
    get() = errorContainer

val ColorScheme.sharedSyncErrorContent: Color
    get() = onErrorContainer

val ColorScheme.sharedSyncStatusOutline: Color
    get() = outlineVariant
