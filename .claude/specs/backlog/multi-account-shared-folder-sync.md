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

### Hypotheses (unconfirmed — this is the actual work of the next session)

1. **Folder-id mismatch, not a Drive-scope limitation.** The in-app folder Picker only shows folders
   the CURRENTLY authenticated account already has access to. If the user never explicitly shared
   the Drive folder with the second Google account BEFORE connecting it in the app (via Drive's own
   native sharing UI, outside this app), account B's Picker cannot select "the same folder" at all —
   it would create or pick a DIFFERENT folder (possibly same name, different Drive file id), so the
   two devices end up with two genuinely different `folderId` values in their `JournalSyncConfigStore`
   and are structurally isolated by design, not by a code bug. This is the most likely candidate and
   would point to a UX/onboarding gap (the app never surfaces "share this folder with the other
   account first" instructions or verifies the folder is actually multi-account-accessible), not a
   data-layer bug.
2. **`drive.file` scope may not grant cross-owner file visibility even for a genuinely shared
   folder.** Even if account B was properly granted Drive-level access to the EXACT SAME folder
   (same file id) before connecting, it is not confirmed whether a `drive.file`-scoped OAuth token
   for account B can see files inside that folder that were CREATED by account A (a different
   Google identity) via `files.list`. This needs a real, controlled experiment (see below) — do not
   assume either way without testing.
3. **Something else** — e.g. `isFolder(accountEmail, folderId)` (line 163) succeeding does not by
   itself prove the SAME Drive resource is being referenced across two different accounts if the
   two accounts have different mount/alias views of a resource (unlikely for Drive's canonical file
   ids, but not yet ruled out).

### Investigation plan (for the next session, before writing any fix)

1. Take two REAL Google accounts (not the same one). From account A, create a folder in Drive and
   explicitly share it (Drive's native "Share" UI) with account B's email, granting Editor access.
2. Connect account A in the app (device/emulator 1), picking that shared folder. Note the exact
   folder id `journalSyncConfig.folderId()` ends up storing.
3. Connect account B in the app (device/emulator 2), attempting to pick the SAME shared folder via
   its own Picker. Confirm (a) the folder is even visible/selectable in account B's Picker, and (b)
   the folder id stored on device 2 is IDENTICAL to device 1's.
4. If the folder ids match: create data on device 1, push, then pull on device 2 with logging
   (mirror the temporary `Log.d`/`runCatching` instrumentation technique used to diagnose the
   currency-id bug — see `journal-sync-noncumulative-bug.md` in Claude's project memory for the
   exact pattern and the gotcha it hit: `android.util.Log` calls in `JournalSyncImpl`/`JournalApplier`
   crash `core:sync`'s plain-JUnit `JournalSyncImplTest`, which is NOT Robolectric-based — always
   `git checkout` such instrumentation before the next full test run). Confirm whether
   `listPeerJournals` on device 2 actually returns device 1's `ops-<deviceId>.jsonl` file at all.
5. Branch on the result: if the folder ids diverge at step 3, the fix is UX/onboarding (guide the
   user through properly sharing the folder BEFORE each account connects, and/or verify + warn in
   `completeGoogleDriveFolderSelection` when a folder appears to have only one Drive-authorized
   account). If the folder ids match but step 4 still shows no cross-account visibility, the fix
   requires a scope change (STOP — surface to the user as an open question per the CONSTRAINTS above,
   do not silently widen scope) or a different sharing/transport mechanism entirely.

## Implementation links
- commit: (pending)
- files: (pending)
