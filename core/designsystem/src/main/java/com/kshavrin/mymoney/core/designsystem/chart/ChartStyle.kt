package com.kshavrin.mymoney.core.designsystem.chart

enum class ChartStyle {
    Line,
    ;

    companion object {
        val Default = Line
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
