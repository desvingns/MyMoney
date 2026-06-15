# Прокинуть комментарий перевода до доменной модели
Epic: records-comment-in-list
Order: 02 of 03
Status: done
Depends-on: —
Date: 2026-06-14

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Комментарий перевода уже персистится (перевод — строка таблицы `transaction` с `kind='transfer'` и колонкой `note`, G5/G7), но не доходит до списка: запрос `getTransfers` не выбирает `note`, проекция `TransferRow` и доменная `TransferRecord` его не содержат. Прокинуть `note` через слой данных и домена: добавить `t.note AS note` в SELECT `getTransfers`, поле `note: String?` в `TransferRow` (projection) и в `TransferRecord` (domain), и перенос `note` в мапере `TransferRow → TransferRecord`. UI в этом SPEC не трогаем (это 03). Миграция НЕ нужна — колонка `note` уже существует (G5).
LAYERS: data, domain
CHANGED_HINT:
  - core/database/.../dao/TransactionDao.kt:123-146 — в `@Query` метода `getTransfers` добавить `t.note AS note` в список SELECT (рядом с `t.occurred_at AS occurredAt`) (G5)
  - core/database/.../projection/TransferRow.kt:5-13 — добавить `@ColumnInfo(name = "note") val note: String?` (G6)
  - core/domain/.../model/TransferRecord.kt:5-12 — добавить `val note: String?` (G6)
  - core/database/.../mapper/Mappers.kt — в мапере `TransferRow.toDomain()`/`toTransferRecord()` (тот, что строит `TransferRecord` из `TransferRow`) перенести `note = note` (зеркало переноса `note` в `TransactionEntity.toDomain()` :219-235)
TEST_TYPES: dao, unit
CONSTRAINTS:
  - Конвенции типов: `note` — nullable `String?` и в проекции, и в домене (G — Data conventions).
  - Обновить ВСЕ места конструирования `TransferRecord` (тесты-фейки/фикстуры) под новое поле — иначе компиляция andTest/unit падёт. Проверить `FakeTransactionRepository` и тесты `GetTransferRecordsUseCaseTest`, `TransactionRepositoryImplTest`, `TransactionsListViewModelTest`, `TransactionsListContentTest` на конструкторы `TransferRecord(...)`.
  - DAO-тест: фикстура перевода с `note` → `getTransfers` возвращает `TransferRow.note`; round-trip до `TransferRecord.note`.
  - Реальный Room (in-memory для unit / on-device для dao), без моков (G — testing stack).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Комментарий перевода доходит до доменной модели

  Scenario: Перевод с комментарием читается из БД
    Given в БД есть перевод с note "Перевёл на отпуск"
    When репозиторий загружает переводы за период
    Then соответствующий TransferRecord имеет note "Перевёл на отпуск"

  Scenario: Перевод без комментария
    Given в БД есть перевод с note = NULL
    Then соответствующий TransferRecord имеет note = null
```

## Gap / context
Фундамент для records-comment-in-list-03 (UI перевода). Без этого слоя строка перевода физически
не имеет доступа к комментарию. Данные уже есть в таблице — только не выбираются и не маппятся.

## Implementation links
- Commit (prod): `22f98e89` — `note` через DAO getTransfers SELECT → TransferRow projection → TransferRecord domain + mapper; обновлены конструкторы TransferRecord в фейках/фикстурах.
- Commit (tests): `e926e04f` — DAO/repo/usecase/VM покрытие note round-trip.
- Files: TransactionDao.kt, TransferRow.kt, Mappers.kt, TransferRecord.kt, TransactionRepository.kt, GetTransferRecordsUseCase.kt + tests (TransactionDaoGetTransfersTest, TransactionRepositoryImplTest, GetTransferRecordsUseCaseTest, TransactionsListViewModelTest).
- Verification: JVM unit (core:domain/core:database/feature:transactionslist) green; DAO instrumented 11/11 on Pixel_5_API_34; ktlintCheck green.
