# Одиночный выбор даты вместо диапазона в форме транзакции (п.2)
Epic: monefy-ux-fixes
Order: 03 of 07
Status: done
Depends-on: 01
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В форме транзакции при тапе по дате открывать выбор ОДНОЙ конкретной даты, а не диапазона. Заменить Material3 DateRangePicker/rememberDateRangePickerState на DatePicker/rememberDatePickerState (возвращает один LocalDate). Образец уже есть в экране редактирования — TransactionDetailScreen.kt:247-269 (одиночный DatePicker). Переименовать диалог в нейтральное TransactionDatePickerDialog (или сохранить имя файла, заменив тело) и подключить из общей TransactionFormContent.
LAYERS: presentation
CHANGED_HINT: feature/transaction/.../TransactionDateRangePickerDialog.kt — тело: DateRangePicker → DatePicker, rememberDateRangePickerState → rememberDatePickerState, чтение selectedStartDateMillis+selectedEndDateMillis → selectedDateMillis; точка вызова в core/designsystem/.../form/TransactionFormContent.kt (контракт onDatePicked: (LocalDate) -> Unit сохранить)
TEST_TYPES: compose-ui
CONSTRAINTS:
  - ВАЖНО: это НЕ затрагивает AS-12. AS-12 — про dashboard-пикер периода (там диапазон Period.CustomRange уместен и ЗАБЛОКИРОВАН; реализован в done-SPEC dashboard-date-range-and-single-date). Текущая правка — ТОЛЬКО про пикер в форме транзакции (другой экран, другой компонент). Не «чинить» dashboard-пикер.
  - Контракт onDatePicked(LocalDate) и initialDate: LocalDate сохранить; конверсия millis↔LocalDate в UTC как сейчас.
  - EN+RU строки apply/cancel без изменений; идентификаторы английские.
=== END SPEC ===

## Gap / context
TransactionDateRangePickerDialog.kt:23-49 использует DateRangePicker и возвращает только startDate, хотя
у транзакции одна дата — это баг (можно выбрать диапазон). Диалог используется только в форме транзакции
(других вызовов нет). Замечание пользователя №2.

## Implementation links
- commit: 61a50b98d65cdab35c1f63dc12a754b3e395b937, 0a6dd57
- files: feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/TransactionDateRangePickerDialog.kt; feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseScreen.kt; feature/transaction/src/main/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeScreen.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/TransactionDateRangePickerDialogUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/expense/AddExpenseScreenUiTest.kt; app/src/androidTest/java/com/kshavrin/mymoney/feature/transaction/income/AddIncomeScreenUiTest.kt
