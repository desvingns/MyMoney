package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

private val NeonBackground = Color(0xFF0A0E1C)
private val NeonSurface = Color(0xFF111A2E)
private val NeonSurfaceAlt = Color(0xFF1B2236)
private val NeonTextPrimary = Color(0xFFE8EAF0)
private val NeonTextSecondary = Color(0xFF7C8290)
private val NeonOnSurfaceVariant = Color(0xFF8E96A8)
private val NeonMint = Color(0xFF5BE3B0)
private val NeonCyan = Color(0xFF46B6E6)
private val NeonCoral = Color(0xFFFF8A80)
private val NeonCoralBright = Color(0xFFFF9EA4)
private val NeonRed = Color(0xFFE63950)
private val NeonTrack = Color(0xFF1A2236)

private val NeonColors =
    darkColorScheme(
        primary = NeonMint,
        onPrimary = NeonBackground,
        primaryContainer = NeonSurfaceAlt,
        onPrimaryContainer = NeonTextPrimary,
        secondary = NeonCyan,
        onSecondary = NeonBackground,
        secondaryContainer = NeonTrack,
        onSecondaryContainer = NeonTextPrimary,
        tertiary = NeonCoral,
        onTertiary = NeonBackground,
        tertiaryContainer = Color(0xFF3B2028),
        onTertiaryContainer = Color(0xFFFFDAD6),
        background = NeonBackground,
        onBackground = NeonTextPrimary,
        surface = NeonSurface,
        onSurface = NeonTextPrimary,
        surfaceVariant = NeonSurfaceAlt,
        onSurfaceVariant = NeonOnSurfaceVariant,
        outline = Color(0xFF536078),
        outlineVariant = Color(0xFF2B354D),
        error = NeonCoral,
        onError = NeonBackground,
        errorContainer = Color(0xFF5C2B2E),
        onErrorContainer = Color(0xFFFFDAD6),
        inverseSurface = NeonTextPrimary,
        inverseOnSurface = NeonBackground,
        inversePrimary = Color(0xFF006C52),
        surfaceTint = NeonMint,
        scrim = Color.Black,
    )

val LightColors = NeonColors
val DarkColors = NeonColors

val CategoryColors: Map<String, Color> =
    mapOf(
        "clothing" to Color(0xFF9C5BB8),
        "bills" to Color(0xFFC9A227),
        "food" to Color(0xFFE07AAE),
        "entertainment" to Color(0xFFF08A3E),
        "taxi" to Color(0xFFE0A52C),
        "housing" to Color(0xFF4A8FCB),
        "health" to Color(0xFFD85A5A),
        "pets" to Color(0xFF3DA98A),
        "sport" to Color(0xFF7AC29A),
        "gifts" to Color(0xFFD9A4A4),
        "phone" to Color(0xFF9CBBA8),
        "transport" to Color(0xFFE07A7A),
        "hygiene" to Color(0xFF3A4F8C),
        "cafe" to Color(0xFF7A9685),
        "car" to Color(0xFF4A5870),
    )

private val DashboardLightBalancePanelContainerNegative = Color(0xFFFCEAEA)
private val DashboardLightBalancePanelContentNegative = Color(0xFFD64545)
private val DashboardDarkBalancePanelContentNegative = Color(0xFFEF9A9A)
private val DashboardDonutOtherSlice = Color(0xFF9E9E9E)

private val ColorScheme.isLightDashboardPalette: Boolean
    get() = background.luminance() > 0.5f

val ColorScheme.recordsHeaderStripContainer: Color
    get() = surface

val ColorScheme.recordsHeaderBalanceContainer: Color
    get() = primary

val ColorScheme.recordsHeaderBalanceContent: Color
    get() = onPrimary

val ColorScheme.recordsHeaderBalanceOutline: Color
    get() = secondary

val ColorScheme.recordsHeaderBalanceShadow: Color
    get() = secondary.copy(alpha = 0.28f)

val ColorScheme.recordsHeaderSortTint: Color
    get() = primary

val ColorScheme.recordsHeaderSortContainer: Color
    get() = surface.copy(alpha = 0f)

val ColorScheme.recordsFilterChipContainer: Color
    get() = primaryContainer

val ColorScheme.recordsFilterChipContent: Color
    get() = onPrimaryContainer

val ColorScheme.recordsFilterChipBorder: Color
    get() = outline

val ColorScheme.dashboardDrawerScrim: Color
    get() = scrim.copy(alpha = 0.32f)

