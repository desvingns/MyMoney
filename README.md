# MyMoney

## Dependency-update radar

Renovate monitors the versions declared in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) on a monthly schedule. It is a report-only radar: updates appear first in the Dependency Dashboard and require a human approval before a grouped Gradle pull request is created. Renovate never automerges these pull requests.

The Android stack is locked by [`TDD/MyMoney_TDD.md`](TDD/MyMoney_TDD.md). Review each proposed update against that TDD before merging it. A major-version update requires an explicit TDD revision first; a dependency radar pull request is information, not authorization to change the stack.

Radar pull requests use the `dependencies` and `dependency-radar` labels. Major-version updates additionally receive `tdd-revision-required`; that label is a review signal, not an approval. The `dependency-radar` label skips only the emulator-heavy connected-test CI job; JVM checks still run.
