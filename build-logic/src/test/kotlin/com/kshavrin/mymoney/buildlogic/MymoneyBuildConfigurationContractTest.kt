package com.kshavrin.mymoney.buildlogic

import java.io.File
import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MymoneyBuildConfigurationContractTest {
    @Test
    fun `all migrated modules use the intended convention plugin`() {
        expectedModulePlugins.forEach { (path, pluginAlias) ->
            val text = file(path).readText()
            assertTrue(
                "$path must apply libs.plugins.$pluginAlias",
                text.contains("alias(libs.plugins.$pluginAlias)"),
            )
            legacyBasePluginAliases.forEach { legacyAlias ->
                assertFalse(
                    "$path must not apply the legacy base plugin $legacyAlias",
                    text.contains("alias(libs.plugins.$legacyAlias)"),
                )
            }
        }
    }

    @Test
    fun `isolated build logic is included and its plugin aliases use the canonical catalog`() {
        val settings = file("settings.gradle.kts").readText()
        val catalog = file("gradle/libs.versions.toml").readText()
        val buildLogic = file("build-logic/build.gradle.kts").readText()
        val buildLogicSettings = file("build-logic/settings.gradle.kts").readText()

        assertTrue(settings.contains("includeBuild(\"build-logic\")"))
        assertTrue(buildLogicSettings.contains("from(files(\"../gradle/libs.versions.toml\"))"))
        expectedConventionAliases.forEach { alias ->
            assertTrue(catalog.contains("$alias = { id = \"mymoney.${alias.removePrefix("mymoney-").replace('-', '.') }\" }"))
        }
        listOf(
            "implementation(libs.android.gradle.plugin)",
            "implementation(libs.hilt.gradle.plugin)",
            "implementation(libs.kotlin.compose.compiler.gradle.plugin)",
            "implementation(libs.kotlin.gradle.plugin)",
            "implementation(libs.ksp.gradle.plugin)",
        ).forEach { dependency -> assertTrue(buildLogic.contains(dependency)) }
    }

    @Test
    fun `SDK and JVM toolchain values are owned by the catalog and convention implementation`() {
        val catalog = file("gradle/libs.versions.toml").readText()
        val rootBuild = file("build.gradle.kts").readText()
        val convention = file("build-logic/src/main/kotlin/com/kshavrin/mymoney/buildlogic/MymoneyConventionPlugins.kt").readText()

        listOf(
            "androidCompileSdk = \"36\"",
            "androidMinSdk = \"31\"",
            "androidTargetSdk = \"36\"",
            "jvmToolchain = \"17\"",
        ).forEach { version -> assertTrue(catalog.contains(version)) }
        assertTrue(convention.contains("configureJavaToolchain(libs.versionInt(\"jvmToolchain\"))"))
        assertTrue(convention.contains("configureKotlinAndroidToolchain(libs.versionInt(\"jvmToolchain\"))"))
        assertTrue(convention.contains("jvmToolchain(libs.versionInt(\"jvmToolchain\"))"))
        assertTrue(convention.contains("compose = true"))
        assertFalse(rootBuild.contains("KotlinCompile"))

        expectedModulePlugins.keys.forEach { path ->
            val module = file(path).readText()
            assertFalse("$path must not declare its own JVM toolchain", module.contains("jvmToolchain("))
            assertFalse("$path must not declare its own Java compile options", module.contains("compileOptions {"))
        }
    }

    @Test
    fun `application-specific identity and git-derived semver versioning remain in the app module`() {
        val app = file("app/build.gradle.kts").readText()
        val defaultConfig = section(app, "defaultConfig {")

        assertTrue(app.contains("applicationId = \"com.kshavrin.mymoney\""))
        assertContainsInOrder(
            app,
            listOf(
                "Release versioning is derived from the checked-out Git history:",
                "val hasCompleteGitHistory = gitOutput(\"rev-parse\", \"--is-shallow-repository\") == \"false\"",
                "val releaseTagPattern = Regex(\"\"\"^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\$\"\"\")",
                ".filter { it.matches(releaseTagPattern) }",
                "val allValidReleaseTags =",
                "gitOutput(\"tag\", \"--list\")",
                "val reachableValidReleaseTags =",
                "gitOutput(\"tag\", \"--merged\", \"HEAD\")",
                "val validReleaseTagsAtHead =",
                "gitOutput(\"tag\", \"--points-at\", \"HEAD\")",
                "fun releaseVersionPart(tag: String, group: Int): BigInteger",
                "BigInteger(releaseTagPattern.matchEntire(tag)!!.groupValues[group])",
                "fun latestReleaseTag(tags: List<String>): String?",
                "tags.maxWithOrNull(",
                "compareBy<String> { releaseVersionPart(it, 1) }",
                ".thenBy { releaseVersionPart(it, 2) }",
                ".thenBy { releaseVersionPart(it, 3) }",
                "val latestGlobalReleaseTag = latestReleaseTag(allValidReleaseTags)",
                "val latestReachableReleaseTag = latestReleaseTag(reachableValidReleaseTags)",
                "fun releaseVersionCode(tag: String): Int",
                "val maxReleaseMajor = BigInteger.valueOf(2_146L)",
                "val maxReleaseMinor = BigInteger.valueOf(999L)",
                "val maxReleasePatch = BigInteger.valueOf(999L)",
                "val releaseCodeMajorMultiplier = BigInteger.valueOf(1_000_000L)",
                "val releaseCodeMinorMultiplier = BigInteger.valueOf(1_000L)",
                "val releaseCodeMigrationOffset = BigInteger.valueOf(1_077L)",
                "val versionCode =",
                ".multiply(releaseCodeMajorMultiplier)",
                ".add(minor.multiply(releaseCodeMinorMultiplier))",
                ".add(patch)",
                ".add(releaseCodeMigrationOffset)",
                "require(versionCode <= BigInteger.valueOf(Int.MAX_VALUE.toLong()))",
                "return versionCode.toInt()",
                "val debugVersionCodeFallback = 1_001_077",
                "val appVersionCode =",
                "?: debugVersionCodeFallback",
                "val appVersionName = latestReachableReleaseTag?.removePrefix(\"v\") ?: \"0.0.0-dev\"",
                "val releaseVersioningReady =",
                "releaseTagAtHead == latestReachableReleaseTag",
                "releaseTagAtHead == latestGlobalReleaseTag",
                "fun isNonDebugPackagingTask(taskPath: String): Boolean",
                "taskName.startsWith(\"build\")",
                "gradle.taskGraph.whenReady",
                "taskGraph.allTasks.any { isNonDebugPackagingTask(it.path) }",
            ),
        )
        assertEquals(1, defaultConfig.lines().count { it.trimStart().startsWith("versionCode =") })
        assertEquals(1, defaultConfig.lines().count { it.trimStart().startsWith("versionName =") })
        assertTrue(defaultConfig.contains("versionCode = appVersionCode"))
        assertTrue(defaultConfig.contains("versionName = appVersionName"))
        assertFalse(defaultConfig.contains("versionCode = 1"))
        assertFalse(defaultConfig.contains("versionName = \"1.0\""))
        assertFalse(app.contains("gitOutput(\"rev-list\""))
        assertFalse(app.contains("gitOutput(\"describe\""))
        assertFalse(app.contains("compileSdk = 36"))
        assertFalse(app.contains("minSdk = 31"))
        assertFalse(app.contains("targetSdk = 36"))

        val releaseCodes =
            listOf(
                sourceVersionCode(app, 1, 0, 0),
                sourceVersionCode(app, 1, 0, 1),
                sourceVersionCode(app, 1, 1, 0),
                sourceVersionCode(app, 2, 0, 0),
            )
        assertTrue(releaseCodes.zipWithNext().all { (lower, higher) -> lower < higher })
        assertEquals(1_001_077, releaseCodes.first())
        assertEquals(2_001_077, releaseCodes.last())
        assertTrue(sourceVersionCode(app, 2_146, 999, 999) <= Int.MAX_VALUE)
        listOf(
            listOf(2_147, 0, 0),
            listOf(0, 1_000, 0),
            listOf(0, 0, 1_000),
        ).forEach { (major, minor, patch) ->
            assertTrue(
                "Expected out-of-range SemVer component to be rejected",
                runCatching { sourceVersionCode(app, major, minor, patch) }.isFailure,
            )
        }
        assertContainsInOrder(
            app,
            listOf(
                "val match = requireNotNull(releaseTagPattern.matchEntire(tag))",
                "require(major <= maxReleaseMajor)",
                "require(minor <= maxReleaseMinor)",
                "require(patch <= maxReleasePatch)",
            ),
        )

        val changelog = file("CHANGELOG.md").readText()
        assertContainsInOrder(
            changelog,
            listOf(
                "# Changelog",
                "The format is based on [Keep a Changelog]",
                "## [Unreleased]",
                "## [1.0.0] - ",
                "### Added",
                "v1.0 baseline:",
                "Journal sync:",
                "Dashboard summary:",
                "Aurora:",
            ),
        )
    }

    private fun sourceVersionCode(
        app: String,
        major: Int,
        minor: Int,
        patch: Int,
    ): Int {
        val majorMultiplier = declaredBigInteger(app, "releaseCodeMajorMultiplier")
        val minorMultiplier = declaredBigInteger(app, "releaseCodeMinorMultiplier")
        val migrationOffset = declaredBigInteger(app, "releaseCodeMigrationOffset")
        val maxMajor = declaredBigInteger(app, "maxReleaseMajor")
        val maxMinor = declaredBigInteger(app, "maxReleaseMinor")
        val maxPatch = declaredBigInteger(app, "maxReleasePatch")

        require(BigInteger.valueOf(major.toLong()) <= maxMajor)
        require(BigInteger.valueOf(minor.toLong()) <= maxMinor)
        require(BigInteger.valueOf(patch.toLong()) <= maxPatch)
        return BigInteger.valueOf(major.toLong())
            .multiply(majorMultiplier)
            .add(BigInteger.valueOf(minor.toLong()).multiply(minorMultiplier))
            .add(BigInteger.valueOf(patch.toLong()))
            .add(migrationOffset)
            .intValueExact()
    }

    private fun declaredBigInteger(
        text: String,
        name: String,
    ): BigInteger =
        Regex("""val $name = BigInteger\.valueOf\(([0-9_]+)L\)""")
            .find(text)
            ?.groupValues
            ?.get(1)
            ?.replace("_", "")
            ?.let { BigInteger(it) }
            ?: error("Expected $name to be declared as a BigInteger constant")

    private fun section(
        text: String,
        header: String,
    ): String {
        val start = text.indexOf(header)
        assertTrue("Expected section '$header'", start >= 0)
        val end = text.indexOf("\n    }", startIndex = start)
        assertTrue("Expected section '$header' to close", end > start)
        return text.substring(start, end)
    }

    private fun assertContainsInOrder(
        text: String,
        fragments: List<String>,
    ) {
        var startIndex = 0
        fragments.forEach { fragment ->
            val index = text.indexOf(fragment, startIndex = startIndex)
            assertTrue("Expected to find '$fragment' after index $startIndex", index >= 0)
            startIndex = index + fragment.length
        }
    }

    private fun file(path: String): File = File(repositoryRoot, path)

    private companion object {
        val expectedModulePlugins =
            linkedMapOf(
                "app/build.gradle.kts" to "mymoney.android.application",
                "core/common/build.gradle.kts" to "mymoney.jvm.library",
                "core/database/build.gradle.kts" to "mymoney.android.library",
                "core/datastore/build.gradle.kts" to "mymoney.android.library",
                "core/designsystem/build.gradle.kts" to "mymoney.android.library",
                "core/domain/build.gradle.kts" to "mymoney.jvm.library",
                "core/network/build.gradle.kts" to "mymoney.android.library",
                "core/sync/build.gradle.kts" to "mymoney.android.library",
                "core/testing/build.gradle.kts" to "mymoney.android.library",
                "core/ui/build.gradle.kts" to "mymoney.android.library",
                "feature/cloudsync/build.gradle.kts" to "mymoney.android.feature",
                "feature/dashboard/build.gradle.kts" to "mymoney.android.feature",
                "feature/dictionaries/build.gradle.kts" to "mymoney.android.feature",
                "feature/lockscreen/build.gradle.kts" to "mymoney.android.feature",
                "feature/onboarding/build.gradle.kts" to "mymoney.android.feature",
                "feature/settings/build.gradle.kts" to "mymoney.android.feature",
                "feature/transaction/build.gradle.kts" to "mymoney.android.feature",
                "feature/transactionslist/build.gradle.kts" to "mymoney.android.feature",
                "macrobenchmark/build.gradle.kts" to "mymoney.android.test",
            )
        val legacyBasePluginAliases =
            listOf(
                "android.application",
                "android.library",
                "kotlin.android",
                "kotlin.jvm",
            )
        val expectedConventionAliases =
            listOf(
                "mymoney-android-application",
                "mymoney-android-feature",
                "mymoney-android-library",
                "mymoney-android-test",
                "mymoney-jvm-library",
            )
        val repositoryRoot: File =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile
                }
                ?: error("Unable to locate the repository root from the test working directory")
    }
}