val ColorScheme.dashboardDrawerPanelContainer: Color
    get() = surface

val ColorScheme.dashboardDrawerPanelContent: Color
    get() = onSurface

val ColorScheme.neonRingGradientStart: Color
    get() = NeonMint

val ColorScheme.neonRingGradientEnd: Color
    get() = NeonCyan

val ColorScheme.neonRingTrack: Color
    get() = NeonTrack

val ColorScheme.neonRingGradientStartExpense: Color
    get() = NeonCoralBright

val ColorScheme.neonRingGradientEndExpense: Color
    get() = NeonRed

val ColorScheme.dashboardNeonBackground: Color
    get() = NeonBackground

val ColorScheme.tileSurface: Color
    get() = NeonSurface

val ColorScheme.tileSurfaceAlt: Color
    get() = NeonSurfaceAlt

val ColorScheme.textPrimary: Color
    get() = NeonTextPrimary

val ColorScheme.textSecondary: Color
    get() = NeonTextSecondary

val ColorScheme.incomeAccent: Color
    get() = NeonMint

val ColorScheme.expenseAccent: Color
    get() = NeonCoral

val ColorScheme.dashboardRingCenterBadgeDivider: Color
    get() = Color.White.copy(alpha = 0.08f)

val ColorScheme.dashboardHeroGradientStart: Color
    get() = NeonMint

val ColorScheme.dashboardHeroGradientEnd: Color
    get() = NeonCyan

val ColorScheme.dashboardPeriodSelectedText: Color
    get() = NeonTextPrimary

val ColorScheme.dashboardPeriodUnselectedText: Color
    get() = NeonTextSecondary

val ColorScheme.dashboardPeriodIndicator: Color
    get() = dashboardPeriodSelectedText

val ColorScheme.dashboardBalancePanelContainer: Color
    get() = NeonSurfaceAlt

val ColorScheme.dashboardBalancePanelContent: Color
    get() = NeonTextPrimary

val ColorScheme.dashboardBalancePanelOutline: Color
    get() = NeonMint.copy(alpha = 0.8f)

val ColorScheme.dashboardBalancePanelShadow: Color
    get() = NeonMint.copy(alpha = 0.18f)

val ColorScheme.dashboardBalancePanelContainerNegative: Color
    get() =
        if (isLightDashboardPalette) {
            DashboardLightBalancePanelContainerNegative
        } else {
            error.copy(alpha = 0.16f).compositeOver(surface)
        }

val ColorScheme.dashboardBalancePanelContentNegative: Color
    get() = if (isLightDashboardPalette) DashboardLightBalancePanelContentNegative else DashboardDarkBalancePanelContentNegative

val ColorScheme.dashboardDonutCenterDivider: Color
    get() = NeonTrack

val ColorScheme.dashboardDonutLeaderLine: Color
    get() = NeonTextSecondary.copy(alpha = 0.7f)

val ColorScheme.dashboardDonutOtherSlice: Color
    get() = DashboardDonutOtherSlice

val ColorScheme.dashboardCalloutLabel: Color
    get() = NeonTextPrimary.copy(alpha = 0.88f)

val ColorScheme.dashboardActionIncome: Color
    get() = NeonMint

val ColorScheme.dashboardActionExpense: Color
    get() = NeonCoral

// Transfer FAB accent — NeonCyan completes the three-button neon triad
// (coral=expense / cyan=transfer / mint=income) per the «Dashboard Final» mockup (RealFabs).
// Named alias for symmetry with dashboardActionIncome / dashboardActionExpense so the
// developer never needs to reference secondary directly for this semantic role.
val ColorScheme.dashboardActionTransfer: Color
    get() = NeonCyan

val ColorScheme.goalSavingsChipContainer: Color
    get() = primaryContainer

val ColorScheme.goalSavingsChipContent: Color
    get() = onPrimaryContainer

val ColorScheme.goalCreditChipContainer: Color
    get() = if (isLightDashboardPalette) Color(0xFFFFF0F0) else surfaceVariant

val ColorScheme.goalCreditChipContent: Color
    get() = if (isLightDashboardPalette) Color(0xFFB91C1C) else tertiary

val ColorScheme.goalIconCircleContent: Color
    get() = onPrimary

val ColorScheme.goalCreditProjectionContainer: Color
    get() = if (isLightDashboardPalette) surfaceVariant.copy(alpha = 0.72f) else surfaceVariant

val ColorScheme.goalCreditProjectionContent: Color
    get() = onSurface

