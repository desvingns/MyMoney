plugins {
    `kotlin-dsl`
}

group = "com.kshavrin.mymoney.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "mymoney.android.application"
            implementationClass = "com.kshavrin.mymoney.buildlogic.MymoneyAndroidApplicationPlugin"
        }
        register("androidFeature") {
            id = "mymoney.android.feature"
            implementationClass = "com.kshavrin.mymoney.buildlogic.MymoneyAndroidFeaturePlugin"
        }
        register("androidLibrary") {
            id = "mymoney.android.library"
            implementationClass = "com.kshavrin.mymoney.buildlogic.MymoneyAndroidLibraryPlugin"
        }
        register("androidTest") {
            id = "mymoney.android.test"
            implementationClass = "com.kshavrin.mymoney.buildlogic.MymoneyAndroidTestPlugin"
        }
        register("jvmLibrary") {
            id = "mymoney.jvm.library"
            implementationClass = "com.kshavrin.mymoney.buildlogic.MymoneyJvmLibraryPlugin"
        }
    }
}
