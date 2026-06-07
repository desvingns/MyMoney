# Отрицательный баланс на dashboard — красные цвета (п.4)
Epic: monefy-ux-fixes
Order: 05 of 07
Status: done
Depends-on: —
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: На dashboard при отрицательном чистом балансе показывать текст баланса «светло-красным», а фон плашки баланса — «светло-светло-красным». Положительный/нулевой баланс — как сейчас. Добавить токены негатива в :core:ui/theme/Color.kt (light+dark) и условно применить в DashboardBalancePanel, пробросив isNegative из точки вызова.
LAYERS: presentation
CHANGED_HINT: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt — новые токены dashboardBalancePanelContainerNegative + dashboardBalancePanelContentNegative (light+dark) рядом с существующими dashboardBalancePanel* (~125-135); feature/dashboard/.../DashboardScreen.kt — DashboardBalancePanel (~397-441) принимает isNegative: Boolean и условно выбирает container/content токены; точка вызова (~201-206) вычисляет isNegative = state.balanceSnapshot?.net?.amount?.signum() ?? 0 < 0 (BigDecimal)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Только отрицательный кейс меняет цвета; положительный и ноль — текущие токены без изменений.
  - Стартовые значения (правятся mp-ui-designer-android): light container ≈ #FFFCEAEA (очень бледно-красный), light content ≈ #FFD64545 (светло-красный); dark container ≈ error-тинт низкой альфы на surface, dark content ≈ #FFEF9A9A.
  - a11y: проверить контраст текста баланса на бледном фоне (цель WCAG AA для крупного текста ≥3:1) — при необходимости поднять насыщенность текста.
  - Тень/границу плашки согласовать с негативом; токены, не хардкод цветов; идентификаторы английские.
=== END SPEC ===

## Gap / context
DashboardBalancePanel (DashboardScreen.kt:397-441) использует статичные токены
dashboardBalancePanelContainer/Content; условного стиля для отрицательного баланса нет (formatBalanceAmount
не проверяет знак). Замечание пользователя №4.

## Implementation links
- commits: c39caaea (tokens) · a9fa6c39 (theme refactor) · e75ec544 (tint negative balance) · f8725bf9 (tighten compose-ui test) · 05470d5a (unit test for color tokens)
- files:
  - core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Color.kt (dashboardBalancePanelContainerNegative + dashboardBalancePanelContentNegative, light+dark)
  - feature/dashboard/src/main/java/com/kshavrin/mymoney/feature/dashboard/DashboardScreen.kt (isNegative via net.amount.signum() threaded into DashboardBalancePanel; conditional container/content tokens)
  - core/ui/src/test/java/com/kshavrin/mymoney/core/ui/theme/DashboardBalancePanelColorsTest.kt (unit)
  - feature/dashboard/src/androidTest/java/com/kshavrin/mymoney/feature/dashboard/DashboardContentUiTest.kt (compose-ui)
- verification: :core:ui + :feature:dashboard testDebugUnitTest green (JDK 21); reviewer pass; verifier pass:true (presentation-only, no wiring deltas)
