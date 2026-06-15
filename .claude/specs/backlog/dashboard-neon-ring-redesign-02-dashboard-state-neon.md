# Состояние дашборда: net периода, доля кольца, развёрнутый список расходов
Epic: dashboard-neon-ring-redesign
Order: 02 of 06
Status: backlog
Depends-on: —
Date: 2026-06-15

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Расширить `DashboardViewModel`/`DashboardState` под неон-дашборд: (а) `periodNet` = доход − расход (для центра, отображается целым, D7); (б) `ringFraction` = расход ÷ доход, clamp 0..1, доход 0 → 0f (D6); (в) `expenseTiles` — полный список расходных категорий БЕЗ свёртки «<2% Другое», по убыванию суммы (D9). Всё из существующего `BalanceSnapshot` — без новых репозиторных вызовов.
LAYERS: presentation
CHANGED_HINT:
  - feature/dashboard/.../DashboardState.kt:11-36 — добавить поля: `expenseTiles: List<CategorySlice>` (или новая UI-модель плитки), `ringFraction: Float`, `periodNet: Money` (G2, G4)
  - feature/dashboard/.../DashboardViewModel.kt:297-336 — построить `expenseTiles` из `BalanceSnapshot.byCategory` (expense-only, sort desc по total, fraction=cat.total/totalExpense, `parseHexColor`) БЕЗ свёртки <2% в OTHER — отличается от `snapshotToSlices()` (G5, G3)
  - feature/dashboard/.../DashboardViewModel.kt:229-287 — вычислить `ringFraction` и `periodNet` из `BalanceSnapshot` при пересчёте (G14, G3)
TEST_TYPES: unit
CONSTRAINTS:
  - Деньги остаются BigDecimal в домене; «до целого» — только на отображении (decimalDigits=0 при форматировании в 04), Money/округление домена не менять (G12, D7).
  - Переиспользовать `BalanceSnapshot` (G3); не дублировать `BalanceCalculator`, никаких новых вызовов репозитория.
  - `snapshotToSlices()` оставить как есть (старый донат отвяжется в 06) — здесь только ДОБАВЛЯем вывод, чтобы 03/04/05 строились независимо.
  - `:feature:dashboard` юнит-тесты — fakes-only, без моков (конвенция проекта); runner модуль пропускает → прогнать вручную (G16); ktlintFormat (G16).

### Calculation: доля кольца и net периода
- Formula: `ringFraction = clamp(expense.amount / income.amount, 0f, 1f)`; `periodNet = income − expense`; центр показывается как `round(periodNet)` (HALF_UP, 0 знаков) на уровне формата.
- Symbols: `income`, `expense` — суммы периода из `BalanceSnapshot` (BigDecimal ≥0); `ringFraction` — Float 0..1; `periodNet` — Money (BigDecimal).
- Precision: `ringFraction` — Float только для отрисовки дуги; деньги не теряют точность (округление только в отображении центра).
- Edge: `income == 0` → `ringFraction = 0f` (пустой трек), не деление на ноль; нет операций → income=expense=0 → fraction 0f, net 0, `expenseTiles` пуст.
- Worked examples:
  | income | expense | ringFraction | periodNet (центр) |
  |--------|---------|--------------|--------------------|
  | 85000  | 47350   | 0.557        | 37 650             |
  | 0      | 1200    | 0.0          | −1 200             |
  | 50000  | 50000   | 1.0          | 0                  |
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Состояние неон-дашборда

  Scenario: Доля кольца = расход ÷ доход
    Given доход 85000 и расход 47350 за период
    When ViewModel пересчитывает состояние
    Then ringFraction ≈ 0.557 и periodNet = 37650

  Scenario: Доход ноль не делит на ноль
    Given доход 0 и расход 1200
    Then ringFraction = 0.0 и periodNet = -1200

  Scenario: Плитки без свёртки "Другое"
    Given расходы по 8 категориям, две из них <2% от расхода
    When строятся expenseTiles
    Then в списке все 8 категорий по убыванию суммы, без слайса OTHER (-1L)
```

## Gap / context
`snapshotToSlices()` сворачивает мелкие категории в «Другое» и даёт только слайсы для доната; центр доната
показывает доход/расход двумя строками. Новому дашборду нужны: net периода (центр), доля расход÷доход (кольцо)
и полный список расходных категорий (плитки) — этот SPEC даёт их в состоянии.

## Implementation links
- commit: <hash>
- files:  <changed files>
