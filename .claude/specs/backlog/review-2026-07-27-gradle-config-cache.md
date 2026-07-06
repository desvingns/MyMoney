# Enable Gradle configuration cache + right-size build heap
Epic: review-2026-07
Order: 27 of 35
Status: draft
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
- commit: (pending)
- files: (pending)
