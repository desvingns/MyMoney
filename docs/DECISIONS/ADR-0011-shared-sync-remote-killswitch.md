# ADR-0011: Shared sync remote killswitch

- Status: Accepted
- Date: 2026-08-16
- Amends: ADR-0011 amends ADR-0010 (Monetization) decision D1

## Context

ADR-0010 D1 flipped `sync.playReleaseEnabled` to the release default (`true`) so the Supabase
shared workspace ships as the flagship paid capability. In the same section D1 stated flatly:

> There is **no remote kill switch** for this. `sharedSyncEnabled()` reads only `BuildConfig`
> flags; the `KEY_SHARED_SYNC` / `DEFAULT_SHARED_SYNC` constants and the `shared_sync_enabled`
> entry in `core/sync/src/main/res/xml/remote_config_defaults.xml` are declared but never read
> (the same is true of the Dropbox and GDrive keys). Rolling shared sync back therefore requires
> a new release. Building an actual remote kill switch is separate work and is not decided by this
> ADR.

That "separate work" is now done, in the last SPEC of the `plus-subscription-gating` epic. Once
shared sync is on by default in production, the project carries an open-ended Supabase cost
(rows, Realtime connections, egress) proportional to adoption. A new-release rollback has a
multi-hour-to-days latency (build, review, staged Play rollout) that is unacceptable if free-tier
Supabase spend spikes. A remote lever that can cut the ongoing cost within a Remote Config fetch
interval is required as a cost circuit-breaker.

This ADR resolves the contradiction with ADR-0010 D1 explicitly rather than silently: the "no
remote kill switch" statement in D1 is superseded by this decision.

## Decision

### D1 (of this ADR) — `sharedSyncEnabled()` now reads `KEY_SHARED_SYNC`

`RemoteConfigRepositoryImpl.sharedSyncEnabled()` is redefined as:

```
(PLAY_INTERNAL_SYNC_ENABLED || PLAY_RELEASE_SYNC_ENABLED || syncForced())
    && (config?.getBoolean(KEY_SHARED_SYNC) ?: DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED)
```

The build-flag disjunction is the gate that must be on for the feature to exist in a build at all;
the `KEY_SHARED_SYNC` factor is the remote killswitch that can force it back off without a release.

The two are AND-combined so the killswitch can only ever **disable an already build-enabled
feature** — it can never enable shared sync in a build that shipped without it. This is why the
absent-Remote-Config default (`config == null`, i.e. every non-Firebase build) is
`DEFAULT_SHARED_SYNC_WHEN_BUILD_ENABLED = true`, not the pre-existing `DEFAULT_SHARED_SYNC = false`:
a killswitch is "turn off something that is on", not "turn on something that is off". Defaulting the
factor to `false` would have silently killed the paid feature in every build without Firebase. The
`remote_config_defaults.xml` `shared_sync_enabled` entry is set to `true` to match.

### D2 (of this ADR) — killswitch semantics = full detach to LocalOnly (D8)

When the killswitch flips a currently-connected user off, the client performs a **full detach to
LocalOnly** via the existing `CloudSyncViewModel` path
(`detachToLocalOnly(LocalOnlyReason.RemoteKillswitch)`), reusing the same coordinator ordering
(cancellable snapshot → durable LocalOnly commit → realtime teardown) as the entitlement-expired
transition. It is deliberately **not** a read-only mode: read-only would keep Realtime connections
and pull traffic alive — precisely the Supabase costs the killswitch exists to cut. No second
detach path is introduced; the killswitch reuses the one shipped in SPEC 06.

### D3 (of this ADR) — killswitch applies to paying users too

The killswitch is a cost circuit-breaker and it detaches **all** shared-workspace users, including
active Plus subscribers. This is an accepted consequence: an uncontrolled Supabase bill is an
existential cost, and the killswitch must be able to stop it unconditionally. If the killswitch is
used while paying users are attached, refunds for the affected subscription period are handled as a
**manual, out-of-band procedure** (Play Console / support), not automated by the app.

### D4 (of this ADR) — a new release is still the preferred rollback

The killswitch is an **emergency instrument for runaway cost**, not the routine way to withdraw the
feature. Ordinary rollback (deprecating shared sync, changing tiers, fixing a defect) remains a
new-release operation as under ADR-0010. The killswitch is pulled only when the cost is spiking
faster than a release can ship.

## Consequences

- Shared sync can be cut remotely within one Remote Config fetch interval without a Play release.
- Non-Firebase builds keep shared sync on (build-flag gated) because the killswitch factor defaults
  to `true` when Remote Config is absent.
- Pulling the killswitch detaches paying users; refunds are a manual procedure.
- ADR-0010 D1's "there is no remote kill switch" sentence is superseded by this ADR.
