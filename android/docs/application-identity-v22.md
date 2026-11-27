# Application identity (v22)

v22 changes the Android application identity **once**, before any Google Play release and before
any Firebase project registration. From now on these values are permanent.

| | Before v22 | v22 |
|---|---|---|
| Release application id | `io.github.illiapohukai.focusmode` | **`app.focusmodeplus`** |
| Debug application id | `io.github.illiapohukai.focusmode.debug` | **`app.focusmodeplus.debug`** |
| Gradle `namespace` (`:app`) | `io.github.illiapohukai.focusmode` | `app.focusmodeplus` |
| Module namespaces | `io.github.illiapohukai.focusmode.<module path>` | `app.focusmodeplus.<module path>` (e.g. `app.focusmodeplus.core.data`) |
| Kotlin packages | `io.github.illiapohukai.focusmode.*` | `app.focusmodeplus.*` (all 40 source roots moved) |
| Release label | Focus Mode+ | **Focus Mode+** |
| Debug label | Focus Mode+ | **Focus Mode+ Debug** (`app/src/debug/res/values/strings.xml`) |
| Version | 21.0.0 (2100) | 22.0.0 (2200); debug `22.0.0-debug` |

The visible label may contain spaces and `+`; the technical id contains neither.

## Why

- `app.focusmodeplus` names the product, not a personal GitHub namespace, and is valid for
  Google Play, Firebase and OAuth clients.
- No user has installed a store build and no Firebase app, OAuth client, App Check registration or
  Play listing exists yet, so this is the last moment the id can change without a new store
  listing and a data migration.
- Namespace and application id are aligned (`namespace = applicationId = app.focusmodeplus`), so
  generated `R` and `BuildConfig` live in the same package as the code.

## What changed in the repository

- `android/app/build.gradle.kts`: `applicationId = "app.focusmodeplus"`, `.debug` suffix kept,
  version 22.0.0 / 2200.
- `android/build-logic/.../ProjectExtensions.kt`: `BASE_NAMESPACE = "app.focusmodeplus"`; every
  module namespace derives from it. Build-logic group `app.focusmodeplus.buildlogic`.
- All Kotlin sources moved with `git mv` from `src/*/kotlin/io/github/illiapohukai/focusmode/` to
  `src/*/kotlin/app/focusmodeplus/` (main, debug, release and test source sets); `package` and
  `import` lines (including generated `R`/`BuildConfig` imports) follow.
- `app/proguard-rules.pro`: keep rules target `app.focusmodeplus.**`.
- Explicit intent actions: `app.focusmodeplus.action.REMINDER`, `app.focusmodeplus.action.SESSION_END`
  (both used only with explicit, immutable PendingIntents to non-exported receivers).
- Room schema export folder moved to `core/data/schemas/app.focusmodeplus.core.data.db.FocusDatabase/`
  (Room names it after the database class). **The JSON files were moved, not edited**: identity
  hashes and the v1→v5 migration tests are unchanged.
- Manifest placeholders were already `${applicationId}`-based and now resolve to the new id:
  - FileProvider `app.focusmodeplus.materialcapture` (debug `app.focusmodeplus.debug.materialcapture`);
  - `androidx.startup` `app.focusmodeplus.androidx-startup`;
  - androidx.core's signature permission `app.focusmodeplus.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- Runtime code derives every package-dependent value from `context.packageName` (FileProvider
  authority, launch intents, notification PendingIntents), so it follows the id automatically.

Not changed (not package-derived, and changing them would only break compatibility):
the in-app route scheme `focusmode://` and alarm data scheme `focusmode-reminder:`, WorkManager
unique work name `focus-account-sync`, notification channel ids, SharedPreferences/DataStore file
names, the Room database file name, the backup format (`"format":"focusmode-backup"`), the
Firestore data model and the Cloudflare Worker contract.

## Consequences

- **Old and new APKs are different Android applications.** A device can have both installed side
  by side (old debug, new debug, new release).
- **Android sandbox data does not move between package ids.** Room, DataStore, the draft photo and
  the account sign-in of an old build stay in the old app's sandbox. The new app starts empty.
  There is no automatic migration, and none is possible without an export.
