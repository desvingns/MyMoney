# Тур на дашборде: метки целей, оверлей, строки, a11y
Epic: onboarding-spotlight-tour
Order: 03 of 05
Status: done
Completed: 2026-09-21
Depends-on: 01, 02
Date: 2026-09-20
Acceptance-matrix: steps=4; locale=en,ru; fontScale=1.0,1.5,2.0
Risk-signals: compose-ui, i18n, a11y, lifecycle

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Подключить тур к настоящему дашборду. Шесть целей получают `Modifier.spotlightTarget`: ряд из трёх
FAB (один блок, форма RoundedRect), кнопка меню и кнопка ⋮ (Circle), содержимое левой панели
(RoundedRect), пункты «Категории» и «Поддержать проект» правой панели (RoundedRect). `SpotlightOverlay`
добавляется ПОСЛЕДНИМ в корневой `Box` `DashboardContent`, поэтому рисуется поверх Scaffold и обеих панелей.
По `state.tour` оверлей выбирает цель: (Actions) → ряд FAB; (LeftPanel, Button) → кнопка меню; (LeftPanel,
Panel) → левая панель; (RightCategories, Button) → ⋮; (RightCategories, Panel) → «Категории»;
(RightSupport) → «Поддержать». Фаза Button сама переходит в Panel через `LaunchedEffect` с задержкой
`TOUR_PANEL_OPEN_DELAY_MS` (событие `TourPanelOpenElapsed`). На `ON_RESUME` шлётся `TourResumed`.
Карточка показывает заголовок, текст и «N из M»; на последнем шаге кнопка «Готово». Системный Back во время
тура = «Пропустить всё». Пока `tour.paused` или `tour == null` — оверлея нет.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:185-291 — в `DashboardContent` создать `rememberSpotlightRegistry()`; после второго `DashboardDrawerOverlay` (правая панель) добавить оверлей тура, выбирающий цель по `state.tour` (G7)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:174-176 — `BackHandler(enabled = state.tour != null && !paused)` → `TourSkipAllClicked`; объявляется ПОСЛЕ существующего `BackHandler` панелей, чтобы иметь приоритет (последний зарегистрированный включённый обработчик выигрывает) (G7)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:511,541,563 — `DashboardTopBar` принимает реестр (`registry: SpotlightTargetRegistry? = null` — параметр по умолчанию, чтобы существующие вызовы и Roborazzi-тесты компилировались без правок); `Modifier.spotlightTarget` на двух `IconButton` (меню и ⋮), форма Circle (G8, G23: иконка меню становится ArrowBack при открытой панели — цель то же самое место)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt:89-108 (`DashboardRoute`) — эффект `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onEvent(TourResumed) }` по образцу `feature/support/.../SupportRoute.kt:26` (G22; альтернативный образец — `CloudSyncScreen.kt:308`)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/ThreeFabLayout.kt:35-45 — обернуть/применить `spotlightTarget` к корневому `Row` через уже существующий параметр `modifier` (G9)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/RightDrawerContent.kt:59-64,89-93,98-112 — `RightDrawerItem` получает `modifier`; `spotlightTarget` на «Категории» и «Поддержать» (G12); `RightDrawerContent(onEvent, registry)`
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/components/LeftDrawerContent.kt:75 — `spotlightTarget` на корневой контейнер содержимого левой панели (G13)
  - feature/dashboard/src/main/res/values/strings.xml — ключи `dashboard_tour_skip_all`, `dashboard_tour_next`, `dashboard_tour_done`, `dashboard_tour_progress` («%1$d of %2$d»), `dashboard_tour_actions_title/_body`, `dashboard_tour_left_title/_body`, `dashboard_tour_categories_title/_body`, `dashboard_tour_support_title/_body`, `dashboard_tour_overlay_description` (assumption: префикс `dashboard_tour_`)
  - feature/dashboard/src/main/res/values-ru/strings.xml — те же ключи: «Пропустить всё», «Далее», «Готово», «%1$d из %2$d»; шаг 1 «Расходы, переводы, доходы» / «Минус — записать расход, стрелки — перевод между счетами, плюс — добавить доход.»; шаг 2 «Период и счета» / «Выберите период, задайте свой диапазон дат и укажите, по каким счетам считать баланс.»; шаг 3 «Категории» / «В правом меню открываются категории — добавляйте свои и настраивайте существующие.»; шаг 4 «Поддержать проект» / «Если MyMoney вам полезен — здесь можно поддержать его развитие.» (O1, assumption: тексты-черновик)
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardTourStringsTest.kt (новый) — паритет EN/RU по префиксу `dashboard_tour_` через разбор XML, по образцу `feature/support/src/test/java/.../PaywallStringsTest.kt:11-72` (G20)
  - feature/dashboard/src/test/java/com/kshavrin/mymoney/feature/dashboard/DashboardTourOverlayTest.kt (новый) — Robolectric + Compose UI (в модуле уже есть `libs.robolectric`/`compose.ui.test.junit4`, `feature/dashboard/build.gradle.kts`): при `state.tour` показываются заголовок, «N из M», обе кнопки; при `tour == null`/`paused` оверлея нет
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Оверлей — ПОСЛЕДНИЙ ребёнок корневого `Box` (после обеих `DashboardDrawerOverlay`): иначе панель окажется выше затемнения (G7, G10).
  - Панели управляются ТОЛЬКО через `state.leftDrawerOpen/rightDrawerOpen` (их выставляет VM из SPEC-02) — UI не открывает панели сам.
  - Все тексты — из `stringResource`, никаких литералов; ключи EN/RU строго в паритете (G20, `dashboard_tour_` покрыт новым тестом).
  - Кнопки оверлея — `onNodeWithText(stringResource(...))` в тестах, не testTag (G20, mp-tester-android: локализационные баги должны всплывать).
  - Тап по подсвеченной цели идёт в существующие обработчики (`onEvent(MinusFabClicked)` и т.д.) — новых click-обработчиков на целях не добавлять; логику паузы держит VM (SPEC-02).
  - При `fontScale` 1.5 и 2.0 карточка и кнопки не обрезаются; кнопки ≥ 48dp (a11y-гейт, G20); карточка не перекрывает подсвеченную цель (выше/ниже выреза, SPEC-01).
  - Не менять визуал и поведение дашборда при `tour == null` — существующие тесты дашборда (в т.ч. Roborazzi-скриншоты) остаются зелёными без обновления эталонов.
  - Визуальный результат подтверждается на Pixel_5 API 34 (visual device gate); полный сквозной прогон — SPEC-05.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Тур на дашборде

  Scenario: Шаг с рядом кнопок
    Given первый запуск, тур на первом шаге
    Then затемнение закрывает экран, а три кнопки внизу подсвечены одним блоком
    And видна карточка «Расходы, переводы, доходы» с подписью «1 из 4»
    And слева внизу «Пропустить всё», справа внизу «Далее»

  Scenario: Шаг левой панели
    Given тур на шаге левой панели
    Then сначала подсвечена кнопка меню, панель закрыта
    When проходит время открытия панели
    Then панель открывается и подсвечена целиком

  Scenario: Шаг «Поддержать проект»
    Given тур на четвёртом шаге
    Then правая панель открыта, подсвечен пункт «Поддержать проект»
    And кнопка справа внизу называется «Готово»

  Scenario: Back равен «Пропустить всё»
    Given тур активен
    When пользователь нажимает системную кнопку «Назад»
    Then тур закрывается, дашборд без затемнения

  Scenario: Тур не мешает обычной работе
    Given тур пройден
    Then на дашборде нет затемнения и подсказок
    And боковые панели и кнопки работают как раньше
```

## Gap / context
Логика тура (SPEC-02) и компонент подсветки (SPEC-01) ещё не подключены к экрану. Целей нет
(у FAB и кнопок топ-бара нет ни testTag, ни привязки к реестру — G8, G9), оверлея в `DashboardContent` нет,
строк тура в ресурсах нет.

## Implementation links
- commit: 40fb08c3
- files: see commits; removed onboarding files archived under archive/onboarding-legacy/
