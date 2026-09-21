# Универсальный оверлей-подсветка (spotlight) в core:designsystem
Epic: onboarding-spotlight-tour
Order: 01 of 05
Status: done
Completed: 2026-09-21
Depends-on: —
Date: 2026-09-20
Acceptance-matrix: cutout_shape=circle,rounded_rect; touch=inside_cutout_passes,outside_blocked; target=registered,unregistered
Risk-signals: compose-pointer-input, design-system

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Новый feature-агностичный компонент подсветки в `:core:designsystem` (пакет `spotlight`):
реестр целей (`Modifier.spotlightTarget(registry, key)` запоминает `boundsInRoot` элемента),
оверлей `SpotlightOverlay`, который рисует лёгкое затемнение на весь экран с вырезом под выбранную цель
(форма `Circle` для круглых кнопок или `RoundedRect` по границам — для ряда FAB, панелей, пунктов списка),
плавно переезжает от одной цели к другой (`LocalMotion`), показывает карточку-подсказку (заголовок,
текст, «N из M») выше или ниже выреза — где больше места — и две кнопки: «Пропустить всё» слева внизу и
«Далее»/«Готово» справа внизу. Касания ВНУТРИ выреза проходят к элементу под оверлеем, все остальные
касания по затемнению поглощаются (не закрывают панели, не листают пейджер). Оверлей не знает про
дашборд, шаги и тексты — всё приходит параметрами.
LAYERS: presentation
DESIGN_TOKENS: colorScheme.spotlightScrim, colorScheme.spotlightCutoutRing, spacing.spotlightCutoutPadding, spacing.spotlightCardMaxWidth, shape.spotlightCard, typography.spotlightTitle, typography.spotlightBody, typography.spotlightProgress
CHANGED_HINT:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/spotlight/SpotlightTargets.kt (новый) — `SpotlightTargetRegistry` (Stable, `SnapshotStateMap<Any, Rect>`), `rememberSpotlightRegistry()`, `Modifier.spotlightTarget(registry: SpotlightTargetRegistry?, key)` через `onGloballyPositioned { boundsInRoot() }` + `DisposableEffect` для снятия регистрации; при `registry == null` модификатор — no-op, чтобы существующие вызовы/превью/Roborazzi-тесты компилировались без правок (assumption: новый файл, аналогов нет — G17)
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/spotlight/SpotlightOverlay.kt (новый) — `SpotlightOverlay(registry, cutout: SpotlightCutout?, card, skipLabel, primaryLabel, onSkip, onPrimary, modifier)`; `SpotlightCutout(key, shape)`. Рисование: `graphicsLayer { compositingStrategy = Offscreen }` + `drawRect(scrim)` + `drawCircle/drawRoundRect(blendMode = BlendMode.Clear)`; кольцо-обводка `spotlightCutoutRing`. Перенос выреза — `animateRect`/`Animatable` на длительностях `LocalMotion` (G21: `DashboardDrawerOverlay.kt:48-59` — образец использования `LocalMotion`)
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/spotlight/SpotlightGeometry.kt (новый) — чистые функции: `cutoutRect(bounds, shape, padding)` (для Circle — квадрат по большей стороне) и `cardPlacement(cutout, containerHeight, cardHeight): Above|Below` (assumption: выносим в pure-функции ради JVM-теста)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt:115 — рядом с `dashboardDrawerScrim` добавить `spotlightScrim` (≈ 0.72 alpha тёмного нейонового фона, O2) и `spotlightCutoutRing` (= primary) — как расширения `ColorScheme`, без сырых `Color(0x…)` (G10; конвенция support-screen-redesign D16)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt — токены `spotlightCutoutPadding` 8.dp, `spotlightCardMaxWidth` 320.dp; ВНИМАНИЕ: файл содержит чужие незакоммиченные правки — добавлять точечно (assumption)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Shape.kt, Typography.kt — `spotlightCard` (RoundedCornerShape 20.dp), `spotlightTitle`/`spotlightBody`/`spotlightProgress` производными от существующих ролей через `.copy()` (assumption, по образцу `support*`-токенов)
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/spotlight/SpotlightOverlayUiTest.kt (новый) — Compose UI: вырез Circle/RoundedRect, карточка, обе кнопки, pass-through и блок касаний (G20: ATF + 48dp)
  - core/designsystem/src/test/java/com/kshavrin/mymoney/core/designsystem/spotlight/SpotlightGeometryTest.kt (новый) — JVM-тест геометрии (assumption: `src/test` в модуле проверить; иначе положить рядом с ближайшим существующим unit-тестом модуля)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **Pass-through внутри выреза — обязательный спайк.** Рекомендованный приём: не вешать `pointerInput` на весь оверлей; затемнение рисуется `Canvas`-ом БЕЗ обработчиков касаний, а поглощают касания ЧЕТЫРЕ прозрачных блокера (сверху/снизу/слева/справа от `cutoutRect`), внутри `cutoutRect` нет ни одного pointer-узла — касание естественно доходит до элемента под оверлеем. Альтернатива (`sharePointerInputWithSiblings`) допустима только если пройдёт тест «тап внутри выреза кликает целевую кнопку, тап снаружи — нет». Тест обязателен на обеих формах выреза.
  - Для Circle углы описанного квадрата тоже пропускают касания — допустимо (это границы той же иконки-кнопки 48dp).
  - Внутри выреза оверлей ничего не рисует поверх цели, кроме кольца-обводки (обводка не должна перехватывать касания).
  - Оверлей не читает `DashboardState`, не знает про панели и тексты; карточка — слот `@Composable`. Никаких `:feature:*` зависимостей (AGENTS.md: feature → core, не наоборот).
  - Кнопки «Пропустить всё» / «Далее» — минимум 48dp по высоте и ширине; при `fontScale` 1.5/2.0 карточка не обрезает текст (прокручивается или уменьшает отступы) — a11y-гейт (G20).
  - Оверлей — один семантический контейнер: `paneTitle`/`liveRegion` объявляют смену шага, фокус TalkBack не «утекает» на элементы под затемнением (кроме элемента в вырезе).
  - Все размеры/цвета/формы — только через токены темы; строк в этом модуле нет (лейблы и тексты приходят параметрами).
  - Visual device gate (AGENTS.md): вырез, кольцо и переезд между целями подтвердить на Pixel_5 API 34; JVM-тестов недостаточно.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Оверлей-подсветка

  Scenario: Круглый вырез вокруг кнопки
    Given на экране есть кнопка, зарегистрированная как цель подсветки
    When оверлей показывает вырез формы «круг» для этой цели
    Then вся остальная область затемнена, а кнопка видна без затемнения
    And вокруг кнопки нарисована обводка

  Scenario: Тап внутри выреза доходит до элемента, тап снаружи — нет
    Given оверлей подсвечивает кнопку
    When пользователь нажимает на подсвеченную кнопку
    Then срабатывает действие кнопки
    When пользователь нажимает на затемнённую область вне выреза
    Then ничего под оверлеем не срабатывает

  Scenario: Кнопки управления
    Given оверлей показан
    Then слева внизу есть «Пропустить всё», справа внизу — «Далее»
    And обе кнопки не меньше 48dp

  Scenario: Цель ещё не измерена
    Given ключ цели не зарегистрирован
    When показывается оверлей
    Then вырез не рисуется, затемнение остаётся, приложение не падает
```

## Gap / context
В проекте нет ни одной утилиты подсветки/coach-mark (G17). Тур поверх дашборда требует вырез с
проходом касаний внутри него — нетривиальный кусок Compose, который надо вынести в переиспользуемый
компонент и подтвердить спайком до того, как на нём строить тур.

## Implementation links
- commit: d13cbda4, 725fd8f8, 348a7977
- files: see commits; removed onboarding files archived under archive/onboarding-legacy/
