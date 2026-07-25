plugins {
    alias(libs.plugins.mymoney.jvm.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.androidx.paging.common)
    implementation(libs.hilt.core)
    ksp(libs.hilt.compiler)
    implementation(project(":core:common"))

    testImplementation(libs.junit)
    testImplementation(libs.kotest.property)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
