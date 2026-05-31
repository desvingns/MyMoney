# Dashboard donut ring icons (S01/S05)
Epic: redesign-monefy-fidelity
Order: 02 of 05
Status: backlog
Depends-on: 01 (categoryIcon registry)
Date: 2026-05-30

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Render each donut perimeter icon as the slice's real category glyph via the SPEC-01 registry, tinted with the slice's own colour — matching S01/S05.
LAYERS: presentation
CHANGED_HINT: core/designsystem/.../donut/CategorySlice.kt (ADD iconKey field); core/designsystem/.../donut/MonefyDonutChart.kt (per-slice painter, not the single global one); feature/dashboard/.../DashboardViewModel.kt (slice mapping -> populate iconKey from Category); CategoryIcons.kt (from SPEC 01); screenshots 01.jpg/05.jpg; TDD §03_style L127-128 & L152-162
TEST_TYPES: unit
CONSTRAINTS:
  - DEPENDS ON SPEC 01 (categoryIcon registry must exist first).
  - Add `iconKey: String` to CategorySlice; populate it in the dashboard slice mapping from the domain Category (already has iconKey + colorHex) — minimal data plumbing, NO new use case / repository change.
  - Each perimeter icon = rememberVectorPainter(categoryIcon(slice.iconKey)) drawn with ColorFilter.tint(slice.color) — replace the single categoryIconPainter at MonefyDonutChart.kt:55/179. Create painters in @Composable scope, look up per slice; keep the Canvas DrawScope draw.
  - Leader line, % label, AS-14 threshold (LABEL_THRESHOLD 0.03f), budget-alert badge geometry unchanged. No hardcoded colours. Registry stays in :core:designsystem (Clean Arch).
=== END SPEC ===

## Gap / context
MonefyDonutChart.kt:55 hardcodes one `categoryIconPainter` (Icons.Filled.Category) and draws it for all slices (line 179). `CategorySlice` carries `color` but no `iconKey`.

## Implementation links
- commit: (pending)
- files:  (pending)
