import java.util.Properties

plugins {
    alias(libs.plugins.mymoney.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.kshavrin.mymoney.core.network"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val playInternalSyncEnabled =
            providers.gradleProperty("sync.playInternalEnabled").orNull?.toBooleanStrictOrNull() ?: false
        buildConfigField("boolean", "PLAY_INTERNAL_SYNC_ENABLED", playInternalSyncEnabled.toString())
        val playReleaseSyncEnabled =
            providers.gradleProperty("sync.playReleaseEnabled").orNull?.toBooleanStrictOrNull() ?: true
        buildConfigField("boolean", "PLAY_RELEASE_SYNC_ENABLED", playReleaseSyncEnabled.toString())
        val syncForceEnabled = providers.gradleProperty("sync.forceEnabled").orNull == "true"
        buildConfigField("boolean", "SYNC_FORCE_ENABLED", syncForceEnabled.toString())

        val localProperties =
            Properties().apply {
                val localPropertiesFile = rootProject.file("local.properties")
                if (localPropertiesFile.isFile) {
                    localPropertiesFile.inputStream().use(::load)
                }
            }
        val supabaseUrl =
            providers.gradleProperty("supabase.url").orNull?.takeUnless { it.isBlank() }
                ?: localProperties.getProperty("supabase.url")?.takeUnless { it.isBlank() }
                ?: "PLACEHOLDER_SUPABASE_URL"
        val supabaseAnonKey =
            providers.gradleProperty("supabase.anonKey").orNull?.takeUnless { it.isBlank() }
                ?: localProperties.getProperty("supabase.anonKey")?.takeUnless { it.isBlank() }
                ?: "PLACEHOLDER_SUPABASE_ANON_KEY"
        val supabaseGoogleWebClientId =
            providers.gradleProperty("supabase.googleWebClientId").orNull?.takeUnless { it.isBlank() }
                ?: localProperties.getProperty("supabase.googleWebClientId")?.takeUnless { it.isBlank() }
                ?: "PLACEHOLDER_SUPABASE_GOOGLE_WEB_CLIENT_ID"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "SUPABASE_GOOGLE_WEB_CLIENT_ID", "\"$supabaseGoogleWebClientId\"")
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
