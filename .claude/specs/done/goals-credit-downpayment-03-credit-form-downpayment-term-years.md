# Credit form — down-payment field, loan term in years, read-only year summary (S29)
Epic: goals-credit-downpayment
Order: 03 of 03
Status: done
Depends-on: 01, 02
Date: 2026-06-07

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Перестроить credit-ветку формы цели (S29) под новую модель. Добавить поле **«Первоначальный взнос»**,
заменить read-only date-picker «Дата погашения» на числовой ввод **«Срок кредита (лет)»**, и показывать новый
read-only итог: **сколько лет на накопление взноса** и **сколько всего лет до полного погашения** (накопление +
срок), плюс ежемесячный платёж (аннуитет) и общую переплату. Логика досрочного погашения (FG-4) уходит из UI.
VM передаёт `downPayment` и `termMonths = termYears × 12` в обновлённый `GoalLoanCalculator` (SPEC 01) и сохраняет
`Goal.downPayment`/`Goal.termMonths` (SPEC 02). Это SPEC, чинящий компиляцию после 01+02.
LAYERS: presentation
CHANGED_HINT:
  - feature/dictionaries/.../goals/GoalEditViewModel.kt — (G5, G6) в `GoalEditState` (`:233`): ДОБАВИТЬ
    `downPayment: String = ""`, `termYears: String = ""`; УБРАТЬ `termDate: LocalDate?` (`:249`); добавить
    форматированные выходы `loanProjectionYearsToDownPaymentFormatted`, `loanProjectionTotalYearsFormatted`.
    В `GoalEditEvent` (`:260`): добавить `DownPaymentChanged(String)` и `TermYearsChanged(String)`; убрать
    `TermDateChanged` (`:269`). В `recompute()` (`:138`): `termMonths = termYears.toIntOrNull()?.times(12)`;
    `loanProjection` считать при `variant==CREDIT && termMonths != null && termMonths>=1` через
    `LoanGoalInput(target, capital, downPayment.parseMoney(), rate, termMonths, monthly)` (новая сигнатура SPEC 01);
    форматировать `accumulationMonths`/`totalMonthsToPayoff` в «X лет Y мес.» хелпером (null → строка «недостижимо»).
    `canSave = variant != CREDIT || (termMonths != null && termMonths>=1)` (H6 — по сроку, НЕ по UNREACHABLE
    накоплению, D4). В `save()` (`:196`) и init-load (`:57`): писать/читать `downPayment`, `termMonths` вместо
    `termDate`; `VariantChanged→SAVINGS` (`:84`) обнулять `downPayment`/`termYears` вместо `termDate`.
  - feature/dictionaries/.../goals/GoalEditScreen.kt — (G1) в `CreditFields` (`:281`): ДОБАВИТЬ
    `OutlinedTextField` «Первоначальный взнос» (Decimal) → `DownPaymentChanged`; ЗАМЕНИТЬ блок date-picker
    (`:298-314`, `:384-413`) на `OutlinedTextField` «Срок кредита (лет)» (`KeyboardType.Number`) → `TermYearsChanged`;
    удалить импорты DatePicker/rememberDatePickerState/ZoneOffset. В read-only `Surface` (`:316`): показать строки
    `goal_years_to_down_payment` и `goal_total_years_to_payoff` (из новых форматированных выходов) + оставить
    `goal_monthly_payment`/`goal_total_interest`/`goal_total_paid`; убрать `goal_overpayment_note` (FG-4 ушёл).
    `underfunded`-блок оставить (информационно, H3). Где форма пишет `LoanProjection.baseMonthlyPayment` — без изменений
    (поле осталось); убрать ссылки на удалённые поля (`finalMonthlyPayment` и т.п. SPEC 01 H7), если они были.
  - feature/dictionaries/src/main/res/values/strings.xml + values-ru/strings.xml — (G11) ДОБАВИТЬ
    `goal_down_payment` («Down payment» / «Первоначальный взнос»), `goal_loan_term_years`
    («Loan term, years» / «Срок кредита, лет»), `goal_years_to_down_payment`
    («Years to accumulate down payment: %1$s» / «Лет на накопление взноса: %1$s»),
    `goal_total_years_to_payoff` («Total years to payoff: %1$s» / «Всего лет до погашения: %1$s»),
    `goal_accumulation_unreachable` («Down payment unreachable with current contribution» /
    «Взнос недостижим при текущем взносе»). `goal_term_date`/`goal_overpayment_note` оставить в файле
    неиспользуемыми ИЛИ удалить, если ни одна ссылка не осталась.
  - feature/dictionaries/src/test/.../goals/GoalEditCreditViewModelTest.kt — (G12) переписать: выбор CREDIT +
    rate + termYears + downPayment + target считает accumulationMonths/totalMonths и платёж (сверка с
    `GoalLoanCalculator`); termYears пустой/0 → проекции нет и `canSave=false`; monthly=0 & capital<downPayment →
    проекция со status=UNREACHABLE, но `canSave=true` (D4); SaveClicked сохраняет CREDIT Goal с downPayment+termMonths.
  - feature/dictionaries/src/test/.../goals/GoalEditCreditContentTest.kt — (G12) обновить: переключение на CREDIT
    показывает поля взноса / срока-в-годах / read-only лет; переключение обратно их прячет; поля date-picker больше нет.
