plugins {
    alias(libs.plugins.mymoney.android.feature)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.kshavrin.mymoney.feature.onboarding"
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(project(":core:datastore"))
    implementation(project(":core:common"))
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.turbine)
}
