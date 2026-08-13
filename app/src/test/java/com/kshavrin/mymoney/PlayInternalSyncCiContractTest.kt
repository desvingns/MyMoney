package com.kshavrin.mymoney

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayInternalSyncCiContractTest {
    private val repositoryRoot = findRepositoryRoot()
    private val appBuild = File(repositoryRoot, "app/build.gradle.kts")
    private val syncBuild = File(repositoryRoot, "core/sync/build.gradle.kts")
    private val networkBuild = File(repositoryRoot, "core/network/build.gradle.kts")
    private val remoteConfig =
        File(
            repositoryRoot,
            "core/sync/src/main/java/com/kshavrin/mymoney/core/sync/remoteconfig/RemoteConfigRepositoryImpl.kt",
        )
    private val workflow = File(repositoryRoot, ".github/workflows/ci.yml")

    @Test
    fun `play internal property is explicit and propagated to every sync module`() {
        val appText = appBuild.readText()
        val syncText = syncBuild.readText()
        val networkText = networkBuild.readText()

        assertContainsAll(
            appText,
            listOf(
                "providers.gradleProperty(\"sync.playInternalEnabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "providers.gradleProperty(\"sync.playReleaseEnabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "buildConfigField(\"boolean\", \"PLAY_INTERNAL_SYNC_ENABLED\", playInternalSyncEnabled.toString())",
                "buildConfigField(\"boolean\", \"PLAY_RELEASE_SYNC_ENABLED\", playReleaseSyncEnabled.toString())",
            ),
        )
        assertContainsAll(
            syncText,
            listOf(
                "providers.gradleProperty(\"sync.playInternalEnabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "providers.gradleProperty(\"sync.playReleaseEnabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "buildConfigField(\"boolean\", \"PLAY_INTERNAL_SYNC_ENABLED\", playInternalSyncEnabled.toString())",
                "buildConfigField(\"boolean\", \"PLAY_RELEASE_SYNC_ENABLED\", playReleaseSyncEnabled.toString())",
            ),
        )
        assertContainsAll(
            networkText,
            listOf(
                "providers.gradleProperty(\"sync.playInternalEnabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "providers.gradleProperty(\"sync.playReleaseEnabled\").orNull?.toBooleanStrictOrNull() ?: false",
                "buildConfigField(\"boolean\", \"PLAY_INTERNAL_SYNC_ENABLED\", playInternalSyncEnabled.toString())",
                "buildConfigField(\"boolean\", \"PLAY_RELEASE_SYNC_ENABLED\", playReleaseSyncEnabled.toString())",
            ),
        )
    }

    @Test
    fun `play internal flag is staging only and fails fast on unsafe runtime configuration`() {
        val text = appBuild.readText()

        assertContainsAll(
            text,
            listOf(
                "fun isStagingPackagingTask(taskPath: String): Boolean =",
                "fun isReleasePackagingTask(taskPath: String): Boolean =",
                "contains(\"staging\", ignoreCase = true)",
                "contains(\"release\", ignoreCase = true)",
                "if (playInternalSyncEnabled && gradle.startParameter.taskNames.any(::isReleasePackagingTask))",
                "if (playReleaseSyncEnabled && gradle.startParameter.taskNames.any(::isStagingPackagingTask))",
                "if (syncEnabled && gradle.startParameter.taskNames.any(::isNonDebugPackagingTask))",
                "gradle.taskGraph.whenReady(",
                "taskGraph.allTasks.any { isReleasePackagingTask(it.path) }",
                "taskGraph.allTasks.any { isStagingPackagingTask(it.path) }",
                "sync.playInternalEnabled=true is supported only for the staging variant, never release packaging.",
                "sync.playReleaseEnabled=true is supported only for release packaging.",
                "\"Dropbox app key\" to",
                "\"Supabase URL\" to",
                "\"Supabase anon key\" to",
                "\"Supabase Google web client ID\" to",
                "uppercase.startsWith(\"PLACEHOLDER_\")",
                "uppercase.startsWith(\"YOUR_\")",
                "uppercase in setOf(\"CHANGEME\", \"CHANGE_ME\", \"TODO\", \"REPLACE_ME\")",
                "uppercase.contains(\"YOUR_PROJECT\")",
                "supabase.serviceRoleKey",
                "supabase.service_role_key",
                "SUPABASE_SERVICE_ROLE_KEY",
                "Supabase service-role keys must not be accepted or packaged in Android builds.",
            ),
        )
        assertContainsAll(
            syncBuild.readText(),
            listOf(
                "PLACEHOLDER_DROPBOX_APP_KEY",
            ),
        )
        assertContainsAll(
            networkBuild.readText(),
            listOf(
                "PLACEHOLDER_SUPABASE_URL",
                "PLACEHOLDER_SUPABASE_ANON_KEY",
                "PLACEHOLDER_SUPABASE_GOOGLE_WEB_CLIENT_ID",
            ),
        )
    }

    @Test
    fun `play internal flag gates Dropbox Google Drive and shared sync together`() {
        val text = remoteConfig.readText().replace("\r\n", "\n")

        listOf(
            "dropboxSyncEnabled",
            "gdriveSyncEnabled",
            "sharedSyncEnabled",
        ).forEach { method ->
            val methodStart = text.indexOf("override fun $method")
            assertTrue("Expected $method to exist", methodStart >= 0)
            val methodEnd = text.indexOf("\n\n", methodStart).let { if (it < 0) text.length else it }
            assertTrue(
                "$method must be gated by PLAY_INTERNAL_SYNC_ENABLED",
                text.substring(methodStart, methodEnd).contains("BuildConfig.PLAY_INTERNAL_SYNC_ENABLED"),
            )
        }
    }

    @Test
    fun `play internal CI job injects public runtime config and leaves release unflagged`() {
        val workflowText = workflow.readText()
        val playInternal = jobText(workflowText, "play-internal")
        val release = jobText(workflowText, "release")

        assertContainsAll(
            playInternal,
            listOf(
                "needs: jvm",
                "DROPBOX_APP_KEY: \${{ secrets.DROPBOX_APP_KEY }}",
                "SUPABASE_URL: \${{ secrets.SUPABASE_URL }}",
                "SUPABASE_ANON_KEY: \${{ secrets.SUPABASE_ANON_KEY }}",
                "SUPABASE_GOOGLE_WEB_CLIENT_ID: \${{ secrets.SUPABASE_GOOGLE_WEB_CLIENT_ID }}",
                "dropbox.appKey=%s",
                "supabase.url=%s",
                "supabase.anonKey=%s",
                "supabase.googleWebClientId=%s",
                "run: ./gradlew :app:bundleStaging -Psync.playInternalEnabled=true -Pbilling.enabled=true --stacktrace",
            ),
        )
        assertFalse(
            "Play Internal CI must not receive a Supabase service-role secret",
            playInternal.contains("SERVICE_ROLE") || playInternal.contains("serviceRole"),
        )
        assertContainsAll(
            release,
            listOf("run: ./gradlew :app:assembleRelease :app:bundleRelease -Psync.playReleaseEnabled=true -Pbilling.enabled=true \$FIREBASE_ARGS --stacktrace"),
        )
        assertFalse("The release job must not use the staging-only flag", release.contains("-Psync.playInternalEnabled=true"))
    }

    private fun assertContainsAll(
        text: String,
        fragments: List<String>,
    ) {
        fragments.forEach { fragment ->
            assertTrue("Expected to find '$fragment'", text.contains(fragment))
        }
    }

    private fun jobText(
        workflowText: String,
        jobName: String,
    ): String {
        val jobStart = workflowText.indexOf("\n  $jobName:")
        assertTrue("Expected $jobName job in CI workflow", jobStart >= 0)
        val nextJob =
            Regex("""\n  [A-Za-z][A-Za-z0-9_-]*:""")
                .find(workflowText, startIndex = jobStart + 1)
                ?.range
                ?.first
                ?: workflowText.length
        return workflowText.substring(jobStart, nextJob)
    }

    private companion object {
        fun findRepositoryRoot(): File {
            val start = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
            return generateSequence(start) { it.parentFile }
                .firstOrNull { candidate ->
                    File(candidate, "settings.gradle.kts").isFile &&
                        File(candidate, "app/build.gradle.kts").isFile &&
                        File(candidate, ".github/workflows/ci.yml").isFile
                } ?: start
        }
    }
}
