# Диалог курса (общий компонент, single + list, правка разовая)
Epic: currency-exchange-rate
Order: 05 of 08
Status: done
Depends-on: 04
Date: 2026-06-20

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Общий Compose-компонент диалога курса (D3), который показывается КАЖДЫЙ раз при обращении к курсу. На строку курса: «дата последнего обновления», «курс на эту дату», поле ручного ввода (предзаполнено текущим курсом). Два режима: **single** (одна валютная пара — для перевода) и **list** (несколько пар одним окном — для свёртки «Все счета», D9). Подтверждение/правка в 1 тап. Ручной ввод — **разовый** (D5): применяется только к текущей операции, в БД не пишется. Компонент stateless: получает `RateInfo` (из SPEC 04) и колбэки, сам ничего не грузит и не сохраняет. Лежит в `:core:designsystem`, т.к. используется и `:feature:transaction`, и `:feature:dashboard` (нельзя feature→feature).
LAYERS: presentation
CHANGED_HINT:
  - core/designsystem/.../dialog/RateConfirmDialog.kt — НОВЫЙ stateless Composable. Вход: `List<RateRow>` (`RateRow { from, to, lastUpdated: LocalDate?, displayRate: BigDecimal?, stale: Boolean, missing: Boolean }`), колбэки `onRateEdited(index, BigDecimal)`, `onConfirm(Map<pair, BigDecimal>)`, `onDismiss`. 1 строка ⇒ single-вид, >1 ⇒ список (D9). **`displayRate` = кросс-курс `from→to` (то, что видит и правит пользователь), НЕ хранимый `EUR→X` base-rate** — адаптер из `RateInfo.crossRate` (SPEC 04) заполняет именно его
  - core/designsystem/.../dialog/RateConfirmDialog.kt — нормализация ввода «,»→«.», парс в BigDecimal, scale 2 HALF_UP (зеркало существующего экрана курса G7); пустой/невалидный ввод ⇒ использовать предзаполненный `displayRate`; missing ⇒ поле обязательно к заполнению
  - core/designsystem/.../dialog/RateConfirmDialog.kt — показывать «устарел»/«нет интернета» подсказкой когда `stale`/`missing` (данные из RateInfo, SPEC 04); НЕ блокировать подтверждение при stale
  - строки UI — в `res/values/strings.xml` (EN) + `res/values-ru/strings.xml` (RU), без хардкода (CLAUDE.md)
  - тесты: Compose-UI/Robolectric — отрисовка single и list, предзаполнение, правка строки, подтверждение возвращает map, dismiss; semantics-теги для проверок
TEST_TYPES: compose-ui
CONSTRAINTS:
  - **Разовость (D5):** компонент НЕ вызывает upsert и не трогает `updatedAt`. Возвращает введённые значения вызывающему, тот применяет их к ТЕКУЩЕЙ операции.
  - Stateless/hoisted state — компонент не держит репозиторий/VM; данные приходят сверху (RateInfo из SPEC 04). Это сохраняет переиспользуемость между фичами.
  - Парс/формат курса — как на существующем экране курса (G7): «,»→«.», 2 знака HALF_UP.
  - Нет хардкод-строк; EN+RU (CLAUDE.md). ktlintFormat (G20). `:core:designsystem` — semantics-теги, но draw-occlusion semantics не ловят (визуально перепроверять).
=== END SPEC ===

## Acceptance (Gherkin)
```gherkin
Feature: Диалог подтверждения курса

  Scenario: Одиночный курс показывается с датой и значением
    Given курс EUR→RUB обновлён 2026-06-20 со значением 84.18
    When открывается диалог курса для одной пары
    Then показаны дата 2026-06-20, курс 84.18 и поле ручного ввода с этим значением

  Scenario: Подтверждение без правки использует показанный курс
    Given диалог курса открыт с предзаполненным значением
    When пользователь подтверждает в один тап
    Then в операции используется показанный курс

  Scenario: Разовая правка не сохраняется в базу
    Given диалог курса открыт
    When пользователь меняет значение и подтверждает
    Then операция использует введённое значение
    And сохранённый в базе курс и его дата остаются прежними

  Scenario: Несколько курсов одним окном
    Given нужно подтвердить курсы для USD и RUB
    When открывается диалог свёртки
    Then обе валюты показаны строками с датой, курсом и полем правки
    And одно подтверждение принимает оба курса
```

## Gap / context
Сейчас курс правится только на отдельном экране и всплывает лишь при отсутствии курса (G7/G10). Нужен переиспользуемый диалог «каждый раз» с разовой правкой и режимом списка для свёртки.

## Implementation links
- commit: b968e62d (component + strings) + b9b5d150 (windowless RateConfirmDialogContent extraction for test isolation)
- files:
  - core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/dialog/RateConfirmDialog.kt (new: RateRow + RateConfirmDialog wrapper + RateConfirmDialogContent stateless body, single/list modes, ",".→"." scale-2 HALF_UP parse, stale/missing badges via rateStale tokens, one-shot confirm returns Map<Int,BigDecimal>)
  - core/designsystem/src/main/res/values/strings.xml (rate_* keys EN)
  - core/designsystem/src/main/res/values-ru/strings.xml (rate_* keys RU)
  - core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/dialog/RateConfirmDialogUiTest.kt (24 instrumented Compose-UI tests, green on emulator-5554)
- Note: foundational reusable component; screen integration deferred to SPECs 06 (transfer dialog), 07/08 ("All accounts" convert). RateConfirmDialogContent is the windowless composable tests render directly (project <Name>Content convention); RateConfirmDialog wraps it in Dialog for production.
