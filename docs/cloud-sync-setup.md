# Cloud sync setup (free tier only)

Cloud sync is available only in a debug build with its feature gate enabled. It stores the
append-only financial journal privately for the selected provider account. Only one provider is
active at a time. A provider switch creates a local safety backup, reads the target privately,
and requires an explicit collision decision before the active binding changes. No paid service or
storage plan is required.

## Dropbox App Folder

1. Open the [Dropbox App Console](https://www.dropbox.com/developers/apps) and create an app.
2. Choose **Scoped access** and **App folder** access.
3. Enable `account_info.read`, `files.metadata.read`, `files.content.read`, and
   `files.content.write`.
4. Put the app key in the ignored root `local.properties` file:

   ```properties
   dropbox.appKey=YOUR_DROPBOX_APP_KEY
   ```

The app writes device journals such as `ops-<deviceId>.jsonl` only inside its Dropbox App Folder.

## Google Drive app data

1. In [Google Cloud Console](https://console.cloud.google.com/), create or select a project and
   enable the Google Drive API.
2. Configure an External OAuth consent screen in Testing and add every test account as a test user.
3. Add only `https://www.googleapis.com/auth/drive.appdata` to the requested Drive scopes.
4. Create an Android OAuth client for package `com.kshavrin.mymoney` and the debug signing SHA-1.

Google journals are created with the `appDataFolder` parent and listed only in the
`appDataFolder` space. They are private to the selected Google account and are intentionally not
visible in the Drive UI. Do not add the broad `drive` scope or any user-visible Drive storage
selection to this setup.

## Same-account two-device round trip

1. Install the same debug build on both devices without clearing existing app data:

   ```bash
   ./gradlew :app:installDebug -Psync.forceEnabled=true -Pdropbox.appKey=YOUR_DROPBOX_APP_KEY
   ```

2. Connect one provider on both devices with the same account, grant fresh consent, and restart
   both apps to confirm the provider label persists.
3. Create a recognizable transaction, account, or category marker on device A and wait for a
   successful pull/push on device B. Create a different marker on device B and confirm it reaches
   device A.
4. Repeat the two-way exercise for Dropbox App Folder and Google Drive app data separately. Record
   the account label, remote upload/list/download evidence, and both peer records.

Do not call an OAuth integration verified merely because its consent UI appeared. A connection is
verified only after fresh consent, persisted identity, and the real two-device push/pull round trip.

## Security and lifecycle

Disconnect removes local provider credentials, the active binding, provider-scoped sync cursors,
and scheduled synchronization. It never deletes financial data on the device or the remote
journal. Legacy remote content is not read, changed, or deleted.

## Provider migration

Switching providers never replaces active credentials in place. MyMoney first asks for a Storage
Access Framework destination and exports a local database backup. It then reads the target provider
without mutating local financial data and identifies records already present locally. The user must
choose whether those colliding records keep current data or use target data; only after that
decision, a complete staged merge, and a target pull/push does MyMoney commit the new active
binding. If any step fails, the original binding remains active and the safety backup is available
for recovery.
