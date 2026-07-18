# Factory reset (deferred TDD §4.17 AC5)
Epic: review-2026-07
Order: 24 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Implement the destructive factory reset deferred from PHASE_12 per TDD §4.17 AC5 (re-read the anchor lines): wipe transactional data, dictionaries back to seeds, settings to defaults, and secrets — behind a double-confirmation flow (explicit dialog + typed/hold confirmation), preserving the DataStore install deviceId semantics already defined for journal sync (AppSettings reset preservation exists in DeviceIdProviderImpl).
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: :feature:settings (reset entry + confirm dialogs), :core:database (wipe/seed), :core:datastore (reset with deviceId preservation), :core:sync (journal implications of a wipe — op_journal handling!)
TEST_TYPES: unit [dao] [compose-ui]
CONSTRAINTS: destructive path — double confirmation mandatory, cancel-safe at every step; define and TEST the op-journal semantics of a reset (must not replay-resurrect wiped data via cloud sync, nor corrupt a paired device); strings EN+RU
=== END SPEC ===

## Gap / context
Deferred TDD scope; now interacts with the shipped journal-sync epic, so the reset
semantics need explicit design. Source: review item 6 (P2/M), second half.

## Implementation links
- commit: (pending)
- files: (pending)
