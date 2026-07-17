# Cloud sync setup (free tier only)

This guide takes MyMoney cloud sync from gated-off to testable on a **debug** build. It covers
registering the two providers, wiring their credentials through the existing BuildConfig seams,
and running one manual round-trip. Everything here is **debug-only** — release builds keep sync
gated OFF regardless of these settings.

Prerequisites: free tiers only. No paid plans are required or expected.

---

## 1. Dropbox

The Dropbox app key is **public** (it is embedded in the app manifest), but it must still never be
committed — keep it in `local.properties` (gitignored).

1. Go to the Dropbox **App Console**: <https://www.dropbox.com/developers/apps>.
2. Click **Create app**.
3. Choose API: **Scoped access**.
4. Choose access type: **App folder** (the app only ever sees its own folder — least privilege).
5. Name the app (e.g. `MyMoney-dev`) and create it.
6. On the app's **Settings** tab, copy the **App key**.
7. If the SDK OAuth flow you use requires an explicit redirect URI, add it under **OAuth 2 →
   Redirect URIs** on the same tab. (The app-folder token flow used by the bundled Dropbox SDK
   generally does not need one; add it only if the sign-in flow reports a redirect mismatch.)
8. Note the free **development-mode limit**: up to **500 linked users** before you would need to
   apply for production. That is far beyond what a personal dev/test round-trip needs.

Put the key in `local.properties` at the repo root (this file is gitignored):

```properties
dropbox.appKey=YOUR_DROPBOX_APP_KEY
```

Never commit this value. The build reads it into `manifestPlaceholders["dropboxAppKey"]` and
`BuildConfig.DROPBOX_APP_KEY`; when the property is absent the build falls back to
`PLACEHOLDER_DROPBOX_APP_KEY` and Dropbox sign-in cannot complete.

---

## 2. Google Drive

Drive uses OAuth by **package name + SHA-1** — there is **no client secret embedded in the app**,
and therefore **no Drive BuildConfig field**. The device just needs a signed-in Google account that
is registered as a test user.

1. Open the **Google Cloud Console**: <https://console.cloud.google.com/>.
2. Create or select a project.
3. **APIs & Services → Library** → search **Google Drive API** → **Enable**.
4. **APIs & Services → OAuth consent screen**:
   - User type: **External**.
   - Publishing status: leave in **Testing**.
   - Add **your Google account** under **Test users**.
   - Add the scope **`.../auth/drive.appdata`** (the app data folder scope — least privilege).
5. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Android**.
   - Package name: `com.kshavrin.mymoney`.
   - SHA-1: the **debug** signing certificate fingerprint (add the release SHA-1 later, when you
     register the production build).

### Getting the debug SHA-1

Either use the Gradle signing report:

```bash
./gradlew signingReport
```

and read the `SHA1` under the `debug` variant, or run keytool directly against the default debug
keystore:

```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey \
  -storepass android -keypass android
```

No client secret is stored in the app. On device, sign in with the Google account you added as a
test user; Drive access is granted for the `drive.appdata` scope only.

---

## 3. Run a debug round-trip (once registered)

This is **debug-only** and never affects release (the gate is `BuildConfig.DEBUG &&
BuildConfig.SYNC_FORCE_ENABLED`).

1. Build/install a debug variant with the force flag (and the Dropbox key, if not already in
   `local.properties`):

   ```bash
   ./gradlew :app:installDebug -Psync.forceEnabled=true -Pdropbox.appKey=YOUR_DROPBOX_APP_KEY
   ```

   (If `dropbox.appKey` is already in `local.properties`, you can drop the `-Pdropbox.appKey`.)
2. Launch the app, open cloud sync settings, and enable the provider you want to test
   (Dropbox or Google Drive). Complete the provider sign-in.
3. Trigger a sync and verify the data lands remotely (Dropbox app folder / Drive app-data), then
   verify a pull restores it.

Because the gate requires `BuildConfig.DEBUG`, a **release** build with the same properties keeps
sync gated OFF — `SYNC_FORCE_ENABLED` is ignored when `DEBUG` is false.

### CI note

CI materializes `dropbox.appKey` into `local.properties` only when the `DROPBOX_APP_KEY` repository
secret is supplied; otherwise the build stays on the placeholder. Drive needs no CI secret.

---

## Deferred

The real, recorded round-trip on the `Pixel_5_API_34` emulator is the **follow-up** step. It is
pending the user's provider registrations (Dropbox app + Google Cloud OAuth client). This slice
only wires the debug gate-flip, the CI secret seam, and this guide.
