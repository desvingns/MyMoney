# Неон-токены: палитра всего приложения + токены кольца/плиток/FAB
Epic: dashboard-neon-ring-redesign
Order: 01 of 06
Status: done
Depends-on: —
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Заменить палитру всего приложения на неон-тёмную и добавить дашборд-токены под кольцо/плитки/FAB. Палитра: фон `#0A0E1C`, поверхности `#111A2E`/`#1B2236`, текст основной `#E8EAF0`, вторичный `#7C8290`, неон-акценты `#5BE3B0`→`#46B6E6`, доход-зелёный (`#5BE3B0`), расход-коралл (`#FF8A80`). FAB +15% (90dp→104dp). Обе ветки ThemeMode мапятся на неон (D8).
LAYERS: presentation
CHANGED_HINT:
  - core/ui/theme/Color.kt:10-52,127-182 — заменить DarkColors/LightColors неон-палитрой; пересмотреть дашборд ColorScheme-расширения (dashboardHeroGradient*, dashboardBalancePanel* и т.д.) под неон; учесть, что `isLightDashboardPalette`(luminance>0.5) теперь всегда false (G10)
  - core/ui/theme/Color.kt — добавить токены: `neonRingGradientStart=#5BE3B0`, `neonRingGradientEnd=#46B6E6`, `neonRingTrack=#1A2236`, `dashboardNeonBackground=#0A0E1C`, `tileSurface=#111A2E`/`tileSurfaceAlt=#1B2236`, `textPrimary=#E8EAF0`, `textSecondary=#7C8290`, `incomeAccent`, `expenseAccent` (G10)
  - core/ui/theme/Theme.kt (assumption) — точка выбора ColorScheme: обе ветки (light/dark/system) → неон-палитра (D8); файл рядом с Color.kt, имя уточнить
  - core/ui/theme/Spacing.kt:5-46 — `dashboardFabSize` 90dp→104dp (+15%, D3); пропорц. масштаб иконки/паддингов FAB; добавить токены: ring diameter/strokeWidth/glowRadius/glowSpread, tile height/cornerRadius/iconChipSize/progressBarHeight (G9)
  - core/ui/theme/Typography.kt:9-80 — добавить стили: крупный «Остаток» (центр кольца), метка «Остаток», бейджи доход/расход, текст плитки (G11)
TEST_TYPES: unit
CONSTRAINTS:
  - Без хардкода цветов/строк в экранах — только токены (G10); ничего не ломать в существующих расширениях ColorScheme (имена сохранить, значения заменить).
  - Перекрас затрагивает ВСЕ экраны (R1) — формы/список/настройки/словари/диалоги не редизайнятся, но обязаны остаться читаемыми; контраст ключевых экранов вычитать вручную.
  - ThemeMode схлопывается в неон (A1): оставить enum, но обе ветки → неон (минимальное изменение), переключатель темы пока не удалять.
  - FAB +15% реализуется ТОЛЬКО изменением токена `dashboardFabSize` (G8: `TwoFabLayout` читает токен) — компонент в этом SPEC не трогаем.
  - `:core:ui` тест-таск проверить (test vs testDebugUnitTest); runner модуль пропускает → прогнать вручную (G16); ktlintFormat перед коммитом (G16).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Неон-палитра применяется ко всему приложению

  Scenario: Дашборд тёмный независимо от системной темы
    Given системная тема "светлая"
    When открыт дашборд
    Then фон экрана соответствует токену dashboardNeonBackground (#0A0E1C)

  Scenario: FAB крупнее на 15%
    Given токен dashboardFabSize
    Then его значение равно 104dp (90dp + 15%)

  Scenario: Токены кольца определены
    Then существуют neonRingGradientStart, neonRingGradientEnd, neonRingTrack с заданными HEX
```

## Gap / context
Сейчас палитра — зелёно-красная Monefy-ish (LightColors/DarkColors), FAB=90dp, токенов неон-кольца/плиток нет.
Этот SPEC — фундамент: вводит неон-палитру всего приложения и дашборд-токены, на которые опираются 03/04/05/06.

## Implementation links
- commit: 7b6f789c, 15c518a3, 674622e5
- files: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt, core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt, core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Theme.kt, core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Typography.kt, core/ui/src/test/java/com/kshavrin/mymoney/core/ui/theme/DashboardBalancePanelColorsTest.kt
