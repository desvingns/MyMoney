package com.kshavrin.mymoney.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.dsl.TestExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class MymoneyAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<ApplicationExtension> {
            compileSdk = libs.versionInt("androidCompileSdk")
            defaultConfig {
                minSdk = libs.versionInt("androidMinSdk")
                targetSdk = libs.versionInt("androidTargetSdk")
            }
            configureJavaToolchain(libs.versionInt("jvmToolchain"))
        }
        configureKotlinAndroidToolchain(libs.versionInt("jvmToolchain"))
    }
}

class MymoneyAndroidFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("mymoney.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.apply("com.google.dagger.hilt.android")
        pluginManager.apply("com.google.devtools.ksp")

        extensions.configure<LibraryExtension> {
            buildFeatures {
                compose = true
            }
        }

        dependencies.run {
            add("implementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("implementation", libs.findBundle("compose").get())
            add("implementation", libs.findBundle("hilt").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }
        Unit
    }
}

class MymoneyAndroidLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<LibraryExtension> {
            compileSdk = libs.versionInt("androidCompileSdk")
            defaultConfig {
                minSdk = libs.versionInt("androidMinSdk")
            }
            configureJavaToolchain(libs.versionInt("jvmToolchain"))
        }
        configureKotlinAndroidToolchain(libs.versionInt("jvmToolchain"))
    }
}

class MymoneyAndroidTestPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.test")
        pluginManager.apply("org.jetbrains.kotlin.android")

        extensions.configure<TestExtension> {
            compileSdk = libs.versionInt("androidCompileSdk")
            defaultConfig {
                minSdk = libs.versionInt("androidMinSdk")
                targetSdk = libs.versionInt("androidTargetSdk")
            }
            configureJavaToolchain(libs.versionInt("jvmToolchain"))
        }
        configureKotlinAndroidToolchain(libs.versionInt("jvmToolchain"))
    }
}

class MymoneyJvmLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(libs.versionInt("jvmToolchain"))
        }
    }
}

private val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

private fun CommonExtension<*, *, *, *, *, *>.configureJavaToolchain(version: Int) {
    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.toVersion(version)
        targetCompatibility = org.gradle.api.JavaVersion.toVersion(version)
    }
}

private fun Project.configureKotlinAndroidToolchain(version: Int) {
    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain(version)
    }
}

private fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()
