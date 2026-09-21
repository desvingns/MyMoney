package com.kshavrin.mymoney.core.designsystem.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kshavrin.mymoney.core.designsystem.R
import com.kshavrin.mymoney.core.designsystem.amountinput.AmountInput
import com.kshavrin.mymoney.core.designsystem.keypad.Keypad
import com.kshavrin.mymoney.core.designsystem.keypad.KeypadEvent
import com.kshavrin.mymoney.core.ui.theme.Spacing
import com.kshavrin.mymoney.core.ui.theme.transactionFormDeleteContainer
import com.kshavrin.mymoney.core.ui.theme.transactionFormDeleteContent

@Composable
fun TransactionFormContent(
    state: TransactionFormState,
    onEvent: (TransactionFormEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(Spacing.m),
    ) {
        DateHeader(
            date = state.occurredAt,
            onClick = { onEvent(TransactionFormEvent.DateHeaderClicked) },
        )

        if (state.categoryStep) {
            AmountEntrySection(
                state = state,
                onEvent = onEvent,
                showNote = false,
                amountInputModifier = Modifier.clickable { onEvent(TransactionFormEvent.BackToAmount) },
                modifier = Modifier.padding(top = Spacing.m),
            )
            CategoryGrid(
                categories = state.categories,
                onCategoryClick = { onEvent(TransactionFormEvent.CategoryPicked(it)) },
                onAddClick = { onEvent(TransactionFormEvent.AddCategoryClicked) },
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(top = Spacing.m),
            )
        } else if (state.mode == TransactionFormMode.Edit) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                AmountEntrySection(
                    state = state,
                    onEvent = onEvent,
                    showNote = true,
                    amountInputModifier = Modifier,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                Keypad(
                    onEvent = { onEvent(TransactionFormEvent.Keypad(it)) },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
                )
                ChooseCategoryButton(
                    state = state,
                    onClick = { onEvent(TransactionFormEvent.SelectCategoryClicked) },
                    modifier = Modifier.padding(top = Spacing.s),
                )
                DeleteButton(
                    onClick = { onEvent(TransactionFormEvent.DeleteClicked) },
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).padding(top = Spacing.m),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AmountInputField(
                    state = state,
                    onEvent = onEvent,
                    amountInputModifier = Modifier,
                )
                NoteField(
                    note = state.note,
                    onNoteChange = { onEvent(TransactionFormEvent.NoteChanged(it)) },
                )
                Keypad(
                    onEvent = { onEvent(TransactionFormEvent.Keypad(it)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ChooseCategoryButton(
                    state = state,
                    onClick = { onEvent(TransactionFormEvent.SelectCategoryClicked) },
                )
                Spacer(modifier = Modifier)
            }
        }
    }
}

@Composable
private fun ChooseCategoryButton(
    state: TransactionFormState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = state.chooseCategoryEnabled,
        modifier = modifier.fillMaxWidth().height(Spacing.transactionFormChooseCategoryHeight),
    ) {
        Text(
            text = stringResource(R.string.transaction_form_choose_category_button),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun DeleteButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.transactionFormDeleteContainer,
                contentColor = MaterialTheme.colorScheme.transactionFormDeleteContent,
            ),
        modifier =
            modifier
                .fillMaxWidth()
                .height(Spacing.transactionFormDeleteButtonHeight)
                .testTag(TRANSACTION_FORM_DELETE_TAG),
    ) {
        Icon(
            imageVector = Icons.Filled.Delete,
            contentDescription = null,
        )
        Text(
            text = stringResource(R.string.transaction_form_delete_button),
            modifier = Modifier.padding(start = Spacing.s),
        )
    }
}

const val TRANSACTION_FORM_DELETE_TAG = "transaction_form_delete"

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
        AmountInputField(
            state = state,
            onEvent = onEvent,
            amountInputModifier = amountInputModifier,
        )
        if (showNote) {
            NoteField(
                note = state.note,
                onNoteChange = { onEvent(TransactionFormEvent.NoteChanged(it)) },
            )
        }
    }
}

@Composable
private fun AmountInputField(
    state: TransactionFormState,
    onEvent: (TransactionFormEvent) -> Unit,
    amountInputModifier: Modifier,
) {
    AmountInput(
        display = state.amountInput,
        expression = state.expression,
        currencyCode = state.currencyCode,
        currencySymbol = state.currencySymbol,
        onClear = { onEvent(TransactionFormEvent.Keypad(KeypadEvent.Backspace)) },
        clearContentDescription = stringResource(R.string.keypad_backspace_cd),
        modifier = amountInputModifier.fillMaxWidth().testTag(TRANSACTION_FORM_AMOUNT_TAG),
    )
}

@Composable
private fun NoteField(
    note: String,
    onNoteChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        label = { Text(stringResource(R.string.amountfield_note_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

const val TRANSACTION_FORM_AMOUNT_TAG = "transaction_form_amount"
