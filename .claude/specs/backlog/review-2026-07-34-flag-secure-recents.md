# Hide financial data from Recents screenshots (FLAG_SECURE option)
Epic: review-2026-07
Order: 34 of 35
Status: draft
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Protect balances from shoulder-surfing via the Recents screen: apply WindowManager FLAG_SECURE unconditionally on the PIN/biometric lock surfaces, and add a settings toggle "Hide app content in Recents" (default per user gate at implement time) that applies FLAG_SECURE app-wide when enabled; persisted in AppSettings DataStore.
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: :feature:lockscreen lock overlay/BiometricSetupScreen, MainActivity window flags, :feature:settings root screen toggle, :core:datastore AppSettings field
TEST_TYPES: unit [compose-ui]
CONSTRAINTS: [beyond-spec] — not in the TDD; note that FLAG_SECURE also blocks the user's own screenshots (document in the toggle subtitle); lock-surface FLAG_SECURE is unconditional, the app-wide flag is opt-in; strings EN+RU; screenshot-based tests (SPEC 12) must account for the flag in debug builds (keep it debuggable-off or test-flag-aware)
=== END SPEC ===

## Gap / context
A money app's balances are currently visible in the Recents carousel and on-device
screen capture. Source: review item 45 (P2/S).

## Implementation links
- commit: (pending)
- files: (pending)
