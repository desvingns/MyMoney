# contentDescription sweep across all actionable UI
Epic: review-2026-07
Order: 14 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Audit every Icon/IconButton/clickable image across the 8 feature modules and :core:designsystem (only 79 contentDescription instances exist for ~30 screens): give every ACTIONABLE element a meaningful localized contentDescription (EN + RU string resources), mark every decorative element with explicit contentDescription = null, and record the per-screen result table in the SPEC report.
LAYERS: [presentation]
CHANGED_HINT: feature/*/src/main (Icon( / IconButton( / painterResource call sites), per-module strings.xml + values-ru/strings.xml
TEST_TYPES: [compose-ui]
CONSTRAINTS: descriptions come from string resources, never literals; RU translations required (pairs with the SPEC 05 lint gate); do not add descriptions to decorative art (that worsens TalkBack); keep the donut/trend charts for SPEC 15
=== END SPEC ===

## Gap / context
79 contentDescriptions across a 27-screen app means most icon buttons are silent in
TalkBack. Source: review item 30 (P2/M).

## Implementation links
- commit: (pending)
- files: (pending)
