package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidSupportPolicyContractTest {
    private val repositoryRoot = findRepositoryRoot()
    private val agentsFile = File(repositoryRoot, "AGENTS.md")
    private val supportMatrixFile = File(repositoryRoot, "docs/ANDROID_SUPPORT_MATRIX.md")
    private val workflowFile = File(repositoryRoot, ".github/workflows/ci.yml")

    @Test
    fun `AGENTS_md does not reference minSdk 31`() {
        val text = agentsFile.readText()
        assertFalse(
            "AGENTS.md must not contain 'minSdk: 31'; the floor was lowered to API 29",
            text.contains("minSdk: 31"),
        )
    }

    @Test
    fun `support matrix lists API 29 and API 30 as actively supported`() {
        assertTrue(
            "docs/ANDROID_SUPPORT_MATRIX.md must exist",
            supportMatrixFile.isFile,
        )
        val text = supportMatrixFile.readText()
        assertContains(text, "API 29", "API 29 must be listed in the support matrix")
        assertContains(text, "API 30", "API 30 must be listed in the support matrix")
        assertContains(text, "Actively supported", "support matrix must mark at least one entry as actively supported")
    }

    @Test
    fun `support matrix names the legacy validation follow-up order`() {
        val text = supportMatrixFile.readText()
        assertContains(
            text,
            "android-10-11-legacy-device-validation",
            "support matrix must name the follow-up order 'android-10-11-legacy-device-validation'",
        )
    }

    @Test
    fun `support matrix does not present API 34 evidence as API 29 or 30 validation`() {
        val text = supportMatrixFile.readText()
        assertFalse(
            "support matrix must not claim that Pixel 5/API 34 results validate API 29 behaviour",
            Regex("(?i)api.?34.{0,80}validate.{0,40}api.?29").containsMatchIn(text),
        )
        assertFalse(
            "support matrix must not claim that Pixel 5/API 34 results validate API 30 behaviour",
            Regex("(?i)api.?34.{0,80}validate.{0,40}api.?30").containsMatchIn(text),
        )
        assertContains(
            text,
            "API 34 regression evidence only",
            "support matrix must explicitly state that API 34 runs are regression evidence only",
        )
    }

    @Test
    fun `ci workflow retains lintDebug and testDebugUnitTest in the jvm job`() {
        val jvmJob = workflowFile.readText().substringAfter("  jvm:").substringBefore("\n  release:")
        assertContains(
            jvmJob,
            "lintDebug",
            "ci.yml jvm job must still run lintDebug",
        )
        assertContains(
            jvmJob,
            "testDebugUnitTest",
            "ci.yml jvm job must still run testDebugUnitTest",
        )
    }

    @Test
    fun `ci workflow retains API 34 connected module checks`() {
        val workflow = workflowFile.readText()
        assertContains(workflow, "  connected:", "ci.yml must still have a connected job")
        assertContains(
            workflow,
            "api-level: 34",
            "ci.yml connected job must still target API 34",
        )
        assertContainsInOrder(
            workflow.substringAfter("  connected:"),
            listOf(
                ":app:connectedDebugAndroidTest",
                ":core:designsystem:connectedDebugAndroidTest",
                ":core:database:connectedDebugAndroidTest",
                ":core:datastore:connectedDebugAndroidTest",
            ),
        )
    }

    private fun assertContains(
        text: String,
        fragment: String,
        message: String = "Expected to find '$fragment'",
    ) {
        assertTrue(message, text.contains(fragment))
    }

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
        fun findRepositoryRoot(): File =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile
                }
                ?: error("Unable to locate the repository root from the test working directory")
    }
}
