package com.kshavrin.mymoney.buildlogic

import java.io.File
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
    fun `application-specific identity and version values remain in the app module`() {
        val app = file("app/build.gradle.kts").readText()

        assertTrue(app.contains("applicationId = \"com.kshavrin.mymoney\""))
        assertTrue(app.contains("versionCode = appVersionCode"))
        assertTrue(app.contains("versionName = appVersionName"))
        assertTrue(app.contains("releaseTagPattern"))
        assertTrue(app.contains("gitOutput(\"tag\", \"--list\")"))
        assertTrue(app.contains("gitOutput(\"tag\", \"--points-at\", \"HEAD\")"))
        assertTrue(app.contains("nonDebugPackagingRequested"))
        assertTrue(app.contains("releaseVersioningReady"))
        assertTrue(app.contains("\"0.0.0-dev\""))
        assertFalse(app.contains("versionCode = 17"))
        assertFalse(app.contains("versionName = \"1.0.16\""))
        assertFalse(app.contains("compileSdk = 36"))
        assertFalse(app.contains("minSdk = 31"))
        assertFalse(app.contains("targetSdk = 36"))
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
