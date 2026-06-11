# Одноразовый нормализатор существующих UTC-полночных транзакций
Epic: audit1-timezone
Order: 03 of 04
Status: done
Depends-on: audit1-timezone-01
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Существующие транзакции, записанные старой конвенцией (occurredAt ровно на UTC-полночь), однократно сдвигаются на локальную полночь того же UTC-календарного дня — данные становятся однородными с новыми записями и Monefy-импортом. Прогон одноразовый (guard-флаг в AppSettings), идемпотентный, off-main.
LAYERS: domain, data
CHANGED_HINT:
  - core/domain/.../usecase/NormalizeLegacyUtcMidnightUseCase.kt — НОВЫЙ pure-логика отбора+сдвига; зависимости только TransactionRepository + ZoneId/Clock параметрами (детерминизм)
  - core/domain/.../repository/TransactionRepository.kt — (assumption) при отсутствии подходящего метода добавить выборку/массовое обновление occurredAt (реализация в TransactionRepositoryImpl, паттерн PHASE_06)
  - core/datastore: AppSettings + AppSettingsKeys + writeTo — новое поле `tzNormalizedAt: Long?` (G8)
  - app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt — запуск в @ApplicationScope на IO-диспетчере при tzNormalizedAt == null, после успеха — записать отметку (assumption: место запуска)
TEST_TYPES: unit
CONSTRAINTS:
  - Файлы AppSettings правятся также в audit2-save-integrity-03 — этот SPEC идёт ПЕРВЫМ.
  - MyMoneyApp.kt правится также в audit5-donut-perf-02 и audit9-sync-hardening-04 — этот SPEC идёт ПЕРВЫМ из трёх.
  - Не блокировать main: запуск только в ApplicationScope(@IoDispatcher); при падении флаг НЕ ставить (повтор на следующем старте).
  - Money/время по конвенциям: Instant в домене, epoch-millis только в Room.

### Calculation: сдвиг UTC-полночной метки на локальную полночь
- Formula: candidate ⇔ `occurredAt.toEpochMilli() % 86_400_000 == 0`;
  `newOccurredAt = occurredAt.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()`
- Symbols: occurredAt — Instant (метка транзакции); zone — ZoneId (системная на момент прогона);
  86_400_000 — millis в сутках; newOccurredAt — Instant (локальная полночь того же UTC-дня).
- Precision: целочисленная millis-арифметика; без округлений.
- Edge: метка НЕ на UTC-полночь → не трогать; zone = UTC → no-op; повторный прогон исключён guard-флагом
  (а в зонах ≠ UTC результат больше не кратен суткам — двойной сдвиг невозможен и без флага).
- Worked examples (fixtures):
  | occurredAt (UTC)      | zone               | newOccurredAt (UTC)   |
  |-----------------------|--------------------|-----------------------|
  | 2026-06-10T00:00:00Z  | America/New_York   | 2026-06-10T04:00:00Z  |
  | 2026-06-10T00:00:00Z  | Europe/Moscow      | 2026-06-09T21:00:00Z  |
  | 2026-06-10T13:37:00Z  | любая              | без изменений         |
  | 2026-06-10T00:00:00Z  | UTC                | без изменений         |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Нормализация легаси-дат

  Scenario: Старые записи выравниваются однократно
    Given в базе записи с occurredAt ровно на UTC-полночь и записи с произвольным временем
    When приложение стартует впервые после обновления
    Then UTC-полночные записи сдвинуты на локальную полночь того же UTC-дня
    And остальные записи не изменены
    And повторный старт ничего не меняет

  Scenario: Сбой не теряет прогон
    Given нормализатор упал на середине
    When приложение стартует снова
    Then нормализация выполняется повторно без дублей и потерь
```

## Gap / context
После audit1-01 новые записи локальны, но старые (UTC-полночь, G1-легаси) остаются «другим инстантом»:
при смене зоны устройства на UTC− они съезжают на день. Решение D2b из grill.

## Implementation links
- commit: f2dfe68b, 8d6c5c61
- files:
  - app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/dao/TransactionDao.kt
  - core/database/src/main/java/com/kshavrin/mymoney/core/database/repository/TransactionRepositoryImpl.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsKeys.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryImpl.kt
  - core/datastore/src/main/java/com/kshavrin/mymoney/core/datastore/model/AppSettings.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/repository/TransactionRepository.kt
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/NormalizeLegacyUtcMidnightUseCase.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/NormalizeLegacyUtcMidnightUseCaseTest.kt
  - core/database/src/test/java/com/kshavrin/mymoney/core/database/repository/TransactionRepositoryImplTest.kt
  - core/datastore/src/test/java/com/kshavrin/mymoney/core/datastore/AppSettingsRepositoryTest.kt
