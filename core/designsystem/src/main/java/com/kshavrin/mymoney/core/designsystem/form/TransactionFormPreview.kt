package com.kshavrin.mymoney.core.designsystem.form

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import java.time.LocalDate

internal const val TRANSACTION_FORM_PREVIEW_WIDTH_DP = 360
internal const val TRANSACTION_FORM_PREVIEW_HEIGHT_DP = 720

private val transactionFormSampleDate: LocalDate = LocalDate.of(2024, 6, 15)

// The add-expense amount step: date header, amount display, calculator keypad, choose-category CTA.
internal fun transactionFormAmountStepSampleState(): TransactionFormState =
    TransactionFormState(
        amountInput = "18.40",
        expression = "",
        currencyCode = "USD",
        currencySymbol = "$",
        note = "Lunch",
        occurredAt = transactionFormSampleDate,
        categories = emptyList(),
        categoryStep = false,
        chooseCategoryEnabled = true,
    )

// The add-expense category-picker step: the amount is committed and the category grid is shown.
internal fun transactionFormCategoryStepSampleState(): TransactionFormState =
    TransactionFormState(
        amountInput = "18.40",
        expression = "",
        currencyCode = "USD",
        currencySymbol = "$",
        note = "Lunch",
        occurredAt = transactionFormSampleDate,
        categories = transactionFormSampleCategories,
        categoryStep = true,
        chooseCategoryEnabled = true,
    )

internal val transactionFormSampleCategories: List<TransactionFormCategory> =
    listOf(
        TransactionFormCategory(id = 1L, name = "Food", colorHex = "#E07AAE", iconKey = "ic_cat_food"),
        TransactionFormCategory(id = 2L, name = "Transport", colorHex = "#E07A7A", iconKey = "ic_cat_transport"),
        TransactionFormCategory(id = 3L, name = "Housing", colorHex = "#4A8FCB", iconKey = "ic_cat_housing"),
        TransactionFormCategory(id = 4L, name = "Health", colorHex = "#D85A5A", iconKey = "ic_cat_health"),
        TransactionFormCategory(id = 5L, name = "Fun", colorHex = "#F08A3E", iconKey = "ic_cat_entertainment"),
        TransactionFormCategory(id = 6L, name = "Gifts", colorHex = "#D9A4A4", iconKey = "ic_cat_gifts"),
    )

@Composable
internal fun TransactionFormSample(
    state: TransactionFormState,
    modifier: Modifier = Modifier,
) {
    TransactionFormContent(state = state, onEvent = {}, modifier = modifier)
}

@Preview(
    name = "Add-expense · amount",
    showBackground = true,
    widthDp = TRANSACTION_FORM_PREVIEW_WIDTH_DP,
    heightDp = TRANSACTION_FORM_PREVIEW_HEIGHT_DP,
)
@Preview(
    name = "Add-expense · amount · Dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = TRANSACTION_FORM_PREVIEW_WIDTH_DP,
    heightDp = TRANSACTION_FORM_PREVIEW_HEIGHT_DP,
)
@Composable
private fun TransactionFormAmountPreview() {
    MyMoneyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            TransactionFormSample(
                state = transactionFormAmountStepSampleState(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(
    name = "Add-expense · category",
    showBackground = true,
    widthDp = TRANSACTION_FORM_PREVIEW_WIDTH_DP,
    heightDp = TRANSACTION_FORM_PREVIEW_HEIGHT_DP,
)
@Preview(
    name = "Add-expense · category · Dark",
    showBackground = true,
    uiMode = UI_MODE_NIGHT_YES,
    widthDp = TRANSACTION_FORM_PREVIEW_WIDTH_DP,
    heightDp = TRANSACTION_FORM_PREVIEW_HEIGHT_DP,
)
@Composable
private fun TransactionFormCategoryPreview() {
    MyMoneyTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            TransactionFormSample(
                state = transactionFormCategoryStepSampleState(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
