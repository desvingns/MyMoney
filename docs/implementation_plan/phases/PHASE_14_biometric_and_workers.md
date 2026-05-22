# PHASE 14 — Biometric lock (S16) + WorkManager background jobs

## Goal

Implement biometric/PIN app lock — both the setup screen (S16) and the lock-overlay Composable that intercepts `onResume` per AS-5. Add three background workers: `RecurringWorker` (daily, generates `RecurringTemplate` instances silently per AS-11), `PruneDeletedWorker` (daily, physically removes soft-deleted rows older than 30 days per §7.9), `BackupRotationWorker` (synchronous helper, already used by S18 — formalise here). Hook `BudgetEvaluator` to recompute on transaction changes.

## TDD anchors

- §4.15 S16 Biometric lock setup — lines 921–948
- §6.9 Haptic feedback — lines 1461–1472 (lock prompt haptics)
- §7.9 Background workers — lines 1984–1997
- §11.8 Recurring & budget — lines 2539–2552
- §5 BR-25 (lock overlay on resume), BR-27 (milestone confetti — already in PHASE_08) — lines 1172–1207
- AS-5 (lock is a Composable overlay, not a destination), AS-11 (recurring silent), AS-13 (block delete) — §14.1
- §9.4 Permission USE_BIOMETRIC — lines 2234–2242

## Prerequisites

- PHASE_05 — done (`SecureStorage` for `pinHash`)
- PHASE_12 — done (S14 settings root)
- PHASE_13 — done (SyncWorker is the template for the others)

## Deliverables (in `:feature:lockscreen`)

- `feature/lockscreen/build.gradle.kts` — feature + `androidx.biometric:biometric:1.2.0-alpha07`.
- `feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/setup/BiometricSetupScreen.kt` — S16. Enable / disable toggle, idle-timeout dropdown (`AppSettings.biometricIdleTimeoutSec` ∈ {30, 60, 120, 300}), PIN setup (4-digit numeric) as fallback when biometric hardware unavailable or lockout.
- `feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/setup/BiometricSetupViewModel.kt`.
- `feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockOverlay.kt` — `@Composable fun LockOverlay(onUnlocked: () -> Unit)`. Renders a full-screen surface over the NavHost. Triggers `BiometricPrompt.authenticate(...)` on `LaunchedEffect(Unit)`. On success → `onUnlocked()`. On lockout/error → PIN fallback UI.
- `feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/LockController.kt` — `class LockController @Inject constructor(...)`. Holds `shouldShowLock: StateFlow<Boolean>`. Logic: on `Lifecycle.Event.ON_PAUSE` records `pausedAt`. On `ON_RESUME` computes idle = `now - pausedAt`; if `idle >= biometricIdleTimeoutSec` AND `biometricLockEnabled` → emit `true`.
- `feature/lockscreen/src/main/java/com/kshavrin/mymoney/feature/lockscreen/overlay/PinKeypad.kt` — 4-digit numeric keypad (mini version of `MonefyKeypad`). On 4 digits entered: hash via PBKDF2-SHA256 + compare to `SecureStorage.pinHash`. Wrong PIN → haptic `WARNING` per §6.9.

## Deliverables (in `:app`)

- Updated `MainActivity.kt` — wraps `MyMoneyNavHost` in `Box { ...; if (lockController.shouldShowLock.collectAsState().value) LockOverlay(onUnlocked = ...) }` per AS-5. The lock overlay is **NOT** a navigation destination.

## Deliverables (in `:core:sync`)

- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/RecurringWorker.kt` — `@HiltWorker`, daily PeriodicWorkRequest. Loops over `recurringTemplateRepository.dueTemplates(now)`, for each: insert a `TransactionEntity` from `RecurringScheduler.computeNextRun()`, update `template.nextRunAt`. **Silent** per AS-11 — no notifications, no toasts, no badge increments, no DataStore flags that would let UI differentiate.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/PruneDeletedWorker.kt` — daily PeriodicWorkRequest. Calls `TransactionDao.pruneDeleted(now - 30.days)` — physically deletes rows with `is_deleted = 1` and `updated_at < cutoff`.
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/worker/BackupRotationWorker.kt` — synchronous helper invoked from `BackupRepository.exportDb` (already wired in PHASE_12 conceptually; formalise the worker here). Keeps last 3 local backups per OQ-8.

## Task checklist

- [ ] Re-read TDD §4.15 + §6.9 + §7.9 + AS-5 + AS-11.
- [ ] **S16 setup screen**: toggle biometric ON → call `BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)`. If `BIOMETRIC_ERROR_HW_NOT_PRESENT` → block toggle + show `"Biometrics not supported on this device"` per §9.5. If OK → prompt user to authenticate once (confirms hardware works). Save `biometricLockEnabled = true`.
- [ ] PIN fallback: 4-digit numeric entry. Hash via `PBKDF2WithHmacSHA256` (10k iterations, random salt). Store `pinHash` in `SecureStorage`. PIN is for fallback only — used when biometric lockout / unavailable.
- [ ] **LockController**: process lifecycle observer that tracks `pausedAt`. On resume, evaluate idle vs `biometricIdleTimeoutSec`. Inject into `MainActivity` via Hilt.
- [ ] **LockOverlay** (per AS-5): wrap MainActivity content in a Box. The overlay is drawn on top when `shouldShowLock = true`. NOT a navigation destination — preserves the back-stack (when unlocked, user sees the screen they were on).
- [ ] **BiometricPrompt** invocation: `BiometricPrompt(activity, mainExecutor, callback).authenticate(PromptInfo.Builder()...)`. On success → `lockController.markUnlocked()`. On `ERROR_LOCKOUT` → switch to PIN fallback. On `ERROR_NEGATIVE_BUTTON` → keep showing lock (user can't dismiss).
- [ ] **App Shortcut integration** (BR-25 / §3.4 line 442): even on shortcut launches (Add Expense / Income / Transfer), the lock overlay still intercepts first.
- [ ] **RecurringWorker** — silent execution. Use `Constraints.NONE` (per §7.9 line 1990). Schedule daily on `WorkManager.initialize` via `Application.onCreate` or `MyMoneyAppInitializer`. Test: create a daily `RecurringTemplate` with `nextRunAt = now - 1 hour`; run `WorkManager.getInstance().getWorkInfosByTag("recurring").await()`; confirm a new transaction row landed and the template's `nextRunAt` advanced.
- [ ] **PruneDeletedWorker** — schedule daily with `requiresBatteryNotLow = true`. Test by seeding a deleted transaction with `updated_at = now - 31 days`; trigger worker; confirm physical removal.
- [ ] **BackupRotationWorker** — formalise. Make `BackupRepository.exportDb` enqueue a `OneTimeWorkRequest` that invokes this worker post-export.
- [ ] **BudgetEvaluator wiring**: hook to `transactionRepository.observeAll()`; on every change re-evaluate active budgets. If a `Budget` crosses its `alertThresholdPct`, emit a `DomainEvent.BudgetAlert(budgetId)`. (No notifications per Q-D3, but the dashboard can show a small chip; PHASE_15 polishes.)
- [ ] Add `USE_BIOMETRIC` permission to manifest if not already (PHASE_01 should have added it; verify).
- [ ] Update PROGRESS.md.

## Done criteria

- `.\gradlew.bat :feature:lockscreen:assembleDebug` succeeds.
- S16 enables biometric lock; subsequent backgrounding + resume after `> idle_timeout` shows the lock overlay before any UI.
- Wrong PIN → haptic + error UI; correct PIN unlocks.
- `RecurringWorker` creates a row silently (`adb logcat | findstr RecurringWorker` shows no Toast/Notification calls).
- `PruneDeletedWorker` physically removes a 30-day-old soft-deleted row.
- Lock overlay is NOT a NavController destination (verify: `navController.currentBackStackEntry.value?.destination?.route` while locked should NOT be `"lock"`).

## Verification commands

```powershell
cd C:\Pet\MyMoney
.\gradlew.bat :feature:lockscreen:assembleDebug
.\gradlew.bat :feature:lockscreen:test
.\gradlew.bat :app:installDebug
# Force worker run (debug):
adb shell cmd jobscheduler run -f com.kshavrin.mymoney.debug 0
```

## Notes for next session

(empty — fill at end of session. Note any biometric-hardware emulator quirks.)
