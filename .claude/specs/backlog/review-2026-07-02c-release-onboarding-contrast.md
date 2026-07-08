# Fix release onboarding text contrast on dark neon background
Epic: review-2026-07
Order: 02c of 35
Status: draft
Depends-on: review-2026-07-02
Date: 2026-07-08

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: Fix the release/benchmark onboarding screen so title and body text remain readable on the dark neon background, then verify the first-run release-style onboarding surface on `Pixel_5_API_34`.
LAYERS: [presentation]
CHANGED_HINT: feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding, core/ui/src/main/java/com/kshavrin/mymoney/core/ui
TEST_TYPES: [compose-ui]
CONSTRAINTS: do not change onboarding flow semantics; keep EN/RU strings unchanged unless a string is genuinely wrong; preserve release `SHOW_ONBOARDING=true`; add or update a contrast/rendering regression where practical; visual evidence must come from a device or approved screenshot path
=== END SPEC ===

## Gap / context
Slice 5 release-style `benchmarkRelease` smoke on 2026-07-08 showed the first
onboarding page with near-black title/body text over the dark background, while the
CTA and icon remained visible. Evidence:
`build/visual-check/mymoney-benchmark-launch-installed.png`. Debug builds skip
onboarding, so this escaped the usual debug launch smoke.

## Implementation links
- commit: (pending)
- files: (pending)
