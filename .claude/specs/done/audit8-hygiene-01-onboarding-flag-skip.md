# Онбординг: флаг по buildTypes + рабочие shortcuts при skip
Epic: audit8-hygiene
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-10

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Релизный пользователь снова видит онбординг, а app-shortcuts работают в обоих режимах: (1) SHOW_ONBOARDING переезжает из defaultConfig в buildTypes — release=true, debug=false («Temporary»-флаг становится осмысленным dev-удобством); (2) skip-путь (флаг=false) проставляет onboardingCompletedAt — DecisionRouter перестаёт гонять каждый холодный старт через Splash-ветку, и обработка shortcuts в Dashboard-ветке оживает; (3) DecisionRouterViewModel получает unit-тест маршрутизации.
LAYERS: build, presentation
CHANGED_HINT:
  - app/build.gradle.kts:61 — buildConfigField из defaultConfig → buildTypes { debug { false }, release { true } } (G1)
  - feature/onboarding/.../SplashViewModel.kt (или место гейта MyMoneyNavHost.kt:39 — выбрать при реализации) — при SHOW_ONBOARDING=false и onboardingCompletedAt==null проставить отметку и идти на Dashboard (G1) (assumption: точка вставки)
  - app/src/test/.../navigation/DecisionRouterViewModelTest.kt — НОВЫЙ: Pending/Splash/Dashboard маршруты по onboardingCompletedAt (G1)
  - проверка G2: лонг-пресс shortcut «Добавить расход» при completedAt!=null открывает форму (существующая Dashboard-ветка)
TEST_TYPES: unit
CONSTRAINTS:
  - Сидирование Splash (seedIfNeeded) обязано выполняться и в skip-пути — первый старт debug-сборки по-прежнему получает данные (текущее поведение Splash сохранить).
  - `app/build.gradle.kts` правится также в audit8-hygiene-02 — этот SPEC первый.
  - Поведение release при уже-завершённом онбординге не меняется (completedAt уже стоит).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Онбординг и shortcuts живы

  Scenario: Релиз показывает онбординг один раз
    Given свежая установка release-сборки
    When приложение запускается
    Then показывается онбординг
    And после завершения повторные старты идут сразу на дашборд

  Scenario: Debug пропускает онбординг без поломки shortcuts
    Given свежая установка debug-сборки
    When приложение запускается и пользователь попадает на дашборд
    Then onboardingCompletedAt установлен
    And лонг-пресс shortcut «Добавить расход» открывает форму расхода

  Scenario: Роутер детерминирован
    Given onboardingCompletedAt установлен
    Then DecisionRouter направляет на Dashboard без Splash-петли
```

## Gap / context
Баг M9/P1.2 аудита (G1, G2): «Temporary»-флаг в defaultConfig убил онбординг в release и
обесточил shortcuts (completedAt никогда не ставится → роутер вечно через Splash).

## Implementation links
- commit: e1346bab (prod), 3a19046f (test)
- files: app/build.gradle.kts, app/src/main/java/com/kshavrin/mymoney/navigation/DecisionRouterViewModel.kt, app/src/main/java/com/kshavrin/mymoney/navigation/OnboardingFlagModule.kt, app/src/test/java/com/kshavrin/mymoney/navigation/DecisionRouterViewModelTest.kt
