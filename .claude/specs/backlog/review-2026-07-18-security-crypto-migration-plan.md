# ADR + plan: migrating off deprecated androidx security-crypto
Epic: review-2026-07
Order: 18 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Write docs/DECISIONS/ADR-0002 with a concrete, dated migration plan away from EncryptedSharedPreferences (androidx.security:security-crypto 1.1.0-alpha07 — deprecated by Google, no further development): inventory what it protects today (Dropbox token, GDrive email, PIN hash), evaluate the replacement (Android Keystore-backed encryption + DataStore, or keeping data in Keystore-protected form directly), define the data-migration path for existing installs, and the trigger condition for executing it. NO code migration in this SPEC — plan only.
LAYERS: [domain]
CHANGED_HINT: new docs/DECISIONS/ADR-0002-*.md; read core/datastore SecureStorage impl for the inventory
TEST_TYPES: unit
CONSTRAINTS: [TDD-revision] — the version is pinned by TDD §8, so the ADR explicitly proposes a TDD amendment rather than silently diverging; existing users' secrets must survive any future migration (no forced re-login/re-PIN without fallback)
=== END SPEC ===

## Gap / context
The secrets layer sits on an abandoned alpha library; unplanned, this becomes an
emergency later. Source: review item 43 (P2/M).

## Implementation links
- commit: (pending)
- files: (pending)
