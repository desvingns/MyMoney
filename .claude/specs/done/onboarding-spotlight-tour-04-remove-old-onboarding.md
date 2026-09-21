# Удалить старый 4-слайдовый онбординг
Epic: onboarding-spotlight-tour
Order: 04 of 05
Status: done
Completed: 2026-09-21
Depends-on: 03
Date: 2026-09-20
Acceptance-matrix: navigation=splash_to_dashboard; removal=archived_not_deleted
Risk-signals: navigation, contract-tests, kover, destructive-move

## SPEC
=== SPEC ===
TASK: refactor
PLATFORM: android
WHAT: Убрать старый pager-онбординг, оставив Splash. Граф навигации: `Splash → Dashboard` напрямую
(тур сам поднимется на дашборде, пока `onboardingCompletedAt == null`); `Destinations.Onboarding` и
композабл `OnboardingScreen` уходят. Файлы старого онбординга НЕ удаляются, а переносятся в
`archive/onboarding-legacy/` (репозиторий, git-ignored) с сохранением относительной структуры;
список путей отдаётся пользователю для ручной очистки (AGENTS.md, D15). Контрактные тесты, жёстко
завязанные на онбординг, обновляются осмысленно (а не ослабляются). Четыре журнейных androidTest больше не
ждут `onboarding_skip`.
LAYERS: presentation
CHANGED_HINT:
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:39-62 — `Splash`: обе ветки (`SHOW_ONBOARDING`/иначе) ведут на `Destinations.Dashboard()` с `popUpTo<Destinations.Splash> { inclusive = true }`, ветка `SHOW_ONBOARDING` схлопывается; удалить `composable<Destinations.Onboarding>` (G2)
  - feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/SplashScreen.kt:29-41 — переименовать колбэк `onNavigateToOnboarding` → `onFinished` (assumption: имя больше не соответствует смыслу); обновить вызов в `MyMoneyNavHost.kt:40` и `app/src/androidTest/.../feature/onboarding/SplashContentUiTest.kt`
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/navigation/Destinations.kt:22-24 — удалить `Destinations.Onboarding` (G6)
  - feature/onboarding/src/main/java/com/kshavrin/mymoney/feature/onboarding/OnboardingScreen.kt, OnboardingViewModel.kt — перенести в `archive/onboarding-legacy/…` (G5)
  - feature/onboarding/src/main/res/drawable/onboarding_hero_1.xml … onboarding_hero_4.xml — перенести в `archive/onboarding-legacy/…` (G5)
  - feature/onboarding/src/main/res/values/strings.xml:1-23, values-ru/strings.xml — вынуть ключи `onboarding_*` (слайды, `onboarding_skip/next/get_started`); оставить то, что использует Splash; текст удалённых строк сохраняется в `archive/onboarding-legacy/` и в git-истории (G5)
  - feature/onboarding/src/test/java/com/kshavrin/mymoney/feature/onboarding/OnboardingViewModelTest.kt, OnboardingPageTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/onboarding/OnboardingContentUiTest.kt — перенести в `archive/onboarding-legacy/…` (покрывали удаляемый код)
  - app/src/test/java/com/kshavrin/mymoney/navigation/DestinationsTest.kt:111,299 — убрать `"Onboarding"` из списка destination'ов и проверку `popUpTo<Destinations.Onboarding> { inclusive = true }`, добавить проверку `Splash → Dashboard` (G18)
  - app/src/test/java/com/kshavrin/mymoney/L10nParityTest.kt:121 — модуль `feature/onboarding` остаётся в списке, пока в нём есть строки (Splash); иначе убрать (G18)
  - build.gradle.kts:29,42,142 и app/src/test/java/com/kshavrin/mymoney/KoverCoverageCiContractTest.kt:26,49,186 — порог `:feature:onboarding` = 13 сверить с замером; модуль остаётся (Splash) (G18)
  - app/src/androidTest/java/com/kshavrin/mymoney/MainActivityAddExpenseJourneyTest.kt:28,53, MainActivityCreateCategoryJourneyTest.kt:35,66, MainActivityTransferJourneyTest.kt:36,73, TransactionsListRuntimeRouteTest.kt:24,85 — убрать импорт `OnboardingR` и шаг «нажать `onboarding_skip`»: debug-сборка (`SHOW_ONBOARDING=false`) идёт сразу на дашборд, а не в онбординг (G4, G19)
  - TDD/MyMoney/MyMoney_TDD.md:~366,~518 — описание S00/S11 обновить под тур-подсветку и записать решение (AS-NN); предложение, требует ревью пользователя (assumption, O3)
  - docs/implementation_plan/PROGRESS.md — итог эпика (единственный писатель состояния проекта; не создавать STATE.md/ROADMAP.md, AGENTS.md)
TEST_TYPES: unit
CONSTRAINTS:
  - **Ничего не удалять** — только `archive/onboarding-legacy/…` (AGENTS.md «archive, never delete»; личное правило пользователя). В отчёте перечислить точные пути для ручной очистки.
  - Порядок: SPEC-03 уже даёт рабочий тур; без него удалять онбординг нельзя (новый пользователь остался бы без гида).
  - `DecisionRouterViewModel` не менять: путь skip (`SHOW_ONBOARDING=false` → штамп `onboardingCompletedAt` → Dashboard) и `Splash` для release остаются как есть; `DecisionRouterViewModelTest` (6 тестов) должен остаться зелёным без правок (G1).
  - `SHOW_ONBOARDING` и `@ShowOnboarding` не трогать (D7).
  - Kover-порог `:feature:onboarding` (13): после переноса тестов сначала предпочесть покрыть Splash (`SplashViewModelTest` остаётся); если замер ниже 13 — не править порог молча: занести замер в `docs/KOVER_COVERAGE_BASELINE.md`, выставить порог = замер вниз, и явно отметить это в отчёте для ревью (G18).
  - Обновлять контрактные тесты по смыслу (список destination'ов, popUpTo), а не удалять проверки ради зелёного билда.
  - Журнейные androidTest в debug сейчас красные ещё до эпика (ждут онбординг, которого в debug нет — память проекта: pre-existing red); после правки они должны пройти либо остаться красными по независимым причинам — указать в отчёте, не выдавать за регрессию.
  - Строки `onboarding_skip` больше нет: не оставлять висячих ссылок `R.string.onboarding_*` (grep по репозиторию, включая `docs/`-скрипты).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Старый онбординг удалён

  Scenario: Первый запуск ведёт на дашборд с туром
    Given чистая установка release-сборки
    When завершается Splash
    Then открывается дашборд, а не слайды онбординга
    And поверх дашборда показан тур

  Scenario: Старый экран недоступен
    Given собранное приложение
    Then в графе навигации нет destination «Onboarding»
    And ресурсов слайдов онбординга в приложении нет

  Scenario: Debug-сборка не показывает тур
    Given debug-сборка и пустые настройки
    When приложение запускается
    Then открывается обычный дашборд без затемнения

  Scenario: Файлы сохранены
    Given старые файлы онбординга
    Then они лежат в archive/onboarding-legacy, а не удалены
```

## Gap / context
После SPEC-03 у приложения два гида сразу: старый pager (Splash → Onboarding) и новый тур. Старый нужно
убрать, не потеряв Splash-инициализацию данных и не сломав контрактные тесты, которые перечисляют
destination'ы, модули и пороги покрытия (G18).

## Implementation links
- commit: 64329e6b
- files: see commits; removed onboarding files archived under archive/onboarding-legacy/
