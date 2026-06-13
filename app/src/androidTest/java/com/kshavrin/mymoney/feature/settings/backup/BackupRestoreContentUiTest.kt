package com.kshavrin.mymoney.feature.settings.backup

import android.text.format.Formatter
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kshavrin.mymoney.core.ui.theme.MyMoneyTheme
import com.kshavrin.mymoney.feature.settings.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreContentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `back button invokes backup restore back callback`() {
        var backed = false

        setContent(onBack = { backed = true })

        composeTestRule
            .onNodeWithContentDescription(targetString(R.string.settings_back))
            .performClick()

        composeTestRule.runOnIdle {
            assertTrue(backed)
        }
    }

    @Test
    fun `idle backup restore action buttons invoke their callbacks`() {
        val invoked = mutableListOf<String>()

        setContent(
            state = BackupRestoreState(dbSizeBytes = 1024L),
            onExport = { invoked += "export-db" },
            onImport = { invoked += "import-db" },
            onExportCsv = { invoked += "export-csv" },
            onImportCsv = { invoked += "import-csv" },
            onRequestReset = { invoked += "reset-request" },
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.backup_size_label, formattedSize(1024L)))
            .assertIsDisplayed()

        listOf(
            R.string.backup_export_db,
            R.string.backup_import_db,
            R.string.backup_export_csv,
            R.string.backup_import_csv,
            R.string.backup_reset_cta,
        ).forEach { labelRes ->
            composeTestRule
                .onNodeWithText(targetString(labelRes))
                .assertIsEnabled()
                .performClick()
        }

        composeTestRule.runOnIdle {
            assertEquals(
                listOf("export-db", "import-db", "export-csv", "import-csv", "reset-request"),
                invoked,
            )
        }
    }

    @Test
    fun `backup restore action buttons are disabled while work is in progress`() {
        setContent(state = BackupRestoreState(inProgress = true))

        listOf(
            R.string.backup_export_db,
            R.string.backup_import_db,
            R.string.backup_export_csv,
            R.string.backup_import_csv,
            R.string.backup_reset_cta,
        ).forEach { labelRes ->
            composeTestRule
                .onNodeWithText(targetString(labelRes))
                .assertIsNotEnabled()
        }
    }

    @Test
    fun `reset confirmation dialog invokes cancel and confirm callbacks`() {
        val invoked = mutableListOf<String>()

        setContent(
            state = BackupRestoreState(resetConfirmationVisible = true),
            onDismissReset = { invoked += "cancel" },
            onConfirmReset = { invoked += "confirm" },
        )

        composeTestRule
            .onNodeWithText(targetString(R.string.backup_reset_title))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(targetString(R.string.delete_database_message))
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText(targetString(R.string.backup_reset_cancel))
            .performClick()
        composeTestRule
            .onNodeWithText(targetString(R.string.backup_reset_confirm))
            .performClick()

        composeTestRule.runOnIdle {
            assertEquals(listOf("cancel", "confirm"), invoked)
        }
    }

    @Test
    fun `backup restore error banner is displayed when present in state`() {
        setContent(state = BackupRestoreState(errorBannerRes = R.string.backup_error))

        composeTestRule
            .onNodeWithText(targetString(R.string.backup_error))
            .assertIsDisplayed()
    }

    private fun setContent(
        state: BackupRestoreState = BackupRestoreState(),
        onExport: () -> Unit = {},
        onImport: () -> Unit = {},
        onExportCsv: () -> Unit = {},
        onImportCsv: () -> Unit = {},
        onRequestReset: () -> Unit = {},
        onDismissReset: () -> Unit = {},
        onConfirmReset: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            MyMoneyTheme {
                BackupRestoreContent(
                    state = state,
                    onExport = onExport,
                    onImport = onImport,
                    onExportCsv = onExportCsv,
                    onImportCsv = onImportCsv,
                    onRequestReset = onRequestReset,
                    onDismissReset = onDismissReset,
                    onConfirmReset = onConfirmReset,
                    onBack = onBack,
                )
            }
        }
    }

    private fun targetString(
        resourceId: Int,
        vararg formatArgs: Any,
    ): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resourceId, *formatArgs)

    private fun formattedSize(bytes: Long): String =
        Formatter.formatShortFileSize(InstrumentationRegistry.getInstrumentation().targetContext, bytes)
}
