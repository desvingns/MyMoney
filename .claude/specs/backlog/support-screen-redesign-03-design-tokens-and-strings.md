# Токены темы и строки нового экрана поддержки
Epic: support-screen-redesign
Order: 03 of 07
Status: backlog
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: token_group=color,shape,spacing,typography; locale=en,ru
Risk-signals: design-system, i18n

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Фундамент под новую вёрстку: в `:core:ui` theme добавляются недостающие семантические
токены (цвета-алиасы, формы, отступы, типографика), а в `feature/support` — новые строки EN/RU
для заголовка с акцентным словом, названий кофе без цены, CTA, текстов благодарности, подписей
трёх счётчиков и contentDescription шести иллюстраций из SPEC-01. Ни одного composable в этом
SPEC не переписывается — он только раскладывает токены и ресурсы, чтобы SPEC-05/06/07 нигде не
хардкодили dp, цвета и текст.
LAYERS: presentation
CHANGED_HINT:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt:497-538 — новые extension-property рядом с существующими `support*`: `supportPanelContainer` (= surface), `supportPanelOutline` (= primary с alpha 0.28f), `supportPanelDivider` (= primary с alpha 0.22f), `supportHeadlineAccent` (= primary), `supportPriceValue` (= primary), `supportBackLabel` (= onSurfaceVariant), `supporterChipContainer` (= surfaceVariant), `supporterChipOutline` (= primary с alpha 0.45f), `supporterChipContent` (= primary), `supportCounterValue` (= onSurface), `supportCounterLabel` (= onSurfaceVariant) — все через существующие роли `ColorScheme`, сырых `Color(0x…)` не вводить (G18, G19, D16)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Shape.kt:49-53 — `supportPanel` = RoundedCornerShape(20.dp), `supportPanelIllustration` = RoundedCornerShape(12.dp), `supportHeroIllustration` = RoundedCornerShape(18.dp), `supportPrimaryAction` = RoundedCornerShape(24.dp) (полная пилюля при высоте 48dp), `supporterChip` = RoundedCornerShape(13.dp); существующие `supportCard`/`supporterBadge` не удалять — они ещё используются (G21)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt:207-211 — новые токены: `supportTopBarHeight` 56.dp, `supportBackTouchTarget` 48.dp, `supportHeroSize` 196.dp, `supportPanelGap` 14.dp, `supportPanelPadding` 16.dp, `supportPanelColumnGap` 10.dp, `supportCoffeeIllustrationWidthSmall` 66.dp, `supportCoffeeIllustrationHeight` 82.dp, `supportPanelIconSize` 52.dp, `supportAvatarSize` 84.dp, `supportSubtitleMaxWidth` 296.dp, `supporterChipHeight` 26.dp, `supportHeadlineUnderlineThickness` 3.dp (G22)
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt:346-371 — новые стили: `supportHeadline` (33sp, ExtraBold, lineHeight 36sp, letterSpacing (-0.8).sp), `supportSubtitle` (15sp, Normal, lineHeight 22sp), `supportPanelTitle` (17sp, Bold, letterSpacing (-0.25).sp), `supportPanelSubtitle` (13.5sp, Normal, lineHeight 20sp), `supportProductName` (15sp, SemiBold), `supportProductPrice` (27sp, ExtraBold, letterSpacing (-0.5).sp), `supportActionLabel` (15sp, Bold), `supportBackLabel` (15sp, SemiBold), `supporterChipLabel` (12sp, Bold, letterSpacing 0.5.sp), `supportCounterValue` (17sp, Bold), `supportCounterLabel` (12sp, Normal) — все производные от существующих ролей через `.copy()`, `FontFamily.Default` (G20, D15)
  - feature/support/src/main/res/values/strings.xml:2-18 — новые ключи: `support_back_label`, `support_headline_lead`, `support_headline_accent`, `support_coffee_small_name`, `support_coffee_large_name`, `support_coffee_generic_name`, `support_purchase_action`, `support_gratitude_title_supporter`, `support_gratitude_body_supporter`, `support_gratitude_title_prospect`, `support_gratitude_body_prospect`, `support_counter_ads_label`, `support_counter_coffee_small_label`, `support_counter_coffee_large_label`, `support_image_hero_description`, `support_image_coffee_small_description`, `support_image_coffee_large_description`, `support_image_ads_description`, `support_image_plus_description`, `support_image_avatar_description` (G23)
  - feature/support/src/main/res/values-ru/strings.xml — те же ключи, русские значения: «Назад», «Помогите MyMoney», «расти», «Маленький кофе», «Большой кофе», «Кофе», «Поддержать», «Спасибо за поддержку MyMoney», «Каждая чашка кофе помогает приложению оставаться независимым.», «MyMoney работает на кофе», «Приложение делается независимо — чашка кофе помогает ему расти.», «Реклама», «Мл. кофе», «Бл. кофе» + описания иллюстраций (G23)
  - feature/support/src/test/java/com/kshavrin/mymoney/feature/support/PaywallStringsTest.kt:11-30 — добавить тест паритета EN/RU для префикса `support_` (сейчас покрыты только `paywall_` и `support_ads_`, G37)
