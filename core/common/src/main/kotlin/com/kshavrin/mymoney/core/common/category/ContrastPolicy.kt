package com.kshavrin.mymoney.core.common.category

import kotlin.math.pow
import kotlin.math.roundToInt

fun readableTextHex(hex: String): String {
    val rgb = parseRgbHex(hex) ?: return "#FFFFFF"
    if (relativeLuminance(rgb) >= MIN_TEXT_RELATIVE_LUMINANCE) return rgb.toHex()

    var low = 0.0
    var high = 1.0
    var candidate = rgb
    repeat(12) {
        val amount = (low + high) / 2.0
        val mixed = rgb.mixWithWhite(amount)
        if (relativeLuminance(mixed) >= MIN_TEXT_RELATIVE_LUMINANCE) {
            candidate = mixed
            high = amount
        } else {
            low = amount
        }
    }
    return candidate.toHex()
}

fun categoryTextColorHex(iconKey: String): String =
    readableTextHex(categoryIconDominantHex(iconKey))

private const val MIN_TEXT_RELATIVE_LUMINANCE = 0.35

private data class Rgb(
    val red: Int,
    val green: Int,
    val blue: Int,
) {
    fun mixWithWhite(amount: Double): Rgb =
        Rgb(
            red = mixChannel(red, amount),
            green = mixChannel(green, amount),
            blue = mixChannel(blue, amount),
        )

    fun toHex(): String =
        "#%02X%02X%02X".format(red, green, blue)
}

private fun parseRgbHex(hex: String): Rgb? {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6 || cleaned.any { !it.isDigit() && it.uppercaseChar() !in 'A'..'F' }) {
        return null
    }
    return Rgb(
        red = cleaned.substring(0, 2).toInt(16),
        green = cleaned.substring(2, 4).toInt(16),
        blue = cleaned.substring(4, 6).toInt(16),
    )
}

private fun relativeLuminance(rgb: Rgb): Double =
    0.2126 * rgb.red.linearizedSrgb() +
        0.7152 * rgb.green.linearizedSrgb() +
        0.0722 * rgb.blue.linearizedSrgb()

private fun Int.linearizedSrgb(): Double {
    val channel = this / 255.0
    return if (channel <= 0.04045) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}

private fun mixChannel(
    channel: Int,
    amount: Double,
): Int =
    (channel + (255 - channel) * amount).roundToInt().coerceIn(0, 255)