TEST_TYPES: unit, compose-ui
CONSTRAINTS:
  - Этот SPEC потребляет НОВЫЕ сигнатуры `LoanGoalInput`/`LoanProjection` (SPEC 01) и `Goal.downPayment/termMonths`
    (SPEC 02) — поэтому `Depends-on: 02` (и транзитивно 01). До их слияния модуль не компилируется — это ожидаемо.
  - Срок вводится в ГОДАХ (D2), внутри ×12. Деньги — `BigDecimal` (`parseMoney`). Формат «X лет Y мес.» — через
    хелпер из месяцев (CEILING уже в domain); `null` accumulationMonths → строка «недостижимо» (D4).
  - Никаких хардкод-строк (EN + RU обе). `:feature:dictionaries → :feature:*` остаётся 0. Без комментариев кроме WHY.
  - Robolectric/compose-ui-test артефактов может не быть в офлайн-кэше → content-тест пинит контракт через
    state-логику (coverage exception, как в `done/financial-goals-06`). Runner компилирует androidTest (G12).
=== END SPEC ===

## Acceptance
```gherkin
Feature: Форма кредитной цели — взнос, срок в годах, итог в годах

  Scenario: Появляются поля взноса и срока вместо даты погашения
    Given экран создания цели
    When выбран вариант «С кредитом»
    Then видно поле «Первоначальный взнос»
    And видно поле «Срок кредита, лет»
    And поля выбора «Дата погашения» больше нет

  Scenario: Read-only показывает годы накопления и общий срок
    Given variant=CREDIT, target=2 000 000, capital=0, downPayment=500 000, monthly=50 000, rate=0, termYears=10
    When все поля заполнены
    Then показано «Лет на накопление взноса» (10 мес)
    And показано «Всего лет до погашения» (130 мес = накопление 10 + срок 120)
    And показан ежемесячный платёж 12 500.00

  Scenario: Пустой срок не даёт сохранить
    Given variant=CREDIT and termYears пустой
    Then кнопка Save отключена и проекция не показывается

  Scenario: Недостижимый взнос показывает предупреждение, но Save доступен
    Given variant=CREDIT, capital < downPayment, monthly = 0, termYears = 5
    Then показано «Взнос недостижим при текущем взносе»
    And кнопка Save доступна

  Scenario: Сохранение пишет взнос и срок
    Given валидная кредитная цель с downPayment и termYears
    When нажата Save
    Then сохранён Goal(variant=CREDIT, downPayment=…, termMonths=termYears×12)
    And происходит NavigateBack
```

## Gap / context
SPEC 01 и 02 меняют расчёт и хранение, ломая компиляцию presentation. Этот SPEC перестраивает форму S29 под новую
модель (взнос + срок-в-годах + read-only итог в годах), удаляет date-picker и FG-4-обвязку, и чинит модуль.

## Implementation links
- commit: 0a4c28cd (rebuild credit form) + 264a14ff (refactor: VM Context-free, raw months in state, localize in Composable)
- files: feature/dictionaries/.../goals/GoalEditViewModel.kt, .../goals/GoalEditScreen.kt, res/values/strings.xml, res/values-ru/strings.xml, + tests GoalEditCreditViewModelTest.kt, GoalEditCreditContentTest.kt, GoalEditSavingsViewModelTest.kt, GoalsListViewModelTest.kt (+ androidTest GoalsListContentUiTest.kt)
- DEVIATION: SPEC named loanProjection*YearsFormatted (String) state fields; implemented as RAW Int loanProjectionAccumulationMonths/loanProjectionTotalMonths + Composable-side localization (stringResource), because injecting @ApplicationContext into the VM made it un-unit-testable offline (Robolectric absent) and clashed with the VM's existing Context-free formatMoney pattern. VM stays pure JVM-testable.
