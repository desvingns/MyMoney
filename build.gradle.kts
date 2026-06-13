import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.gms.google.services) apply false
    alias(libs.plugins.gms.oss.licenses) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
    }

    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        baseline = file("detekt-baseline.xml")
        parallel = true
        ignoreFailures = false
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = JvmTarget.JVM_17.target
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(false)
            sarif.required.set(false)
        }
    }
    tasks.withType<DetektCreateBaselineTask>().configureEach {
        jvmTarget = JvmTarget.JVM_17.target
    }

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        android.set(true)
        ignoreFailures.set(false)
        filter {
            exclude { it.file.path.contains("generated/") }
            exclude { it.file.path.contains("/build/") }
        }
    }
}

dependencies {
    kover(project(":core:domain"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
}

kover {
    reports {
        total {
            html { onCheck = false }
            xml { onCheck = false }
        }
    }
}
