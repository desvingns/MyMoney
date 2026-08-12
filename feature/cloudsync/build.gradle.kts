plugins {
    alias(libs.plugins.mymoney.android.feature)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.kshavrin.mymoney.feature.cloudsync"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:sync"))
    implementation(project(":core:network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.dropbox.android.sdk)
    implementation(libs.google.api.services.drive)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
}
