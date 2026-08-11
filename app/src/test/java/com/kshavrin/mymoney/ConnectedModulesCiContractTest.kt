package com.kshavrin.mymoney

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
                "timeout 30m ./gradlew :app:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "timeout 20m ./gradlew :core:designsystem:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "timeout 20m ./gradlew :core:database:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "timeout 20m ./gradlew :core:datastore:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "timeout 20m ./gradlew :core:sync:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "timeout 20m ./gradlew :core:network:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "timeout 20m ./gradlew :feature:lockscreen:connectedDebugAndroidTest \$FIREBASE_ARGS --stacktrace || { rc=\$?; if [ \"\$status\" -eq 0 ]; then status=\$rc; fi; }",
                "exit \$status",
            ),
        )
    }

    @Test
    fun `workflow cancels only superseded pull request runs within a stable group`() {
        val topLevel = workflowFile.readText().substringBefore("\njobs:")

        assertContainsInOrder(
            topLevel,
            listOf(
                "concurrency:",
                "  group: \${{ github.workflow }}-\${{ github.event.pull_request.number || github.ref }}",
                "  cancel-in-progress: \${{ github.event_name == 'pull_request' }}",
            ),
        )
    }

    @Test
    fun `connected job waits for jvm checks before starting emulator`() {
        val connectedJob = workflowFile.readText().substringAfter("  connected:")

        assertContainsInOrder(
            connectedJob,
            listOf(
                "    needs: jvm",
                "      - name: Run connected tests on API 34 emulator",
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

    @Test
    fun `connected job skips radar pull requests while jvm checks remain enabled`() {
        val workflow = workflowFile.readText()
        val connectedJob = workflow.substringAfter("  connected:")
        val jvmJob = workflow.substringAfter("  jvm:").substringBefore("\n  release:")

        assertContainsInOrder(
            connectedJob,
            listOf(
                "if: >-",
                "github.event_name != 'pull_request' ||",
                "!(github.event.pull_request.user.login == 'renovate[bot]' ||",
                "contains(github.event.pull_request.labels.*.name, 'dependency-radar'))",
            ),
        )
        assertTrue(
            "The connected job must not skip arbitrary Renovate branches without the bot or label contract",
            !connectedJob.contains("startsWith(github.head_ref, 'renovate/')"),
        )
        assertContainsAll(
            connectedJob,
            listOf(
                "name: Materialize Sentry DSN only when the OQ-1 secret is supplied",
                "name: Materialize Dropbox app key only when the secret is supplied",
                "name: Materialize Firebase config only when the OQ-9 secret is supplied",
            ),
        )
        assertTrue(
            "Connected-job secrets must stay unavailable on every pull request",
            Regex("if: github.event_name != 'pull_request'").findAll(connectedJob).count() >= 3,
        )
        assertContainsAll(
            jvmJob,
            listOf(
                "name: Lint, JVM tests, static analysis, and coverage",
                "run: ./gradlew lintDebug testDebugUnitTest \$FIREBASE_ARGS --stacktrace",
                "run: ./gradlew detekt --stacktrace",
                "run: ./gradlew ktlintCheck --stacktrace",
                "run: ./gradlew koverVerify --stacktrace",
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

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
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
