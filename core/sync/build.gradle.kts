plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.kshavrin.mymoney.core.sync"
    compileSdk = 36

    defaultConfig {
        minSdk = 31

        buildConfigField(
            "boolean",
            "HAS_FIREBASE",
            (providers.gradleProperty("firebase.enabled").orNull == "true").toString(),
        )
        buildConfigField(
            "boolean",
            "SYNC_DISABLED",
            (providers.gradleProperty("sync.enabled").orNull != "true").toString(),
        )

        val dropboxAppKey =
            providers.gradleProperty("dropbox.appKey").orNull ?: "PLACEHOLDER_DROPBOX_APP_KEY"
        manifestPlaceholders["dropboxAppKey"] = dropboxAppKey
        buildConfigField("String", "DROPBOX_APP_KEY", "\"$dropboxAppKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
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

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.dropbox.core.sdk)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config.ktx)

    implementation(libs.sentry.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
