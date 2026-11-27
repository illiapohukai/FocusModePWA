# Live validation required — v22

What the repository could **not** verify, because it needs external setup (a real Firebase
project, Google accounts, signing keys, physical devices or a network lab). Mocks, fakes, the
Emulator Suite and Robolectric passing does **not** mean these areas are secure. Each item lists
exact future steps. Use a dedicated staging Firebase project and test Google accounts; never a
production project or real user accounts for adversarial steps.

| ID | Area | Not verified because | Steps |
|---|---|---|---|
| L01 | Live Google account chooser (Credential Manager) | No OAuth client / Firebase project | 1. Complete firebase-setup-v21.md §A with `app.focusmodeplus.debug`. 2. On a Play-services device, tap "Mit Google anmelden": chooser appears, cancel → no state change, choose account → signed in. 3. Repeat with two accounts on the device. 4. `adb logcat` contains no token, email or uid lines from the app. |
| L02 | Real OAuth client configuration | Not created | Verify the web client id (`client_type: 3`) is read from `google-services.json`; wrong/missing SHA-1 must end in "Anmeldung nicht möglich — Die Google-Anmeldung ist auf diesem Gerät nicht verfügbar." (or another mapped account state), not a crash. |
| L03 | Real Firebase ID token lifecycle | No project | Sign in; disable the user in the Console → next refresh shows "Konto deaktiviert"; delete the user → "Konto nicht mehr verfügbar"; revoke refresh tokens (Admin SDK `revokeRefreshTokens`) → "Anmeldung abgelaufen", sync pauses. |
| L04 | Reauthentication before deletion | No project | Delete account with a different Google account in the chooser → refused and nothing is deleted (expected message: the account-mismatch text "Bitte wähle dasselbe Google-Konto, …" or the deletion-failed state); cancel → nothing deleted. |
| L05 | Production Firestore Rules deployment | No project | Deploy `firebase/firestore.rules`; with two staging accounts, repeat the cross-account read/write/delete and reserved-field cases of `firestore.rules.adversarial.test.cjs` through the REST API using each account's own ID token; confirm the Console Rules match the file byte for byte. |
| L06 | Real App Check token (debug provider) | No registration | Register `app.focusmodeplus.debug`; add the logged debug secret; confirm requests show as verified in App Check metrics; revoke the secret afterwards. |
| L07 | Play Integrity verdict | No Play listing / signing | Link the Play Console app, add release SHA-256, install from an internal testing track; confirm verified requests; only then consider enforcement (metrics first). |
| L08 | Recursive account-deletion Cloud Function | Not deployed (Blaze plan) | Deploy `functions:focus-mode`; delete a staging account with extra documents under `/users/{uid}` (including subcollections the app never writes) → everything removed; check function logs contain no personal data. |
| L09 | Real network interception | No lab | Route a device through an intercepting proxy with a user-installed CA → Worker and Firebase requests must fail (system anchors only); with no proxy → succeed. Confirm no cleartext request is attempted (`adb shell dumpsys netstats`, proxy logs). |
| L10 | Rooted-device behavior | No rooted device | On a rooted test device: inspect `/data/data/app.focusmodeplus/`: Room and DataStore readable (expected, R06); `shared_prefs` contain Firebase's own session; `no_backup` holds the route key and draft photo; nothing contains plaintext passwords or app-stored tokens. |
| L11 | OEM backup / device transfer | OEM-specific | Auto Backup (`adb shell bmgr backupnow app.focusmodeplus`), restore on a second device: Missions/History restored, account signed out, Session frozen, reminders rebuilt, draft photo and route key absent. Repeat with one OEM transfer tool. |
| L12 | Physical-device secure storage, notifications and lock screen | No devices in CI | Lock-screen privacy per detail level; notification tap after process death navigates (tagged route); `adb shell am start -a android.intent.action.VIEW -d focusmode://session/end -n app.focusmodeplus/.MainActivity` opens the app **without** navigating. |
| L13 | Release signing identity | Signing is outside the repository | Sign with the upload key; `apksigner verify --print-certs`; record SHA-256 in Firebase and Play; keep the key backed up — updates need the same certificate forever. |
| L14 | Photo picker and document providers on Android 8–12 | No devices | Pick an image through `ACTION_OPEN_DOCUMENT` from a third-party provider; cancel; pick a non-image; all end in a German message, never a crash. |
| L15 | Worker behavior with real Gemini responses | Worker/Gemini out of scope | Owner-side monitoring only; no fuzzing or load tests against the Worker or Gemini. |

Explicitly **not** claimed by v22: live Google Sign-In security, live Firebase security, App Check
protection, Play Integrity, physical-device secure storage, rooted-device resistance, resistance
to reverse engineering, and any formal penetration test or certification.
