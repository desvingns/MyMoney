package com.kshavrin.mymoney.buildlogic

import java.io.File
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MymoneyConventionPluginsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `jvm library convention applies Kotlin JVM and centralizes the JVM toolchain`() {
        val result = runFixture(
            "jvm-library",
            """
                plugins {
                    id("mymoney.jvm.library")
                }

                val java = extensions.getByType<org.gradle.api.plugins.JavaPluginExtension>()

                tasks.register("verifyConvention") {
                    doLast {
                        check(plugins.hasPlugin("org.jetbrains.kotlin.jvm"))
                        check(java.toolchain.languageVersion.get().asInt() == 17)
                        println("CONVENTION_VERIFY:jvm-library")
                    }
                }
            """.trimIndent()
        )

        assertSuccessfulVerification(result, "CONVENTION_VERIFY:jvm-library")
    }

    @Test
    fun `android library convention applies shared SDK and Java compatibility defaults`() {
        val result = runFixture(
            "android-library",
            """
                plugins {
                    id("mymoney.android.library")
                }

                android {
                    namespace = "com.example.fixture.library"
                }

                val android = extensions.getByType<com.android.build.api.dsl.LibraryExtension>()

                tasks.register("verifyConvention") {
                    doLast {
                        check(plugins.hasPlugin("com.android.library"))
                        check(plugins.hasPlugin("org.jetbrains.kotlin.android"))
                        check(android.compileSdk == 36)
                        check(android.defaultConfig.minSdk == 31)
                        check(android.compileOptions.sourceCompatibility == javaVersion(17))
                        check(android.compileOptions.targetCompatibility == javaVersion(17))
                        check(android.buildFeatures.compose != true)
                        println("CONVENTION_VERIFY:android-library")
                    }
                }

                fun javaVersion(version: Int) = org.gradle.api.JavaVersion.toVersion(version)
            """.trimIndent(),
        )

        assertSuccessfulVerification(result, "CONVENTION_VERIFY:android-library")
    }

    @Test
    fun `android application convention applies shared SDK and application defaults`() {
        val result = runFixture(
            "android-application",
            """
                plugins {
                    id("mymoney.android.application")
                }

                android {
                    namespace = "com.example.fixture.application"
                }

                val android = extensions.getByType<com.android.build.api.dsl.ApplicationExtension>()

                tasks.register("verifyConvention") {
                    doLast {
                        check(plugins.hasPlugin("com.android.application"))
                        check(plugins.hasPlugin("org.jetbrains.kotlin.android"))
                        check(android.compileSdk == 36)
                        check(android.defaultConfig.minSdk == 31)
                        check(android.defaultConfig.targetSdk == 36)
                        check(android.compileOptions.sourceCompatibility == javaVersion(17))
                        check(android.compileOptions.targetCompatibility == javaVersion(17))
                        println("CONVENTION_VERIFY:android-application")
                    }
                }

                fun javaVersion(version: Int) = org.gradle.api.JavaVersion.toVersion(version)
            """.trimIndent(),
        )

        assertSuccessfulVerification(result, "CONVENTION_VERIFY:android-application")
    }

    @Test
    fun `android test convention applies shared SDK and test defaults`() {
        val result = runFixture(
            "android-test",
            """
                plugins {
                    id("mymoney.android.test")
                }

                android {
                    namespace = "com.example.fixture.test"
                    targetProjectPath = ":target"
                }

                val android = extensions.getByType<com.android.build.api.dsl.TestExtension>()

                tasks.register("verifyConvention") {
                    doLast {
                        check(plugins.hasPlugin("com.android.test"))
                        check(plugins.hasPlugin("org.jetbrains.kotlin.android"))
                        check(android.compileSdk == 36)
                        check(android.defaultConfig.minSdk == 31)
                        check(android.defaultConfig.targetSdk == 36)
                        check(android.compileOptions.sourceCompatibility == javaVersion(17))
                        check(android.compileOptions.targetCompatibility == javaVersion(17))
                        println("CONVENTION_VERIFY:android-test")
                    }
                }

                fun javaVersion(version: Int) = org.gradle.api.JavaVersion.toVersion(version)
            """.trimIndent(),
            requiresAndroidTarget = true,
        )

        assertSuccessfulVerification(result, "CONVENTION_VERIFY:android-test")
    }

    @Test
    fun `android feature convention resolves its isolated plugin classpath and common dependencies`() {
        val result = runFixture(
            "android-feature",
            """
                plugins {
                    id("mymoney.android.feature")
                }

                android {
                    namespace = "com.example.fixture.feature"
                }

                val android = extensions.getByType<com.android.build.api.dsl.LibraryExtension>()

                fun dependencyCoordinates(configuration: String) =
                    configurations.getByName(configuration).allDependencies
                        .mapNotNull { dependency -> dependency.group?.let { "${'$'}it:${'$'}{dependency.name}" } }
                        .toSet()

                tasks.register("verifyConvention") {
                    doLast {
                        check(plugins.hasPlugin("mymoney.android.library"))
                        check(plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose"))
                        check(plugins.hasPlugin("com.google.dagger.hilt.android"))
                        check(plugins.hasPlugin("com.google.devtools.ksp"))
                        check(android.buildFeatures.compose == true)

                        val implementation = dependencyCoordinates("implementation")
                        check("androidx.compose:compose-bom" in implementation)
                        check("com.google.dagger:hilt-android" in implementation)
                        check("androidx.hilt:hilt-navigation-compose" in implementation)
                        check("androidx.hilt:hilt-work" in implementation)

                        val ksp = dependencyCoordinates("ksp")
                        check("com.google.dagger:hilt-compiler" in ksp)

                        val testImplementation = dependencyCoordinates("testImplementation")
                        check("junit:junit" in testImplementation)
                        check("org.jetbrains.kotlinx:kotlinx-coroutines-test" in testImplementation)
                        println("CONVENTION_VERIFY:android-feature")
                    }
                }
            """.trimIndent(),
        )

        assertSuccessfulVerification(result, "CONVENTION_VERIFY:android-feature")
    }

    private fun runFixture(
        name: String,
        buildScript: String,
        requiresAndroidTarget: Boolean = false,
    ): BuildResult {
        val projectDir = temporaryFolder.newFolder(name)
        projectDir.resolve("libs.versions.toml").writeText(
            repositoryRoot.resolve("gradle/libs.versions.toml").readText(),
        )
        val settings =
            """
                pluginManagement {
                    repositories {
                        google()
                        mavenCentral()
                        gradlePluginPortal()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        google()
                        mavenCentral()
                    }
                    versionCatalogs {
                        create("libs") {
                            from(files("libs.versions.toml"))
                        }
                    }
                }

                rootProject.name = "fixture"
            """.trimIndent()
        projectDir.resolve("settings.gradle.kts").writeText(
            if (requiresAndroidTarget) "$settings\ninclude(\":target\")" else settings,
        )
        if (requiresAndroidTarget) {
            projectDir.resolve("target").mkdir()
            projectDir.resolve("target/build.gradle.kts").writeText(
                """
                    plugins {
                        id("com.android.application")
                    }

                    android {
                        namespace = "com.example.fixture.target"
                        compileSdk = 36

                        defaultConfig {
                            applicationId = "com.example.fixture.target"
                            minSdk = 31
                            targetSdk = 36
                        }
                    }
                """.trimIndent(),
            )
        }
        projectDir.resolve("build.gradle.kts").writeText(buildScript)

        return GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "verifyConvention",
                "--offline",
                "--gradle-user-home",
                File(System.getProperty("user.home"), ".gradle").absolutePath,
            )
            .build()
    }

    private fun assertSuccessfulVerification(
        result: BuildResult,
        marker: String,
    ) {
        val task = result.task(":verifyConvention")
        assertNotNull(task)
        assertEquals(TaskOutcome.SUCCESS, task?.outcome)
        assertTrue("Expected Gradle fixture output to contain $marker", result.output.contains(marker))
    }

    private companion object {
        val repositoryRoot: File =
            generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile
                }
                ?: error("Unable to locate the repository root from the test working directory")
    }
}
