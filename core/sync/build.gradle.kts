import java.util.Properties

plugins {
    alias(libs.plugins.mymoney.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kshavrin.mymoney.core.sync"

    defaultConfig {
        testInstrumentationRunner = "com.kshavrin.mymoney.core.sync.HiltTestRunner"

        buildConfigField(
            "boolean",
            "HAS_FIREBASE",
            (providers.gradleProperty("firebase.enabled").orNull == "true").toString(),
        )

        val localProperties =
            Properties().apply {
                val localPropertiesFile = rootProject.file("local.properties")
                if (localPropertiesFile.isFile) {
                    localPropertiesFile.inputStream().use(::load)
                }
            }
        val dropboxAppKey =
            providers.gradleProperty("dropbox.appKey").orNull
                ?: localProperties.getProperty("dropbox.appKey")?.takeUnless { it.isBlank() }
                ?: "PLACEHOLDER_DROPBOX_APP_KEY"
        manifestPlaceholders["dropboxAppKey"] = dropboxAppKey
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")

        // Debug-only override to exercise cloud sync without Firebase Remote Config.
        // Honoured only when BuildConfig.DEBUG is also true (see RemoteConfigRepositoryImpl).
        buildConfigField(
            "boolean",
            "SYNC_FORCE_ENABLED",
            (providers.gradleProperty("sync.forceEnabled").orNull == "true").toString(),
        )
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/INDEX.LIST",
                    "META-INF/LICENSE.md",
                    "META-INF/NOTICE.md",
                    "META-INF/*.kotlin_module",
                )
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.dropbox.android.sdk)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.play.services.auth)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)

    implementation(libs.sentry.core)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.androidx.room.runtime)
    androidTestImplementation(libs.androidx.datastore.preferences)
}
