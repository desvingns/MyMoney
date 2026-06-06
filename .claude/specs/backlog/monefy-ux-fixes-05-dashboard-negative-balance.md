# Отрицательный баланс на dashboard — красные цвета (п.4)
Epic: monefy-ux-fixes
Order: 05 of 07
Status: draft
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
- commit: (pending)
- files: (pending)
