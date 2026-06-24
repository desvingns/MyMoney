# Distilled lessons — self-improvement loop (signal)

Git-tracked. The reflection step (`REFLECTION-PROMPT.md`) promotes durable, cross-run lessons here
from raw telemetry. Keep each lesson one tight paragraph: **what** was observed (with a metric),
**why** it matters, **what** to change. Promote only repeatable signal — not one-offs.

> Format: `### <slug>` heading + one paragraph.

---

_(none yet — seeded 2026-05-29; the first retro that finds a repeatable pattern lands here.)_
- 2026-06-13 audit4-records-01-transfers-tab: feedback 2/5 — user-built debug APK crashed on startup (NoClassDefFoundError RecurringWorker at WorkSchedulerImpl/MyMoneyApp.onCreate). Root cause = corrupted INCREMENTAL dex-merge (RecurringWorker dropped from app dex despite compiling); NOT the shipped feature. Fixed by clean :app:assembleDebug. Gap: the /mp pipeline never runs assembleDebug, so a startup/DI/dex crash escapes every gate (unit tests + androidTest-compile + verifier all green). Consider a post-ship clean-assemble + launch smoke for app-wide changes.
- 2026-06-20 dashboard-toolbar-period-fit: feedback 2/5 — single-row hybrid (auto-shrink+tighten) still wrapped "декабрь" mid-word; correct fix per user = move period switcher to its OWN full-width 2nd row, top row keeps only 4 icons. Lesson: when a label is starved in a packed row, prefer a dedicated row over font auto-shrink.
- 2026-06-19 seamless-ring: feedback 3/5 — symmetric gradient removed the color seam but a sharp band remained at the arc origin. Root cause = StrokeCap.Round on a full 360-degree arc: the two round caps overlap at the start/top (plus the blurred glow), forming a visible ridge. A seamless closed ring needs StrokeCap.Butt (and a butt-capped glow) when fraction>=1f; keep Round for partial progress.- 2026-06-22 dashboard-balance-trend-chart-07 (epic complete): feedback 3/5 — visual quality far from reference; tests/wiring green but the look diverges. User will supply a NEW reference in a separate session; redesign the trend-chart visuals against it.
- 2026-06-24 aurora-chart-blend: feedback 3/5 — misread the target. User said "плашка с графиком"/"тёмный фон на котором он рисуется" = a dark backing panel behind the chart, wanted its rectangular PERIMETER feathered into the card. I instead full-bled + edge-faded the chart line/fill. Lesson: for subtle visual asks, reproduce on-device WITH DATA and confirm the exact element before coding.
