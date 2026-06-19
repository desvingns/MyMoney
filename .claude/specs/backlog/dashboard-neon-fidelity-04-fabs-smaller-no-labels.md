# FAB −10% и без текстовых подписей
Epic: dashboard-neon-fidelity
Order: 04 of 04
Status: backlog
Depends-on: —
Date: 2026-06-19

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Уменьшить круглые кнопки −/+ на 10% (диаметр 104dp → ~94dp; иконка, обводка и внутр. паддинги — пропорционально) и убрать текстовые подписи «РАСХОД»/«ДОХОД» под кнопками. Остаются только два круга с иконками −/+ (как в мокапе). Доступность: contentDescription «Расход»/«Доход» сохранить для screen reader.
LAYERS: presentation
CHANGED_HINT:
  - core/ui/.../theme/Spacing.kt (G15) — `dashboardFabSize` 104→94dp (−10%); пропорц. уменьшить иконку и `dashboardFabOutlineWidth`; убрать использование `dashboardFabLabel*`/`dashboardFabLabelTopPadding`
  - feature/dashboard/.../components/TwoFabLayout.kt (G15, :30-120) — убрать Column-обёртку с `Text`-подписью; Row из двух круглых FAB без текста; сохранить иконки `Icons.Filled.Remove`/`Add` и события клика; задать `contentDescription` на иконки для доступности
  - feature/dashboard/.../DashboardScreen.kt (G2) — если высота FAB-зоны зависела от наличия подписей — поправить отступы
  - app/src/androidTest/.../DashboardContentUiTest.kt (G16, :821-851) — обновить `assertWidth/HeightIsEqualTo` 104→94dp; убрать проверки видимого текста «РАСХОД»/«ДОХОД»; при необходимости проверять по contentDescription
TEST_TYPES: compose-ui instrumented
CONSTRAINTS:
  - G16: `DashboardContentUiTest.kt:821-851` хардкодит `assertWidth/HeightIsEqualTo(104.dp)` — обновить В ТОЙ ЖЕ правке, иначе тест падает (G18: runner компилирует/гоняет androidTest).
  - Удаляются ВИДИМЫЕ подписи, но contentDescription «Расход»/«Доход» сохранить (a11y) — проверить, нет ли других тестов/строк, завязанных на видимый текст FAB-подписи (assumption).
  - События клика FAB (−расход / +доход → навигация ввода операции) не менять (G6).
  - Same-file clash: `Spacing.kt` + `DashboardScreen.kt` + `DashboardContentUiTest.kt` правятся также в 01/02/03 — этот SPEC идёт ПОСЛЕДНИМ.
  - ktlintFormat перед коммитом; прогон на устройстве `./gradlew :app:connectedDebugAndroidTest` (G18).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Кнопки −/+ дашборда

  Scenario: FAB уменьшены на 10%
    Given открыт главный экран S01
    Then диаметр кнопок −/+ ≈94dp (на 10% меньше прежних 104dp)

  Scenario: Подписи убраны
    Given открыт главный экран
    Then под кнопками нет текста «РАСХОД» и «ДОХОД»
    And видны только два круга с иконками − и +

  Scenario: Доступность сохранена
    Then у кнопок есть contentDescription «Расход» и «Доход» для screen reader

  Scenario: Клик по-прежнему открывает ввод операции
    When нажать кнопку «+»
    Then открывается экран ввода дохода (поведение как прежде)
```

## Gap / context
Сейчас FAB крупные (104dp) с текстовыми подписями «РАСХОД»/«ДОХОД» под ними. Мокап показывает два
меньших круга только с иконками −/+, без подписей. Заказчик просит −10% и убрать подписи.

## Implementation links
- commit: —
- files:  —
