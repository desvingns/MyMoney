package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.StringReader
import java.util.Properties

class CoreAdsWiringContractTest {
    private val repositoryRoot = findRepositoryRoot()

    @Test
    fun `core ads module is included and consumed by the app`() {
        val settings = file("settings.gradle.kts").readText()
        val app = file("app/build.gradle.kts").readText()
        val build = file("core/ads/build.gradle.kts").readText()

        assertTrue(settings.contains("include(\":core:ads\")"))
        assertTrue(app.contains("implementation(project(\":core:ads\"))"))
        assertContainsInOrder(
            build,
            listOf(
                "alias(libs.plugins.mymoney.android.library)",
                "alias(libs.plugins.ksp)",
                "alias(libs.plugins.hilt)",
                "namespace = \"com.kshavrin.mymoney.core.ads\"",
                "buildFeatures {",
                "buildConfig = true",
            ),
        )
        assertFalse(build.contains("productFlavors"))
        assertFalse(build.contains("flavorDimensions"))
    }

    @Test
    fun `version catalog owns the AdMob and UMP aliases used by core ads`() {
        val catalog = file("gradle/libs.versions.toml").readText()
        val build = file("core/ads/build.gradle.kts").readText()

        assertContainsAll(
            catalog,
            listOf(
                "playServicesAds = \"24.0.0\"",
                "userMessagingPlatform = \"3.1.0\"",
                "play-services-ads = { group = \"com.google.android.gms\", name = \"play-services-ads\", version.ref = \"playServicesAds\" }",
                "user-messaging-platform = { group = \"com.google.android.ump\", name = \"user-messaging-platform\", version.ref = \"userMessagingPlatform\" }",
            ),
        )
        assertContainsAll(
            build,
            listOf(
                "implementation(libs.play.services.ads)",
                "implementation(libs.user.messaging.platform)",
            ),
        )
        assertFalse(build.contains("play-services-ads:"))
        assertFalse(build.contains("user-messaging-platform:"))
        assertFalse(build.contains("24.0.0"))
        assertFalse(build.contains("3.1.0"))
    }

    @Test
    fun `debug and release BuildConfig fields use test ids and external properties`() {
        val build = file("core/ads/build.gradle.kts").readText()
        val gradleProperties =
            Properties().apply {
                load(StringReader(file("gradle.properties").readText()))
            }

        assertContainsInOrder(
            build,
            listOf(
                "fun runtimeProperty(propertyName: String): String?",
                "providers.gradleProperty(propertyName).orNull?.takeUnless { it.isBlank() }",
                "localProperties.getProperty(propertyName)?.takeUnless { it.isBlank() }",
                "val releaseAdmobApplicationId = runtimeProperty(\"admob.applicationId\") ?: \"PLACEHOLDER_ADMOB_APPLICATION_ID\"",
                "val releaseRewardedUnitId = runtimeProperty(\"admob.rewardedUnitId\") ?: \"PLACEHOLDER_ADMOB_REWARDED_UNIT_ID\"",
                "val releaseAdsEnabled = runtimeProperty(\"ads.enabled\")?.toBooleanStrictOrNull() ?: true",
                "getByName(\"debug\")",
                "buildConfigField(\"boolean\", \"ADS_ENABLED\", \"false\")",
                "testRewardedUnitId.asBuildConfigString()",
                "getByName(\"release\")",
                "buildConfigField(\"boolean\", \"ADS_ENABLED\", releaseAdsEnabled.toString())",
                "releaseRewardedUnitId.asBuildConfigString()",
            ),
        )
        assertContainsAll(
            build,
            listOf(
                "val testAdmobApplicationId = \"ca-app-pub-3940256099942544~3347511713\"",
                "val testRewardedUnitId = \"ca-app-pub-3940256099942544/5224354917\"",
                "manifestPlaceholders[\"admobApplicationId\"] = testAdmobApplicationId",
                "manifestPlaceholders[\"admobApplicationId\"] = releaseAdmobApplicationId",
            ),
        )
        assertTrue(gradleProperties.containsKey("admob.applicationId"))
        assertTrue(gradleProperties.containsKey("admob.rewardedUnitId"))
        assertTrue(gradleProperties.containsKey("ads.enabled"))
        assertTrue(gradleProperties.getProperty("admob.applicationId").isEmpty())
        assertTrue(gradleProperties.getProperty("admob.rewardedUnitId").isEmpty())
        assertTrue(gradleProperties.getProperty("ads.enabled").isEmpty())
    }

    @Test
    fun `ads manifest declares AD ID and application id while removing automatic provider`() {
        val manifest = file("core/ads/src/main/AndroidManifest.xml").readText()
        val build = file("core/ads/build.gradle.kts").readText()

        assertContainsInOrder(
            manifest,
            listOf(
                "<uses-permission android:name=\"com.google.android.gms.permission.AD_ID\" />",
                "android:name=\"com.google.android.gms.ads.APPLICATION_ID\"",
                "android:value=\"\${admobApplicationId}\"",
                "android:name=\"com.google.android.gms.ads.MobileAdsInitProvider\"",
                "tools:node=\"remove\"",
            ),
        )
        assertContainsInOrder(
            build,
            listOf(
                "manifestPlaceholders[\"admobApplicationId\"] = testAdmobApplicationId",
                "manifestPlaceholders[\"admobApplicationId\"] = releaseAdmobApplicationId",
            ),
        )
        assertFalse(manifest.contains("androidx.startup.InitializationProvider"))
    }

    @Test
    fun `application startup does not initialize MobileAds or duplicate library manifest entries`() {
        val appManifest = file("app/src/main/AndroidManifest.xml").readText()
        val appSource = file("app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt").readText()
        val productionSources =
            listOf(
                file("app/src/main"),
                file("core/ads/src/main"),
            ).flatMap { root ->
                if (!root.isDirectory) {
                    emptyList()
                } else {
                    root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList()
                }
            }

        assertFalse(appSource.contains("MobileAds.initialize"))
        assertFalse(appSource.contains("MobileAdsInitProvider"))
        assertFalse(appManifest.contains("com.google.android.gms.permission.AD_ID"))
        assertFalse(appManifest.contains("com.google.android.gms.ads.APPLICATION_ID"))
        assertFalse(
            "MobileAds initialization must remain absent from app and core ads production sources",
            productionSources.any { it.readText().contains("MobileAds.initialize") },
        )
    }

    @Test
    fun `core ads wiring stays outside protected Supabase paths`() {
        val scopedFiles =
            listOf(
                "settings.gradle.kts",
                "app/build.gradle.kts",
                "core/ads/build.gradle.kts",
                "core/ads/src/main/AndroidManifest.xml",
                "gradle.properties",
                "gradle/libs.versions.toml",
            )

        scopedFiles.forEach { path ->
            val text = file(path).readText()
            assertFalse("$path must not reference protected Supabase config", text.contains("supabase/config.toml"))
            assertFalse("$path must not reference protected Supabase functions", text.contains("supabase/functions"))
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

    private fun file(path: String): File = File(repositoryRoot, path)

    private companion object {
        fun findRepositoryRoot(): File =
            generateSequence(File(requireNotNull(System.getProperty("user.dir"))).absoluteFile) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile
                } ?: error("Unable to locate repository root")
    }
}
