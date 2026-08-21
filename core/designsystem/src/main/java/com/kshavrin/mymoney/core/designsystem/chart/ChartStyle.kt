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

enum class ChartColorRule {
    BySign,
    Income,
    Expense,
    ;

    companion object {
        val Default = BySign
    }
}
