package com.kshavrin.mymoney.feature.dashboard

import androidx.compose.ui.graphics.Color

internal fun parseHexColor(hex: String): Color =
    try {
        val cleaned = hex.removePrefix("#")
        val argb = if (cleaned.length == 6) "FF$cleaned" else cleaned
        Color(argb.toLong(16))
    } catch (_: Exception) {
        Color.Gray
    }
