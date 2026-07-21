# Cross-account journal sync: two Google accounts on the same shared folder don't converge
Epic: —
Order: standalone
Status: draft
Depends-on: journal-sync-cumulative-journal-and-apply-fix (done — commits 708c0570, 84d6c4e6)
Date: 2026-07-21

## SPEC
=== SPEC ===
TASK: bugfix
WHAT: When the SAME Google Drive account is connected on two devices, journal sync now converges
bidirectionally (verified on-device 2026-07-21, Pixel 9 emulator <-> OnePlus physical, after the
cumulative-journal + honest-apply-marking + currency-id-portability fixes). But when TWO DIFFERENT
Google accounts are each connected to what the user intends to be the SAME shared Drive folder, they
behave as if they have separate, isolated storage — no convergence, "separate memory" per the user's
own words. Expected/target behaviour: two different Google accounts pointed at one shared folder
must sync exactly like the single-account case — a change made under account A's device must reach
account B's device (and vice versa) via that shared folder, with no special-casing.

ROOT CAUSE IS NOT YET CONFIRMED. This SPEC captures the evidence and hypotheses gathered so far;
the next session MUST diagnose before implementing (see "Investigation plan" below) — do not jump
straight to a fix from assumption.

**UPDATE 2026-07-21 (same day, after real on-device investigation): hypothesis 1 (folder-id
mismatch) is RULED OUT.** All three real Google accounts involved (`desvingns` the owner,
`desving123456`, `shavrinaveronikans`) were confirmed to reference the IDENTICAL Drive folder id,
and journal files from multiple different accounts were directly observed landing inside that one
physical folder (Drive web UI, owner's view). Despite this, cross-account convergence still does
NOT happen — devices under different Google accounts still don't see each other as sync peers. The
leading hypothesis is now hypothesis 2 (drive.file scope / `files.list` query semantics), with a
NEW, more specific and concrete candidate added below (hypothesis 4, the `corpora` parameter) that
does NOT require any OAuth scope change and should be tried FIRST.

LAYERS: [data] [presentation] (UX/onboarding flow may need to change too, not just data-layer logic)
CHANGED_HINT: feature/cloudsync/.../CloudSyncScreen.kt (Picker request + account-picker flow),
feature/cloudsync/.../CloudSyncViewModel.kt (connect / folder-selection), core/sync/.../gdrive/
GoogleDriveJournalBackend.kt (listPeerJournals query, isFolder, client()), core/sync/.../gdrive/
AuthorizationClientDriveAuthorizer.kt (token scope), possibly new onboarding UI if the fix turns out
to be "guide the user to Drive-share the folder with the second account before connecting"
TEST_TYPES: unit [dao] — plus, since this is fundamentally a REAL Drive API / REAL multi-account
behaviour question, the diagnosis phase needs actual on-device testing with two real Google accounts
sharing one real folder; a unit test alone cannot prove or disprove the hypotheses below
CONSTRAINTS: FREE TIER ONLY (no Google Cloud billing enablement); do NOT widen the OAuth scope
beyond `DriveScopes.DRIVE_FILE` to a broader scope (e.g. full `drive`) without an explicit human
go/no-go first — that is a real permission-footprint change the user must knowingly approve, not
something to silently implement as part of a "bugfix"; if investigation concludes the desired
behaviour is fundamentally impossible under DRIVE_FILE scope, stop and surface that as an open
question rather than silently escalating scope; fakes-only for any new unit tests; deterministic
=== END SPEC ===

## Gap / context

Discovered 2026-07-21 during the user's own on-device acceptance testing right after the
cumulative-journal (708c0570) + currency-id-portability (84d6c4e6) fixes shipped. The user's exact
words: "если всё делать через один и тот же гугл аккаунт - то всё работает в обе стороны
синхронизации, если два разных гугл аккаунта - у них раздельная память" (same account: works both
ways; two different accounts: separate memory). The user wants: two different Google accounts
connected to the same shared folder should behave like one account — shared/unified sync.

### Current flow (evidence, file:line)

**Connecting an account + picking a folder:**
- `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncScreen.kt:225-238`
  — `LaunchGoogleDrivePicker` action opens `AccountPicker.newChooseAccountIntent(...)`, letting the
  user choose WHICH Google account on the device to use. This shows only accounts already signed in
  on the device.