- **Old installed debug data will not appear in the new application.**
- **Moving test data requires an explicit backup export and import** (below).
- **Firebase** apps must be registered under `app.focusmodeplus` and `app.focusmodeplus.debug`
  (no Firebase app exists for the old ids, so nothing has to be deleted there).
- **SHA fingerprints** are tied to the signing certificate, not to the id, but Firebase records
  them per registered app: add them to the new apps.
- **Google Play**: the first upload makes `app.focusmodeplus` permanent. It can never be renamed;
  every update must use the same id **and the same signing certificate** (Play App Signing upload
  key rules apply).

## Developer migration steps

1. Pull v22 and rebuild: `cd android && ./gradlew :app:assembleDebug`.
2. On a test device that has an old build with data you want to keep:
   1. open the **old** app → Einstellungen → Daten → *Backup exportieren* (choose the full
      backup) and save the file to the device's Downloads or a cloud folder;
   2. install the new debug build (`adb install -r app/build/outputs/apk/debug/app-debug.apk`);
   3. open **Focus Mode+ Debug** → Einstellungen → Daten → *Backup importieren* and choose the file;
      the preview shows what will be imported (Missions, History) and what is never imported
      (active Session, app selection, streaks/League values, integrity metadata).
3. Clean up old test installs once the data is safe:
   ```bash
   adb uninstall io.github.illiapohukai.focusmode.debug
   adb uninstall io.github.illiapohukai.focusmode      # only if an old release build was installed
   adb shell pm list packages | grep -i focus          # expect app.focusmodeplus(.debug) only
   ```
4. Re-grant the special accesses the new app needs (Usage Access, Do Not Disturb access,
   notifications): Android ties them to the package and the new app has none of them.

## Backup compatibility

- The backup file format is unchanged (`format: "focusmode-backup"`, `exportVersion: 1`, the same
  integrity canonicalization). Files written by the old Android build, by v22 and by the PWA can
  be imported by v22. No historical identifier inside backups was rewritten.
- A backup never contains the application id, account tokens, the Firebase uid binding or the
  selected-app inventory, so nothing in it depends on the package name.
- v22 import validation is stricter in three places (see the findings document): times before
  2000-01-01 or more than one day in the future are skipped (V22-F03), out-of-range ISO years no
  longer crash the preview (V22-F04) and number literals longer than 40 characters reject the file
  (V22-F05). Every file written by `JSON.stringify` or by this app passes these checks.

## Firebase registration (owner, not done by the repository)

Follow [firebase-setup-v21.md](firebase-setup-v21.md) with the v22 ids:

1. Project settings → Add app → Android: `app.focusmodeplus.debug`, then `app.focusmodeplus`.
2. Add the debug keystore SHA-1/SHA-256 to the debug app and the release (or Play App Signing)
   SHA-1/SHA-256 to the release app.
3. Download `google-services.json` (it must list both package names; the Google Services plugin
   fails a variant whose package is missing) to `android/app/google-services.json` — never commit it.
4. App Check: register both apps (Play Integrity for release, debug provider for debug).
5. Credential Manager / Sign in with Google uses the Firebase-created web OAuth client
   (`client_type: 3`) from that file; Android OAuth clients are created per package and SHA by
   Firebase when the fingerprints are added.

The repository contains no Firebase project id, OAuth client, web client id, SHA fingerprint,
`google-services.json` or App Check registration, and builds without them remain fully functional
in guest mode. The emulator tests use only the demo project `demo-focus-mode`.

## Verification

- `scripts/security/apk_audit.py` asserts id, label, version, debuggable flag and provider
  authorities for both APKs and that debug and release ids differ by exactly `.debug`.
- `scripts/security/source_audit.py` asserts that the old id appears only in this document and
  the v22 security documents, and that every Kotlin source sits below `app/focusmodeplus/`.
- `app/src/test/.../SecurityV22ManifestAndStorageTest.kt` checks the debug identity and label at
  runtime (Robolectric).
