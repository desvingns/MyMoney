import java.math.BigInteger
import java.util.Properties
import org.gradle.api.Action

plugins {
    alias(libs.plugins.mymoney.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.gms.oss.licenses)
    alias(libs.plugins.androidx.baselineprofile)
}

val localProperties =
    Properties().apply {
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.isFile) {
            localPropertiesFile.inputStream().use(::load)
        }
    }
val sentryDsn =
    providers.gradleProperty("sentry.dsn").orNull
        ?: localProperties.getProperty("sentry.dsn")
        ?: localProperties.getProperty("SENTRY_DSN")
        ?: providers.environmentVariable("SENTRY_DSN").orNull
        ?: ""
val releaseSigningProperties = localProperties

fun Properties.propertyOrEnv(
    propertyName: String,
    environmentName: String,
): String? =
    getProperty(propertyName)?.takeUnless { it.isBlank() }
        ?: providers.environmentVariable(environmentName).orNull?.takeUnless { it.isBlank() }

val releaseKeystorePath =
    releaseSigningProperties.propertyOrEnv(
        propertyName = "keystore.path",
        environmentName = "MYMONEY_RELEASE_KEYSTORE_PATH",
    )
val releaseKeystorePassword =
    releaseSigningProperties.propertyOrEnv(
        propertyName = "keystore.pass",
        environmentName = "MYMONEY_RELEASE_KEYSTORE_PASSWORD",
    )
val releaseKeyAlias =
    releaseSigningProperties.propertyOrEnv(
        propertyName = "keystore.key.alias",
        environmentName = "MYMONEY_RELEASE_KEY_ALIAS",
    )
val releaseKeyPassword =
    releaseSigningProperties.propertyOrEnv(
        propertyName = "keystore.key.pass",
        environmentName = "MYMONEY_RELEASE_KEY_PASSWORD",
    )
