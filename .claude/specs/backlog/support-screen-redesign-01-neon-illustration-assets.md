# Неон-иллюстрации экрана поддержки (PNG-ассеты)
Epic: support-screen-redesign
Order: 01 of 07
Status: backlog
Depends-on: —
Date: 2026-08-20
Acceptance-matrix: asset=hero,coffee_small,coffee_large,ads,plus,avatar
Risk-signals: visual, assets

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В `core/designsystem` добавляются шесть растровых иллюстраций экрана «Поддержать проект» в
едином неон-line-art стиле — по прецеденту существующих `category_neon_*.png` (G25): hero-чашка,
маленький стакан кофе, большая кружка, иконка рекламы, звезда Plus и аватар автора. Файлы кладутся
в `core/designsystem/src/main/res/drawable-nodpi/` с префиксом `support_neon_`, чтобы UI-SPEC'и
(05/06/07) могли ссылаться на них через `R.drawable.support_neon_*`. SPEC вводит ТОЛЬКО ресурсы —
ни одного composable, ни одной строки Kotlin.
LAYERS: resources
CHANGED_HINT:
  - core/designsystem/src/main/res/drawable-nodpi/support_neon_hero_cup.png — hero-иллюстрация, 588×588px (196dp @3x), прозрачный фон (G25, D14)
  - core/designsystem/src/main/res/drawable-nodpi/support_neon_coffee_small.png — маленький стакан, 198×246px (66×82dp @3x)
  - core/designsystem/src/main/res/drawable-nodpi/support_neon_coffee_large.png — большая кружка, 246×246px (82dp @3x)
  - core/designsystem/src/main/res/drawable-nodpi/support_neon_ads.png — иконка rewarded-рекламы (экран/плеер), 156×156px (52dp @3x)
  - core/designsystem/src/main/res/drawable-nodpi/support_neon_plus.png — звезда Plus, 156×156px (52dp @3x)
  - core/designsystem/src/main/res/drawable-nodpi/support_neon_avatar.png — аватар автора, 252×252px (84dp @3x)
  - (справочно, читать не менять) core/designsystem/src/main/res/drawable-nodpi/category_neon_coffee.png — образец стиля и плотности линии (G25)
  - (справочно, читать не менять) docs/design/category-icons-new-28-neon.png — лист-референс неон-стиля проекта; docs/design/support-screen-1A.html — места вставки и точные размеры заглушек
TEST_TYPES: none
CONSTRAINTS:
  - Стиль (жёстко, все шесть): тонкий line-art, обводка мятная `#5BE3B0`, акценты — коралловый
    `#FF8A80` (сердечки, «живые» детали), фон ПРОЗРАЧНЫЙ (не заливка `#0A0E1C` — плашки под
    иллюстрациями имеют собственный фон). Толщина линии одинаковая во всех шести файлах.
  - Формат PNG-32 с альфой, без теней и без встроенного текста. Аватар — единственный, где
    допустимы заливки цветом (персонаж), остальные пять — контурные.
  - Ассеты кладутся в `core/designsystem`, НЕ в `feature/support`: у `feature/support` нет папки
    `res/drawable*` (G26), а designsystem уже владеет всеми неон-ассетами приложения (G25).
  - Никаких изменений в Kotlin, темах или `strings.xml` — contentDescription'ы для этих
    иллюстраций объявляются в SPEC-03, использование — в SPEC-05/06/07.
  - Codex не генерирует растр (O1). Если на момент реализации финальных картинок нет — кладутся
    временные PNG-заглушки ТОЧНО тех же размеров и имён (например, контур-рамка на прозрачном
    фоне), сборка и тесты обязаны остаться зелёными, а факт заглушки фиксируется в
    `## Implementation links` этого файла. Пропускать SPEC и оставлять UI без `R.drawable.*`
    ссылок запрещено — иначе 05/06/07 не соберутся.
  - `category_neon_coffee.png` НЕ переиспользовать под `support_neon_coffee_small`: это иконка
    категории 1:1, а плашке нужен вертикальный формат 66×82dp.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Ассеты иллюстраций экрана поддержки

  Scenario: Все шесть файлов существуют с ожидаемыми именами
    Given модуль core:designsystem собран
    Then в drawable-nodpi присутствуют support_neon_hero_cup, support_neon_coffee_small, support_neon_coffee_large, support_neon_ads, support_neon_plus и support_neon_avatar

  Scenario: Пропорции соответствуют местам вставки
    Given иллюстрации добавлены
    Then hero, большая кружка, иконки рекламы и Plus, а также аватар имеют квадратные пропорции
    And маленький стакан имеет вертикальные пропорции 66 к 82

  Scenario: Иллюстрации ложатся на тёмный фон без ореола
    Given иллюстрация отрисована поверх фона плашки
    Then фон иллюстрации прозрачен и вокруг контура нет светлой каймы

  Scenario: Стиль совпадает с существующими неон-ассетами
    Given иллюстрация показана рядом с иконкой категории из category_neon_*
    Then обводка выполнена в том же мятном цвете и той же толщиной линии
```

## Gap / context
Макет `Экран поддержки 1A` рисует все иллюстрации как штрихованные заглушки («чашка кофе»,
«S CUP», «L CUP», «AD», «★», «аватар»); референс-скриншот показывает целевой неон-line-art стиль.
Ассеты нужны раньше вёрстки, иначе SPEC-05/06/07 не смогут сослаться на `R.drawable.*`.
Заблокировано D14 (PNG в `drawable-nodpi`), открыто O1 (чем именно рисуются картинки).

## Implementation links
- commit: —
- files: —
