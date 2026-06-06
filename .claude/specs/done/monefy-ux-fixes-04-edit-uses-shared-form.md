# «Изменить расход/доход» рендерит общую форму (п.3)
Epic: monefy-ux-fixes
Order: 04 of 07
Status: done
Depends-on: 01, 02, 03
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: Экран редактирования транзакции (расход/доход) переключить на рендер общей TransactionFormContent из :core:designsystem, предзаполненной данными транзакции, чтобы «Изменить» выглядел и работал как «Новый». TransactionDetailViewModel/State маппят в форм-модель; правки SPEC-02 (раскладка) и SPEC-03 (одиночная дата) применяются к edit автоматически. Категория в edit становится редактируемой через тот же двухшаговый CategoryGrid.
LAYERS: presentation
CHANGED_HINT: feature/transactionslist/.../detail/TransactionDetailScreen.kt — тело → TransactionFormContent (для kind expense/income); feature/transactionslist/.../detail/TransactionDetailViewModel.kt + TransactionDetailState.kt — маппинг state→форм-модель, категория редактируема; общая форма (core/designsystem/.../form/) — добавить параметр режима mode = New | Edit (showDelete, триггер сохранения)
TEST_TYPES: unit compose-ui
CONSTRAINTS:
  - Режим формы: TransactionFormContent параметризуется mode = New | Edit через флаги. В Edit: показывается удаление; категория редактируема (шаг категорий доступен для пере-выбора); сохранение — ЯВНОЕ (текущий FAB/save + dirty-tracking), НЕ «сохранить по выбору категории» (это поведение New). Отличия — через флаги/колбэки формы, без дублирования раскладки.
  - Переводы (transfers): редактирование переводов — это НЕ расход/доход; оставить как сейчас (ветка transfer в TransactionDetail с целевым счётом/курсом/exchangeRate НЕ трогается; на общую форму переводим только expense/income).
  - Сохранить удаление с undo (5 c), dirty-tracking (isDirty/canSave), навигацию назад. :feature:transactionslist зависит от :core:designsystem (разрешено). Файл TransactionDetailScreen.kt НЕ удаляем — перепиливаем тело.
  - EN+RU; без доменных/data-изменений; идентификаторы английские; комментарии только при неочевидном WHY.
=== END SPEC ===

## Gap / context
Сейчас редактирование идёт через отдельный TransactionDetailScreen (:feature:transactionslist): категория
read-only, своя раскладка, FAB-save, удаление, dirty-tracking, одиночный DatePicker. Пользователь хочет,
чтобы окно «Изменить» было таким же, как «Новый». После SPEC-01 это достигается переиспользованием общей
формы. Замечание пользователя №3.

## Implementation links
- commit: 87e16d7a (feat) + 3952a4fa (design tokens) + 74bb5336 (test import fix) — pushed to main 312ee29c..74bb5336
- files:
  - core/ui/.../theme/Color.kt, Spacing.kt (delete-action tokens)
  - core/designsystem/.../form/TransactionFormState.kt, TransactionFormContent.kt (mode = New | Edit), strings.xml (EN+RU)
  - feature/transactionslist/.../detail/TransactionDetailScreen.kt (expense/income body → shared form; transfer branch untouched), TransactionDetailState.kt, TransactionDetailEvent.kt, TransactionDetailAction.kt, TransactionDetailViewModel.kt (state→form-model, editable category)
  - feature/transaction/.../expense/AddExpenseScreen.kt, income/AddIncomeScreen.kt; app/.../navigation/MyMoneyNavHost.kt
  - tests: TransactionFormStateContractTest (19), TransactionDetailFormMappingTest (14), TransactionDetailFormDispatchContractTest (15), DestinationsTest (40) + extended androidTest compose-ui (TransactionDetailContentUiTest, Add{Expense,Income}ScreenUiTest — instrumented, device-deferred)
- verification: 366 JVM unit tests green (designsystem 208 / transactionslist 105 / app 53), 0 failures. On-device visual check of Edit screen deferred to Pixel_5_API_34 run.
