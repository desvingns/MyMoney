# Execute Slice 5: manual QA + minified release walk + macrobenchmark
Epic: review-2026-07
Order: 02 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Execute the pending device-verification Slice 5 on Pixel_5_API_34 — full manual QA pass over the 27 screens, a complete walk of the minified R8 release build, and a macrobenchmark run — recording results (pass/defect per area) in docs/DEVICE_VERIFICATION_PROGRESS.md and filing one backlog SPEC per defect found.
LAYERS: [presentation]
CHANGED_HINT: docs/DEVICE_VERIFICATION_PROGRESS.md (tracker), scripts/preflight_device_health.ps1 (preflight), app release buildType — no production code changes expected
TEST_TYPES: [compose-ui]
CONSTRAINTS: hard device gate — Pixel_5_API_34 booted and verified before starting; never claim a check ran without device evidence; defects are filed as SPECs, not hot-fixed inside this run; all local, no CI minutes
=== END SPEC ===

## Gap / context
DEVICE_VERIFICATION_PROGRESS.md line 62: Slice 5 (manual QA, minified release walk,
macrobenchmark/Baseline Profile) is still pending — the release is blind without it.
Source: project review 2026-07-06, item 2 (P1/M).

## Implementation links
- commit: (pending)
- files: (pending)