- `CloudSyncScreen.kt:564-580` (`googlePickerRequest`) — builds an `AuthorizationRequest` scoped to
  `Scope(DriveScopes.DRIVE_FILE)` (line 567) with `PICKER_ALLOW_FOLDER_SELECTION=true` and
  `PICKER_OAUTH_TRIGGER=true`. This is Google's file/folder Picker UI, scoped to `drive.file` — an
  app using this scope can generally only see/act on files it created OR files the user explicitly
  selected via this Picker (a picked FOLDER additionally grants visibility into that folder's
  contents at pick time, per Google's documented Picker + drive.file behaviour).
- `feature/cloudsync/src/main/java/com/kshavrin/mymoney/feature/cloudsync/CloudSyncViewModel.kt:155-176`
  (`completeGoogleDriveFolderSelection`) — after the Picker returns a folder id, calls
  `journalBackend.isFolder(event.accountEmail, folderId)` (line 163), then
  `snapshotSync.connect(SyncTarget.GoogleDrive, event.accountEmail)` (line 167, persists the account
  email via `GoogleDriveRepository.connect` ->
  `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/gdrive/GoogleDriveRepository.kt:25-26` ->
  `SecureStorageImpl.writeGdriveAccountEmail`), then `setFolderId(event.folderId)` (line 168, persists
  the folder id to `JournalSyncConfigStore`).

**Listing peer journals (used every pull):**
- `core/sync/src/main/java/com/kshavrin/mymoney/core/sync/gdrive/GoogleDriveJournalBackend.kt:90-117`
  (`listPeerJournals`) — queries `'$folderId' in parents and trashed = false and name contains
  'ops-'` (the `peerJournalsQuery` helper, line 152-153), using THIS device's stored account's OAuth
  token (`client()`, no explicit `accountEmail` param passed from `JournalSyncImpl`, so it always
  falls back to `storedEmail()`). The query itself has no owner/account filter — if the Drive API
  returns cross-owner files for a shared folder under this scope, this query should find them.

### Hypotheses

1. ~~**Folder-id mismatch, not a Drive-scope limitation.**~~ **RULED OUT 2026-07-21.** Real-world
   test: `desvingns` (owner) shared the "Sync" folder with `desving123456` and `shavrinaveronikans`
   as Editors from the very start (confirmed via the Share dialog — both listed as "Редактор"
   before any of this investigation began). `shavrinaveronikans` and `desving123456` already
   resolved to the identical folder id from the start; only `desvingns` (the owner) initially
   showed a different id when compared by URL — explained by the owner viewing the folder via its
   canonical "My Drive" path while the finding/comparison for the other two accounts may have gone
   through a Drive **shortcut** object (a shortcut has its own distinct file id, separate from its
   target — a Drive UI/object-model quirk, not a real folder difference). After adding a shortcut
   for `desvingns` too, all three accounts showed the identical id. Journal files from multiple
   different accounts (`desving123456`, and the owner `desvingns`/"я") were directly observed
   landing inside this one physical folder (Drive web UI). **Despite all ids matching and multiple
   accounts successfully writing into the SAME physical folder, cross-account convergence still
   does not happen.** This decisively rules out "wrong folder" as the (sole) explanation.
2. **`drive.file` scope may not grant cross-owner file visibility even for a genuinely shared
   folder.** Now the LEADING hypothesis given (1) is ruled out. It is not confirmed whether a
   `drive.file`-scoped OAuth token for account B can see files inside a shared folder that were
   CREATED by account A (a different Google identity) via `files.list`, even when the folder
   access is unquestionably correct. Needs a real, controlled experiment (see below).
3. **Something else** — e.g. `isFolder(accountEmail, folderId)` (line 163) succeeding does not by
   itself prove the SAME Drive resource is being referenced across two different accounts if the
   two accounts have different mount/alias views of a resource (unlikely for Drive's canonical file
   ids, and now largely superseded by finding 1 above, but kept for completeness).
4. **NEW — `files.list` `corpora` parameter, NOT a scope issue at all.** `listPeerJournals`
   (`GoogleDriveJournalBackend.kt:90-117`) builds its Drive query with `setIncludeItemsFromAllDrives(true)`
   and `setSupportsAllDrives(true)` (lines 102-103) but never calls `.setCorpora(...)`. Per the Drive
   API v3 reference, `corpora` defaults to `"user"` when unset, and the `"user"` corpus is
   documented as "files created by, opened by, or **shared directly with** the user" — a file
   created by a DIFFERENT account inside a folder whose ACCESS was granted at the FOLDER level
   (not the individual file), and that this device's account never individually "opened" via its
   own Picker, may simply fall outside that corpus even though `'<folderId>' in parents` matches
   the query filter. `includeItemsFromAllDrives`/`supportsAllDrives` are Shared-Drive-specific flags
   and likely have NO effect for a plain "My Drive" folder shared via normal ACL sharing (which is
   exactly this scenario, not a Shared Drive/Team Drive). **This is the cheapest, lowest-risk thing
   to try first** — it requires NO OAuth scope change (respects the CONSTRAINTS below), just adding
   `.setCorpora("allDrives")` (or trying `"domain"`/experimenting with values) to the `files().list()`
   builder chain in both `listPeerJournals` (line 96-104) and `findOwnFileId` (line 127-141, same
   `.list()` pattern, same missing parameter). Should be tried and on-device verified BEFORE
   concluding the desired behaviour needs a scope change per hypothesis 2.

### Secondary finding (separate from the main bug, worth checking)

While inspecting the shared folder's contents directly (Drive web UI) during this investigation,
TWO files were observed with the **exact same name** —
`ops-4043ba55-5116-43de-92bb-7b13b2be46fd.jsonl` — both owned by `desving123456`, uploaded ~2
minutes apart (13:32 and 13:34). Per `GoogleDriveJournalBackend.uploadJournal` (lines 59-88), a
push should always resolve to the SAME file via `findOwnFileId` (exact-name lookup within the
folder) and either `files.update` it or `files.create` only if truly absent — two Drive files
sharing one device's expected filename suggests `findOwnFileId`'s lookup returned nothing on (at
least) one push despite the file already existing, causing a duplicate `create` instead of an
`update`. Not yet investigated further; may be a separate, real bug (e.g. Drive query eventual
consistency between rapid successive test syncs, or a genuine flaw in `findOwnFileId`/`ownFileQuery`)
or may turn out to be explained by the same `corpora` issue above (the second push's `findOwnFileId`
query silently missing the file it itself created moments earlier, under the same default-corpus
restriction). Worth a dedicated look once the main cross-account issue is resolved.

### Investigation plan (for the next session, before writing any fix)

**Already done (2026-07-21, real accounts, real folder — do NOT redo):** folder properly shared
(Editor) with 2 accounts from the start; all 3 accounts confirmed on the identical folder id;
multi-account writes into the same physical folder directly observed; cross-account convergence
still fails. Hypothesis 1 is closed. Start from step 1 below.

1. **Try hypothesis 4 first (cheapest, no scope change, no human gate needed for a code experiment):**
   add `.setCorpora("allDrives")` to the `files().list()` calls in `listPeerJournals`
   (`GoogleDriveJournalBackend.kt:96-104`) and `findOwnFileId` (lines 127-141). Rebuild, install on
   the Pixel 9 emulator (account `desving123456`) and re-test against the real shared folder that
   already has `shavrinaveronikans`'s / the owner's files in it. Confirm via `adb logcat` +
   `listPeerJournals`'s return value (reuse the temporary `Log.d`/`runCatching` instrumentation
   technique from the currency-id bug diagnosis — see `journal-sync-noncumulative-bug.md` in
   Claude's project memory for the exact pattern and its gotcha: those `Log.d` calls, added to
   `JournalSyncImpl`/`JournalApplier`, crash `core:sync`'s plain-JUnit — non-Robolectric —
   `JournalSyncImplTest` the instant it exercises a real `JournalApplier`; always `git checkout`
   the instrumentation before the next full test run, never leave it in a commit) whether OTHER
   accounts' `ops-<deviceId>.jsonl` files now appear.
2. If step 1 fixes it: this is a small, low-risk, no-scope-change bugfix — implement properly
   (SPEC → `/mp --bugfix`, full gate chain given the "high risk" precedent from the journal-cumfix
   SPEC), and ALSO investigate whether it explains/fixes the duplicate-filename finding above (a
   `findOwnFileId` query missing its own just-created file could be the exact same `corpora` gap).
3. If step 1 does NOT fix it: run a raw, app-independent Drive API experiment (e.g. a short Python
   script or `curl` with an OAuth token scoped to `drive.file` for account B) against the same real
   shared folder, to isolate whether the limitation is in this app's Kotlin code/query construction
   or a genuine, unavoidable property of the `drive.file` scope itself. If it's the latter — STOP,
   this is exactly the scope-change fork the CONSTRAINTS section warns about: surface it to the
   user as an explicit go/no-go (full `drive` scope vs. a different sharing/transport design
   entirely) rather than silently implementing either path.

## Implementation links
- commit: (pending)
- files: (pending)
