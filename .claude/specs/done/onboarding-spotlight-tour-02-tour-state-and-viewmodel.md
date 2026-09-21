# Состояние тура и логика в DashboardViewModel
Epic: onboarding-spotlight-tour
Order: 02 of 05
Status: done
Completed: 2026-09-21
Depends-on: —
Date: 2026-09-20
Acceptance-matrix: steps=4; phases=button,panel; outcome=next,skip_all,paused_resume,finish
Risk-signals: viewmodel-constructor, datastore, savedstate

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Логика тура без UI. Чистая модель `TourStep` (Actions, LeftPanel, RightCategories, RightSupport),
`TourPhase` (Button, Panel — только у LeftPanel/RightCategories), `TourUiState(step, phase, paused)` и
чистый редьюсер с правилами: `next()`, `skipAll()`, `panelOpenElapsed()`, `targetActivated(event)`,
`resumed()`. Функция `drawersFor(step, phase)` — единственный источник истины о состоянии боковых
панелей на каждом (шаге, фазе). `DashboardViewModel` при старте читает `AppSettings.onboardingCompletedAt`;
если он `null` — поднимает тур (шаг из `SavedStateHandle` или первый). Реальные клики по подсвеченным
целям (FAB, «Категории», «Поддержать», кнопки меню/⋮ в фазе Button) переводят тур в паузу или в фазу Panel,
не ломая обычную навигацию; событие возврата `TourResumed` продолжает тур со СЛЕДУЮЩЕГО шага (D8) и
восстанавливает панель, нужную шагу. «Готово» / «Пропустить всё» / Back закрывают тур, закрывают обе
панели и записывают `onboardingCompletedAt`.
LAYERS: domain, presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/tour/DashboardTour.kt (новый) — `TourStep`, `TourPhase`, `TourUiState`, редьюсер, `drawersFor`, `const val TOUR_PANEL_OPEN_DELAY_MS = 900L` (assumption: новый файл; образец состояния «immutable data class + чистые функции» — G11)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt:52-53 — после `leftDrawerOpen/rightDrawerOpen` добавить `val tour: TourUiState? = null` (G11)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardState.kt:247-251 — рядом с `LeftDrawerToggled`/`RightDrawerToggled`/`DrawerDismissed` добавить события `TourNextClicked`, `TourSkipAllClicked`, `TourPanelOpenElapsed`, `TourResumed` (G11)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:57-69 — конструктор: добавить `AppSettingsRepository` (модуль `:core:datastore` уже подключён, G16) и `SavedStateHandle`; ключи `tour_step`, `tour_paused` (G16)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt (init) — `appSettingsRepository.settings.first()`; `onboardingCompletedAt == null` → `tour = restored ?: first`; до первого чтения `tour` остаётся `null` (G3)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:1080-1091 — `LeftDrawerToggled`/`RightDrawerToggled`: если `tour.phase == Button` и кнопка соответствует шагу — перевести в фазу Panel (панель откроется обычной веткой), иначе прежнее поведение (G11)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt:1095-1097, 1106-1109, 1122-1125 — `MinusFabClicked`/`PlusFabClicked`/`TransferClicked` (шаг Actions), `CategoriesClicked` (RightCategories/Panel), `SupportClicked` (RightSupport): помимо существующего `emit(Navigate…)` выставить `tour.paused = true` (G14, G15)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModel.kt (новый метод) — `finishTour()`: `tour = null`, закрыть панели, `appSettingsRepository.update { it.copy(onboardingCompletedAt = System.currentTimeMillis()) }` с `catch` → `reportToSentry()` по образцу `DecisionRouterViewModel.skipOnboarding` (`app/.../DecisionRouterViewModel.kt:47-56`, G1); `CancellationException` пробрасывать
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTest.kt — обновить ВСЕ места конструирования `DashboardViewModel(` под новые параметры (G16); использовать `FakeAppSettingsRepository` из `core/testing/src/main/kotlin/com/kshavrin/mymoney/core/testing/fake/FakeAppSettingsRepository.kt:8` (проверить, что `:core:testing` доступен в тестовом classpath модуля — assumption)
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/tour/DashboardTourTest.kt (новый) — чистые тесты редьюсера и `drawersFor`
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardViewModelTourTest.kt (новый) — тесты VM с Fake-репозиторием и `SavedStateHandle()`
  - app/src/androidTest/java/com/kshavrin/mymoney/ImportFocusColdStartRegressionTest.kt — обновить конструирование `DashboardViewModel(` (G16)
