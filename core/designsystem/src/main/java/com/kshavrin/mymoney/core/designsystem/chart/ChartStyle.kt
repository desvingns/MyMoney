package com.kshavrin.mymoney.core.designsystem.chart

enum class ChartStyle {
    NeonLine,
    NeonArea,
    SmoothLine,
    SmoothArea,
    SteppedLine,
    SteppedArea,
    Bars,
    RoundedBars,
    DotsLine,
    DotsOnly,
    GradientStroke,
    DualGlow,
    DashedLine,
    ThinMinimal,
    ThickBold,
    BaselineFill,
    VerticalGradientArea,
    CandySegments,
    Mountain,
    Ribbon,
    Line,
    ;

    companion object {
        val Default = NeonLine
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
