import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.gms.oss.licenses)
    alias(libs.plugins.androidx.baselineprofile)
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(::load)
        }
    }
val sentryDsn =
    providers.gradleProperty("sentry.dsn").orNull
        ?: localProperties.getProperty("sentry.dsn")
        ?: localProperties.getProperty("SENTRY_DSN")
        ?: providers.environmentVariable("SENTRY_DSN").orNull
        ?: ""
val releaseSigningProperties = localProperties
val releaseSigningKeys =
    listOf(
        "keystore.path",
        "keystore.pass",
        "keystore.key.alias",
        "keystore.key.pass",
    )
val hasReleaseSigningConfig =
    releaseSigningKeys.all { key ->
        !releaseSigningProperties.getProperty(key).isNullOrBlank()
    }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

// google-services is applied only when a google-services.json is present
// (firebase.enabled=true); the default build ships without Firebase.
if (providers.gradleProperty("firebase.enabled").orNull == "true") {
    apply(
        plugin =
            libs.plugins.gms.google.services
                .get()
                .pluginId,
    )
}

android {
    namespace = "com.kshavrin.mymoney"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kshavrin.mymoney"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.kshavrin.mymoney.HiltTestRunner"

        buildConfigField("String", "SENTRY_DSN", sentryDsn.asBuildConfigString())
        buildConfigField(
            "boolean",
            "HAS_FIREBASE",
            (providers.gradleProperty("firebase.enabled").orNull == "true").toString(),
        )
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("keystore.path"))
                storePassword = releaseSigningProperties.getProperty("keystore.pass")
                keyAlias = releaseSigningProperties.getProperty("keystore.key.alias")
                keyPassword = releaseSigningProperties.getProperty("keystore.key.pass")
            }
        }
    }

    buildTypes {
        debug {
            // Dev convenience: skip the 4-slide tutorial on debug installs.
            buildConfigField("boolean", "SHOW_ONBOARDING", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "SHOW_ONBOARDING", "true")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("staging") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
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

baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:sync"))
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:transaction"))
    implementation(project(":feature:transactionslist"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:dictionaries"))
    implementation(project(":feature:cloudsync"))
    implementation(project(":feature:lockscreen"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    implementation(libs.bundles.hilt)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sentry.android.core)
    implementation(libs.play.services.oss.licenses)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":macrobenchmark"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.paging.compose)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(project(":core:datastore"))
    androidTestImplementation(project(":core:domain"))
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(libs.androidx.datastore.preferences)
    androidTestImplementation(libs.androidx.room.runtime)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
