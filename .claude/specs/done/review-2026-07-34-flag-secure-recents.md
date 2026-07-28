# Hide financial data from Recents screenshots (FLAG_SECURE option)
Epic: review-2026-07
Order: 34 of 35
Status: done
Depends-on: —
Date: 2026-07-06
Completed: 2026-07-28

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
- commits: 135e185d, 9c9ad469, 430307cf, 3935d9fc, 29acc3fb, b2b6fde6, 7c536431,
  d77d5b19, 2c10277c, 1bee408e, ff945b24 (feature + startup-readiness fixes),
  f01272c9, de2aebcc (lockscreen androidTest isolation + deflake),
  88c50f5c (repository/VM/strings test coverage)
- files: app/.../MainActivity.kt; core/ui/.../window/SecureWindowController.kt (new);
  core/datastore/.../{AppSettingsKeys.kt, AppSettingsRepositoryImpl.kt, model/AppSettings.kt};
  feature/lockscreen/.../overlay/{LockController.kt, LockOverlay.kt}, setup/BiometricSetupScreen.kt;
  feature/settings/.../root/{SettingsRootScreen.kt, SettingsViewModel.kt};
  feature/settings res values + values-ru strings.xml
- tests: AppSettingsRepositoryTest, SettingsViewModelTest, SettingsStringsTest (new),
  SettingsRootContentTest, LockControllerTest (JVM); MainActivityWindowSecurityTest 6/6,
  LockOverlayUiTest + BiometricSetupWindowSecurityUiTest 5/5 (connected, Pixel 5 API 34);
  feature/lockscreen androidTest TestDataStoreModule (new)
- known note: LockController no-arg markUnlocked() overload is now unused by MainActivity
  (uses markUnlocked(activityStartId)); critic flagged it as a future-caller hazard —
  candidate for review-2026-07-35 repo-hygiene.
