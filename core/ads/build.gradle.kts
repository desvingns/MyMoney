import java.util.Properties

plugins {
    alias(libs.plugins.mymoney.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(::load)
        }
    }

fun runtimeProperty(propertyName: String): String? =
    providers.gradleProperty(propertyName).orNull?.takeUnless { it.isBlank() }
        ?: localProperties.getProperty(propertyName)?.takeUnless { it.isBlank() }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val testAdmobApplicationId = "ca-app-pub-3940256099942544~3347511713"
val testRewardedUnitId = "ca-app-pub-3940256099942544/5224354917"
val releaseAdmobApplicationId = runtimeProperty("admob.applicationId") ?: "PLACEHOLDER_ADMOB_APPLICATION_ID"
val releaseRewardedUnitId = runtimeProperty("admob.rewardedUnitId") ?: "PLACEHOLDER_ADMOB_REWARDED_UNIT_ID"
val releaseAdsEnabled = runtimeProperty("ads.enabled")?.toBooleanStrictOrNull() ?: true

android {
    namespace = "com.kshavrin.mymoney.core.ads"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "ADS_ENABLED", "false")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", testRewardedUnitId.asBuildConfigString())
            manifestPlaceholders["admobApplicationId"] = testAdmobApplicationId
        }
        getByName("release") {
            buildConfigField("boolean", "ADS_ENABLED", releaseAdsEnabled.toString())
            buildConfigField(
                "String",
                "ADMOB_REWARDED_UNIT_ID",
                releaseRewardedUnitId.asBuildConfigString(),
            )
            manifestPlaceholders["admobApplicationId"] = releaseAdmobApplicationId
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:network"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
