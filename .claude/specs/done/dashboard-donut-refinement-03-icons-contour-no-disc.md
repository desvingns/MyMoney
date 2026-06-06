# Donut icons — убрать круглый фон + привязать к углу своего сектора на внешнем контуре
Epic: dashboard-donut-refinement
Order: 03 of 03
Status: done
Depends-on: —   # soft: 02 reduces tiny-slice count, easing icon collisions
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Two coupled changes to MonefyDonutChart category-icon rendering on S01. (1) Remove the solid circular background behind each category icon — icons become bare slice-tinted outlines. (2) Position each icon just outside the donut's outer edge AT the mid-angle of the slice it represents, with a small radial margin, so a right-side slice has its icon on the right (today icons sit on a rectangular frame by linear index, decoupled from slice angle). The percentage label moves radially outward from its icon along the same ray.
LAYERS: presentation
CHANGED_HINT: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt (drawIconDisc L619-642 — drop drawCircle L627 + the discColor param + `val discColor` L117; layoutSlices L500-519 -> mid-angle placement; populated draw loop L295-328; leader-line block L263-275 -> drop; drawCalloutText L420-453 -> anchor % radially outward; remove computeIconFrame L482-498 usage); core/designsystem/.../donut/DonutGeometry.kt (reuse midAngleRadians L45-48; KEEP framePoint L13-26 — used by DonutGeometryTest).
TEST_TYPES: unit compose-ui screenshot
CONSTRAINTS:
  - #1 disc removal: delete drawCircle(discColor...) at MonefyDonutChart L627; remove the now-unused discColor parameter from drawIconDisc + its two call sites (empty-state ~L240, populated ~L303) and the unused `val discColor = MaterialTheme.colorScheme.background` (L117). Icons keep their slice-color tint via ColorFilter.tint. Re-derive any icon spacing/margin from iconSize, not from the removed disc.
  - #4 placement (POPULATED slices): icon center = donutCenter + explodedOffset + (outerRadius + iconMargin + iconSize/2f) * (cos(mid), sin(mid)), where mid = DonutGeometry.midAngleRadians(arc) and explodedOffset is the slice's existing radial displacement (icon must track its displaced slice colinearly — current code omits the exploded offset on icons; add it). iconMargin = small gap (~ Spacing.s, 6-8dp). Replace the framePoint/rectangular-frame layout for populated slices. The empty-state placement (emptyIconSlot, even-by-index) is separate and stays as-is.
  - Percentage label: anchor radially outward — text top at radius (outerRadius + iconMargin + iconSize + Spacing.xxs) along the same mid-angle ray, horizontally centered on that point (reuse drawTextCentered). Keep showCategoryLabels default false (do NOT add category-name labels — that is a 38-redesign item). The DEFAULT_LABEL_MIN_FRACTION (>=3%) gate on whether the % is drawn stays unchanged.
  - Leader lines: with icons directly outside the ring on the slice's own ray, drop the leader-line draw block (L263-275). Leave leaderLineColor/leaderLineThickness params in the public signature (dormant) for backward compatibility.
  - Collisions: place strictly at slice mid-angle (accept minor overlap for adjacent small slices; SPEC 02 mitigates by reducing tiny slices). Do NOT add an angular-spread de-collision pass here unless device verification shows unacceptable overlap — if so, file a follow-up SPEC.
  - Hit-testing UNCHANGED: DonutGeometry.hitTest operates on the ring annulus, not icon positions — moving icons does not change slice tap targets. Preserve onSliceClick, budget-alert badge, empty-state ring + placeholder icons, growth animation (icons appear at progress>=1), semantics/contentDescription, DASHBOARD_DONUT_TAG.
  - Keep MonefyDonutChart public params/signature backward-tolerant (no required new params). :core:designsystem may NOT depend on :feature:*/app. No hardcoded strings. Comments zero unless non-obvious WHY.
  - Tests: keep MonefyDonutChartUiTest + DonutGeometryTest green (framePoint kept). Optionally extract icon-center math into a pure helper and assert mid~0deg -> icon right of center, mid~-90deg -> icon above center (locks "right slice -> right icon"). Device visual verification on booted Pixel_5_API_34: bare icons (no disc), each beside its own slice on the outer contour, % readable, no clipping at 390dp; compare to dashboard-final-38.png.
=== END SPEC ===

## Gap / context
`drawIconDisc` рисует диск цвета фона под каждой иконкой (L627), а `layoutSlices` ставит иконки на
прямоугольную рамку по линейному индексу (`framePoint`), а не по углу сектора — поэтому иконка может
оказаться на противоположной от своего сектора стороне. Нужны голые иконки, привязанные к mid-angle
своего сектора на внешнем контуре. Совпадает с `dashboard-final-38.png`.

## Implementation links
- commit: 8a26168, f817f6a, ac901dc, 84ecb36
- files: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt; feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt; core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChartUiTest.kt; core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/donut/DonutGeometryTest.kt
