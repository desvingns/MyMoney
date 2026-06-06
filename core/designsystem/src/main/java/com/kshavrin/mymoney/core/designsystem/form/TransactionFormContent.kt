package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.amountinput.MonefyAmountInput
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.designsystem.keypad.MonefyKeypad
import com.kshavrin.mymoney.core.ui.theme.Spacing

@Composable
fun TransactionFormContent(
    state: TransactionFormState,
    onEvent: (TransactionFormEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.m),
    ) {
        DateHeader(
            date = state.occurredAt,
            onClick = { onEvent(TransactionFormEvent.DateHeaderClicked) },
        )

        AmountEntrySection(
            state = state,
            onEvent = onEvent,
            showNote = !state.categoryStep,
            amountInputModifier = if (state.categoryStep) {
                Modifier.clickable { onEvent(TransactionFormEvent.BackToAmount) }
            } else {
                Modifier
            },
            modifier = Modifier.padding(top = Spacing.m),
        )

        if (state.categoryStep) {
            CategoryGrid(
                categories = state.categories,
                onCategoryClick = { onEvent(TransactionFormEvent.CategoryPicked(it)) },
                onAddClick = { onEvent(TransactionFormEvent.AddCategoryClicked) },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = Spacing.m),
            )
        } else {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                MonefyKeypad(
                    onEvent = { onEvent(TransactionFormEvent.Keypad(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { onEvent(TransactionFormEvent.SelectCategoryClicked) },
                    enabled = state.chooseCategoryEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = Spacing.s)
                        .defaultMinSize(minHeight = Spacing.transactionFormChooseCategoryMinHeight),
                ) {
                    Text(stringResource(R.string.transaction_form_choose_category_button))
                }
            }
        }
    }
}

@Composable
private fun AmountEntrySection(
    state: TransactionFormState,
    onEvent: (TransactionFormEvent) -> Unit,
    showNote: Boolean,
    amountInputModifier: Modifier,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        MonefyAmountInput(
            display = state.amountInput,
            expression = state.expression,
            currencyCode = state.currencyCode,
            currencySymbol = state.currencySymbol,
            onClear = { onEvent(TransactionFormEvent.Keypad(KeypadEvent.Backspace)) },
            clearContentDescription = stringResource(R.string.keypad_backspace_cd),
            modifier = amountInputModifier.fillMaxWidth(),
        )
        if (showNote) {
            OutlinedTextField(
                value = state.note,
                onValueChange = { onEvent(TransactionFormEvent.NoteChanged(it)) },
                label = { Text(stringResource(R.string.amountfield_note_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
