package com.kshavrin.mymoney

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SentryFreeTierContractTest {
    private val appBuildFile = resolveFile("build.gradle.kts", "app/build.gradle.kts")
    private val appSourceFile =
        resolveFile(
            "src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt",
            "app/src/main/java/com/kshavrin/mymoney/MyMoneyApp.kt",
        )
    private val workflowFile =
        resolveFile(
            "../.github/workflows/ci.yml",
            ".github/workflows/ci.yml",
        )
    private val operationsDocFile =
        resolveFile(
            "../docs/operations/sentry-free-tier.md",
            "docs/operations/sentry-free-tier.md",
        )

    @Test
    fun `app build gradle resolves sentry dsn in the documented precedence and exports BuildConfig`() {
        val text = appBuildFile.readText()

        assertContainsInOrder(
            text,
            listOf(
                "providers.gradleProperty(\"sentry.dsn\").orNull",
                "localProperties.getProperty(\"sentry.dsn\")",
                "localProperties.getProperty(\"SENTRY_DSN\")",
                "providers.environmentVariable(\"SENTRY_DSN\").orNull",
                "?: \"\"",
            ),
        )
        assertTrue(
            text.contains("buildConfigField(\"String\", \"SENTRY_DSN\", sentryDsn.asBuildConfigString())"),
        )
    }

    @Test
    fun `app build gradle keeps sentry android core and does not switch to umbrella sdk`() {
        val text = appBuildFile.readText()

        assertTrue(text.contains("implementation(libs.sentry.android.core)"))
        assertFalse(text.contains("implementation(libs.sentry.android)"))
        assertFalse(text.contains("implementation(\"io.sentry:sentry-android"))
    }

    @Test
    fun `MyMoneyApp initializes Sentry only for non blank dsn and disables free tier extras`() {
        val text = appSourceFile.readText()

        assertContainsAll(
            text,
            listOf(
                "if (BuildConfig.SENTRY_DSN.isBlank()) {",
                "SentryAndroid.init(this) { options ->",
                "options.dsn = BuildConfig.SENTRY_DSN",
                "options.tracesSampleRate = 0.0",
                "options.setEnableTracing(false)",
                "options.profilesSampleRate = 0.0",
                "options.profilesSampler = null",
                "options.isEnableAppStartProfiling = false",
                "options.isEnableAutoActivityLifecycleTracing = false",
                "options.isEnableActivityLifecycleTracingAutoFinish = false",
                "options.isEnablePerformanceV2 = false",
                "options.isEnableFramesTracking = false",
                "options.isEnableAutoSessionTracking = false",
                "options.isAttachScreenshot = false",
                "options.isAttachViewHierarchy = false",
            ),
        )
    }

    @Test
    fun `workflow injects sentry secret into every ci job and preserves blank secret fallback`() {
        val text = workflowFile.readText()

        assertEquals(
            3,
            Regex("Materialize Sentry DSN only when the OQ-1 secret is supplied").findAll(text).count(),
        )
        assertContainsAll(
            text,
            listOf(
                "SENTRY_DSN: \${{ secrets.SENTRY_DSN }}",
                "printf '%s\\n' \"sentry.dsn=\$SENTRY_DSN\" >> local.properties",
                "OQ-1 unresolved or no CI secret supplied: Sentry remains disabled.",
            ),
        )
    }

    @Test
    fun `operations doc records free tier limits secret setup and manual quota guard`() {
        val text = operationsDocFile.readText()

        assertContainsAll(
            text,
            listOf(
                "free Developer plan",
                "GitHub Actions repository secret named `SENTRY_DSN`.",
                "5,000 accepted errors per month",
                "If accepted errors reach 4,500",
                "Do not enable or rely on Spike Protection, per-DSN rate limits,",
            ),
        )
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
