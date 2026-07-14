# Period chips meet the 48dp touch-target minimum
Epic: review-2026-07
Order: 13b of 35
Status: done
Depends-on: review-2026-07-13
Date: 2026-07-13
Completed: 2026-07-14

## SPEC
=== SPEC ===
TASK: feature
WHAT: Ensure every dashboard period control, including the Pick a date AssistChip, exposes at least 48dp touch width and height while retaining the existing period selection and two-date range-picker behavior.
LAYERS: [presentation]
CHANGED_HINT: feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodStrip.kt; existing PeriodStripUiTest touch-target coverage
TEST_TYPES: [compose-ui]
CONSTRAINTS: keep AS-12 CustomRange behavior unchanged; do not suppress the touch-target assertion; verify on Pixel_5_API_34
=== END SPEC ===

## Gap / context
The automated gate measured one dashboard period chip at 45.82dp wide on Pixel 5. The current test correctly exposes a production touch-target gap.

## Implementation links
- commits: ff6681ea · 0cf02519 (production) · 2236ebeb (test)
- files: feature/dashboard/.../components/PeriodStrip.kt — Material3 chips ignore an outer minWidth constraint (custom Layout sizes to content), so the 48dp width is reached by padding the label Text (content growth) plus defaultMinSize for height; AS-12 CustomRange/DateRangePicker behavior unchanged.
- test: PeriodStripUiTest scrolls each chip into view (performScrollTo) before measuring, since PeriodStrip is a horizontalScroll strip whose edge chips are otherwise clipped. Both PeriodStripUiTest tests green on Pixel_5 API 34.