TEST_TYPES: unit
CONSTRAINTS:
  - Все цвета — только через роли существующей `NeonColors` (`primary`, `surface`, `surfaceVariant`,
    `onSurface`, `onSurfaceVariant`, `outlineVariant`) и их alpha-производные. Палитра макета
    полностью совпадает с текущей темой (G18) — новых hex-констант в `Color.kt` быть НЕ должно.
  - `supportActionMinHeight` = 48.dp уже существует (G22) и остаётся минимальной высотой кнопок и
    цели касания: макетные 44px не переносить (H4).
  - Существующие токены `support*`/`supporter*` (G19, G20, G21, G22) не переименовывать и не
    удалять — на них ещё ссылается текущий код и тесты вплоть до SPEC-07.
  - Строки `support_coffee_small/large/price` («Small coffee · %1$s») и `support_coffee_title`
    в этом SPEC НЕ удалять: их продолжает использовать нынешний `SupportScreen.kt` (G8, G10), и
    удаление сломает сборку/тесты до SPEC-05. Их вывод из употребления — в SPEC-05.
  - Строку `support_ads_total_watched` тоже не удалять здесь: её выводит `TotalAdsWatchedBadge`
    (G11), который уходит в SPEC-05.
  - Строки `support_title` и `support_back` сохраняются — заголовок экрана остаётся (D5).
  - Тексты EN/RU должны укладываться в 390dp ширину без обрезки: подписи счётчиков — короткие
    («Реклама» / «Мл. кофе» / «Бл. кофе»), длинные варианты не использовать.
  - `13.5sp` — допустимое дробное значение в Compose; не округлять до 14sp, иначе плашки разойдутся
    с макетом по вертикали.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Токены и строки нового экрана поддержки

  Scenario: Токены объявлены в теме, а не в экране
    Given модуль core:ui собран
    Then новые цвета, формы, отступы и стили текста доступны как расширения темы
    And ни один из них не содержит собственного шестнадцатеричного цвета

  Scenario: Паритет локалей соблюдён
    Given файлы строк EN и RU
    When сверяются ключи с префиксом support_
    Then множества ключей совпадают

  Scenario: Существующий экран продолжает собираться
    Given токены и строки добавлены
    Then текущая вёрстка экрана поддержки компилируется без изменений
    And существующие тесты экрана остаются зелёными

  Scenario: Минимальная цель касания сохранена
    Given токены отступов
    Then токен высоты кнопки поддержки не меньше сорока восьми точек
```

## Gap / context
Макет вводит размеры и начертания, которых в теме нет (33sp ExtraBold заголовок, 27sp цена,
плашка 20dp, чип 13dp, аватар 84dp), и тексты, которых нет в ресурсах (акцентное слово заголовка,
названия кофе отдельно от цены, два варианта благодарности, подписи счётчиков). Без этого SPEC
любой из UI-SPEC'ов был бы вынужден хардкодить dp и строки, что запрещено конвенциями проекта
(G19, G23). Заблокировано D15 (шрифт Default) и D16 (цвета только через алиасы).

## Implementation links
- commit: —
- files: —
