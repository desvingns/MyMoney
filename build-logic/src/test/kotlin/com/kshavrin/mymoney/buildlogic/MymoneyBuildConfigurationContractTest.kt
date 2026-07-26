package com.kshavrin.mymoney.buildlogic

import java.io.File
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
    fun `application-specific identity and semver versioning remain in the app module`() {
        val app = file("app/build.gradle.kts").readText()

        assertTrue(app.contains("applicationId = \"com.kshavrin.mymoney\""))
        assertContainsInOrder(
            app,
            listOf(
                "val releaseTagPattern = Regex(\"\"\"^v(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\$\"\"\")",
                "val allValidReleaseTags =",
                "gitOutput(\"tag\", \"--list\")",
                "val reachableValidReleaseTags =",
                "gitOutput(\"tag\", \"--merged\", \"HEAD\")",
                "val validReleaseTagsAtHead =",
                "gitOutput(\"tag\", \"--points-at\", \"HEAD\")",
                "val latestGlobalReleaseTag = latestReleaseTag(allValidReleaseTags)",
                "val latestReachableReleaseTag = latestReleaseTag(reachableValidReleaseTags)",
                "fun releaseVersionCode(tag: String): Int",
                "val maxReleaseMajor = BigInteger.valueOf(2_146L)",
                "val maxReleaseMinor = BigInteger.valueOf(999L)",
                "val maxReleasePatch = BigInteger.valueOf(999L)",
                "val releaseCodeMajorMultiplier = BigInteger.valueOf(1_000_000L)",
                "val releaseCodeMinorMultiplier = BigInteger.valueOf(1_000L)",
                "val releaseCodeMigrationOffset = BigInteger.valueOf(1_077L)",
                "val debugVersionCodeFallback = 1_001_077",
                "val releaseVersioningReady =",
                "releaseTagAtHead == latestReachableReleaseTag",
                "releaseTagAtHead == latestGlobalReleaseTag",
                "fun isNonDebugPackagingTask(taskPath: String): Boolean",
                "taskName.startsWith(\"build\")",
                "gradle.taskGraph.whenReady",
                "taskGraph.allTasks.any { isNonDebugPackagingTask(it.path) }",
            ),
        )
        assertTrue(app.contains("versionCode = appVersionCode"))
        assertTrue(app.contains("versionName = appVersionName"))
        assertTrue(app.contains("\"0.0.0-dev\""))
        assertFalse(app.contains("versionCode = 17"))
        assertFalse(app.contains("versionName = \"1.0.16\""))
        assertFalse(app.contains("gitOutput(\"rev-list\""))
        assertFalse(app.contains("gitOutput(\"describe\""))
        assertFalse(app.contains("compileSdk = 36"))
        assertFalse(app.contains("minSdk = 31"))
        assertFalse(app.contains("targetSdk = 36"))

        val releaseCodes =
            listOf(
                encodeReleaseSemVer(1, 0, 0),
                encodeReleaseSemVer(1, 0, 1),
                encodeReleaseSemVer(1, 1, 0),
                encodeReleaseSemVer(2, 0, 0),
            )
        assertTrue(releaseCodes.zipWithNext().all { (lower, higher) -> lower < higher })
        assertEquals(1_001_077, releaseCodes.first())
        assertEquals(2_001_077, releaseCodes.last())
        assertTrue(encodeReleaseSemVer(2_146, 999, 999) <= Int.MAX_VALUE)
        assertEncodingRejected(major = 2_147, minor = 0, patch = 0)
        assertEncodingRejected(major = 0, minor = 1_000, patch = 0)
        assertEncodingRejected(major = 0, minor = 0, patch = 1_000)
    }

    private fun assertEncodingRejected(
        major: Int,
        minor: Int,
        patch: Int,
    ) {
        var rejected = false
        try {
            encodeReleaseSemVer(major, minor, patch)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Expected SemVer component to be rejected", rejected)
    }

    private fun encodeReleaseSemVer(
        major: Int,
        minor: Int,
        patch: Int,
    ): Int {
        require(major in 0..2_146)
        require(minor in 0..999)
        require(patch in 0..999)
        val versionCode = major * 1_000_000 + minor * 1_000 + patch + 1_077
        require(versionCode <= Int.MAX_VALUE)
        return versionCode
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
