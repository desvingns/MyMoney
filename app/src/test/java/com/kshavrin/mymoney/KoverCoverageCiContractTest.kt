package com.kshavrin.mymoney

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KoverCoverageCiContractTest {
    private val rootBuildFile = resolveFile("build.gradle.kts", "../build.gradle.kts")
    private val workflowFile =
        resolveFile(
            ".github/workflows/ci.yml",
            "../.github/workflows/ci.yml",
        )

    @Test
    fun `measured line floors configure the exact module ladder`() {
        val lineFloors = rootBuildFile.readText().namedBlock("koverLineFloors", "koverVerificationTasks")

        assertEquals(
            mapOf(
                ":core:domain" to 90,
                ":core:database" to 17,
                ":core:datastore" to 68,
                ":feature:cloudsync" to 41,
                ":feature:dashboard" to 33,
                ":feature:lockscreen" to 31,
                ":feature:onboarding" to 13,
                ":feature:settings" to 34,
                ":feature:transaction" to 46,
                ":feature:transactionslist" to 35,
            ),
            Regex("\\\"(:[^\\\"]+)\\\" to (\\d+)")
                .findAll(lineFloors)
                .associate { match -> match.groupValues[1] to match.groupValues[2].toInt() },
        )
    }

    @Test
    fun `every ladder module applies Kover and has the matching verification task`() {
        val rootBuild = rootBuildFile.readText()
        val verificationTasks = rootBuild.namedBlock("koverVerificationTasks", "plugins")
        val expectedTasks =
            listOf(
                ":core:domain:koverVerifyJvm",
                ":core:database:koverVerifyDebug",
                ":core:datastore:koverVerifyDebug",
                ":feature:cloudsync:koverVerifyDebug",
                ":feature:dashboard:koverVerifyDebug",
                ":feature:lockscreen:koverVerifyDebug",
                ":feature:onboarding:koverVerifyDebug",
                ":feature:settings:koverVerifyDebug",
                ":feature:transaction:koverVerifyDebug",
                ":feature:transactionslist:koverVerifyDebug",
            )

        assertEquals(
            expectedTasks,
            Regex("\\\"(:[^\\\"]+:koverVerify(?:Jvm|Debug))\\\"")
                .findAll(verificationTasks)
                .map { it.groupValues[1] }
                .toList(),
        )
        assertContains(rootBuild, "tasks.named(\"koverVerify\")")
        assertContains(rootBuild, "dependsOn(koverVerificationTasks)")
        assertEquals(
            expectedTasks.map { it.substringBeforeLast(":koverVerify") },
            Regex("kover\\(project\\(\\\"(:[^\\\"]+)\\\"\\)\\)")
                .findAll(rootBuild)
                .map { it.groupValues[1] }
                .toList(),
        )

        moduleBuildFiles.values.forEach { moduleBuildFile ->
            assertContains(resolveFile(moduleBuildFile, "../$moduleBuildFile").readText(), "alias(libs.plugins.kover)")
        }
    }

    @Test
    fun `Kover excludes generated Hilt Room and Android classes from all reports`() {
        val rootBuild = rootBuildFile.readText()
        val generatedClasses = rootBuild.namedBlock("koverGeneratedClasses", "koverLineFloors")

        assertContainsAll(
            generatedClasses,
            listOf(
                "hilt_aggregated_deps.*",
                "dagger.hilt.internal.aggregatedroot.codegen.*",
                "dagger.hilt.internal.processedrootsentinel.codegen.*",
                "*HiltComponents*",
                "*HiltModules*",
                "*HiltWrapper*",
                "*Hilt_*",
                "*_Factory",
                "*MembersInjector*",
                "*_Impl",
                "*.BuildConfig",
                "*.R",
                "*.R\$*",
            ),
        )
        assertEquals(2, Regex("classes\\(koverGeneratedClasses\\)").findAll(rootBuild).count())
    }

    @Test
    fun `CI generates and always uploads the Kover HTML report around verification`() {
        val jvmJob = workflowFile.readText().substringAfter("  jvm:").substringBefore("\n  release:")

        assertContainsInOrder(
            jvmJob,
            listOf(
                "- name: Generate Kover HTML coverage report",
                "run: ./gradlew koverHtmlReport --stacktrace",
                "- name: Verify Kover coverage thresholds",
                "run: ./gradlew koverVerify --stacktrace",
                "- name: Upload Kover HTML coverage report",
                "if: always()",
                "uses: actions/upload-artifact@v4",
                "name: kover-html-report",
                "path: build/reports/kover/html/",
                "if-no-files-found: warn",
            ),
        )
    }

    private fun String.namedBlock(
        startName: String,
        endName: String,
    ): String =
        substringAfter("private val $startName")
            .substringBefore("private val $endName")

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment -> assertContains(text, fragment) }
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

    private fun assertContains(
        text: String,
        fragment: String,
    ) {
        assertTrue("Expected to find '$fragment' in ${text.take(200)}...", text.contains(fragment))
    }

    private companion object {
        val moduleBuildFiles =
            mapOf(
                ":core:domain" to "core/domain/build.gradle.kts",
                ":core:database" to "core/database/build.gradle.kts",
                ":core:datastore" to "core/datastore/build.gradle.kts",
                ":feature:cloudsync" to "feature/cloudsync/build.gradle.kts",
                ":feature:dashboard" to "feature/dashboard/build.gradle.kts",
                ":feature:lockscreen" to "feature/lockscreen/build.gradle.kts",
                ":feature:onboarding" to "feature/onboarding/build.gradle.kts",
                ":feature:settings" to "feature/settings/build.gradle.kts",
                ":feature:transaction" to "feature/transaction/build.gradle.kts",
                ":feature:transactionslist" to "feature/transactionslist/build.gradle.kts",
            )

        fun resolveFile(vararg candidates: String): File =
            candidates
                .asSequence()
                .map(::File)
                .firstOrNull(File::isFile)
                ?: File(candidates.first()).absoluteFile
    }
}