TEST_TYPES: unit
CONSTRAINTS:
  - **Таблица переходов (единый источник, D8/D9/D16).** Шаги по порядку: Actions → LeftPanel → RightCategories → RightSupport → конец.
    `drawersFor`: Actions=(closed, closed); LeftPanel/Button=(closed, closed); LeftPanel/Panel=(left open, right closed); RightCategories/Button=(closed, closed); RightCategories/Panel=(left closed, right open); RightSupport=(left closed, right open, без фазы).
  - `next()` из Actions → LeftPanel/Button; из LeftPanel (любая фаза) → RightCategories/Button; из RightCategories (любая фаза) → RightSupport; из RightSupport → завершение. Вход в шаг применяет `drawersFor` к `leftDrawerOpen/rightDrawerOpen`.
  - `panelOpenElapsed()` меняет фазу Button → Panel и игнорируется в остальных состояниях (устаревший таймер после «Далее» не должен ничего ломать).
  - `TourResumed` действует ТОЛЬКО при `paused == true`: `ON_RESUME` приходит и при первом показе экрана, и после возврата — в непаузном состоянии он no-op. После снятия паузы: `next()` (если был последний шаг — завершение) и применение `drawersFor` для нового шага (после `closeDrawers()` из G14 правая панель для шага 4 открывается заново).
  - Пока `paused == true`, оверлей не показывается, а события `TourNextClicked`/`TourSkipAllClicked` игнорируются.
  - Запись `onboardingCompletedAt` — только в `finishTour()`; при смерти процесса шаг восстанавливается из `SavedStateHandle`, но если `onboardingCompletedAt != null` (тур уже пройден в другом процессе) тур не поднимается.
  - Не менять существующие обработчики навигации (`emit(Navigate…)`, `closeDrawers()`) — только дополнить. Поведение дашборда при `tour == null` идентично текущему.
  - Тесты — только Fake (`FakeAppSettingsRepository`), без MockK/Mockito; `SavedStateHandle()` без `toRoute` → Robolectric не нужен (в отличие от G-gotcha про `toRoute`).
  - Строк и UI в этом SPEC нет; счётчик шагов = `TourStep.entries.size` (не хардкодить 4 нигде, кроме тестов).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Логика тура

  Scenario: Первый запуск поднимает тур
    Given onboardingCompletedAt равен null
    When дашборд создан
    Then активен шаг «Расходы, переводы, доходы»
    And обе боковые панели закрыты

  Scenario: Уже пройденный тур не поднимается
    Given onboardingCompletedAt заполнен
    When дашборд создан
    Then тура нет

  Scenario: Двухфазный шаг с панелью
    Given активен шаг левой панели в фазе кнопки
    When проходит время открытия панели
    Then левая панель открывается, фаза становится «панель»
    And повторный сигнал таймера ничего не меняет

  Scenario: Реальное действие ставит тур на паузу и продолжает со следующего шага
    Given активен шаг «Расходы, переводы, доходы»
    When пользователь нажимает подсвеченную кнопку дохода
    Then навигация на ввод дохода выполняется, тур на паузе
    When пользователь возвращается на дашборд
    Then тур продолжается с шага левой панели

  Scenario: Возврат после «Поддержать» завершает тур
    Given активен шаг «Поддержать проект»
    When пользователь нажимает пункт и затем возвращается
    Then тур завершён и записан как пройденный

  Scenario: Пропустить всё
    Given активен любой шаг
    When пользователь нажимает «Пропустить всё»
    Then тур закрыт, панели закрыты, onboardingCompletedAt записан

  Scenario: Смерть процесса посреди тура
    Given тур на шаге правой панели
    When процесс пересоздан с сохранённым состоянием
    Then тур продолжается с того же шага
```

## Gap / context
У `DashboardViewModel` нет ни доступа к `onboardingCompletedAt`, ни понятия «тур» (G16). Нужна тестируемая
логика шагов/фаз/паузы отдельно от Compose, чтобы UI в SPEC-03 был тонким, а сложные случаи
(реальный тап, закрытие панелей из G14, смерть процесса) проверялись JVM-тестами.

## Implementation links
- commit: 1162e893, 559073e3, 89ae6de2, 6938b95a
- files: see commits; removed onboarding files archived under archive/onboarding-legacy/
