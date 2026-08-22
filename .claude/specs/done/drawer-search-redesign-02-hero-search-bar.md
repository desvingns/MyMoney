# Строка поиска в hero-стиле MoneyHeroAppBar
Epic: drawer-search-redesign
Order: 02 of 02
Status: done
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: ui_state=empty_query,typed_query,both_entry_points
Risk-signals: navigation, visual

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Старая строка поиска (`SearchTopBar`: плоский `Surface` 64.dp + `BasicTextField`, не
совпадает ни с одним паттерном приложения) перерисована под текущий hero-дизайн: градиентный фон
`dashboardHeroGradientStart/End`, высота `Spacing.heroAppBarHeight`, типографика и отступы из
токенов `:core:ui` theme — визуально едино с `MoneyHeroAppBar`. Поведение сохранено: автофокус +
клавиатура, back слева, справа Mic при пустом запросе / Close при введённом, фазы
Empty/Loading/Results/EmptyResults/Error и поиск по заметкам не тронуты. Обновляются ОБА входа
(drawer-overlay и route из списка транзакций) — компонент общий (D3).
LAYERS: presentation
CHANGED_HINT:
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchScreen.kt:187-270 — перестроить `SearchTopBar` под hero-стиль: градиент `Brush.linearGradient(dashboardHeroGradientStart/End)`, высота `Spacing.heroAppBarHeight`, слоты по образцу `MoneyHeroAppBar` (G34, G36); поле ввода, автофокус (:141-145), логику Mic/Close и back сохранить (G32, D2)
  - feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchScreen.kt:128-186 — `SearchContent`: семантику `contextualOverlay` (фон тела в фазе Empty) и `voiceSearchAvailable` сохранить (G39)
  - feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentTest.kt — обновить JVM-ветвления mic/clear и bodyByPhase под новую разметку (G38)
  - app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentUiTest.kt — обновить под новую структуру бара; ноды искать по contentDescription из ресурсов, как сейчас (G38)
  - feature/transactionslist/src/main/res/values/strings.xml + values-ru/strings.xml — новые строки (если появятся, напр. hint/contentDescription) с префиксом `search_`, EN+RU один в один (G37, G63)
TEST_TYPES: unit, compose-ui, instrumented
CONSTRAINTS:
  - Дизайн-токены только из `:core:ui` theme (`Spacing`/`Color`/Typography/Shape) — захардкоженные
    dp/цвета запрещены (G36); старые `64.dp` и плоский `colorScheme.primary` — убрать (G32).
  - Готового SearchBar в `:core:designsystem` нет (G35): либо переиспользовать слоты
    `MoneyHeroAppBar`, либо зеркалить его градиент/высоту — решение за ui-designer, НО новый
    публичный компонент designsystem в этом SPEC не создавать без необходимости.
  - Существующие ключи `search_*` и их contentDescription не переименовывать — UI-тесты ищут
    ноды по ним (G37, G38).
  - Логику поиска не менять: `searchByNote`, debounce 200мс, история запросов, фазы
    `SearchPhase`, `OpenDetail` (G33) — только presentation бара.
  - Скоуп — оба входа одновременно (D3): drawer-overlay (`contextualOverlay=true`) и route из
    TransactionsList; раздваивать компонент запрещено.
  - Instrumented UI-тесты живут в app/src/androidTest → `:app:connectedDebugAndroidTest`,
    обязателен Pixel 5 API 34 (visual device gate, G61); Roborazzi-suite дашборда не затрагивается
    (поиск в :feature:transactionslist, G62).
  - mp-runner гоняет repo-wide `testDebugUnitTest --continue` (G60).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Строка поиска в текущем hero-дизайне

  Scenario: Бар поиска соответствует hero-стилю приложения
    Given пользователь открыл поиск
    Then строка поиска отображается на градиентном фоне приложения
    And её высота равна высоте hero app bar
    And поле ввода получает фокус и поднимает клавиатуру

  Scenario: Пустой запрос показывает голосовой ввод
    Given открыт поиск с пустым запросом
    Then справа в баре отображается кнопка голосового ввода

  Scenario: Введённый запрос показывает очистку
    Given открыт поиск
    When пользователь вводит текст
    Then кнопка голосового ввода сменяется кнопкой очистки
    When пользователь нажимает очистку
    Then запрос очищается и снова отображается кнопка голосового ввода

  Scenario: Поведение поиска не изменилось
    Given открыт поиск
    When пользователь вводит запрос, совпадающий с заметкой транзакции
    Then отображаются результаты поиска
    And нажатие на результат открывает детали транзакции

  Scenario: Оба входа показывают новый бар
    Given пользователь открыл поиск из drawer дашборда
    Then отображается бар в hero-стиле
    When пользователь открывает поиск из списка транзакций
    Then отображается тот же бар в hero-стиле
```

## Gap / context
Требование пользователя: бар поиска визуально устарел («старая строка поиска») и не совпадает
ни с hero-паттерном дашборда (G34), ни с M3 TopAppBar списков (G40). Заблокировано: hero-стиль
(D1), mic остаётся (D2), оба входа в скоупе (D3).

## Implementation links
- commit: 3b8b1188, 47616f0b
- files: feature/transactionslist/src/main/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchScreen.kt; feature/transactionslist/src/test/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transactionslist/search/SearchContentUiTest.kt
