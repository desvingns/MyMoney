package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseSigningCiContractTest {
    private val appBuildFile = resolveFile("build.gradle.kts", "app/build.gradle.kts")
    private val workflowFile =
        resolveFile(
            "../.github/workflows/ci.yml",
            ".github/workflows/ci.yml",
        )

    @Test
    fun `app build gradle resolves release signing properties from local properties before environment`() {
        val text = appBuildFile.readText()

        assertContainsInOrder(
            text,
            listOf(
                "fun Properties.propertyOrEnv(",
                "getProperty(propertyName)?.takeUnless { it.isBlank() }",
                "providers.environmentVariable(environmentName).orNull?.takeUnless { it.isBlank() }",
            ),
        )
        assertContainsAll(
            text,
            listOf(
                "propertyName = \"keystore.path\"",
                "environmentName = \"MYMONEY_RELEASE_KEYSTORE_PATH\"",
                "propertyName = \"keystore.pass\"",
                "environmentName = \"MYMONEY_RELEASE_KEYSTORE_PASSWORD\"",
                "propertyName = \"keystore.key.alias\"",
                "environmentName = \"MYMONEY_RELEASE_KEY_ALIAS\"",
                "propertyName = \"keystore.key.pass\"",
                "environmentName = \"MYMONEY_RELEASE_KEY_PASSWORD\"",
            ),
        )
    }

    @Test
    fun `app build gradle creates release signing config only when all values are present`() {
        val text = appBuildFile.readText()

        assertContainsAll(
            text,
            listOf(
                "val hasReleaseSigningConfig =",
                ").all { !it.isNullOrBlank() }",
                "signingConfigs {",
                "if (hasReleaseSigningConfig) {",
                "create(\"release\") {",
                "storeFile = rootProject.file(requireNotNull(releaseKeystorePath))",
                "storePassword = releaseKeystorePassword",
                "keyAlias = releaseKeyAlias",
                "keyPassword = releaseKeyPassword",
                "if (hasReleaseSigningConfig) {",
                "signingConfig = signingConfigs.getByName(\"release\")",
                "create(\"staging\") {",
                "initWith(getByName(\"release\"))",
            ),
        )
    }

    @Test
    fun `workflow materializes signing secrets into gradle env contract without writing passwords to local properties`() {
        val text = workflowFile.readText()

        assertContainsAll(
            text,
            listOf(
                "RELEASE_KEYSTORE_BASE64: \${{ secrets.RELEASE_KEYSTORE_BASE64 }}",
                "RELEASE_KEYSTORE_PASSWORD: \${{ secrets.RELEASE_KEYSTORE_PASSWORD }}",
                "RELEASE_KEY_ALIAS: \${{ secrets.RELEASE_KEY_ALIAS }}",
                "RELEASE_KEY_PASSWORD: \${{ secrets.RELEASE_KEY_PASSWORD }}",
                "missing+=(\"RELEASE_KEYSTORE_BASE64\")",
                "missing+=(\"RELEASE_KEYSTORE_PASSWORD\")",
                "missing+=(\"RELEASE_KEY_ALIAS\")",
                "missing+=(\"RELEASE_KEY_PASSWORD\")",
                "printf '::error::Missing release signing secrets: %s\\n' \"\${missing[*]}\"",
                "keystore_path=\"\$RUNNER_TEMP/mymoney-release.jks\"",
                "printf '%s' \"\$RELEASE_KEYSTORE_BASE64\" | base64 --decode > \"\$keystore_path\"",
                "chmod 600 \"\$keystore_path\"",
                "echo \"MYMONEY_RELEASE_KEYSTORE_PATH=\$keystore_path\"",
                "echo \"MYMONEY_RELEASE_KEYSTORE_PASSWORD=\$RELEASE_KEYSTORE_PASSWORD\"",
                "echo \"MYMONEY_RELEASE_KEY_ALIAS=\$RELEASE_KEY_ALIAS\"",
                "echo \"MYMONEY_RELEASE_KEY_PASSWORD=\$RELEASE_KEY_PASSWORD\"",
            ),
        )
        assertFalse(text.contains("keystore.path="))
        assertFalse(text.contains("keystore.pass="))
        assertFalse(text.contains("keystore.key.alias="))
        assertFalse(text.contains("keystore.key.pass="))
    }

    @Test
    fun `workflow builds and uploads signed apk and aab artifacts`() {
        val text = workflowFile.readText()

        assertContainsAll(
            text,
            listOf(
                "name: Signed release APK and AAB",
                "needs: jvm",
                "if: github.event_name != 'pull_request'",
                "run: ./gradlew :app:assembleRelease :app:bundleRelease \$FIREBASE_ARGS --stacktrace",
                "name: app-release.apk",
                "path: app/build/outputs/apk/release/*.apk",
                "name: app-release.aab",
                "path: app/build/outputs/bundle/release/*.aab",
            ),
        )
    }

    @Test
    fun `workflow keeps public repo push and pull request triggers while jvm checks stay on every run`() {
        val text = workflowFile.readText()

        assertContainsInOrder(
            text,
            listOf(
                "on:",
                "  push:",
                "    branches: [main]",
                "  pull_request:",
                "  workflow_dispatch:",
            ),
        )
        assertContainsAll(
            text,
            listOf(
                "name: Lint, JVM tests, static analysis, and coverage",
                "run: ./gradlew lintDebug testDebugUnitTest \$FIREBASE_ARGS --stacktrace",
                "run: ./gradlew detekt --stacktrace",
                "run: ./gradlew ktlintCheck --stacktrace",
                "run: ./gradlew koverVerify --stacktrace",
            ),
        )
        assertTrue(text.contains("name: Connected tests - Pixel 6 API 34"))
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment' in ${text.take(200)}...", text.contains(fragment))
        }
    }

    private fun assertContainsInOrder(
        text: String,
        fragments: List<String>,
    ) {
        var startIndex = 0
        fragments.forEach { fragment ->
            val index = text.indexOf(fragment, startIndex = startIndex)
            assertTrue("Expected to find '$fragment' after index $startIndex", index >= 0)
            startIndex = index + fragment.length
        }
    }

    private companion object {
        fun resolveFile(vararg candidates: String): File =
            candidates
                .asSequence()
                .map(::File)
                .firstOrNull(File::isFile)
                ?: File(candidates.first()).absoluteFile
    }
}
