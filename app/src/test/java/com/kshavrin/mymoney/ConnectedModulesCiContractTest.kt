package com.kshavrin.mymoney

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedModulesCiContractTest {
    private val workflowFile =
        resolveFile(
            "../.github/workflows/ci.yml",
            ".github/workflows/ci.yml",
        )

    @Test
    fun `connected job runs every module sequentially with per module timeouts and failure aggregation`() {
        val script = connectedTestScript(workflowFile.readText())

        assertContainsInOrder(
            script,
            listOf(
                "status=0",
                "timeout 30m ./gradlew :app:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "timeout 20m ./gradlew :core:designsystem:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "timeout 20m ./gradlew :core:database:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "timeout 20m ./gradlew :core:datastore:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "timeout 20m ./gradlew :core:sync:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "timeout 20m ./gradlew :core:network:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "timeout 20m ./gradlew :feature:lockscreen:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || status=\$?",
                "exit \$status",
            ),
        )
    }

    @Test
    fun `connected reports upload even when a module fails`() {
        val text = workflowFile.readText()
        val uploadStep = text.substringAfter("      - name: Upload connected test reports")

        assertContainsInOrder(
            uploadStep,
            listOf(
                "if: always()",
                "uses: actions/upload-artifact@v4",
                "name: connected-android-test-reports",
                "path: \"**/build/reports/androidTests/connected/**\"",
                "if-no-files-found: warn",
            ),
        )
    }

    private fun connectedTestScript(workflow: String): String =
        workflow
            .substringAfter("      - name: Run connected tests on API 34 emulator")
            .substringBefore("      - name: Upload connected test reports")

    private fun assertContainsInOrder(
        text: String,
        fragments: List<String>,
    ) {
        var startIndex = 0
        fragments.forEach { fragment ->
            val index = text.indexOf(fragment, startIndex)
            assertTrue("Expected to find '$fragment' after index $startIndex", index >= 0)
            startIndex = index + fragment.length
        }
    }

    private companion object {
        fun resolveFile(vararg candidates: String): File =
            candidates
                .asSequence()
                .map(::File)
                .firstOrNull(File::isFile)
                ?: File(candidates.first()).absoluteFile
    }
}
