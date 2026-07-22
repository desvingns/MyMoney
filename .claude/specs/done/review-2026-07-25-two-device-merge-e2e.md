# Integration scenario: two-device journal merge with LWW conflicts
Epic: review-2026-07
Order: 25 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add an integration test simulating the full two-device journal-sync story without a real backend: two independent in-memory Room databases + two DeviceIdProviders + a fake shared-folder transport, driving concurrent conflicting edits (same transaction edited both sides, delete-vs-edit, category rename race), a full exchange in both directions, and asserting both devices converge to the identical OperationMerger-resolved state (LWW by updatedAt then deviceId, tombstones win per the shipped semantics).
LAYERS: [domain] [data]
CHANGED_HINT: :core:sync test sources (JournalApplier/JournalBootstrap tests as the pattern), :core:database in-memory setup, fake transport over the JournalBackend interface
TEST_TYPES: unit [dao]
CONSTRAINTS: fakes-only (fake transport implements the real interface); deterministic — fixed clocks/ids, no sleeps; convergence asserted on FULL entity state, not just op counts; no network, no device needed
=== END SPEC ===

## Gap / context
Merge logic is unit-tested per class, but no test proves two devices actually
converge end-to-end. Source: review item 14 (P2/M).

## Implementation links
- commit: 27fd43c5, 1bb19fcf
- files: core/database/src/main/java/com/kshavrin/mymoney/core/database/journal/JournalApplier.kt; core/sync/build.gradle.kts; core/sync/src/test/java/com/kshavrin/mymoney/core/sync/TwoDeviceJournalSyncIntegrationTest.kt