val hasReleaseSigningConfig =
    listOf(
        releaseKeystorePath,
        releaseKeystorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

/*
 * Release versioning is derived from the checked-out Git history:
 * - Stable release tags are exactly vMAJOR.MINOR.PATCH. Other tags never affect a release.
 * - versionName is the highest valid release tag reachable from HEAD; release packaging
 *   requires exactly one valid release tag at HEAD, also highest among all visible tags.
 * - versionCode is major * 1_000_000 + minor * 1_000 + patch plus the migration offset.
 *   Components have explicit Android Int-range limits, and immutable release tags must be
 *   appended in strictly increasing SemVer order: never delete or retarget a release tag.
 *   Full Git history makes local and CI release builds use the same value.
 * - A fixed name/code fallback is available only to debug IDE builds without Git metadata.
 */
fun gitOutput(vararg arguments: String): String? =
    runCatching {
        val execution =
            providers.exec {
                workingDir = rootProject.projectDir
                commandLine("git", *arguments)
                isIgnoreExitValue = true
            }
        if (execution.result.get().exitValue == 0) {
            execution.standardOutput.asText.get().trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }.getOrNull()

val hasCompleteGitHistory = gitOutput("rev-parse", "--is-shallow-repository") == "false"
val releaseTagPattern = Regex("""^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$""")
fun validReleaseTags(rawTags: String?): List<String> =
    rawTags
        .orEmpty()
        .lineSequence()
        .map(String::trim)
        .filter { it.matches(releaseTagPattern) }
        .toList()

val allValidReleaseTags =
    if (hasCompleteGitHistory) {
        validReleaseTags(gitOutput("tag", "--list"))
    } else {
        emptyList()
    }
val reachableValidReleaseTags =
    if (hasCompleteGitHistory) {
        validReleaseTags(gitOutput("tag", "--merged", "HEAD"))
    } else {
        emptyList()
    }
val validReleaseTagsAtHead =
    if (hasCompleteGitHistory) {
        validReleaseTags(gitOutput("tag", "--points-at", "HEAD"))
    } else {
        emptyList()
    }
fun releaseVersionPart(tag: String, group: Int): BigInteger =
    BigInteger(releaseTagPattern.matchEntire(tag)!!.groupValues[group])

fun latestReleaseTag(tags: List<String>): String? =
    tags.maxWithOrNull(
        compareBy<String> { releaseVersionPart(it, 1) }
            .thenBy { releaseVersionPart(it, 2) }
            .thenBy { releaseVersionPart(it, 3) },
    )
val latestGlobalReleaseTag = latestReleaseTag(allValidReleaseTags)
val latestReachableReleaseTag = latestReleaseTag(reachableValidReleaseTags)
val maxReleaseMajor = BigInteger.valueOf(2_146L)
val maxReleaseMinor = BigInteger.valueOf(999L)
val maxReleasePatch = BigInteger.valueOf(999L)
val releaseCodeMajorMultiplier = BigInteger.valueOf(1_000_000L)
val releaseCodeMinorMultiplier = BigInteger.valueOf(1_000L)
val releaseCodeMigrationOffset = BigInteger.valueOf(1_077L)

fun releaseVersionCode(tag: String): Int {
    val match = requireNotNull(releaseTagPattern.matchEntire(tag))
    val major = BigInteger(match.groupValues[1])
    val minor = BigInteger(match.groupValues[2])
    val patch = BigInteger(match.groupValues[3])
    require(major <= maxReleaseMajor) { "SemVer major exceeds Android versionCode range." }
    require(minor <= maxReleaseMinor) { "SemVer minor exceeds Android versionCode range." }
    require(patch <= maxReleasePatch) { "SemVer patch exceeds Android versionCode range." }
    val versionCode =
        major
            .multiply(releaseCodeMajorMultiplier)
            .add(minor.multiply(releaseCodeMinorMultiplier))
            .add(patch)
            .add(releaseCodeMigrationOffset)
    require(versionCode <= BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
        "SemVer exceeds Android versionCode Int range."
    }
    return versionCode.toInt()
}

val debugVersionCodeFallback = 1_001_077
val appVersionCode =
    latestReachableReleaseTag
        ?.let { tag -> runCatching { releaseVersionCode(tag) }.getOrNull() }
        ?: debugVersionCodeFallback
val appVersionName = latestReachableReleaseTag?.removePrefix("v") ?: "0.0.0-dev"
val releaseTagAtHead = validReleaseTagsAtHead.singleOrNull()
val releaseTriggerTag =
    providers.environmentVariable("GITHUB_REF_NAME").orNull?.takeIf { it.isNotBlank() }
val releaseVersioningReady =
    hasCompleteGitHistory &&
        releaseTagAtHead != null &&
        releaseTagAtHead == latestReachableReleaseTag &&
        releaseTagAtHead == latestGlobalReleaseTag &&
        releaseTagAtHead?.let { tag -> runCatching { releaseVersionCode(tag) }.isSuccess } == true &&
        (releaseTriggerTag == null || releaseTriggerTag == releaseTagAtHead)

fun isNonDebugPackagingTask(taskPath: String): Boolean {
    val taskName = taskPath.substringAfterLast(':').lowercase()
    val isPackagingTask =
        taskName == "build" ||
            taskName.startsWith("build") ||
            taskName == "assemble" ||
            taskName.startsWith("assemble") ||
            taskName == "bundle" ||
            taskName.startsWith("bundle") ||
            taskName == "package" ||
            taskName.startsWith("package")
    return isPackagingTask && !taskName.contains("debug")
}

fun requireReleaseVersioning() {
    check(releaseVersioningReady) {
        "Non-debug packaging requires the highest valid vMAJOR.MINOR.PATCH tag at HEAD in complete Git history."
    }
}

if (gradle.startParameter.taskNames.any(::isNonDebugPackagingTask)) {
    requireReleaseVersioning()
}
gradle.taskGraph.whenReady(
    Action { taskGraph ->
        if (taskGraph.allTasks.any { isNonDebugPackagingTask(it.path) }) {
            requireReleaseVersioning()
        }
    },
)

// google-services is applied only when a google-services.json is present
// (firebase.enabled=true); the default build ships without Firebase.
if (providers.gradleProperty("firebase.enabled").orNull == "true") {
    apply(
        plugin =
            libs.plugins.gms.google.services
                .get()
                .pluginId,
    )
}

android {
    namespace = "com.kshavrin.mymoney"

    defaultConfig {
        applicationId = "com.kshavrin.mymoney"
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "com.kshavrin.mymoney.HiltTestRunner"

        buildConfigField("String", "SENTRY_DSN", sentryDsn.asBuildConfigString())
        buildConfigField(
            "boolean",
            "HAS_FIREBASE",
            (providers.gradleProperty("firebase.enabled").orNull == "true").toString(),
        )
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseKeystorePath))
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Dev convenience: skip the 4-slide tutorial on debug installs.
            buildConfigField("boolean", "SHOW_ONBOARDING", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            buildConfigField("boolean", "SHOW_ONBOARDING", "true")
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("staging") {
            initWith(getByName("release"))
            // Library modules (:core:*, :feature:*) publish only debug/release, so a
            // staging consumer must fall back to their release variant to resolve.
            matchingFallbacks += "release"
        }
    }

    // staging initWith(release) copies build config but not the release variant's
    // generated baselineProfiles source set, so staging would otherwise ship no
    // baseline profile. Reuse the committed release profile for staging packaging.
    sourceSets.getByName("staging") {
        baselineProfiles.srcDir("src/release/generated/baselineProfiles")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes +=
                listOf(
                    "META-INF/DEPENDENCIES",
                    "META-INF/INDEX.LIST",
                    "META-INF/LICENSE.md",
                    "META-INF/NOTICE.md",
                    "META-INF/*.kotlin_module",
                )
        }
    }
}

hilt {
    enableAggregatingTask = true
}

baselineProfile {
    automaticGenerationDuringBuild = false
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:sync"))
    implementation(project(":core:domain"))
    implementation(project(":core:common"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:dashboard"))
    implementation(project(":feature:transaction"))
    implementation(project(":feature:transactionslist"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:dictionaries"))
    implementation(project(":feature:cloudsync"))
    implementation(project(":feature:lockscreen"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)

    implementation(libs.bundles.hilt)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.sentry.android.core)
    implementation(libs.play.services.oss.licenses)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":macrobenchmark"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.paging.compose)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.androidx.navigation.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
    androidTestImplementation(project(":core:database"))
    androidTestImplementation(project(":core:datastore"))
    androidTestImplementation(project(":core:domain"))
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(libs.androidx.datastore.preferences)
    androidTestImplementation(libs.androidx.room.runtime)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
