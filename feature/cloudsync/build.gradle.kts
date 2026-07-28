plugins {
    alias(libs.plugins.mymoney.android.feature)
    alias(libs.plugins.kover)
}

android {
    namespace = "com.kshavrin.mymoney.feature.cloudsync"
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

    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
}
