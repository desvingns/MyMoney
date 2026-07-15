# Manual accessibility pass: contrast check + TalkBack journeys
Epic: review-2026-07
Order: 16 of 35
Status: done
Depends-on: review-2026-07-15
Date: 2026-07-06
Completed: 2026-07-15

## SPEC
=== SPEC ===
TASK: feature
WHAT: Run a recorded manual accessibility pass: (a) contrast measurement of the Aurora balance panel (text over the translucent dark fill, DashboardAuroraInnerPanelFill alpha 0.045) and other low-contrast candidates against WCAG AA 4.5:1, from actual device screenshots; (b) full TalkBack walkthrough of three journeys — onboarding→first launch, add expense via calculator, dashboard reading (donut+trend) — with a per-step checklist (focus order, announcements, actionable labels) saved under docs/; defects filed as follow-up backlog SPECs.
LAYERS: [presentation]
CHANGED_HINT: docs/ (new a11y checklist report), build/visual-check screenshots for contrast sampling — no production code expected
TEST_TYPES: [compose-ui]
CONSTRAINTS: run AFTER SPECs 13–15 land (otherwise it re-finds known gaps); device gate Pixel_5_API_34; report facts with screenshots/measured ratios, not impressions
DESIGN_TOKENS: colorScheme.primary, colorScheme.secondary, colorScheme.tertiary, typography.displayLarge, typography.displayMedium, typography.displaySmall, typography.headlineLarge, typography.headlineMedium, typography.headlineSmall, typography.titleLarge, typography.titleMedium, typography.titleSmall, typography.bodyLarge, typography.bodyMedium, typography.bodySmall, typography.labelLarge, typography.labelMedium, typography.labelSmall, shape.extraSmall, shape.small, shape.medium, shape.large, shape.extraLarge, spacing.xxs, spacing.xs, spacing.s, spacing.m, spacing.l, spacing.xl, spacing.xxl, motion.durationShort, motion.durationMedium, motion.durationLong, motion.easeStandard, motion.easeEmphasized
=== END SPEC ===

## Gap / context
Automated checks can't judge focus order or real TalkBack UX; one recorded manual
pass closes the loop. Source: review items 34+35 (P3/S + P3/M).

## Implementation links
- commit: pending
- files: `docs/a11y/2026-07-15-manual-accessibility-pass.md`; follow-up backlog `review-2026-07-16-aurora-balance-label-contrast.md`; device evidence under `build/visual-check/a11y-2026-07-15-manual-pass/`
