# Factory reset (deferred TDD §4.17 AC5)
Epic: review-2026-07
Order: 24 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Add a dedicated "Factory reset" entry to the :feature:settings ROOT screen (a NEW, distinct entry — do NOT modify the existing Backup/Restore "Delete all data" flow, which stays as-is). It runs a NEW domain use case FactoryResetUseCase (:core:domain) that performs a full destructive reset per TDD §4.17 AC5: wipe transactional data + dictionaries (Room clearAllTables) so InitialDataSeeder re-seeds defaults on next launch, reset AppSettings to defaults preserving the install deviceId, clear EncryptedSharedPreferences secrets (SecureStorage.clearAll), AND perform a FULL cloud-sync detach — wipe op_journal and clear the journal-sync config (folderId, bootstrap_done, all peer high-water keys) so a re-linked cloud cannot replay-resurrect wiped data. Reset is behind a DOUBLE-confirmation flow: an explicit AlertDialog, then a second gate requiring the user to TYPE the confirmation word RESET (localized) before the destructive action enables. On success emit the existing RestartToOnboardingAfterReset-style navigation so the seeder path re-runs.
LAYERS: [domain] [data] [presentation]
CHANGED_HINT: :core:domain (NEW FactoryResetUseCase composing the repositories; this is the canonical wipe orchestrator), :feature:settings/root (new Settings entry + double-confirm state/dialog/typed-word field in SettingsViewModel + SettingsRootScreen), :core:datastore (add explicit journal-sync-config clear + confirm AppSettingsRepository.reset() preserves device_id), :core:sync/:core:database (explicit op_journal wipe + JournalSyncConfigStore.clear()). Read: BackupRestoreViewModel.reset() (feature/settings/backup, existing 3-part wipe to mirror), AppSettingsRepositoryImpl.reset() (core/datastore, deviceId preservation — NOTE: preservation lives HERE, not in DeviceIdProviderImpl as the original SPEC text wrongly said), BackupRepositoryImpl.clearDatabase()/clearAllTables(), SecureStorageImpl.clearAll(), JournalSyncConfigStoreImpl (folderId/bootstrap/peer-hw keys), JournalSyncImpl.syncNow(), InitialDataSeeder.seedIfNeeded().
TEST_TYPES: unit [dao] [compose-ui]
CONSTRAINTS: Destructive path — double confirmation MANDATORY and cancel-safe at every step (cancelling the dialog OR clearing/mismatching the typed word must abort with zero side effects; only exact word RESET enables the final action). DEFINE + TEST the op-journal reset semantics: after FactoryResetUseCase runs, op_journal is empty AND journal folderId/bootstrap_done/peer-high-water are cleared, so JournalSyncImpl.syncNow() is a no-op (no pull → cannot resurrect wiped data; no push → cannot corrupt a paired device) until the user manually re-links cloud. deviceId MUST survive the reset (assert it is unchanged). Leave the existing Backup/Restore reset untouched. Clean Architecture verbosity is intentional (dedicated use case, repository interfaces). Strings EN+RU (values + values-ru), no hardcoded user-facing text. Tests are JVM/Robolectric only (unit + DAO + Robolectric Compose-UI) — NOT instrumented/screenshot; no device gate.
RESOLVED_DECISIONS: (1) separate Settings entry, not an upgrade of the Backup reset; (2) typed-word "RESET" second confirmation, not hold-to-confirm; (3) full cloud detach with deviceId preserved.
=== END SPEC ===

## Gap / context
Deferred TDD scope; now interacts with the shipped journal-sync epic, so the reset
semantics need explicit design. Source: review item 6 (P2/M), second half.

## Implementation links
- commits: 70758415 (feat: use case + entry + double-confirm + cloud detach),
  efee69c0 (fix: VM enforces typed-word gate; detach-before-wipe order),
  76e9701a (test: use case, gateway detach, DAO, VM, dialog — 8 files),
  ba2faf01 (fix: cancel periodic sync before detach; carry reset-failure
  flag through app restart — independent-critic follow-up),
  b977977d (test: sync-cancel-first order + pull mid-loop guard),
  67097c26 (test: reconcile SettingsRootContentTest 5→6 sections, 7→8 rows)
- files:
  core/domain/.../reset/FactoryResetGateway.kt (A),
  core/domain/.../usecase/FactoryResetUseCase.kt (A),
  core/sync/.../FactoryResetGatewayImpl.kt (A),
  core/sync/.../di/SyncModule.kt (M),
  core/sync/.../JournalSyncImpl.kt (M, mid-loop folderId guard),
  core/database/.../dao/OperationDao.kt (M, +deleteAll),
  core/datastore/.../JournalSyncConfigStore(Impl).kt (M, +clear),
  feature/settings/.../root/SettingsViewModel.kt (M),
  feature/settings/.../root/SettingsRootScreen.kt (M),
  feature/settings/.../res/values(-ru)/strings.xml (M),
  core/ui/.../restart/RestartExtras.kt (A),
  app/.../MainActivity.kt (M),
  app/build.gradle.kts (M, version bump)
  + 10 test files (FactoryResetUseCaseTest, FactoryResetGatewayDetachTest,
  OperationDaoTest, FactoryResetDialogContentTest, SettingsViewModelTest,
  SettingsRootContentTest, JournalSyncImplTest, WorkSchedulerImplTest,
  TransactionRepositoryImplTest — reconciled stale fakes)
