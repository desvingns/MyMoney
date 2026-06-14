package com.kshavrin.mymoney.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

val LightColors =
    lightColorScheme(
        primary = Color(0xFF7AC794), // APK green_2 — top app bar / FAB / income half
        onPrimary = Color(0xFFFFFFFF), // APK
        primaryContainer = Color(0xFFA9E0BB), // APK green_1 — active selection
        onPrimaryContainer = Color(0xFF2E4A3A), // decision — text on primaryContainer
        secondary = Color(0xFF50AB6F), // APK green_3 — outlines
        onSecondary = Color(0xFFFFFFFF), // decision
        tertiary = Color(0xFFF66561), // APK red — expense half, error
        onTertiary = Color(0xFFFFFFFF), // decision
        background = Color(0xFFF2FFF7), // APK main_background
        onBackground = Color(0xFF1C1B1F), // M3-default
        surface = Color(0xFFF2FFF7), // APK
        onSurface = Color(0xAE000000), // APK ARGB — 68% black opacity (deliberate)
        surfaceVariant = Color(0xFFD7F3E1), // APK green_0
        onSurfaceVariant = Color(0xFF7A9685), // screenshots ±
        outline = Color(0xFFA9E0BB), // APK green_1
        outlineVariant = Color(0xFFCFE3D2), // screenshots ±
        error = Color(0xFFF66561), // APK — same red as tertiary
        onError = Color(0xFFFFFFFF), // decision
    )

val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7AC794), // APK — same mint
        onPrimary = Color(0xFFFFFFFF), // decision
        primaryContainer = Color(0xFF2F5D3D), // decision derived
        onPrimaryContainer = Color(0xFFFFFFFF), // decision
        secondary = Color(0xFF9CBBA8), // decision — lighter contrast
        onSecondary = Color(0xFF000000), // decision
        tertiary = Color(0xFFF66561), // APK — same red
        onTertiary = Color(0xFFFFFFFF), // decision
        background = Color(0xFF424242), // APK main_background dark
        onBackground = Color(0xFFE6E1E5), // M3-default
        surface = Color(0xFF424242), // APK — flat
        onSurface = Color(0xFFFFFFFF), // APK primaryTextColor dark
        surfaceVariant = Color(0xFF616161), // APK action_bar_background dark
        onSurfaceVariant = Color(0xFFCAC4D0), // M3-default
        outline = Color(0xFF616161), // APK
        outlineVariant = Color(0xFF4B4B4B), // decision
        error = Color(0xFFF66561), // APK
        onError = Color(0xFFFFFFFF), // decision
    )

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

private val DashboardLightHeroGradientEnd = Color(0xFF8FD6A8)
private val DashboardLightPrimaryText = Color(0xFF066A35)
private val DashboardLightBalancePanelContainer = Color(0xFFE9F7EF)
private val DashboardLightBalancePanelOutline = Color(0xFF9ED8B2)
private val DashboardLightBalancePanelContainerNegative = Color(0xFFFCEAEA)
private val DashboardLightBalancePanelContentNegative = Color(0xFFD64545)
private val DashboardDarkBalancePanelContentNegative = Color(0xFFEF9A9A)
private val DashboardLightCenterDivider = Color(0xFFD8E7DD)
private val DashboardDonutOtherSlice = Color(0xFF9E9E9E)
private val DashboardIncomeAccent = Color(0xFF15995B)
private val DashboardExpenseAccent = Color(0xFFF94F4B)

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

val ColorScheme.dashboardHeroGradientStart: Color
    get() = if (isLightDashboardPalette) primary else primaryContainer

val ColorScheme.dashboardHeroGradientEnd: Color
    get() = if (isLightDashboardPalette) DashboardLightHeroGradientEnd else primary

val ColorScheme.dashboardPeriodSelectedText: Color
    get() = if (isLightDashboardPalette) DashboardLightPrimaryText else onSurface

val ColorScheme.dashboardPeriodUnselectedText: Color
    get() = if (isLightDashboardPalette) onSurfaceVariant.copy(alpha = 0.84f) else onSurfaceVariant

val ColorScheme.dashboardPeriodIndicator: Color
    get() = dashboardPeriodSelectedText

val ColorScheme.dashboardBalancePanelContainer: Color
    get() = if (isLightDashboardPalette) DashboardLightBalancePanelContainer else surfaceVariant.copy(alpha = 0.92f)

val ColorScheme.dashboardBalancePanelContent: Color
    get() = if (isLightDashboardPalette) DashboardLightPrimaryText else onSurface

val ColorScheme.dashboardBalancePanelOutline: Color
    get() = if (isLightDashboardPalette) DashboardLightBalancePanelOutline else primary.copy(alpha = 0.8f)

val ColorScheme.dashboardBalancePanelShadow: Color
    get() = if (isLightDashboardPalette) DashboardLightPrimaryText.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.32f)

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
    get() = if (isLightDashboardPalette) DashboardLightCenterDivider else outlineVariant.copy(alpha = 0.9f)

val ColorScheme.dashboardDonutLeaderLine: Color
    get() = if (isLightDashboardPalette) outlineVariant.copy(alpha = 0.78f) else onSurfaceVariant.copy(alpha = 0.7f)

val ColorScheme.dashboardDonutOtherSlice: Color
    get() = DashboardDonutOtherSlice

val ColorScheme.dashboardCalloutLabel: Color
    get() = onSurface.copy(alpha = if (isLightDashboardPalette) 0.92f else 0.88f)

val ColorScheme.dashboardActionIncome: Color
    get() = if (isLightDashboardPalette) DashboardIncomeAccent else primary

val ColorScheme.dashboardActionExpense: Color
    get() = if (isLightDashboardPalette) DashboardExpenseAccent else tertiary

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
