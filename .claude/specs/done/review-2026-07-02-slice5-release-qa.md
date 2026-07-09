# Execute Slice 5: manual QA + minified release walk + macrobenchmark
Epic: review-2026-07
Order: 02 of 35
Status: done
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

## Current status

2026-07-08: release/staging signing is locally configured and signed
`app/build/outputs/apk/release/app-release.apk` installs/cold-starts on the required
`Pixel_5_API_34` fallback (`emulator-5554`, `Pixel_5`, SDK 34). Native runner smoke
`MainActivityLaunchTest` is green 1/1 after clearing the release/debug signature mismatch.
Release walkthrough screenshots/XML are under `build/visual-check/release-walk/`.

Final pass: S00 launch evidence, S11, S01/S05, S02/S04, S03, S06/S07/S09, S08,
S13-S27 are covered by signed-release evidence. S27 was reached through a real
release UI path by adding an EUR account and selecting it in Transfer. The only
product defect found is full S12 transactions-list runtime reachability: the
destination constant exists, but the current app graph has no production route/UI
entry for it. Filed backlog SPEC `review-2026-07-02d-transactions-list-runtime-route`.

## Implementation links
- commit: local report-only; no production/test code changes
- files: `docs/DEVICE_VERIFICATION_PROGRESS.md`, `docs/implementation_plan/PROGRESS.md`, `.claude/specs/backlog/review-2026-07-02d-transactions-list-runtime-route.md`, `build/visual-check/release-walk/`
