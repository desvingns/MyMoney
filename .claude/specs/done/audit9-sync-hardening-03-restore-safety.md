# Безопасный restore: рестарт после keepRemote, снапшот, user_version
Epic: audit9-sync-hardening
Order: 03 of 04
Status: done
Depends-on: audit7-forms-hardening-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: (1) Разрешение конфликта keepRemote (cloud-pull) идёт тем же путём, что локальный restore: после подмены файла БД — рестарт процесса (RestartAfterRestore), а не продолжение работы на закрытом @Singleton Room с живыми Flow-подписками. (2) Safety-снапшот больше не удаляется при ПРОВАЛЕ импорта — только при успехе; при провале выполняется откат на снапшот. (3) Перед подменой файла сверяется PRAGMA user_version: бэкап с более новой схемой отклоняется с понятной ошибкой ДО swap (вместо Room downgrade краш-лупа после).
LAYERS: data
CHANGED_HINT:
  - core/sync/.../SnapshotSyncRepository.kt:72-92 — keepRemote: после успешного swap вернуть наружу сигнал «требуется рестарт»; :87-90 — снапшот удалять только при успехе, при провале — откат (G3)
  - feature/cloudsync/.../CloudSyncViewModel.kt — обработка сигнала → экшен рестарта (зеркало BackupRestoreScreen RestartAfterRestore, G3)
  - core/database/.../repository/BackupRepositoryImpl.kt:81,371 — перед swap читать PRAGMA user_version входного файла; version > текущей версии MoneyDatabase → ошибка «бэкап создан более новой версией приложения» без подмены (G4)
  - тесты: user_version-гейт (unit на проверку); снапшот-откат при провале импорта
TEST_TYPES: unit, dao
CONSTRAINTS:
  - Код DevOps-гейтнут OFF (OQ-2/3) — поведенческая проверка cloud-пути возможна только юнитами/фейками; локальный restore-путь регрессий не получает.
  - `BackupRepositoryImpl.kt` — ПОСЛЕ audit4-records-02 и audit7-forms-hardening-01 (третья правка файла в волне).
  - Строка ошибки версии — EN+RU, без хардкода.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Restore не оставляет приложение в неопределённости

  Scenario: keepRemote перезапускает процесс
    Given конфликт синка разрешён в пользу удалённой копии
    When файл БД подменён
    Then приложение перезапускается и открывается на свежих данных

  Scenario: Провал импорта откатывается
    Given подмена файла провалилась на середине
    Then база восстановлена из safety-снапшота
    And снапшот не удалён до успешного завершения

  Scenario: Бэкап из будущего отклоняется
    Given файл бэкапа создан более новой версией приложения
    When пользователь импортирует его
    Then показывается понятная ошибка
    And текущая база не тронута
```

## Gap / context
Баги H4/M11 аудита (G3, G4): cloud-pull подменяет файл под живыми подписками (краши коллекторов,
устаревший UI), снапшот гибнет в finally даже при провале, downgrade-бэкап окирпичивает приложение.

## Implementation links
- commit: 2ea54e0f (fix), 82bf7082 (tests)
- files:
  - core/sync/.../SnapshotSyncRepository.kt — keepRemote: snapshot exported before remote import; deleted only on success, rollback on failure; returns SyncOutcome.PulledRequiresRestart
  - core/sync/.../SyncOutcome.kt — PulledRequiresRestart outcome
  - feature/cloudsync/.../CloudSyncViewModel.kt + CloudSyncAction.kt (RestartAfterRestore) + CloudSyncScreen.kt — restart signal handling
  - core/database/.../repository/BackupRepositoryImpl.kt — PRAGMA user_version gate before swap (BackupSchemaTooNewException); core/database/.../MoneyDatabase.kt
  - core/domain/.../repository/BackupRepository.kt — schema-too-new contract
  - feature/settings/.../BackupRestoreViewModel.kt — version-too-new banner (EN/RU strings)
  - tests: SnapshotSyncRepositoryTest, CloudSyncViewModelTest, BackupRestoreViewModelTest, BackupImportSchemaGateTest (androidTest, 3/3 green on emulator-5554)
- verification: core:sync/core:database/feature:cloudsync/feature:settings unit + ktlintCheck green; schema-gate 3/3 on device
