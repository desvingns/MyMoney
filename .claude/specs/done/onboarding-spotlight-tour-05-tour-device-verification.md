# Сквозная проверка тура на устройстве
Epic: onboarding-spotlight-tour
Order: 05 of 05
Status: done
Completed: 2026-09-21
Depends-on: 04
Date: 2026-09-20
Acceptance-matrix: device=Pixel_5_API_34; build=release_flag; fontScale=1.0,2.0; locale=en,ru
Risk-signals: instrumented, visual-gate, a11y

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Инструментальный сквозной тест `DashboardTourJourneyTest` и ручная/скриншотная проверка на
Pixel_5 API 34: полный проход всех 4 шагов, «Пропустить всё», реальный тап по FAB → возврат → тур на
шаге 2, реальные тапы «Категории»/«Поддержать» → возврат, Back = пропустить, повторный запуск без тура,
a11y-гейт (ATF + 48dp + fontScale 2.0). Так как в debug-сборке `SHOW_ONBOARDING=false`, тест включает тур
через подмену Hilt-флага (`@UninstallModules(OnboardingFlagModule::class)` + собственный `@ShowOnboarding`
= true), не меняя `BuildConfig`.
LAYERS: tests
CHANGED_HINT:
  - app/src/androidTest/java/com/kshavrin/mymoney/DashboardTourJourneyTest.kt (новый) — `@HiltAndroidTest`, `@UninstallModules(OnboardingFlagModule::class)`, `@BindValue @JvmField @ShowOnboarding val showOnboarding = true` (G4: `OnboardingFlagModule.kt:13-21` провайдит флаг из `BuildConfig`); каркас и хелперы `waitForText`/`targetString` — по образцу `MainActivityAddExpenseJourneyTest.kt:30-75` (G19)
  - app/src/androidTest/java/com/kshavrin/mymoney/DashboardTourA11yTest.kt (новый) — `enableAccessibilityChecks()` + `assertTouchWidthIsAtLeast/HeightIsAtLeast(48.dp)` для «Пропустить всё»/«Далее»/«Готово», прогон при fontScale 2.0 через `LocalDensity` (G20)
  - docs/implementation_plan/PROGRESS.md — запись о проверке на устройстве и приложенные скриншоты шагов 1–4 (G20)
TEST_TYPES: instrumented
CONSTRAINTS:
  - **Подключённое загруженное устройство обязательно** (Pixel_5 API 34, `sys.boot_completed=1`; серийник определять поиском, не хардкодить — AGENTS.md «Emulator access», docs/DEVICE_SETUP.md). Без устройства — СТОП и вопрос пользователю; не имитировать и не заявлять прохождение.
  - Запуск Gradle-инструментальных тестов — через `scripts/run_connected_test_on_host_avd.ps1` (AGP 8.7.3 UTP на Windows).
  - Проверять по строкам (`onNodeWithText(stringResource…)`), не по testTag; ожидания — `waitUntil`, не `Thread.sleep` (G20).
  - Сценарии: (1) чистый старт → тур шаг 1 → «Далее» ×3 → «Готово» → дашборд без затемнения, панели закрыты, `onboardingCompletedAt` записан; (2) «Пропустить всё» на шаге 2; (3) тап по «+» на шаге 1 → экран дохода → назад → шаг 2; (4) на шаге 3 тап «Категории» → список категорий → назад → шаг 4, правая панель снова открыта; (5) на шаге 4 тап «Поддержать» → экран поддержки → назад → тур завершён; (6) системный Back = пропустить; (7) повторный запуск активности после прохождения — тура нет.
  - Фаза A→B шага панели проверять управляемо (тест ждёт `TOUR_PANEL_OPEN_DELAY_MS` + запас, либо жмёт кнопку меню/⋮), без флака на таймере.
  - Отдельно подтвердить гипотезу D16 на устройстве: открытая панель закрывает свою кнопку в топ-баре; если нет — скорректировать спеку/поведение и отметить в отчёте.
  - Visual device gate: скриншоты шагов 1–4 (light dim, кружок/контур) приложить к отчёту; не подтверждать визуал только JVM-тестами. Известные pre-existing красные тесты (AuroraBalanceCardUiTest, journey ComposeTimeouts — память проекта) не считать регрессией этого эпика.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Сквозной тур на устройстве

  Scenario: Полный проход
    Given чистая установка и включённый флаг онбординга
    When пользователь проходит четыре шага кнопкой «Далее» и нажимает «Готово»
    Then дашборд остаётся без затемнения, панели закрыты, повторный запуск тура не показывает

  Scenario: Пропустить всё
    Given тур на втором шаге
    When пользователь нажимает «Пропустить всё»
    Then тур закрыт и не появляется при следующем запуске

  Scenario: Реальный тап по подсвеченной кнопке
    Given тур на первом шаге
    When пользователь нажимает подсвеченную кнопку дохода и затем возвращается назад
    Then тур продолжается со второго шага

  Scenario: Доступность
    Given тур активен, масштаб шрифта 200%
    Then текст карточки не обрезан, кнопки не меньше 48dp
    And проверки доступности не находят нарушений
```

## Gap / context
Часть поведения тура (проход касаний внутри выреза, панель, закрывающая свою кнопку, реальная навигация
и возврат) проверяется только на реальном устройстве; JVM/Robolectric-тесты SPEC-01…03 её не доказывают.

## Implementation links
- commit: 19fca24e
- files: see commits; removed onboarding files archived under archive/onboarding-legacy/
