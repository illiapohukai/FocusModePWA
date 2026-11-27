# Firebase App Check (v21 foundation, v22 application ids)

## What is implemented

- `core/account/FirebaseRuntime.kt` installs an App Check provider factory when — and only when —
  Firebase is configured. An unconfigured build never touches App Check.
- The provider comes from the build type (`AppCheckProviderSource`):
  - **release**: `PlayIntegrityAppCheckProviderFactory` (`app/src/release/.../AppCheckModule.kt`,
    dependency `firebase-appcheck-playintegrity`, release only);
  - **debug**: `DebugAppCheckProviderFactory` (`app/src/debug/.../AppCheckModule.kt`, dependency
    `firebase-appcheck-debug`, debug only). It prints a per-install debug secret to logcat; the
    owner registers it in the Console. **No debug token is stored in Git or in the APK** (v22:
    `scripts/security/apk_audit.py` asserts that the library's `firebase_app_check_debug_token`
    resource is empty in debug and absent in release, and that release has no debug provider).
- Versions come from the Firebase BoM (35.2.0).

## What App Check is not

App Check attests that requests come from the genuine app on a genuine device. It does not
replace Firebase Authentication (who the user is) or the Security Rules (what the user may do).
All access control stays in `firebase/firestore.rules`; App Check only adds a signal.

## Rollout (owner, not done)

1. Register both Android apps under App Check — `app.focusmodeplus` (Play Integrity, release) and
   `app.focusmodeplus.debug` (debug provider) — the v22 application ids
   ([application identity v22](application-identity-v22.md)). Apps registered under any earlier id
   would never receive tokens from these builds.
2. Play Integrity needs the **SHA-256** of the signing certificate(s) in the Firebase app settings
   and a Play Console link (App integrity) for Play-distributed builds.
3. Debug builds: run the app once, copy the debug secret from logcat (`DebugAppCheckProvider`),
   add it under App Check → Apps → Manage debug tokens. Never commit it; revoke it when unused.
4. **Metrics first**: keep enforcement off for Cloud Firestore and Authentication, watch the
   verified/unverified request ratio for real installs for several days.
5. Enforce only after the metrics show that legitimate traffic passes; enforcement can be turned
   off again in the Console if a regression appears.

Enforcement is **disabled** today and must not be enabled before the steps above are verified.

## Failure behavior

With enforcement off, a failed attestation (no Play services, emulator without Google Play,
missing Play link) does not block requests. With enforcement on, such requests fail with
`PERMISSION_DENIED`; the app then shows "Zugriff verweigert" and keeps working locally.
