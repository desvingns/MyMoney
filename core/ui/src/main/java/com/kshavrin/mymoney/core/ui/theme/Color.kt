package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    primary             = Color(0xFF7AC794), // APK green_2 — top app bar / FAB / income half
    onPrimary           = Color(0xFFFFFFFF), // APK
    primaryContainer    = Color(0xFFA9E0BB), // APK green_1 — active selection
    onPrimaryContainer  = Color(0xFF2E4A3A), // decision — text on primaryContainer
    secondary           = Color(0xFF50AB6F), // APK green_3 — outlines
    onSecondary         = Color(0xFFFFFFFF), // decision
    tertiary            = Color(0xFFF66561), // APK red — expense half, error
    onTertiary          = Color(0xFFFFFFFF), // decision
    background          = Color(0xFFF2FFF7), // APK main_background
    onBackground        = Color(0xFF1C1B1F), // M3-default
    surface             = Color(0xFFF2FFF7), // APK
    onSurface           = Color(0xAE000000), // APK ARGB — 68% black opacity (deliberate)
    surfaceVariant      = Color(0xFFD7F3E1), // APK green_0
    onSurfaceVariant    = Color(0xFF7A9685), // screenshots ±
    outline             = Color(0xFFA9E0BB), // APK green_1
    outlineVariant      = Color(0xFFCFE3D2), // screenshots ±
    error               = Color(0xFFF66561), // APK — same red as tertiary
    onError             = Color(0xFFFFFFFF), // decision
)

val DarkColors = darkColorScheme(
    primary             = Color(0xFF7AC794), // APK — same mint
    onPrimary           = Color(0xFFFFFFFF), // decision
    primaryContainer    = Color(0xFF2F5D3D), // decision derived
    onPrimaryContainer  = Color(0xFFFFFFFF), // decision
    secondary           = Color(0xFF9CBBA8), // decision — lighter contrast
    onSecondary         = Color(0xFF000000), // decision
    tertiary            = Color(0xFFF66561), // APK — same red
    onTertiary          = Color(0xFFFFFFFF), // decision
    background          = Color(0xFF424242), // APK main_background dark
    onBackground        = Color(0xFFE6E1E5), // M3-default
    surface             = Color(0xFF424242), // APK — flat
    onSurface           = Color(0xFFFFFFFF), // APK primaryTextColor dark
    surfaceVariant      = Color(0xFF616161), // APK action_bar_background dark
    onSurfaceVariant    = Color(0xFFCAC4D0), // M3-default
    outline              = Color(0xFF616161), // APK
    outlineVariant      = Color(0xFF4B4B4B), // decision
    error               = Color(0xFFF66561), // APK
    onError             = Color(0xFFFFFFFF), // decision
)

val CategoryColors: Map<String, Color> = mapOf(
    "clothing"      to Color(0xFF9C5BB8),
    "bills"         to Color(0xFFC9A227),
    "food"          to Color(0xFFE07AAE),
    "entertainment" to Color(0xFFF08A3E),
    "taxi"          to Color(0xFFE0A52C),
    "housing"       to Color(0xFF4A8FCB),
    "health"        to Color(0xFFD85A5A),
    "pets"          to Color(0xFF3DA98A),
    "sport"         to Color(0xFF7AC29A),
    "gifts"         to Color(0xFFD9A4A4),
    "phone"         to Color(0xFF9CBBA8),
    "transport"     to Color(0xFFE07A7A),
    "hygiene"       to Color(0xFF3A4F8C),
    "cafe"          to Color(0xFF7A9685),
    "car"           to Color(0xFF4A5870),
)
