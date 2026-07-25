plugins {
    alias(libs.plugins.mymoney.android.library)
}

android {
    namespace = "com.kshavrin.mymoney.core.testing"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(project(":core:datastore"))
    api(project(":core:domain"))
    api(libs.junit)
    api(libs.turbine)
    api(libs.kotlinx.coroutines.test)
    api(libs.androidx.room.testing)

    testImplementation(libs.androidx.datastore.preferences)
}
