# Aurora balance label contrast
Epic: review-2026-07
Order: follow-up to 16
Status: backlog
Depends-on: review-2026-07-16-a11y-manual-pass
Date: 2026-07-15

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Raise the Aurora `FREE BALANCE` label contrast to WCAG AA 4.5:1 against the actual dark panel fill while preserving the approved Aurora visual hierarchy and the DashboardAuroraInnerPanelFill alpha 0.045 token. Verify the result with a fresh Pixel 5 API 34 screenshot and a measured ratio.
LAYERS: [presentation]
CHANGED_HINT: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt; core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/AuroraBalanceCard.kt
TEST_TYPES: [compose-ui]
CONSTRAINTS: do not change balance semantics, chart semantics, or AS-12/AS-14 behavior; no hardcoded user-facing strings; preserve the Aurora layout; prove the ratio from an actual device screenshot rather than source-color inference alone
=== END SPEC ===

## Evidence

The manual pass captured `build/visual-check/a11y-2026-07-15-manual-pass/05-dashboard-first-launch.png` at 1080x2340. Dominant core text pixels for `FREE BALANCE` were `#98B4B3` over adjacent panel pixels `#1A5956`; WCAG ratio = **3.65:1**, below the normal-text AA threshold 4.5:1. Other sampled candidates passed. Full report: `docs/a11y/2026-07-15-manual-accessibility-pass.md`.

## Acceptance

```gherkin
Feature: Aurora balance label meets WCAG AA

  Scenario: Free balance label is readable on the Aurora panel
    When the dashboard is rendered on Pixel 5 API 34 at the default font scale
    Then the measured screenshot ratio for FREE BALANCE against its panel background is at least 4.5:1
    And the balance value, pills, chart summary semantics, and layout remain unchanged
```
