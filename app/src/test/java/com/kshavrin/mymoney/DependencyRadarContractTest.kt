package com.kshavrin.mymoney

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DependencyRadarContractTest {
    private val repositoryRoot = findRepositoryRoot()
    private val renovateFile = File(repositoryRoot, "renovate.json")
    private val readmeFile = File(repositoryRoot, "README.md")
    private val workflowFile = File(repositoryRoot, ".github/workflows/ci.yml")

    @Test
    fun `renovate config is valid and watches only the gradle version catalog`() {
        val config = readRenovateConfig()

        assertEquals(
            "https://docs.renovatebot.com/renovate-schema.json",
            config["\$schema"]?.jsonPrimitive?.content,
        )
        assertEquals(listOf("gradle"), config.stringArray("enabledManagers"))
        assertEquals(
            setOf(
                "**/*.gradle",
                "**/*.gradle.kts",
                "**/gradle.properties",
                "build-logic/**",
            ),
            config.stringArray("ignorePaths").toSet(),
        )
        assertTrue(File(repositoryRoot, "gradle/libs.versions.toml").isFile)
        assertFalse(config.stringArray("ignorePaths").contains("gradle/libs.versions.toml"))
    }

    @Test
    fun `renovate config is monthly dashboard approved grouped and low noise`() {
        val config = readRenovateConfig()

        assertEquals(listOf("* 0-3 1 * *"), config.stringArray("schedule"))
        assertEquals("Europe/Budapest", config["timezone"]?.jsonPrimitive?.content)
        assertEquals(true, config["dependencyDashboard"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(true, config["dependencyDashboardApproval"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals(1, config["prConcurrentLimit"]?.jsonPrimitive?.content?.toInt())
        assertEquals(1, config["prHourlyLimit"]?.jsonPrimitive?.content?.toInt())
        assertEquals(
            setOf("dependencies", "dependency-radar"),
            config.stringArray("labels").toSet(),
        )
        assertEquals(
            setOf("dependencies", "dependency-radar"),
            config.stringArray("dependencyDashboardLabels").toSet(),
        )

        val gradleGrouping =
            config
                .jsonArray("packageRules")
                .first { rule ->
                    rule.jsonObject["groupName"]?.jsonPrimitive?.content == "Gradle version catalog"
                }.jsonObject
        assertEquals(listOf("gradle"), gradleGrouping.stringArray("matchManagers"))
        assertEquals("gradle-version-catalog", gradleGrouping["groupSlug"]?.jsonPrimitive?.content)
    }

    @Test
    fun `renovate config keeps automerge off and marks major updates for tdd review`() {
        val config = readRenovateConfig()
        val automergeValues = config.findValues("automerge")

        assertTrue(automergeValues.isNotEmpty())
        assertTrue(
            "Every automerge property must be explicitly false",
            automergeValues.all { value ->
                value is JsonPrimitive && value.content == "false"
            },
        )

        val majorRule =
            config
                .jsonArray("packageRules")
                .first { rule ->
                    rule.jsonObject["matchUpdateTypes"]
                        ?.jsonArray
                        ?.map { it.jsonPrimitive.content } == listOf("major")
                }.jsonObject
        assertEquals(listOf("gradle"), majorRule.stringArray("matchManagers"))
        assertEquals(listOf("tdd-revision-required"), majorRule.stringArray("addLabels"))
        assertContainsAll(
            config["prHeader"]?.jsonPrimitive?.content.orEmpty(),
            listOf(
                "report-only radar never automerges",
                "Do not merge an update until its impact is reviewed",
                "every major-version update also requires an explicit revision of `TDD/MyMoney/MyMoney_TDD.md`",
            ),
        )
    }

    @Test
    fun `readme documents the tdd locked report only policy and radar labels`() {
        val readme = readmeFile.readText()

        assertContainsAll(
            readme,
            listOf(
                "Renovate monitors the versions declared in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) on a monthly schedule",
                "It is a report-only radar",
                "Dependency Dashboard",
                "human approval",
                "grouped Gradle pull request",
                "Renovate never automerges these pull requests",
                "The Android stack is locked by [`TDD/MyMoney/MyMoney_TDD.md`](TDD/MyMoney/MyMoney_TDD.md)",
                "A major-version update requires an explicit TDD revision first",
                "a dependency radar pull request is information, not authorization",
                "`dependencies` and `dependency-radar` labels",
                "`tdd-revision-required`",
                "skips only the emulator-heavy connected-test CI job; JVM checks still run",
            ),
        )
        assertTrue(File(repositoryRoot, "TDD/MyMoney/MyMoney_TDD.md").isFile)
    }

    @Test
    fun `ci isolates secrets and skips only emulator work for radar pull requests`() {
        val workflow = workflowFile.readText()
        val connectedJob = workflow.substringAfter("  connected:")
        val jvmJob = workflow.substringAfter("  jvm:").substringBefore("\n  release:")

        assertContainsAll(
            connectedJob,
            listOf(
                "if: >-",
                "github.event_name != 'pull_request' ||",
                "!(github.event.pull_request.user.login == 'renovate[bot]' ||",
                "contains(github.event.pull_request.labels.*.name, 'dependency-radar'))",
            ),
        )
        assertEquals(
            3,
            Regex("if: github.event_name != 'pull_request'").findAll(connectedJob).count(),
        )
        assertContainsAll(
            jvmJob,
            listOf(
                "run: ./gradlew lintDebug testDebugUnitTest \$FIREBASE_ARGS --stacktrace",
                "run: ./gradlew detekt --stacktrace",
                "run: ./gradlew ktlintCheck --stacktrace",
                "run: ./gradlew koverVerify --stacktrace",
            ),
        )
        assertFalse(jvmJob.contains("if: >-"))
    }

    private fun readRenovateConfig(): JsonObject {
        assertTrue("Missing ${renovateFile.path}", renovateFile.isFile)
        return Json.parseToJsonElement(renovateFile.readText()).jsonObject
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private fun JsonObject.stringArray(name: String): List<String> =
        requireNotNull(this[name]).jsonArray.map { it.jsonPrimitive.content }

    private fun JsonObject.jsonArray(name: String): JsonArray = requireNotNull(this[name]).jsonArray

    private fun JsonElement.findValues(name: String): List<JsonElement> =
        when (this) {
            is JsonObject ->
                entries.flatMap { (key, value) ->
                    (if (key == name) listOf(value) else emptyList()) + value.findValues(name)
                }
            is JsonArray -> flatMap { it.findValues(name) }
            else -> emptyList()
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
