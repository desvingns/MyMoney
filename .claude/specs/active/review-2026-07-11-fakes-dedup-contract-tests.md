# Repository contract tests + deduplicate fakes into :core:testing
Epic: review-2026-07
Order: 11 of 35
Status: active
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Introduce contract test suites for the most-duplicated repository boundaries (AppSettingsRepository, CurrencyRepository first): one abstract test class per interface asserting observable behavior, executed against BOTH the fake and the real implementation (in-memory Room / test DataStore); then move the canonical StateFlow-based fakes into :core:testing and delete-by-archive the per-module copies (6× FakeAppSettingsRepository, 3× FakeCurrencyRepository), pointing all module tests at the shared ones.
LAYERS: [domain] [data]
CHANGED_HINT: core/testing/src/main (new fake/ + contract/ packages), per-module src/test/**/fake/ duplicates, core:database repository impls for the real-side runs
TEST_TYPES: unit [dao]
CONSTRAINTS: fakes-only policy unchanged (no mocking libs); contract tests are the safety net — land them BEFORE consolidating fakes; superseded fake files go to archive/ (never deleted); behavior of existing tests must not change
=== END SPEC ===

## Gap / context
Six copies of the same fake can silently drift from the real implementation; a
contract suite pins them. Source: review items 10+11 (P2/M).

## Implementation links
- commit: (pending)
- files: (pending)
