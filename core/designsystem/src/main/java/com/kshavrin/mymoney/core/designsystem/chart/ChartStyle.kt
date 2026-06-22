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
    ;

    companion object {
        // Neon-wave look from the dashboard mockup (ChartWave / SecAurora): a smooth accent area
        // with a glowing line and a highlighted last point. SmoothArea's renderer is refined to
        // reproduce ChartWave 1:1.
        val Default = SmoothArea
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
