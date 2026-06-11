# Регрессионная таймзонная сюита (America/New_York)
Epic: audit1-timezone
Order: 04 of 04
Status: done
Depends-on: audit1-timezone-01, audit1-timezone-02, audit1-timezone-03
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Тестовая полоса, в которой JVM-зона фиксируется на America/New_York (UTC−4/−5) и пиннится весь таймзонный контракт: сохранение→попадание в период→отображение, импорт→редактирование round-trip, границы периодов. Закрывает системную дыру «809 зелёных тестов в зоне разработчика не ловят C1».
LAYERS: test
CHANGED_HINT:
  - core/testing или core/common test-utils — (assumption) JUnit Rule `FixedTimeZoneRule(zoneId)`: TimeZone.setDefault в before, восстановление в after
  - feature/transaction/src/test/.../TimezoneRegressionTest.kt — НОВЫЙ: save в UTC−4 → occurredAt = локальная полночь; попадание в toEpochMillisRange(Day/Month) (G1, G3)
  - feature/transactionslist/src/test/.../detail/TimezoneDetailRoundTripTest.kt — НОВЫЙ: запись с локальной полночью (Monefy-стиль, G4) открывается и сохраняется без сдвига даты (G2)
  - core/domain/src/test/.../time/PeriodArithmeticTzTest.kt — НОВЫЙ: границы Day/Week/Month/CustomRange в UTC−4 и UTC+3
TEST_TYPES: unit
CONSTRAINTS:
  - TimeZone.setDefault — JVM-глобален: обязательно восстанавливать в @After/Rule (иначе соседние тесты флакуют).
  - Не дублировать фикстуры нормализатора (уже в SPEC 03) — здесь сквозные сценарии VM+domain.
  - Fakes-only на границе репозитория (конвенция проекта), без мок-фреймворков.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Таймзонный контракт закреплён тестами

  Scenario: Сквозной сценарий в UTC-минус зоне
    Given тестовая зона America/New_York
    When VM сохраняет расход с датой 10 июня
    Then занесённый occurredAt попадает в диапазон Period.Day(10 июня)
    And попадает в диапазон Period.Month(июнь)

  Scenario: Round-trip импортированной записи
    Given тестовая зона Europe/Moscow
    And транзакция с occurredAt = локальная полночь 1 июня
    When detail-VM читает дату и сохраняет без изменений
    Then occurredAt не изменился
```

## Gap / context
Аудит §3: все тесты проекта зелёные при живом баге C1, потому что ни один не варьирует зону.
Полоса делает таймзонные регрессии невозможными молча.

## Implementation links
- commit: 2bb8cc16
- files:
  - core/testing/src/main/java/com/kshavrin/mymoney/core/testing/FixedTimeZoneRule.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/time/PeriodArithmeticTzTest.kt
  - feature/transaction/build.gradle.kts
  - feature/transaction/src/test/java/com/kshavrin/mymoney/feature/transaction/TimezoneRegressionTest.kt
  - feature/transactionslist/build.gradle.kts
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/detail/TimezoneDetailRoundTripTest.kt
