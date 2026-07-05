# MyMoney shared memory (both tools) — index + facts

Durable, git-tracked facts BOTH Claude and Codex need. Append-mostly; English;
never delete (refine in place with a provenance trail). Seeded 2026-07-05 from
tool-local memories — tool-local copies remain historical snapshots.

- Authoritative spec is the TDD (`TDD/MyMoney/MyMoney_TDD.md` relative to the checkout;
  absolute paths differ host vs guest). Cite line ranges, never paraphrase.
- Visual-change device gate: `Pixel_5_API_34` (SDK 34, boot-complete) is mandatory for
  visual/instrumented work; never claim on-device verification without it (AGENTS.md
  "Visual-change device gate").
- File deletion: move to repo-root `archive/` (git-ignored) with a `.<reason>` suffix;
  user empties it manually (AGENTS.md).
- Post-ship ordering: deliver the built APK to Telegram BEFORE asking the 1-5 feedback
  question; pass the explicit `app/build/outputs/apk/debug/app-debug.apk` path so the
  test APK is not sent by mistake (2026-06-21 lesson, now also in the mp orchestrator).
- Deviations AS-12 (range date picker) and AS-14 (donut labels >=3%) are locked —
  never "fix" them back to Monefy behavior.
