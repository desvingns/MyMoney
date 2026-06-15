# Эпик: records-comment-in-list — комментарий операции в списке операций
Epic: records-comment-in-list
Order: 00 of 03 (overview)
Status: done
Depends-on: —
Date: 2026-06-14
Completed: 2026-06-14

## Цель

Комментарий операции (`note`) должен отображаться в списке операций (экран по тапу баланса на
дашборде) — в строке операции **между суммой и датой**. Поле `note` уже существует и персистится
во всём стеке (domain → Room → DAO → mapper → repo → форма ввода), но в списке нигде не рендерится.
Для обычных операций оно уже загружено в память (`Transaction.note` внутри `CategoryRecordGroup`) —
правка чисто presentation. Для переводов `note` тоже уже хранится (перевод = строка `transaction` с
`kind='transfer'` и колонкой `note`), но не прокинут в `TransferRow`/`TransferRecord` — нужен
data+domain слой перед UI.

## Заблокированные решения (из grill)

- D2: скоуп — И обычные операции (`TransactionLeaf`), И переводы (`TransferRow`).
- D3: пустой/отсутствующий `note` → не рендерить ничего; высота строки не меняется (без плейсхолдеров).
- D4: длинный `note` → одна строка, занимает гибкое место между суммой и датой, при переполнении
  ellipsis (`…`); дата всегда видна, высота строки стабильна.
- Out of scope: миграция схемы (колонка `note` уже есть), правки формы ввода (note уже вводится),
  редактирование/удаление из списка, многострочный режим.

## SPEC'и (собираются через `/mp --feature --next` в порядке)

| Order | File | Depends-on | Layers | Summary |
|---|---|---|---|---|
| 01 | `records-comment-in-list-01-operations-row-note.md` | — | presentation | note в `TransactionLeaf` между суммой и датой |
| 02 | `records-comment-in-list-02-transfer-note-plumbing.md` | — | data+domain | прокинуть note перевода: query → projection → domain + mapper |
| 03 | `records-comment-in-list-03-transfer-row-note.md` | 01, 02 | presentation | note в строке перевода `TransferRow` между суммой и датой |

## Почему такой порядок

01 — основной запрос пользователя («операции»), крошечный и независимый (note уже в памяти) →
отгружается первым. 02 — фундамент для переводов (данные перед UI), без зависимостей. 03 — UI
переводов: зависит от данных (02) И секвенируется после 01, т.к. оба правят один файл
`TransactionsListScreen.kt` (⚠ clash 01↔03).

## Ключевые факты (verified, из grounding)

- G1: тап по балансу → `DashboardEvent.BalanceCardClicked` → nav на `TransactionsListRoute` — `feature/dashboard/.../DashboardViewModel.kt:440`, `app/.../navigation/MyMoneyNavHost.kt:122-154`.
- G2: доменная `Transaction.note: String?` уже есть — `core/domain/.../model/Transaction.kt:13`; Room-колонка `note` — `core/database/.../entity/TransactionEntity.kt:54`.
- G3: список операций строится из `List<CategoryRecordGroup>`, каждая группа держит `List<Transaction>` → `note` уже в памяти — `feature/transactionslist/.../list/TransactionsListUiState.kt:10-37`.
- G4 (цель 01): строка операции `TransactionLeaf()` рендерит ТОЛЬКО сумму + дату — `feature/transactionslist/.../list/TransactionsListScreen.kt:625-674`.
- G5: перевод = строка `transaction` c `kind='transfer'`; DAO `getTransfers` SELECT НЕ содержит note — `core/database/.../dao/TransactionDao.kt:123-146`.
- G6 (цель 02): `TransferRecord` (`core/domain/.../model/TransferRecord.kt:5-12`) и `TransferRow` projection (`core/database/.../projection/TransferRow.kt:5-13`) без поля `note`.
- G7: `note` перевода персистится — `TransferState.note` → `TransferViewModel.kt:246` (`note = s.note.takeIf { it.isNotBlank() }`).
- G8 (gotcha): после Tester-шага обязателен `:<module>:ktlintFormat` + `ktlintCheck` перед коммитом — `memory/mymoney-tester-ktlint-gate.md`.
- G9 (gotcha): mp-runner script даёт ложный pass:false на Windows; реальные гейты — `:<module>:testDebugUnitTest` + `:app:lintDebug` (detekt/jacoco отсутствуют) — `memory/mymoney-mp-runner-script-mismatch.md`.
- G10 (gotcha): instrumented Compose-тесты — один @Test на слайс, рендер `<Screen>Content` в `MyMoneyTheme` через `createComposeRule()`, captured-events list — `.claude/mp/extras/mp-runner-instrumented-android.md:60-69`.

## Implementation links
- 01 operations row note: d20a0a92 + 6a6eba4a + 2ba6f865 — `TransactionLeaf` note (instrumented 26/26)
- 02 transfer note plumbing: 22f98e89 + e926e04f — DAO→projection→domain+mapper (DAO instrumented 11/11)
- 03 transfer row note: d4479b0a + 6531841c — `TransferRow` note slot маршрут→комментарий→дата (instrumented 30/30)
- EPIC COMPLETE 2026-06-14: note рендерится в строке списка операций для обычных операций И переводов; D3 (пусто→ничего) + D4 (длинный→ellipsis) покрыты в обоих слайсах; всё в main.
