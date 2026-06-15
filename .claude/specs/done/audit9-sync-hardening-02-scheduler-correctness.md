# RecurringScheduler: якорный день месяца и weekly-interval
Epic: audit9-sync-hardening
Order: 02 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: (1) Месячный повтор «31-е число» перестаёт навсегда деградировать в «28-е» после февраля: день месяца якорится на startsAt шаблона и переустанавливается с клампом к длине месяца. (2) Weekly с byDay начинает уважать interval: «раз в 2 недели по понедельникам» срабатывает раз в 2 недели, а не еженедельно.
LAYERS: domain
CHANGED_HINT:
  - core/domain/.../usecase/RecurringScheduler.kt:17 — monthly: candidate = current.plusMonths(interval); next = candidate.withDayOfMonth(min(anchorDay, candidate.lengthOfMonth())), anchorDay = startsAt.dayOfMonth (G2)
  - core/domain/.../usecase/RecurringScheduler.kt:24-37 — weekly: база скана = current.plusWeeks(interval - 1), затем первый byDay-день строго после базы (G2)
  - core/domain/src/test/.../RecurringSchedulerTest.kt — дополнить фикстурами Calculation-блока (существующие кейсы interval=1 не ломать)
TEST_TYPES: unit
CONSTRAINTS:
  - Чистая функция, clock/now инжектится (текущая сигнатура) — без скрытого now().
  - daily/yearly ветки не трогать.

### Calculation: следующий запуск повтора
- Formula (monthly): `next = (current + interval мес.).withDayOfMonth(min(anchorDay, lengthOfMonth))`,
  `anchorDay = startsAt.dayOfMonth` (хранится/выводится из шаблона, НЕ из current).
- Formula (weekly+byDay): `base = current + (interval − 1) нед.`; next = первый день из byDay строго
  после base (скан base+1d … base+7d).
- Symbols: current — LocalDate последнего запуска; interval — Int ≥ 1; anchorDay — Int 1..31;
  byDay — набор DayOfWeek; next — LocalDate.
- Edge: interval < 1 → invalid (reject); месяц короче anchorDay → кламп к последнему дню,
  следующий длинный месяц ВОЗВРАЩАЕТСЯ к anchorDay; byDay пуст → текущее поведение weekly без byDay.
- Worked examples (fixtures):
  | вид     | current     | startsAt    | interval | byDay | next        |
  |---------|-------------|-------------|----------|-------|-------------|
  | monthly | 2026-01-31  | 2025-12-31  | 1        | —     | 2026-02-28  |
  | monthly | 2026-02-28  | 2025-12-31  | 1        | —     | 2026-03-31  |
  | weekly  | 2026-06-08 (пн) | —       | 2        | MON   | 2026-06-22  |
  | weekly  | 2026-06-08 (пн) | —       | 1        | MON   | 2026-06-15  |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Расписание повторов точное

  Scenario: 31-е число возвращается после февраля
    Given месячный шаблон с якорем 31-е
    When повтор проходит февраль (28 дней)
    Then мартовский запуск назначен на 31 марта

  Scenario: Раз в две недели
    Given weekly-шаблон по понедельникам с interval=2
    When текущий запуск — понедельник 8 июня
    Then следующий запуск — понедельник 22 июня
```

## Gap / context
Баг M15 аудита (G2): plusMonths теряет якорный день навсегда; weekly-скан с current+1d делает
interval бесполезным. Спящий (G6), закрыть до UI рекуррентов.

## Implementation links
- commit: 7bb6df74 (prod) + f1d7d98e (tests)
- files:
  - core/domain/src/main/kotlin/com/kshavrin/mymoney/core/domain/usecase/RecurringScheduler.kt
  - core/domain/src/test/kotlin/com/kshavrin/mymoney/core/domain/usecase/RecurringSchedulerTest.kt
- verification: :core:domain:test green (RecurringSchedulerTest 9/9, 0 failures); :core:domain:ktlintCheck green. Runner script gave documented false-negative (phantom :app:detekt/jacoco) — verified manually.
