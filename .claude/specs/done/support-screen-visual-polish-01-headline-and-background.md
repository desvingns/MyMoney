# Фон, центрирование заголовка и удаление подзаголовка на экране поддержки

Epic: support-screen-visual-polish
Order: 01 of 01
Status: done
Depends-on: —
Date: 2026-08-23
Implementation links: commit f976f743 (SupportScreen.kt background+headline+subtitle removal); tests added in SupportScreenContentTest.kt (uncommitted at close-out)
Risk-signals: visual
Acceptance-matrix: surface=background,headline-centering,subtitle-removal

## SPEC
=== SPEC ===
TASK: bugfix
PLATFORM: android
WHAT: Задать корневому Column в SupportContent тот же фон-токен, что и на остальных экранах
приложения, реально отцентрировать блок заголовка «Help MyMoney / grow» по ширине экрана и убрать
подзаголовок «MyMoney is made independently. A coffee helps keep it growing.» вместе с его строковыми
ресурсами.
LAYERS: [presentation]
CHANGED_HINT:
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:81-86` —
    добавить `.background(MaterialTheme.colorScheme.background)` на корневой `Column` в
    `SupportContent`, сразу после `.fillMaxSize()`, до `.statusBarsPadding()`; не менять остальную
    структуру Column (G1, G3).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt:143-182` —
    в `SupportHeadline()` добавить `Modifier.fillMaxWidth()` на внешний `Column`, чтобы существующие
    `TextAlign.Center`/`horizontalAlignment = Alignment.CenterHorizontally` центрировали блок
    относительно всей ширины экрана, а не относительно себя; удалить `Text`, рендерящий
    `stringResource(R.string.support_description)`, и связанный с ним `Modifier.widthIn(max = Spacing.supportSubtitleMaxWidth)`
    (G4, G6).
  - `feature/support/src/main/java/com/kshavrin/mymoney/feature/support/SupportScreen.kt` — если после
    удаления подзаголовка `Spacing.supportSubtitleMaxWidth` и/или импорт `supportSubtitle`
    typography-токена остаются нигде не используемыми в этом файле, убрать неиспользуемый import;
    сам токен в `core:designsystem`/theme не трогать (может использоваться где-то ещё) (G6).
  - `feature/support/src/main/res/values/strings.xml:4` и
    `feature/support/src/main/res/values-ru/strings.xml:4` — удалить строку `support_description` из
    обоих файлов локализации, только если после правки выше она больше нигде не используется (G6, G8).
  - `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportScreenContentTest.kt` —
    обновить существующие проверки заголовка/layout: убрать ассерты на текст подзаголовка,
    при необходимости поправить проверки структуры/семантики под новый layout без подзаголовка (G7).
TEST_TYPES: [compose-ui, visual-device]
CONSTRAINTS:
  - Не менять текст заголовка (`support_headline_lead`/`support_headline_accent`), back-row,
    карточку покупки кофе, карточку благодарности, ad/plus слоты, навигацию и любую
    billing/rewarded-ad/Paywall-логику — только фон, центрирование заголовка и удаление подзаголовка (G1, G4, G6).
  - Фон обязан использовать существующий M3-токен `MaterialTheme.colorScheme.background` — не
    вводить новый цвет/градиент и не хардкодить hex (G3).
  - Не удалять файлы физически (project-wide archive-only policy); допустимо удалить неиспользуемую
    строку `support_description` из `strings.xml` как правку существующего файла (G8).
  - Это visual-surface изменение: пройти Pixel 5/API 34 device gate и визуально сверить фон и
    центрирование заголовка на устройстве до подтверждения верификации (G9, AGENTS.md
    "Visual-change device gate").
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Фон и заголовок экрана поддержки

  Scenario: Фон экрана поддержки соответствует остальному приложению
    Given открыт экран поддержки
    When экран отрисован
    Then корневой фон экрана равен MaterialTheme.colorScheme.background
    And фон не является белым/дефолтным фоном окна

  Scenario: Заголовок центрирован по ширине экрана
    Given открыт экран поддержки
    When экран отрисован
    Then блок заголовка «Help MyMoney / grow» растянут на всю ширину экрана
    And текст заголовка визуально центрирован относительно экрана, а не только внутри своего блока

  Scenario: Подзаголовок отсутствует
    Given открыт экран поддержки
    When экран отрисован
    Then текст "MyMoney is made independently. A coffee helps keep it growing." не отображается
    And остальные элементы экрана (back-row, покупка кофе, карточка благодарности, ad/plus слоты) не изменились
```

## Gap / context
Экран поддержки — единственный full-screen экран в приложении, у которого корневой контейнер не
задаёт `MaterialTheme.colorScheme.background`; остальные экраны (например, Onboarding) уже следуют
этой конвенции. Заголовок технически уже "центрирован" (`TextAlign.Center` + `CenterHorizontally`),
но контейнер вокруг него не растянут на всю ширину, из-за чего центрирование работает только внутри
самого узкого блока. Подзаголовок убирается по прямому запросу пользователя — правка чисто
presentation-слоя, без вовлечения domain/data.
