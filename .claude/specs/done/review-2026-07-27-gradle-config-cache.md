# Enable Gradle configuration cache + right-size build heap
Epic: review-2026-07
Order: 27 of 35
Status: done
Depends-on: —
Date: 2026-07-06

## SPEC
=== SPEC ===
TASK: feature
WHAT: Turn on org.gradle.configuration-cache=true in gradle.properties, fix any incompatible tasks it flags (KSP/Hilt/Room/kover/detekt/ktlint/oss-licenses are the usual suspects), and raise org.gradle.jvmargs from -Xmx2048m to -Xmx4096m; verify with timed before/after runs of :app:assembleDebug and testDebugUnitTest (clean + incremental) on this 16-module build.
LAYERS: [data]
CHANGED_HINT: gradle.properties, root build.gradle.kts, any task registrations the config-cache report names
TEST_TYPES: unit
CONSTRAINTS: full local verification — CI must also pass with the flag (JVM job); if a plugin is fundamentally config-cache-incompatible, document it and gate the flag off rather than fight it; report measured timings, not estimates
=== END SPEC ===

## Gap / context
Free ~40% incremental-build savings on a 16-module project; heap 2GB is likely
throttling KSP. Source: review items 16+21 (P2/S + P3/S).

## Implementation links
- commit: 7dd70bae (enable), 1c11282a (drop out-of-scope version bump), 7bcb2068 (cap Kotlin daemon)
- files: gradle.properties, gradle/libs.versions.toml
- date: 2026-07-25

### Outcome

Configuration cache enabled; `org.gradle.jvmargs` 2048m -> 4096m.

Blocker: `oss-licenses-plugin` 0.10.6 marks `:app:debugOssLicensesTask` incompatible, so the cache
entry was discarded on every `:app` assemble/bundle. Bumped `gmsOssLicenses` 0.10.6 -> 0.13.0;
generated `third_party_licenses` / `third_party_license_metadata` are byte-identical between the
two plugin versions. Not a TDD-pinned version, so the epic's `[TDD-revision]` rule does not apply.

`kotlin.daemon.jvmargs=-Xmx2048m` added: without it the Kotlin daemon inherits `org.gradle.jvmargs`,
putting 8GB of max-heap ceilings on the 7GB `ubuntu-latest` runner used by all three CI jobs.
Measured committed peaks: Gradle daemon 2652MB + Kotlin daemon 576MB = ~3.2GB (~5.2GB with a 2GB
emulator).

Measured timings (not estimates):

| task | run | before | after |
|---|---|---|---|
| `:app:assembleDebug` | no-op | 15s | 2s |
| `:app:assembleDebug` | clean | 49s | 12s |
| `testDebugUnitTest` | no-op | 8s | 4s |
| `testDebugUnitTest` | clean | 19s | 9s |

Open item carried to the manual checklist: the OSS-licenses screen was not opened on device, so
plugin 0.13.0 against the unchanged `play-services-oss-licenses` 17.1.0 runtime is argued from
byte-identical generated assets rather than observed.