val ColorScheme.goalCreditUnderfundedContainer: Color
    get() = if (isLightDashboardPalette) Color(0xFFFFF3E0) else Color(0xFF4E3800)

val ColorScheme.goalCreditUnderfundedContent: Color
    get() = if (isLightDashboardPalette) Color(0xFF7C4B00) else Color(0xFFFFD54F)

val ColorScheme.goalFormReadOnlyContainer: Color
    get() = surfaceVariant.copy(alpha = 0.56f)

val ColorScheme.goalBreakdownSectionContainer: Color
    get() = surfaceVariant.copy(alpha = 0.38f)

val ColorScheme.goalBreakdownSectionOutline: Color
    get() = outline.copy(alpha = 0.38f)

// Edit-form delete action — uses M3 low-prominence destructive pattern
// (errorContainer/onErrorContainer) so the button is clearly destructive
// without the full visual weight of a filled error button.
val ColorScheme.transactionFormDeleteContainer: Color
    get() = errorContainer

val ColorScheme.transactionFormDeleteContent: Color
    get() = onErrorContainer

// Transaction leaf row tokens (S11 «Операции» list — TransactionLeaf)
// Note text colour — subordinate muted text sitting between amount and date.
// Alias of onSurfaceVariant; named alias lets the developer reference the semantic intent
// without repeating MaterialTheme.colorScheme.onSurfaceVariant at each call site.
val ColorScheme.transactionLeafNoteColor: Color
    get() = onSurfaceVariant

// Transfer list row tokens (S12 «Переводы» tab)
// Neutral direction arrow between accounts — uses outline so it sits quietly
// between the two account names without implying income or expense polarity.
val ColorScheme.transferArrowTint: Color
    get() = outline

// Transfer amount — uses onSurface (neither income-green nor expense-red);
// matches the neutral semantic of a balance-neutral movement between accounts.
val ColorScheme.transferRowAmount: Color
    get() = onSurface

// Import-migration wizard — strategy option card (selected state).
// M3 selection pattern: primaryContainer fill so the chosen option is clearly distinct
// from the default surface unselected card without implying error or destructive intent.
val ColorScheme.wizardStrategyCardSelectedContainer: Color
    get() = primaryContainer

val ColorScheme.wizardStrategyCardSelectedContent: Color
    get() = onPrimaryContainer

val ColorScheme.wizardStrategyCardSelectedBorder: Color
    get() = primary

// Import-migration wizard — orphan-category warning dialog container.
// A warm amber tone distinct from the error-red used by the destructive-action dialog;
// signals "attention needed" rather than "data will be deleted".
private val WizardOrphanWarningLightContainer = Color(0xFFFFF3E0)
private val WizardOrphanWarningLightContent = Color(0xFF7C4B00)
private val WizardOrphanWarningDarkContainer = Color(0xFF4E3800)
private val WizardOrphanWarningDarkContent = Color(0xFFFFD54F)

val ColorScheme.wizardOrphanWarningContainer: Color
    get() = if (isLightDashboardPalette) WizardOrphanWarningLightContainer else WizardOrphanWarningDarkContainer

val ColorScheme.wizardOrphanWarningContent: Color
    get() = if (isLightDashboardPalette) WizardOrphanWarningLightContent else WizardOrphanWarningDarkContent

// Import-migration wizard — step progress indicator ("k / N" chip).
// Reuses surfaceVariant/onSurfaceVariant so the counter is quiet — informational,
// not a primary action affordance. Named aliases preserve semantic intent at call sites.
val ColorScheme.wizardStepProgressContainer: Color
    get() = surfaceVariant

val ColorScheme.wizardStepProgressContent: Color
    get() = onSurfaceVariant

// Import-migration wizard — color picker selected swatch border.
// Primary ring makes the selected color clearly distinct from unselected swatches;
// matches M3 selection ring convention (FilterChip, RadioButton tint).
val ColorScheme.wizardColorPickerSelectedBorder: Color
    get() = primary

// Rate confirm dialog — stale-rate badge (warning state: last update is old).
// Amber tone signals "attention needed but not an error"; reuses the same warm-amber
// constants as the wizard orphan-warning so the palette stays consistent.
// The "missing/no-internet" state maps to the standard M3 error tokens
// (colorScheme.error / onError) — no separate alias required.
val ColorScheme.rateStaleContainer: Color
    get() = if (isLightDashboardPalette) WizardOrphanWarningLightContainer else WizardOrphanWarningDarkContainer

