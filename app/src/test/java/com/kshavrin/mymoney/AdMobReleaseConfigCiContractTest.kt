package com.kshavrin.mymoney

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AdMobReleaseConfigCiContractTest {
    private val repositoryRoot = findRepositoryRoot()
    private val appBuild = File(repositoryRoot, "app/build.gradle.kts")
    private val adsBuild = File(repositoryRoot, "core/ads/build.gradle.kts")
    private val workflow = File(repositoryRoot, ".github/workflows/ci.yml")

    @Test
    fun `release and play internal jobs materialize AdMob ids or fail the job`() {
        val text = workflow.readText().replace("\r\n", "\n")

        listOf("\n  release:", "\n  play-internal:").forEach { jobHeader ->
            val job = jobText(text, jobHeader)
            assertContainsAll(
                job,
                listOf(
                    "name: Materialize AdMob release configuration",
                    "ADMOB_APPLICATION_ID: \${{ secrets.ADMOB_APPLICATION_ID }}",
                    "ADMOB_REWARDED_UNIT_ID: \${{ secrets.ADMOB_REWARDED_UNIT_ID }}",
                    "Missing AdMob release secrets",
                    "admob.applicationId=%s",
                    "admob.rewardedUnitId=%s",
                ),
            )
            assertTrue(
                "AdMob step must run before the Gradle packaging step in $jobHeader",
                job.indexOf("Materialize AdMob release configuration") in 0 until job.indexOf("./gradlew"),
            )
        }
    }

    @Test
    fun `non debug packaging refuses placeholder or Google test AdMob ids`() {
        val text = appBuild.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "fun requireAdsRuntimeConfiguration()",
                "\"AdMob application ID\", \"admob.applicationId\"",
                "\"AdMob rewarded unit ID\", \"admob.rewardedUnitId\"",
                "ca-app-pub-3940256099942544",
                "if (adsEnabled && gradle.startParameter.taskNames.any(::isNonDebugPackagingTask))",
                "if (adsEnabled && taskGraph.allTasks.any { isNonDebugPackagingTask(it.path) })",
            ),
        )
    }

    @Test
    fun `ads module still falls back to placeholders that the app guard rejects`() {
        val text = adsBuild.readText().replace("\r\n", "\n")

        assertContainsAll(
            text,
            listOf(
                "runtimeProperty(\"admob.applicationId\") ?: \"PLACEHOLDER_ADMOB_APPLICATION_ID\"",
                "runtimeProperty(\"admob.rewardedUnitId\") ?: \"PLACEHOLDER_ADMOB_REWARDED_UNIT_ID\"",
                "runtimeProperty(\"ads.enabled\")?.toBooleanStrictOrNull() ?: true",
            ),
        )
    }

    private fun jobText(
        workflow: String,
        jobHeader: String,
    ): String {
        val start = workflow.indexOf(jobHeader)
        assertTrue("Expected '$jobHeader' job in the workflow", start >= 0)
        val end =
            Regex("""\n  [A-Za-z][A-Za-z0-9_-]*:""")
                .find(workflow, startIndex = start + 1)
                ?.range
                ?.first
                ?: workflow.length
        return workflow.substring(start, end)
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment' in ${text.take(200)}...", text.contains(fragment))
        }
    }

    private companion object {
        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile &&
                        File(candidate, ".github/workflows/ci.yml").isFile
                } ?: start
        }
    }
}
