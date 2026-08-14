# Модуль :feature:support — экран поддержки и девятый пункт drawer
Epic: support-hub-tip
Order: 07 of 08
Status: done
Depends-on: 03, 04, 06
Date: 2026-08-12

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Появляется модуль `:feature:support` с экраном, собранным сверху вниз в целевом порядке: короткий текст «почему это нужно» → слот рекламы → слот Plus → блок кофе с двумя кнопками → бейдж Supporter и благодарность со счётчиком. Слоты рекламы и Plus — пустые composable-параметры с дефолтом `{}`: порядок и точки вставки зафиксированы, но в этом релизе пользователь не видит ни карточек, ни неактивных кнопок. `SupportViewModel` покрывает все обязательные состояния: биллинг выключен в сборке, недоступен на устройстве, недоступен в регионе, отмена покупки, ошибка сети, покупка в pending, повторное восстановление. В правый drawer добавляется девятый пункт с иконкой сердца — RU «Поддержать проект», EN «Support the app», — ведущий на новый маршрут.
LAYERS: presentation
CHANGED_HINT:
  - settings.gradle.kts:34-52 — `include(":feature:support")` (G10, G12)
  - feature/support/build.gradle.kts — по образцу `feature/settings/build.gradle.kts:1-32`: плагин `mymoney.android.feature`, namespace `com.kshavrin.mymoney.feature.support`, зависимости на `:core:ui`, `:core:designsystem`, `:core:domain`, `:core:common` (G11)
  - app/build.gradle.kts:408-415 — `implementation(project(":feature:support"))` (G12)
  - feature/support/src/main/java/.../support/SupportViewModel.kt, SupportState.kt, SupportScreen.kt, SupportRoute.kt — `@HiltViewModel`, `StateFlow<SupportState>` с иммутабельным UiState + `SharedFlow(replay = 0)` действий (`AGENTS.md`, раздел «Architecture pattern», TDD §2.3 строки 181-228); форма Route/Screen/ViewModel зеркалит `:feature:settings` (G11)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/navigation/Destinations.kt:6-145 — `@Serializable data object Support` (G7)
  - app/src/main/java/com/kshavrin/mymoney/navigation/MyMoneyNavHost.kt:59-104 — регистрация маршрута и ветка `NavigateSupport` в `when` (G8)
  - app/src/test/java/com/kshavrin/mymoney/navigation/DestinationsTest.kt:98-133 — добавить `Support` в захардкоженный список из 28 destination'ов (T8)
  - feature/dashboard/.../components/RightDrawerContent.kt:40-102 — девятый `RightDrawerItem` после About, `Icons.Outlined.FavoriteBorder` (G1, G2, G3)
  - feature/dashboard/.../components/RightDrawerContent.kt:141-148 — `RIGHT_DRAWER_SUPPORT_TAG` (G14)
  - feature/dashboard/.../DashboardState.kt:260-297 — `SupportClicked` в `DashboardEvent` (G4)
  - feature/dashboard/.../DashboardAction.kt:31-55 — `NavigateSupport` в `DashboardAction` (G5)
  - feature/dashboard/.../DashboardViewModel.kt:1108-1132 — ветка `SupportClicked` → `closeDrawers()` + `emit(NavigateSupport)` (G6)
  - feature/dashboard/src/main/res/values/strings.xml:77-84 и values-ru/strings.xml — ключ `right_drawer_support` (G9)
  - feature/support/src/main/res/values/strings.xml + values-ru/strings.xml — строки экрана в обеих локалях; строки живут в модуле-владельце, как `right_drawer_*` в dashboard (G9)
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - **`DestinationsTest.kt:98-133` перечисляет 28 маршрутов вручную и требует регистрации каждого в `MyMoneyNavHost`** (T8). Новый `Destinations.Support` красит этот тест, пока список не обновлён в том же коммите.
  - Модуль **не добавлять** в `ConnectedModulesCiContractTest.kt:22-28` (T7) — инструментальных тестов у него нет; Compose-тесты идут через Robolectric в JVM-прогоне.
  - Если `SupportViewModel` читает `savedStateHandle.toRoute<Destinations.Support>()`, его unit-тест обязан быть `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk=[34])` — иначе «not mocked» на `Bundle` (T4). У маршрута без аргументов чтения route можно избежать вовсе — предпочесть этот путь.
  - **Недоступность биллинга заменяет только блок кофе** (D6). Вводный текст, бейдж и благодарность остаются на месте; текст объяснения — спокойный, без слова «ошибка» и без кнопки «Повторить» (ADR-0010:143-144). Для пользователей из России это постоянное состояние экрана, а не редкий край.
  - Состояния `DisabledInBuild` и `UnavailableInRegion` **различимы в UI-тексте**: первое видит только разработчик в debug, второе — реальный пользователь.
  - Слоты рекламы и Plus — `@Composable () -> Unit = {}` (D5). Никаких строк «скоро появится» и никаких неактивных кнопок в релизе: тексты и ресурсы этих блоков принадлежат эпикам `support-rewarded-ads` и `plus-subscription-gating`.
  - Цены на кнопках берутся из `SupportProduct.formattedPrice` (форматирование Play по локали пользователя), **не** из строкового ресурса «€1»/«€5» (O2).
  - a11y: цель нажатия ≥48dp. У `RightDrawerItem` это уже выполнено конструкцией (иконка 44dp в `Box` 56dp, `mergeDescendants = true`, `RightDrawerContent.kt:105-139`) — не ломать; кнопки покупки на экране обеспечить самостоятельно.
  - Все три события аналитики (открытие раздела, начало и завершение покупки) логируются через `AnalyticsGateway` из SPEC-06, а не напрямую через Firebase.
  - Тесты — только фейки `FakeBillingGateway` / `FakeSupporterRepository` / `FakeAnalyticsGateway`, без MockK (T2).
  - Roborazzi-эталон dashboard снимается с закрытыми drawer'ами (`DashboardScreenshotTest.kt:37`), поэтому девятый пункт его не ломает (T6). Новый эталон под экран поддержки в этом SPEC не заводится.
  - Экран About не трогать вообще.
  - `settings.gradle.kts` и `app/build.gradle.kts` делятся со SPEC-03 и SPEC-06 — этот идёт последним из трёх.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Раздел поддержки проекта

  Scenario: Вход из правого drawer
    Given открытый правый drawer
    When пользователь нажимает девятый пункт «Поддержать проект»
    Then drawer закрывается
    And открывается экран поддержки
    And записано событие открытия раздела

  Scenario: Порядок блоков зафиксирован
    Given открытый экран поддержки
    When пользователь смотрит на экран
    Then вводный текст расположен выше блока кофе
    And блок бейджа и благодарности расположен ниже блока кофе
    And блоки рекламы и Plus не отображаются

  Scenario: Успешная покупка выдаёт бейдж
    Given доступный биллинг и пользователя без бейджа
    When пользователь покупает кофе за меньшую сумму
    Then показывается бейдж Supporter
    And показывается благодарность со счётчиком «1»
    And записано событие завершения покупки

  Scenario: Повторная покупка увеличивает счётчик
    Given пользователя с бейджем и счётчиком 1
    When пользователь покупает кофе ещё раз
    Then счётчик показывает «2»
    And кнопки покупки остаются доступными

  Scenario: Биллинг недоступен в регионе
    Given биллинг, недоступный в регионе пользователя
    When открывается экран поддержки
    Then вместо кнопок покупки показано объяснение о недоступности покупок в регионе
    And вводный текст остаётся видимым
    And кнопка повтора не показывается

  Scenario: Биллинг недоступен на устройстве
    Given устройство без поддержки Play Billing
    When открывается экран поддержки
    Then показано объяснение о недоступности покупок на устройстве
    And оно отличается от объяснения про регион

  Scenario: Биллинг выключен в сборке
    Given debug-сборку без -Pbilling.enabled=true
    When открывается экран поддержки
    Then блок кофе показывает, что покупки выключены в этой сборке

  Scenario: Пользователь отменяет покупку
    Given открытый диалог оплаты
    When пользователь его закрывает
    Then экран возвращается в исходное состояние
    And сообщение об ошибке не показывается
    And счётчик не меняется

  Scenario: Ошибка сети во время покупки
    Given недоступную сеть
    When пользователь запускает покупку
    Then показывается сообщение об ошибке сети
    And предлагается повторить попытку
    And бейдж не выдаётся

  Scenario: Покупка в состоянии pending
    Given покупку, ожидающую подтверждения
    When экран отображает её состояние
    Then показано, что покупка обрабатывается
    And бейдж не выдаётся
    And счётчик не меняется

  Scenario: Повторное восстановление не задваивает счётчик
    Given авторизованного пользователя с бейджем и счётчиком 3
    When восстановление состояния выполняется ещё раз
    Then счётчик остаётся равным 3
```

## Gap / context
`:feature:support` описан в ADR-0010 D7, но на диске отсутствует (G10), а правый drawer сейчас
содержит ровно восемь пунктов (G1). Это единственный SPEC эпика, который пользователь видит
глазами: всё, что было построено раньше — контракт, реализация Play, состояние, сервер,
аналитика — здесь впервые собирается в экран.

## Implementation links
- commits: `1234da3e`, `1f73545d`, `f6635d8d`, `1fe3fcca`, `4ab874fb`, `e9ded25e`, `517c8ad8`, `2b787f60`, `6e69bb3d`, `ac098924`, `d8e65eb2`, `0671cbe2`, `98bea1c1`, `a2ca9229`
- files: `:feature:support`, dashboard Support drawer/navigation, `Destinations.Support`, billing/reconciliation coordinator wiring, Supporter persistence bridge, M3 support tokens, fake billing terminal-flow contracts, and unit/Compose/Android UI tests.
- verification: reviewer PASS; semantic review PASS; independent critic PASS after terminal-flow fake repair; verifier PASS; Pixel 5/API 34 boot gate confirmed; feature-scoped Support tests PASS. Repo-wide runner was run twice (the second found two Support test failures subsequently repaired); no third full runner was run per runner contract.
