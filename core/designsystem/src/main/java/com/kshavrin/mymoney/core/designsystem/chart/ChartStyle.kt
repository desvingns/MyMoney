package com.kshavrin.mymoney.core.designsystem.chart

enum class ChartStyle {
    Bars,
    Line,
    Smooth,
    ;

    companion object {
        val Default = Smooth
    }
}

enum class ChartColorRule(
    val id: String,
) {
    Solid("solid"),
    AlwaysGreen("always_green"),
    AlwaysRed("always_red"),
    ByDirection("by_direction"),
    ;

    companion object {
        val Default = ByDirection

        @Deprecated("Use ByDirection")
        val BySign = ByDirection

        @Deprecated("Use AlwaysGreen")
        val Income = AlwaysGreen

        @Deprecated("Use AlwaysRed")
        val Expense = AlwaysRed
    }
}
