plugins {
    alias(libs.plugins.mymoney.android.feature)
    alias(libs.plugins.kover)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.kshavrin.mymoney.feature.dashboard"

    // JVM Roborazzi + Robolectric screenshot tests render the dashboard screen states off-device;
    // they need Android resources (theme, strings, drawables) on the unit-test classpath.
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
    implementation(project(":core:datastore"))
    implementation(project(":core:common"))
    implementation(project(":core:sync"))
    implementation(libs.androidx.core.ktx)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
