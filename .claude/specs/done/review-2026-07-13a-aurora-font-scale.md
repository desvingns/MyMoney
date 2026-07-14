# Aurora balance remains readable at large fontScale
Epic: review-2026-07
Order: 13a of 35
Status: done
Depends-on: review-2026-07-13
Date: 2026-07-13
Completed: 2026-07-14

## SPEC
=== SPEC ===
TASK: feature
WHAT: Make the dashboard Aurora balance value remain visually readable without text overflow at fontScale 1.5 and 2.0, including the fixed-size balance typography and the compact multi-currency balance surface.
LAYERS: [presentation]
CHANGED_HINT: feature/dashboard/src/main and core/ui typography tokens; existing DashboardContentUiTest fontScale coverage
TEST_TYPES: [compose-ui]
CONSTRAINTS: preserve the approved Aurora visual hierarchy and normal-scale typography; do not weaken or suppress the automated overflow assertion; verify on Pixel_5_API_34
=== END SPEC ===

## Gap / context
The automated accessibility gate found visual overflow for the long Aurora balance value at fontScale 1.5 and 2.0. This is a product-layout defect, not a test-only failure.

## Implementation links
- commit: d77c3daa
- files: feature/dashboard/.../components/AuroraBalanceCard.kt (AutoShrinkAuroraBalance — measure-and-shrink, floor 16sp, keeps 36sp at fontScale 1.0); core/ui/.../theme/Typography.kt (dashboardAuroraBalanceValueMinSp)
- verified: DashboardContentUiTest fontScale 1.5 + 2.0 !hasVisualOverflow assertions green on Pixel_5 API 34
