# Финальные растровые ассеты экрана поддержки
Epic: support-screen-artwork-counters
Order: 01 of 02
Status: done
Depends-on: —
Date: 2026-08-23
Risk-signals: visual, assets
Acceptance-matrix: asset=avatar,plus,coffee_small,coffee_large,ads; validation=mapping,alpha,visual_treatment

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Заменить пять design-system bitmap-ресурсов экрана поддержки утверждёнными растровыми изображениями: аватаром с жестом сердечка, зерном с короной для Plus, takeaway-чашкой для большого кофе, espresso для маленького кофе и иллюстрацией просмотра рекламы; у takeaway-чашки сохранить cappuccino и надпись `Thanks`, но убрать широкое чрезмерное свечение.
LAYERS: [presentation]
CHANGED_HINT:
  - `core/designsystem/src/main/res/drawable-nodpi/support_neon_avatar.png` — заменить ресурс staged-изображением `C:\Users\Admin\AppSpecs\support-screen-artwork-counters\pipeline\assets\support_neon_avatar.png`; сохранить прозрачный alpha и лицо/одежду из утверждённого результата (G11, G12).
  - `core/designsystem/src/main/res/drawable-nodpi/support_neon_plus.png` — заменить звездный placeholder staged-изображением зерна с короной `C:\Users\Admin\AppSpecs\support-screen-artwork-counters\pipeline\assets\support_neon_plus.png` (G11, G12).
  - `core/designsystem/src/main/res/drawable-nodpi/support_neon_coffee_large.png` — взять финальный raster-вариант `C:\Users\Admin\AppSpecs\support-screen-artwork-counters\pipeline\assets\support_neon_coffee_large_soft.png`, созданный из `source-large-coffee-original.png`: оставить ту же takeaway-чашку, cappuccino и `Thanks`, уменьшить cyan/red halo и внешний bloom (G11, G12; O1).
  - `core/designsystem/src/main/res/drawable-nodpi/support_neon_coffee_small.png` — заменить ресурс staged-изображением espresso `C:\Users\Admin\AppSpecs\support-screen-artwork-counters\pipeline\assets\support_neon_coffee_small.png` (G11, G12).
  - `core/designsystem/src/main/res/drawable-nodpi/support_neon_ads.png` — заменить ресурс staged-изображением телефона/просмотра рекламы `C:\Users\Admin\AppSpecs\support-screen-artwork-counters\pipeline\assets\support_neon_ads.png` (G11, G12).
TEST_TYPES: [asset-contract, compose-ui, visual-device]
CONSTRAINTS:
  - Сохранить существующие имена ресурсов и их consumers; `SupportScreen`, `RewardedAdScreen` и `PaywallScreen` не должны получать новые resource IDs (G12).
  - Все итоговые PNG должны быть реальными растровыми файлами, без встроенного checkerboard-фона; прозрачность сохраняется там, где она есть у исходного ассета.
  - Не создавать replacement для `support_neon_hero_cup.png`: hero-ресурс перестанет использоваться в SPEC-02, но файлы не удаляются по правилу проекта.
  - Не превращать изображения в SVG/vector placeholders; проверить визуально на тёмном фоне приложения. Большой кофе не должен выбиваться широким неоновым ореолом (D1, H3, O1).
  - Asset contract должен проверять наличие, декодируемость, mapping и отсутствие checkerboard-артефакта; визуальную пригодность подтвердить на Pixel 5/API 34 (G11, G15).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Финальные иллюстрации Support

  Scenario: Пять ресурсов соответствуют назначению
    Given design-system resources Support загружены
    Then avatar показывает утверждённого пользователя с жестом сердечка
    And Plus показывает зерно с короной, а не звезду
    And small coffee показывает espresso
    And large coffee показывает takeaway cappuccino
    And ads показывает иллюстрацию просмотра рекламы

  Scenario: Большая чашка не выбивается свечением
    Given отображается large coffee asset
    Then на стакане сохранены cappuccino и надпись "Thanks"
    And вокруг стакана нет широкого cyan/red halo
    And сам стакан и его читаемость сохранены

  Scenario: Прозрачный ассет не содержит подложку
    Given avatar asset декодирован как PNG
    Then его прозрачные области остаются alpha-прозрачными
    And checkerboard-паттерн не является частью bitmap
```

## Gap / context
Сейчас шесть `support_neon_*` файлов были placeholders/предыдущими вариантами, а пользователь утвердил новый набор ассетов и попросил смягчить glow большой takeaway-чашки. Имена ресурсов и места потребления уже зафиксированы в design-system/feature-support.

## Implementation links
- commit: b3e81e5f, 22b02f19
- files: `core/designsystem/src/main/res/drawable-nodpi/support_neon_avatar.png`, `core/designsystem/src/main/res/drawable-nodpi/support_neon_plus.png`, `core/designsystem/src/main/res/drawable-nodpi/support_neon_coffee_large.png`, `core/designsystem/src/main/res/drawable-nodpi/support_neon_coffee_small.png`, `core/designsystem/src/main/res/drawable-nodpi/support_neon_ads.png`, `feature/support/src/test/java/com/kshavrin/mymoney/feature/support/SupportArtworkAssetContractTest.kt`
