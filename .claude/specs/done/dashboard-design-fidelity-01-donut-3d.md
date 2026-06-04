# Donut — 3D extrude + иконки-рамка + центр без копеек
Epic: dashboard-design-fidelity
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-06-04

## SPEC
=== SPEC ===
TASK: feature
WHAT: Recreate the Claude-Design donut in MonefyDonutChart — extruded 3D ring, design geometry, rectangular-frame category icons with category-colored leader lines + percentages, and a kopeck-less income/expense center — to match docs/design/dashboard-redesign/phone.jsx pixel-for-pixel.
LAYERS: presentation
CHANGED_HINT: core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/MonefyDonutChart.kt, core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/DonutGeometry.kt, core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/donut/CategorySlice.kt ; authoritative design source: docs/design/dashboard-redesign/phone.jsx (function Donut + Phone) and icons.jsx ; visual refs: docs/design/dashboard-redesign/render-current.png, monefy-reference-05.jpg
TEST_TYPES: unit compose-ui screenshot
CONSTRAINTS:
- Read docs/design/dashboard-redesign/phone.jsx first — it is the authoritative geometry/visual spec; port its math, not its structure.
- Ring geometry: strokeWidth(th) = outerRadius * 0.39 (was 0.30); per-sector gap = min(5°, sweep*0.6) split half each side; butt caps; r = outerRadius - strokeWidth/2. Add params to MonefyDonutChart: ringThicknessFraction (default 0.39), sliceGapDegrees (default 5f), iconScale (default 1.7f), centerDecimalDigits (default = decimalDigits), and a DonutStyle enum { Flat, Extrude } (default Extrude). Keep existing public params/signature backward-tolerant (new params have defaults).
- 3D extrude (from phone.jsx Donut): depth = clamp(round(th*0.62), 7, 22). Draw, in order: (1) cast shadow — a blurred ring stroke below center at cy + th*0.95, color argb(0.28, 35,60,48), width th*0.92, soft-blurred (BlurMaskFilter via nativeCanvas Paint or a translated low-alpha pass); (2) side wall — for k=depth..1, each category arc stroked with darken(sliceColor, 40%) translated down by k px (stacked → solid wall); (3) base/top-face arcs in the real slice colors; (4) top highlight band at r + th*0.5 - 1.2, white@0.40, width 2.2, clipped to the top half (clipRect upper half); (5) inner-edge shadow band at r - th*0.5 + 1, black@0.18, width 1.6. darken(hex,f): mix toward black by f (per phone.jsx shadeHex with negative f).
- Category icons → rectangular frame (port phone.jsx framePoint(t,hw,hhTop,hhBot) into DonutGeometry): distribute N icons evenly by index around a rectangle, ASYMMETRIC (hhTop>hhBot) so the top row rises toward the period strip. Ratios vs the Canvas box W×H: hw ≈ 0.406·W, hhTop ≈ 0.874·(H/2), hhBot ≈ 0.739·(H/2) (clamp like phone.jsx). Each icon centered in its frame slot.
- iconScale 1.7 → iconSize ≈ round(26*1.7).dp; icon drawn in a disc of (iconSize+12) filled with the dashboard background color (masks the leader line behind it); icon tinted with the category color, stroke ~1.5.
- Leader line: from the slice mid-point on the ring (radius ringR+1, ringR = outerRadius) to the icon slot center; color = category color at alpha 0.55; width 1dp; do not draw through the icon disc (disc masks it).
- Percentage label: BELOW each icon (not white-on-ring), color = category color, FontWeight 800/ExtraBold, size ≈ round(13*1.7).sp. Remove the old white on-ring % labels.
- AS-14 override (FLAGGED): show the % label for ALL slices with pct>0 (drop the >=3% LABEL_THRESHOLD) per design. This intentionally overrides the locked AS-14 for S01 only; keep it a single named constant so it is reversible. Note it in code only if a WHY is non-obvious (else no comment).
- Center: stacked income over expense; income text color = colorScheme.secondary (NOT primary), expense = colorScheme.tertiary; render with centerDecimalDigits (Dashboard will pass 0 = no kopecks). Keep MoneyFormatter (sans), symbol AFTER.
- Preserve ALL existing behaviour: growth animation (3D + labels follow the animated sweep / appear at progress>=1 as today), onSliceClick hit-test, budget-alert badge, empty-state ring + placeholder icons, semantics/contentDescription (donut_chart_cd / donut_chart_slice / budget_alert), DASHBOARD_DONUT_TAG.
- Module boundary: :core:designsystem may NOT depend on :feature:* or app. No Dispatchers.IO in classes. No hardcoded user-facing strings (use existing string resources). Comments: zero by default (only non-obvious WHY).
=== END SPEC ===

## Gap / context
Текущий донат плоский (strokeWidth=R·0.30, без зазора), иконки орбитально на R+24dp, % белым на
кольце при ≥3%, центр income=primary с копейками. Дизайн (`phone.jsx`) — extrude-3D, толще, иконки
на асимметричной прямоугольной рамке с диск-маской и выноской/процентом цветом категории, центр
income=secondary без копеек. Этот SPEC закрывает геометрию+рендер доната.

## Implementation links
- commit: 4c00a88 3812a7b 3d4ddcc 4302287 161caa1 21135fb (+ parallel donut d252a0c ee4db96 9fc7c66 2e4ac61)
- files: MonefyDonutChart.kt, DonutGeometry.kt, MonefyBalanceBar.kt, DashboardScreen.kt, PeriodLabel.kt, TwoFabLayout.kt + UI/unit tests