val ColorScheme.rateStaleContent: Color
    get() = if (isLightDashboardPalette) WizardOrphanWarningLightContent else WizardOrphanWarningDarkContent

// Dashboard «Все счета» Separate mode — per-currency balance card header label.
// Shows the ISO currency code (e.g. «USD», «KZT») as a muted badge above the balance
// figures. Aliased from textSecondary so the label stays subordinate to the balance value
// while clearly marking which currency group the card belongs to.
val ColorScheme.dashboardCurrencyCardCurrencyLabel: Color
    get() = NeonTextSecondary

// Balance trend chart — zero-baseline rule (D8).
// Rendered only when the data series crosses zero (min < 0 < max); uses outline so the
// line is visible against both positive (neonMint) and negative (neonCoral) segments
// without competing with the series line itself.
val ColorScheme.trendChartZeroLine: Color
    get() = outline

// Balance trend chart — decorative vertical grid lines (D6).
// Three equidistant hairlines drawn at low alpha so they recede behind the series line.
val ColorScheme.trendChartGridLine: Color
    get() = NeonTextSecondary.copy(alpha = 0.25f)

// Balance trend chart — glow halo around the last-point marker (D9).
// Mirrors neonRingGradientStart so the pulsing dot feels consistent with the ring chart.
val ColorScheme.trendChartMarkerGlow: Color
    get() = NeonMint

// ── Aurora hero card (SecAurora / ChartWave design tokens) ───────────────────
// Accent for the Aurora card and the default ChartWave series stroke.
// Source: accentOf() in neon-core.jsx returns '#37E1C0' for multi-hue mode (the default).
// Used for: radial-gradient top stop, inset border, neon glow, wave polyline + fill,
//           and as the text-glow accent on the balance value.
private val DashboardAuroraAccentBase = Color(0xFF37E1C0)

val ColorScheme.dashboardAuroraAccent: Color
    get() = DashboardAuroraAccentBase

// Income pill — green neon (#3DF59B). Distinct from NeonMint (0xFF5BE3B0).
// Used as: pill text color, pill background @0.12, pill inset border @0.3.
private val DashboardIncomePillBase = Color(0xFF3DF59B)

val ColorScheme.dashboardIncomePill: Color
    get() = DashboardIncomePillBase

// Expense pill — coral-pink neon (#FF8A9B). Distinct from NeonCoral (0xFFFF8A80).
// Used as: pill text color, pill background @0.12, pill inset border @0.3.
private val DashboardExpensePillBase = Color(0xFFFF8A9B)

val ColorScheme.dashboardExpensePill: Color
    get() = DashboardExpensePillBase

// Aurora card sign-aware accent (G9).
// net >= 0 (positive or zero) → NeonMint; net < 0 → NeonRed.
// Use instead of dashboardAuroraAccent when the card tint should reflect the sign
// of the period net balance. The caller determines the sign via signum() >= 0.
// dashboardAuroraAccent (DashboardAuroraAccentBase) is kept as the neutral default.
fun ColorScheme.dashboardAuroraAccentForSign(positive: Boolean): Color =
    if (positive) NeonMint else NeonRed

// Aurora hero card — dark display panel behind all card content (balance + pills + chart).
// Black @0.34 reads as an intentional inset "screen" over the card interior tint without
// collapsing to a pure void; soft enough not to fight the neon accent border glow.
// The panel corner reuses dashboardAuroraCard (24dp) and its perimeter is feathered via
// dashboardAuroraInnerPanelFeather so there is no hard edge between panel and card fill.
private val DashboardAuroraInnerPanelFill = Color.Black.copy(alpha = 0.16f)

val ColorScheme.dashboardAuroraInnerPanel: Color
    get() = DashboardAuroraInnerPanelFill

// Dashboard inline category accordion (SPEC 02 — CategoryRecordsInlineList).
// The accordion block sits flush under the expanded tile; it uses NeonSurfaceAlt so the
// block is visually distinct from the dashboard background (NeonBackground) and from the
// tile surface (NeonSurface) without introducing a new colour.
val ColorScheme.dashboardInlineRecordContainer: Color
    get() = NeonSurfaceAlt

// Hairline divider between individual record rows inside the accordion.
// outlineVariant (@0x2B354D in the neon palette) is dim enough not to compete with
// the amount/note text while still giving the eye a row separator.
val ColorScheme.dashboardInlineRecordDivider: Color
    get() = outlineVariant
