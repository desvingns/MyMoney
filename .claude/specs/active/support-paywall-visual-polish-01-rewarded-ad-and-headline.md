# Объединение строк рекламного блока, удаление текстового счётчика, контрастный цвет заголовков

Epic: support-paywall-visual-polish
Order: 01 of 02
Status: active
Depends-on: —
Date: 2026-08-23
Risk-signals: visual
Acceptance-matrix: surface=ads-headline-merge,progress-counter-text-removal,headline-font-contrast

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: В рекламном блоке Support-экрана объединить заголовок и правило в одну жирную строку,
убрать текстовый счётчик "Watched N of 5" (оставить только графический ряд сегментов), и задать
явный контрастный цвет шрифта заголовкам «Support the app» и «Help MyMoney» на themed-фоне.
LAYERS: [presentation]
CHANGED_HINT:
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:154-161` —
    в `RewardedAdContent` внутри `Column` (строки 151-162) удалить `Text`, рендерящий
    `stringResource(R.string.support_ads_title)` (строки 154-157); у оставшегося
    `Text(stringResource(R.string.support_ads_rule, required))` заменить
    `style = MaterialTheme.typography.supportPanelSubtitle` на
    `style = MaterialTheme.typography.supportPanelTitle` (G1).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreen.kt:191-194` —
    в `RewardProgressRow` удалить видимый `Text(text = progressText, ...)`; `Column`-обёртку с
    `Modifier.clearAndSetSemantics { contentDescription = progressText }` (строка 188) и сам ряд
    сегментов (`Row` со `repeat(DEFAULT_REQUIRED_VIEWS)`, строки 195+) не менять — accessibility
    описание прогресса должно остаться прежним (G2).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:135-138` —
    у `Text(stringResource(R.string.support_title))` в `SupportBackRow` добавить
    `color = MaterialTheme.colorScheme.onBackground` (G3).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:151-155` —
    у `Text(stringResource(R.string.support_headline_lead))` в `SupportHeadline` добавить
    `color = MaterialTheme.colorScheme.onBackground` (G3).
  - `feature/support/src/main/res/values/strings.xml` и `feature/support/src/main/res/values-ru/strings.xml` —
    удалить строку `support_ads_title`, только если после правки выше она больше нигде не
    используется в main-исходниках (тесты не считаются — их обновит Tester) (G1, G5).
  - `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/rewardedad/RewardedAdScreenContentTest.kt:62` —
    заменить `onNodeWithText(string(R.string.support_ads_title)).assertIsDisplayed()` на проверку
    отсутствия этого текста (`assertDoesNotExist()`); все существующие
    `onNodeWithContentDescription(string(R.string.support_ads_progress, ...))`-проверки (a11y)
    оставить без изменений — они не зависят от видимого `Text` (G5).
TEST_TYPES: [compose-ui, visual-device]
CONSTRAINTS:
  - Не менять `support_ads_rule`/`support_ads_progress` строковые ресурсы, кнопку "Watch ads",
    статус-строку под кнопкой, аватар/gratitude-карточку, карточку покупки кофе, back-row
    (кроме цвета `support_title`), навигацию и любую billing/rewarded-ad/Paywall-логику — только
    четыре точечные правки выше (G1-G3).
  - Цвет обязан быть существующим M3-токеном `MaterialTheme.colorScheme.onBackground` — не
    вводить новый цвет/hex и не заводить отдельный именованный `ColorScheme`-токен под это
    (соответствует конвенции `OnboardingScreen.kt:158,165`) (G3).
  - Accessibility: `contentDescription` прогресса рекламного блока обязан остаться доступным для
    screen reader — только визуальный `Text` убирается, семантика на `Column` не трогается (G2).
  - Не удалять файлы физически (project-wide archive-only policy); удаление неиспользуемой
    строки `support_ads_title` из `strings.xml` — это правка существующего файла, не удаление
    файла (G5).
  - Это visual-surface изменение: пройти Pixel 5/API 34 device gate и визуально сверить
    объединённую строку, отсутствие текстового счётчика и контраст заголовков на устройстве до
    подтверждения верификации (G6, AGENTS.md "Visual-change device gate").
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Полировка рекламного блока и заголовков Support

  Scenario: Рекламный блок показывает одну жирную строку вместо двух
    Given открыт экран поддержки с доступным рекламным блоком
    When экран отрисован
    Then текст "Watch ads for temporary Plus" не отображается
    And текст "Watch 5 ads to unlock 24 hours of Plus." отображается жирным (стиль supportPanelTitle)

  Scenario: Текстовый счётчик просмотров отсутствует, графический остаётся
    Given открыт экран поддержки, просмотрено 2 из 5 рекламных роликов
    When экран отрисован
    Then текст "Watched 2 of 5" не отображается
    And узел с contentDescription "Watched 2 of 5" присутствует для screen reader
    And 5 сегментов графического счётчика отображаются, 2 из них — активные

  Scenario: Заголовки экрана контрастны фону
    Given открыт экран поддержки
    When экран отрисован
    Then цвет текста "Support the app" равен MaterialTheme.colorScheme.onBackground
    And цвет текста "Help MyMoney" равен MaterialTheme.colorScheme.onBackground
```

## Gap / context
После добавления фона `MaterialTheme.colorScheme.background` на корневой `Column`
(SPEC `support-screen-visual-polish-01`, коммит `f976f743`) заголовок «Support the app»
(back-row) и «Help MyMoney» (headline) остались без явного цвета текста и наследуют
произвольный `LocalContentColor` — визуально сливаются с новым фоном. Рекламный блок и текстовый
счётчик — отдельные точечные правки по прямому запросу пользователя, не связанные с фоном.
