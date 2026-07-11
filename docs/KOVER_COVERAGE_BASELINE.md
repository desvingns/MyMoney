# Kover coverage baseline

Measured on 2026-07-11 with JBR 21 and the current JVM unit-test suites. Each
module report excludes generated Hilt, Room, and `BuildConfig` classes before
calculating line coverage.

```bash
./gradlew :core:domain:koverXmlReportJvm \
  :core:database:koverXmlReportDebug \
  :core:datastore:koverXmlReportDebug \
  :feature:cloudsync:koverXmlReportDebug \
  :feature:dashboard:koverXmlReportDebug \
  :feature:lockscreen:koverXmlReportDebug \
  :feature:onboarding:koverXmlReportDebug \
  :feature:settings:koverXmlReportDebug \
  :feature:transaction:koverXmlReportDebug \
  :feature:transactionslist:koverXmlReportDebug
```

| Module | Covered / total lines | Observed | Gate | Target |
| --- | ---: | ---: | ---: | ---: |
| `:core:domain` | 1267 / 1393 | 90.95% | 90% | 80%+ |
| `:core:database` | 357 / 2077 | 17.19% | 17% | 60%+ |
| `:core:datastore` | 178 / 260 | 68.46% | 68% | 60%+ |
| `:feature:cloudsync` | 153 / 370 | 41.35% | 41% | 50%+ |
| `:feature:dashboard` | 1059 / 3174 | 33.36% | 33% | 50%+ |
| `:feature:lockscreen` | 188 / 599 | 31.39% | 31% | 50%+ |
| `:feature:onboarding` | 26 / 199 | 13.07% | 13% | 50%+ |
| `:feature:settings` | 495 / 1436 | 34.47% | 34% | 50%+ |
| `:feature:transaction` | 656 / 1417 | 46.29% | 46% | 50%+ |
| `:feature:transactionslist` | 453 / 1285 | 35.25% | 35% | 50%+ |

The gate is deliberately one whole percentage point or less below the measured
result. The target is a ratchet direction, not an unmeasured threshold that
would make the current CI red. Raising a gate requires a fresh report and
additional coverage; it must not be achieved by weakening or deleting tests.

`:feature:dictionaries` is intentionally outside this first ladder because its
pre-existing JVM suite currently fails five locale decimal-separator assertions.
It needs a clean measurement before it can receive a defensible floor.
