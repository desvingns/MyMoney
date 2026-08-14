pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:${requested.version}")
            }
        }
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyMoney"
include(":app")
include(":core:ui")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:sync")
include(":core:domain")
include(":core:common")
include(":core:billing")
include(":core:ads")
include(":core:testing")
include(":feature:onboarding")
include(":feature:dashboard")
include(":feature:transaction")
include(":feature:transactionslist")
include(":feature:settings")
include(":feature:dictionaries")
include(":feature:cloudsync")
include(":feature:lockscreen")
include(":feature:support")
include(":macrobenchmark")
