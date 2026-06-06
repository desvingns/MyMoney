# Поднять циферблат, увеличить кнопку «ВЫБОР КАТЕГОРИИ» (п.1)
Epic: monefy-ux-fixes
Order: 02 of 07
Status: done
Depends-on: 01
Date: 2026-06-06

## SPEC
=== SPEC ===
TASK: feature
PLATFORM: android
WHAT: В общей TransactionFormContent (после SPEC-01) переработать нижнюю область шага суммы так, чтобы убрать пустое пространство между полем «Заметка» и клавиатурой: клавиатура прижата сразу под «Заметкой» (без верхнего зазора), а кнопка «ВЫБОР КАТЕГОРИИ» получает всю освободившуюся вертикаль и становится крупным заметным CTA.
LAYERS: presentation
CHANGED_HINT: core/designsystem/.../form/TransactionFormContent.kt — только нижняя область шага суммы (categoryStep=false): убрать Arrangement.Bottom у внешнего Column; клавиатуру оставить естественной высоты вверху сразу под заметкой; кнопке дать рост на освободившееся место (Modifier.weight(1f) у кнопки или увеличенная min-height)
TEST_TYPES: compose-ui
CONSTRAINTS:
  - Кнопка активна только при amount > 0 (как сейчас).
  - Не ломать шаг категорий (categoryStep=true) и переход BackToAmount.
  - Высота кнопки — разумный минимум; проверить, что на узком/коротком экране ничего не выходит за границы и клавиатура не сжимается до неюзабельной.
  - EN+RU строка choose_category_button без изменений; токены, не хардкод; идентификаторы английские.
=== END SPEC ===

## Gap / context
В шаге categoryStep=false внешний Column использует Arrangement.Bottom (AddIncomeScreen.kt:192-211 до
рефактора SPEC-01), из-за чего весь вертикальный слэк падает НАД клавиатурой — между «Заметкой» и
циферблатом образуется пустота, а кнопка прижата к низу. Кнопка уже fillMaxWidth(), поэтому «шире» =
крупнее по вертикали за счёт этой пустоты. Замечание пользователя №1.

## Implementation links
- commit: 9590e67, a578380, a6439a7
- files: core/ui/src/main/java/com/kshavrin/mymoney/core/ui/theme/Spacing.kt; core/designsystem/src/main/java/com/kshavrin/mymoney/core/designsystem/form/TransactionFormContent.kt; core/designsystem/src/androidTest/java/com/kshavrin/mymoney/core/designsystem/form/TransactionFormContentUiTest.kt
