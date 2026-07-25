import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

private val koverGeneratedClasses = listOf(
    "hilt_aggregated_deps.*",
    "dagger.hilt.internal.aggregatedroot.codegen.*",
    "dagger.hilt.internal.processedrootsentinel.codegen.*",
    "*HiltComponents*",
    "*HiltModules*",
    "*HiltWrapper*",
    "*Hilt_*",
    "*_Factory",
    "*MembersInjector*",
    "*_Impl*",
    "*.BuildConfig",
    "*.R",
    "*.R$*",
)

private val koverLineFloors = mapOf(
    ":core:domain" to 90,
    ":core:database" to 17,
    ":core:datastore" to 68,
    ":feature:cloudsync" to 41,
    ":feature:dashboard" to 33,
    ":feature:lockscreen" to 31,
    ":feature:onboarding" to 13,
    ":feature:settings" to 34,
    ":feature:transaction" to 46,
    ":feature:transactionslist" to 35,
)

private val koverVerificationTasks = listOf(
    ":core:domain:koverVerifyJvm",
    ":core:database:koverVerifyDebug",
    ":core:datastore:koverVerifyDebug",
    ":feature:cloudsync:koverVerifyDebug",
    ":feature:dashboard:koverVerifyDebug",
    ":feature:lockscreen:koverVerifyDebug",
    ":feature:onboarding:koverVerifyDebug",
    ":feature:settings:koverVerifyDebug",
    ":feature:transaction:koverVerifyDebug",
    ":feature:transactionslist:koverVerifyDebug",
)

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
    alias(libs.plugins.roborazzi) apply false
}

subprojects {
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

    val lintGate: com.android.build.api.dsl.Lint.() -> Unit = {
        lintConfig = rootProject.file("lint.xml")
        error += listOf("MissingTranslation", "ExtraTranslation")
    }
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension> { lint(lintGate) }
    }
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension> { lint(lintGate) }
    }

    pluginManager.withPlugin("org.jetbrains.kotlinx.kover") {
        val lineFloor = koverLineFloors[path]
        extensions.configure<KoverProjectExtension> {
            reports {
                filters {
                    excludes {
                        classes(koverGeneratedClasses)
                    }
                }
                if (lineFloor != null) {
                    verify {
                        rule {
                            bound {
                                minValue = lineFloor
                                coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE
                            }
                        }
                    }
                }
            }
        }
    }
}

dependencies {
    kover(project(":core:domain"))
    kover(project(":core:database"))
    kover(project(":core:datastore"))
    kover(project(":feature:cloudsync"))
    kover(project(":feature:dashboard"))
    kover(project(":feature:lockscreen"))
    kover(project(":feature:onboarding"))
    kover(project(":feature:settings"))
    kover(project(":feature:transaction"))
    kover(project(":feature:transactionslist"))
}

tasks.named("koverVerify") {
    dependsOn(koverVerificationTasks)
}

kover {
    reports {
        filters {
            excludes {
                classes(koverGeneratedClasses)
            }
        }
        total {
            html { onCheck = false }
            xml { onCheck = false }
        }
    }
}
