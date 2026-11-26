# Firebase setup for v21 (owner checklist)

Nothing in this list has been done by the repository or by any automated session. The repository
contains no Firebase project, no `google-services.json`, no OAuth client, no SHA fingerprint, no
App Check debug token and no service-account key. Every step below is a manual action in the
[Firebase Console](https://console.firebase.google.com/) or on the owner's machine.

## Facts from the code

| Item | Value | Source |
|---|---|---|
| Android `applicationId` (release) | `io.github.illiapohukai.focusmode` | `android/app/build.gradle.kts` |
| Android `applicationId` (debug) | `io.github.illiapohukai.focusmode.debug` (suffix `.debug`) | same file, `buildTypes.debug.applicationIdSuffix` |
| Configuration file location | `android/app/google-services.json` (gitignored) | `android/app/build.gradle.kts` |
| Firebase libraries | Firebase Android BoM 35.2.0: Auth, Firestore, App Check (+ Play Integrity in release, debug provider in debug) | `android/gradle/libs.versions.toml` |
| Sign in with Google | Credential Manager 1.6.0 + `googleid` 1.2.1 (`GetSignInWithGoogleOption`) | `core/account/FirebaseAccountRepository.kt` |
| Google Services Gradle plugin | 4.6.0, applied only when the file exists | `android/app/build.gradle.kts` |
| Emulator project | `demo-focus-mode` (never a real project) | `firebase/.firebaserc` |

Recommendation (not applied): the id is valid for Firebase and Google Play, so it was not changed.
If the app is ever published under another name, change it **before** the first store upload and
before registering it in Firebase; afterwards it cannot change without a new Play listing.

Register **both** package names as Android apps in the same Firebase project. The downloaded
`google-services.json` then contains both clients; the Google Services plugin fails the build of
a variant whose package is missing from the file.

## A. Required for local development

1. In the Firebase Console, **create a new project** for Focus Mode+ only (for example
   "Focus Mode Plus"). Do not reuse the Google AI Studio / Gemini billing project, the Cloudflare
   project or any existing Google Cloud project.
2. Do not enable Google Analytics for the project (the app ships no analytics).
3. Project settings → General → **Add app → Android**:
   - package `io.github.illiapohukai.focusmode.debug` (for debug builds);
   - again for `io.github.illiapohukai.focusmode` (for release builds).
4. Generate the debug fingerprints on your machine:
   ```bash
   keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android -keypass android
   ```
   Copy **SHA-1** and **SHA-256** into the debug app's settings (Project settings → Your apps → Add fingerprint).
5. Authentication → Get started → Sign-in method → **Google → Enable**; set a project support
   email. No other provider is enabled (no email/password, phone, anonymous, Apple, Microsoft, GitHub).
6. Download `google-services.json` (after adding both apps and the fingerprints) and place it at
   `android/app/google-services.json`. It is listed in `.gitignore`; never commit it.
7. Build: `cd android && ./gradlew :app:assembleDebug`. `BuildConfig.FIREBASE_CONFIGURED` becomes
   `true` and the OAuth web client id is read from the file (`client_type: 3`). If the file has no
   web client (Google provider not enabled when downloading), download it again.
8. Install the Firebase CLI (Node 24): `npm install -g firebase-tools@15.37.0`, or use `npx`.
9. Run the Rules and deletion tests against the Emulator Suite:
   ```bash
   cd firebase
   npm ci
   npm run test:emulator   # starts Firestore + Auth emulators for demo-focus-mode only
   ```
10. Remove (or rename) `android/app/google-services.json` once and build again to confirm the
    unconfigured build still works: Settings shows "Cloud-Synchronisierung ist in diesem Build
    noch nicht eingerichtet." and every other feature is unchanged.

## B. Required for staging

1. Firestore Database → **Create database** in production mode, Native mode, in a European
   location (for example `eur3` multi-region or `europe-west3` Frankfurt) **[TO BE CONFIRMED]**.
   The location cannot be changed later.
2. Deploy only the tested Rules (never permissive development Rules):
   ```bash
   cd firebase
   npm run test:emulator                                   # must pass first
   npx firebase-tools@15.37.0 deploy --only firestore:rules,firestore:indexes --project <your-project-id>
   ```
3. Add the staging signing key's SHA-1 and SHA-256 to the release app (if staging builds are signed
   with a separate key).
4. App Check → Apps → register the Android apps with **Play Integrity** (release) and
   **Debug provider** (debug). Keep enforcement **off** (monitoring only). See
   [firebase-app-check-v21.md](firebase-app-check-v21.md).
5. On an emulator image **with Google Play** or a real device: sign in, check the Account screen.
6. Verify local-data migration: create Missions and Sessions as a guest, sign in, review the
   preview, tap "Lokale Daten mit diesem Konto verbinden", check the Firestore data browser.
7. Verify synchronization with a second device (or a second emulator) signed into the same account.
8. Verify sign-out (data stays; "Abmelden" never deletes cloud data).
9. Verify account switching with two test Google accounts (no data crosses accounts).
10. Verify the deletion boundary: "Konto löschen" removes `/users/{uid}` and the Auth user; see
    [account-deletion-v21.md](account-deletion-v21.md).

## C. Required before a pilot

1. Configure release signing outside the repository (keystore never committed).
2. Add the **release** SHA-1 and SHA-256 (and the Play App Signing key's fingerprints once the app
   is in Play) to the release Firebase app; download `google-services.json` again.
3. Verify Google sign-in with a signed release APK (R8 enabled).
4. Link the Firebase project to Google Play (Play Console → App integrity) for Play Integrity.
5. Watch App Check metrics (verified vs. unverified requests) for real installs.
6. Keep App Check enforcement off until the metrics show legitimate traffic passing.
7. Deploy and test the recursive deletion function (`firebase/functions`), see the deletion doc.
8. Test backup export before and after deletion.
9. Test account switching on the pilot devices.
10. Update the privacy texts ([privacy-account-sync-v21.md](privacy-account-sync-v21.md)) and the in-app privacy section.
11. Define an incident contact and a process for credential or data incidents **[TO BE CONFIRMED]**.

## D. Required before production

1. Deploy the production Rules (same file, tests green).
2. Deploy the production recursive deletion function.
3. Enable App Check enforcement for Firestore and Authentication only after validation.
4. Configure Google Cloud budget alerts for the project.
5. Decide retention for cloud data of inactive accounts **[TO BE CONFIRMED]**.
6. Complete the legal review (controller, legal basis, processors, transfers) **[TO BE CONFIRMED]**.
7. Complete the Google Play Data Safety form (account data, Missions/History synced to Google
   Cloud, deletion available in the app).
8. Add production monitoring for Rules denials and function errors (no user content in logs).
9. Verify release-build authentication from the Play-distributed binary.
10. Test account restoration on another device (sign in on a fresh install: data downloads automatically).

## What the code already does without any of this

- Builds, tests and runs completely without Firebase (guest mode).
- Never initializes Firebase without a configuration (`FirebaseInitProvider` removed from the manifest).
- Ships no Firebase Admin SDK, no service-account key and no debug token.
