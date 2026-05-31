# Dashboard donut ring icons (S01/S05)
Epic: redesign-monefy-fidelity
Order: 02 of 05
Status: done
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
- commit: 65c08c7 (feat) + 8a9d5ec (test)
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/CategorySlice.kt
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/model/BalanceSnapshot.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/TransactionRepository.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/BalanceCalculator.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/TransactionDao.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/mapper/Mappers.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/projection/CategorySummaryRow.kt

## Deviation note
SPEC constraint said "populate iconKey from the domain Category ... NO new use case / repository
change" assuming `iconKey` was already one hop from the slice mapping. In fact the dashboard maps
`CategoryBalance` (from `BalanceCalculator`), which carried `colorHex` but NOT `iconKey`. The
perimeter-glyph goal is unreachable without `iconKey` on the slice, so `iconKey` was threaded
additively through the existing aggregate chain (DAO SELECT -> CategorySummaryRow -> CategorySummary
-> CategoryBalance -> CategorySlice). New fields default to "" so no test construction broke and no
test file was touched. No new method, interface, repository, or use case was added; only an existing
column was carried. Alternative (inject CategoryRepository into the ViewModel) was rejected as more
invasive: a new VM dependency that breaks the locked DashboardViewModelTest constructor.
