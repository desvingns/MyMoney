# PeriodStrip: корректная UTC-граница Material3-пикера дат
Epic: audit1-timezone
Order: 02 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Диапазон «Pick a date» на дашборде должен совпадать с выбранным в пикере в любой таймзоне. Material3 DateRangePicker возвращает UTC-millis; PeriodStrip сейчас конвертирует их через systemDefault — в UTC−зонах диапазон съезжает на день назад. Конвертация переводится на ZoneOffset.UTC (эталон — транзакционный диалог).
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodStrip.kt:82-89 — `Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())` → `ZoneOffset.UTC` для selectedStartDateMillis/selectedEndDateMillis (G5, зеркало G6)
  - (assumption) проверить одиночный «Pick a date» в левом drawer (`LeftDrawerContent`/его пикер из эпика dashboard-date-range): если конвертация там тоже через systemDefault — исправить аналогично в этом же SPEC
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Меняется ТОЛЬКО граница пикер→LocalDate; семантика Period.CustomRange/Period.Day (AS-12) не меняется.
  - Тест фиксирует зону America/New_York и проверяет: millis полуночи 10 июня UTC → LocalDate(10 июня).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Выбор диапазона дат на дашборде точен в любой зоне

  Scenario: Диапазон не съезжает в UTC-минус зоне
    Given системная зона America/New_York (UTC-4)
    When пользователь выбирает в пикере диапазон 10–15 июня
    Then период дашборда становится «10 июня – 15 июня»

  Scenario: Одиночная дата не съезжает
    Given системная зона America/New_York (UTC-4)
    When пользователь выбирает «Pick a date» 10 июня
    Then период дашборда становится «День: 10 июня»
```

## Gap / context
Баг H2-tz аудита: внутренняя несогласованность — транзакционные диалоги конвертируют через UTC (G6),
дашбордовый PeriodStrip через systemDefault (G5). В UTC−зонах заголовок и суммы периода неверны на день.

## Implementation links
- commit: dde6d7b7, 241af2c2
- files:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerContent.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/MaterialPickerDateConverters.kt
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodStrip.kt
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/components/DashboardPickerUtcConversionTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/PeriodStripUiTest.kt
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerPeriodSelectorUiTest.kt
