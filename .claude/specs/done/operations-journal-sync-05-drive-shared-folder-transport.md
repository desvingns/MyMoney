# Транспорт журнала через общую папку Google Drive
Epic: operations-journal-sync
Order: 05 of 07
Status: done
Depends-on: 01, 02
Date: 2026-06-25

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Транспортный слой журнала поверх Drive в `:core:sync`. Новая абстракция `JournalBackend` (per-device файлы в одной ОБЩЕЙ видимой папке) + реализация `GoogleDriveJournalBackend` (scope `DRIVE_FILE`, файл `ops-<deviceId>.jsonl` в общем `folderId`, формат JSONL — одна op на строку). Push = выгрузить свой файл; pull = перечислить и скачать файлы соседних устройств. Движок строится на абстракции и тестируется FAKE-бекендом; живой Google-вход/consent/получение папки — отложенный OQ (D2).
LAYERS: data
CHANGED_HINT:
  - core/sync/src/main/java/com/kshavrin/mymoney/core/sync/JournalBackend.kt — новый: `interface JournalBackend { suspend fun uploadJournal(folderId: String, deviceId: String, bytes: ByteArray): Result<Unit>; suspend fun listPeerJournals(folderId: String): Result<List<RemoteJournalFile>>; suspend fun downloadJournal(fileId: String): Result<ByteArray> }` + `data class RemoteJournalFile(fileId, deviceId, modifiedAtEpochMs)`. Параллель `CloudSyncBackend` (G3). (G3)
  - core/sync/.../JournalSerializer.kt — новый: `Operation` (01) ⇄ JSONL-строка (включая `opId/deviceId/entityKind/entityUuid/opType/updatedAt/payload`); парс/сериализация батча. (assumption)
  - core/sync/.../gdrive/GoogleDriveJournalBackend.kt — новый: `@Singleton`, реализует `JournalBackend`; scope `DriveScopes.DRIVE_FILE` (НЕ `DRIVE_APPDATA`, D1/G5); работает с заданным общим `folderId`; `uploadJournal` — update если файл `ops-<deviceId>.jsonl` существует, иначе create в `folderId`; `listPeerJournals` — `files().list()` по `folderId` (исключая свой `deviceId`); `downloadJournal` — `executeMediaAndDownloadTo`. По образцу `GoogleDriveRepository` (G4), но для общей видимой папки. (G4, G5, D1, D10)
  - core/sync/.../di/SyncModule.kt — `@Binds JournalBackend → GoogleDriveJournalBackend`. КЛЭШ с 06 (там rebind `SnapshotSync→JournalSync`): 05 раньше. (G9; clash 06)
  - core/sync/src/test/java/.../fake/FakeJournalBackend.kt — новый: in-memory `folderId → (deviceId → bytes)`; на нём гоняются юнит-тесты транспорта/оркестратора (D2, G26). (D2)
TEST_TYPES: unit
CONSTRAINTS:
  - Scope `DRIVE_FILE` — отличается от текущего `DRIVE_APPDATA` (G4/G5); `DRIVE_APPDATA` НЕ умеет общие папки между аккаунтами (D1).
  - ОТЛОЖЕННЫЙ OQ (D2/H1/H2): живой Google sign-in + `DRIVE_FILE` consent + получение/создание/шаринг общей папки требуют живого аккаунта — НЕ в этом SPEC. Реализация `GoogleDriveJournalBackend` пишется, но проверяется FAKE-бекендом; живой путь остаётся за RC-гейтом (G5).
  - `folderId` приходит ПАРАМЕТРОМ (источник — конфиг DataStore, D10); транспорт не занимается его получением.
  - JSONL: одна op на строку; per-device файл `ops-<deviceId>.jsonl`; Drive не поддерживает истинный append — файл перевыгружается целиком (компакция — out-of-scope, H4/O2).
  - `SyncModule` — общий файл с 06: строго последовательно (05 раньше).
  - Новый интерфейс `JournalBackend` → создать фейк во всех потребляющих модулях (G26).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Транспорт журнала через общую папку Drive
  Покрывает JournalBackend + сериализацию JSONL (на fake-бекенде).

  Scenario: Выгрузка своего файла операций
    Given локальные операции, сериализованные в JSONL
    When устройство выгружает свой журнал в общую папку
    Then в папке появляется файл ops-<deviceId>.jsonl с этими операциями

  Scenario: Перечисление файлов соседей
    Given в общей папке есть файлы нескольких устройств
    When устройство запрашивает журналы соседей
    Then возвращаются файлы всех устройств, кроме собственного

  Scenario: Скачивание и разбор журнала соседа
    Given файл операций соседнего устройства в общей папке
    When устройство скачивает и разбирает его
    Then получается список операций, эквивалентный исходному (round-trip JSONL)

  Scenario: Перевыгрузка обновляет существующий файл
    Given файл ops-<deviceId>.jsonl уже выгружен
    When устройство выгружает дополненный журнал
    Then существующий файл обновляется (а не создаётся второй)
```

## Gap / context
Текущий `GoogleDriveRepository` умеет лишь снапшот в приватную appData-папку (G4) и не поддерживает общую папку для разных аккаунтов (G5). Этот SPEC даёт транспорт per-device файлов журнала в общей папке — на абстракции, тестируемой без живого Google.

## Implementation links
- commit: 9db6cec9 (prod) + 8ddb79cf (tests)
- files: core/sync/.../JournalBackend.kt, JournalSerializer.kt, gdrive/GoogleDriveJournalBackend.kt, di/SyncModule.kt, build.gradle.kts, test/fake/FakeJournalBackend.kt, test/JournalSerializerTest.kt, test/fake/FakeJournalBackendTest.kt
