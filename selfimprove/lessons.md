# Distilled lessons — self-improvement loop (signal)

Git-tracked. The reflection step (`REFLECTION-PROMPT.md`) promotes durable, cross-run lessons here
from raw telemetry. Keep each lesson one tight paragraph: **what** was observed (with a metric),
**why** it matters, **what** to change. Promote only repeatable signal — not one-offs.

> Format: `### <slug>` heading + one paragraph.

---

_(none yet — seeded 2026-05-29; the first retro that finds a repeatable pattern lands here.)_
- 2026-06-13 audit4-records-01-transfers-tab: feedback 2/5 — user-built debug APK crashed on startup (NoClassDefFoundError RecurringWorker at WorkSchedulerImpl/MyMoneyApp.onCreate). Root cause = corrupted INCREMENTAL dex-merge (RecurringWorker dropped from app dex despite compiling); NOT the shipped feature. Fixed by clean :app:assembleDebug. Gap: the /mp pipeline never runs assembleDebug, so a startup/DI/dex crash escapes every gate (unit tests + androidTest-compile + verifier all green). Consider a post-ship clean-assemble + launch smoke for app-wide changes.
