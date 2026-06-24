# Чистая функция «доминирующий цвет иконки → цвет текста» (:core:common)
Epic: category-icon-text-color
Order: 01 of 04
Status: done
Depends-on: —
Date: 2026-06-22

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Завести чистый (без Compose/Room) источник цвета категории по `iconKey` в :core:common —
фундамент, на который опираются миграция (SPEC 02), сохранение (SPEC 02) и отрисовка (SPEC 03/04).
Две функции:
  1) `categoryIconDominantHex(iconKey: String): String` — курируемая таблица «ключ иконки →
     доминирующий цвет иконки» в hex (`#RRGGBB`), покрывающая ВСЕ ~82 ключа иконок (а не 26 + хэш).
     Сид — существующая `categoryIconAccent` (NeonCategoryIcon.kt:138-193, 26 явных цветов);
     остальные ключи (полный список — `categoryNeonIconAssets`, CategoryIconAssets.kt:14) дополнить
     реальным доминирующим цветом соответствующего PNG (подобрать визуально по ассету). Неизвестный
     ключ → детерминированный фолбэк (как сейчас `fallbackAccents` по хэшу) — но БЕЗ Compose Color,
     в hex.
  2) `readableTextHex(hex: String): String` — порог контраста: если относительная яркость цвета
     ниже порога (тёмный цвет на тёмном неоновом фоне dashboard), осветлить до читаемого уровня
     (поднять luminance/смешать с белым) и вернуть скорректированный hex; иначе вернуть как есть.
     Чистая ARGB-математика (relative luminance по WCAG), без androidx.
  3) `categoryTextColorHex(iconKey: String): String = readableTextHex(categoryIconDominantHex(iconKey))`
     — то, что хранит/рисует текст. (Для долек/тинта иконки используется `categoryIconDominantHex`
     БЕЗ контраст-коррекции — см. SPEC 02 D2/A1.)
LAYERS: domain
CHANGED_HINT:
  - core/common/src/main/.../category/CategoryIconColor.kt — НОВЫЙ: `categoryIconDominantHex(iconKey)`
    (полная hex-таблица на ~82 ключа, сид из NeonCategoryIcon.kt:138-193 + дополнить по
    CategoryIconAssets.kt:14) + детерминированный hex-фолбэк (D1)
  - core/common/src/main/.../category/ContrastPolicy.kt — НОВЫЙ: `readableTextHex(hex)` + relative
    luminance ARGB-математика (D3; утилит контраста ещё нет — G13) + `categoryTextColorHex(iconKey)`
  - core/common/src/test/.../category/CategoryIconColorTest.kt — НОВЫЙ: юнит-кейсы (см. ниже)
TEST_TYPES: unit
CONSTRAINTS:
  - БЕЗ зависимости от androidx.compose / Room — модуль :core:common, чтобы функцию мог звать и
    Room-миграция (:core:database, SPEC 02), и репозиторий, и :core:designsystem (SPEC 03).
  - Проверить, что :core:database и :core:designsystem уже зависят от :core:common (A2); если нет —
    добавить зависимость в их build.gradle.kts (НЕ наоборот, :core:common ни от кого не зависит).
  - hex-формат строго `#RRGGBB` (под существующий `parseHexColor`, common/ColorPicker.kt:70-77, G16).
  - НЕ дублировать таблицу: SPEC 03 рефакторит `categoryIconAccent` (designsystem) так, чтобы она
    делегировала сюда (A3) — здесь источник правды.
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Цвет категории из иконки (чистая функция)

  Scenario: Известный ключ иконки даёт его доминирующий цвет
    Given ключ иконки "taxi" с жёлтым PNG
    When вызывается categoryIconDominantHex("taxi")
    Then возвращается жёлтый hex (#RRGGBB)

  Scenario: Полное покрытие ключей
    Given список всех ключей из categoryNeonIconAssets (~82)
    When для каждого вызывается categoryIconDominantHex(key)
    Then ни один не падает в общий хэш-фолбэк (у каждого явный цвет)

  Scenario: Тёмный цвет осветляется для текста
    Given очень тёмный цвет иконки (#102018)
    When вызывается readableTextHex("#102018")
    Then относительная яркость результата не ниже порога читаемости
    And для уже яркого цвета (#FFD54A) readableTextHex возвращает его без изменений

  Scenario: Неизвестный ключ детерминирован
    Given неизвестный ключ "zzz"
    When categoryIconDominantHex("zzz") вызывается дважды
    Then оба раза возвращается один и тот же hex
```

## Gap / context
Нет чистого, переиспользуемого источника «цвет по иконке»: единственный per-icon цвет —
`categoryIconAccent` в :core:designsystem (Compose Color, 26 ключей + хэш), недоступен из Room-миграции
и неполный. Этот SPEC выносит каноническую hex-таблицу + контраст в :core:common.

## Implementation links
- commit: 8944bc4603f97ec8ae83b50183688a34ecea5445, 501282c00069b7530b216a55e6086d0047745d26
- files:
  - core/common/src/main/kotlin/com/kshavrin/mymoney/core/common/category/CategoryIconColor.kt
  - core/common/src/main/kotlin/com/kshavrin/mymoney/core/common/category/ContrastPolicy.kt
  - core/common/src/test/kotlin/com/kshavrin/mymoney/core/common/category/CategoryIconColorTest.kt
