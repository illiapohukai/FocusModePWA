# OWASP MASVS self-assessment — Focus Mode+ Android v22

Reference: OWASP MASVS v2 control groups, with test approaches from the OWASP MASTG. This is a
self-assessment of the repository at v22 by its development process. It is **not an OWASP
certification, not a MAS L1/L2 attestation and not a penetration test**. A control is marked
**Pass** only when code, configuration and an automated test or audit in this repository show it;
architectural intent alone is never a Pass.

Status values: **Pass** · **Partial** · **Fail** · **N/A** (not applicable) · **Device** (requires
device validation) · **Live Firebase** (requires a real Firebase project) · **Deferred** (with
justification).

Evidence keys: `apk_audit` = `scripts/security/apk_audit.py` (both APKs), `source_audit` =
`scripts/security/source_audit.py`, `Rules` = `firebase/test/*.cjs` (Emulator Suite, demo project),
test classes are JVM/Robolectric tests under `android/**/src/test`.

## Summary

| Group | Pass | Partial | Device / Live | N/A / Deferred | Fail |
|---|---|---|---|---|---|
| STORAGE | 1 | 1 | — | — | 0 |
| CRYPTO | 1 | — | — | 1 | 0 |
| AUTH | — | 1 | 1 | 1 | 0 |
| NETWORK | 1 | — | — | 1 (Deferred) | 0 |
| PLATFORM | 2 | 1 | — | — | 0 |
| CODE | 2 | 1 | — | 1 (Deferred) | 0 |
| RESILIENCE | — | 1 | 1 | 2 (Deferred) | 0 |
| PRIVACY | 3 | 1 | — | — | 0 |

No control is marked Fail. The Partial and Device/Live entries are the honest boundary of what
JVM tests and an emulator can show.

## MASVS-STORAGE

**STORAGE-1 — The app securely stores sensitive data.** — **Partial**
- No tokens or credentials are stored by app code: the Google ID token lives for one call
  (`FirebaseAccountRepository.googleIdToken`), Firebase Auth keeps its own session in shared
  preferences. Room holds the Firebase uid only (`account_binding`). Evidence:
  `SecurityV22ManifestAndStorageTest.theRoomSchemaHoldsNoTokensCredentialsOrAccountProfile`,
  `source_audit` (no logging), `AccountBoundaryTest`.
- Temporary images live in `noBackupFilesDir` (draft) and one cache file (camera), deleted after
  processing or with the draft. Evidence: `SecurityV22AttachmentTest.noTemporaryFileSurvives…`,
  `AndroidMissionMaterialStoreTest.cameraCaptureTarget…`.
- Firestore uses a memory-only cache (`FirebaseRuntime`), so no second on-disk copy of cloud data.
- Partial because: data at rest (Room, DataStore) relies on Android file-based encryption and the
  app sandbox, without app-level encryption — a deliberate decision (no key-management benefit
  against the device owner, the realistic actor); rooted-device behavior is not validated (Device).

**STORAGE-2 — The app prevents leakage of sensitive data.** — **Pass**
- Backups: explicit include list (database, DataStore); shared preferences (Firebase Auth),
  `no_backup` and caches excluded. Evidence: `SecurityV22ManifestAndStorageTest.backupPoliciesIncludeOnlyTheDatabaseAndPreferences`.
  The included database is the user's own backup by product decision (residual risk V22-F15).
- Logs: no logging in production code (`source_audit` `kotlin.logging`). No analytics, no crash reporting.
- Notifications: lock-screen variants without task text; HIDDEN mode is secret
  (`FocusNotifierTest.lockScreenNeverShowsTaskTextAndHiddenModeIsSecret`).
- Clipboard: not used (`source_audit` `kotlin.clipboard`). Keyboard caches: standard text fields.
- FileProvider: one cache sub-directory (`SecurityV22ManifestAndStorageTest.fileProviderExposesOnlyTheCameraCacheDirectory`).

## MASVS-CRYPTO

**CRYPTO-1 — Strong cryptography used correctly.** — **Pass**
- TLS by the platform (system CAs). Backup integrity: SHA-256 over canonical JSON (corruption
  detection only, documented as such). v22 route tags: HMAC-SHA256 with a 256-bit `SecureRandom`
  key, constant-time comparison (`MessageDigest.isEqual`). No custom ciphers, no hard-coded keys.
  Evidence: `SecurityV22DeepLinkAuthTest`, `BackupExportAndIntegrityTest`, `apk_audit` secret rules.

**CRYPTO-2 — Key management.** — **N/A (scoped)**
- The only app key is the per-install route-tag key in `noBackupFilesDir`; it protects navigation
  integrity, never leaves the device and is regenerated if lost. No encryption keys exist. Keystore
  use would add nothing for this purpose; revisit if app-level encryption is ever introduced.

## MASVS-AUTH

**AUTH-1 — Secure authentication and authorization protocols.** — **Partial**
- Sign in with Google via Credential Manager (`GetSignInWithGoogleOption`) → Firebase Auth; no
  passwords; Google is the only provider. Authorization is in Firestore Rules: owner-only paths,
  exact schemas, reserved server fields, versions. Evidence: `Rules` (70 tests incl. adversarial),
  `SecurityV22SyncTest`, `AccountLifecycleTest`, `AccountBoundaryTest`.
- Partial because the real OAuth client, real tokens, the deployed Rules and App Check are not
  available to this repository (Live Firebase; see live-validation-required-v22.md).

**AUTH-2 — Local authentication.** — **N/A**
- The app has no local authentication (no PIN/biometric gate; passkey biometric permissions are removed).

**AUTH-3 — Additional authentication for sensitive operations.** — **Live Firebase**
- Account deletion requires a fresh Google reauthentication that must return the same uid
  (`AccountLifecycle.deleteAccount`; `AccountLifecycleTest.accountDeletionNeedsARecentSignIn…`), with
  fakes only. Needs live validation of Firebase's recent-login enforcement.

## MASVS-NETWORK

**NETWORK-1 — All traffic secured.** — **Pass**
- `network_security_config.xml`: cleartext off, system trust anchors only, no debug overrides; no
  `usesCleartextTraffic`. OkHttp: HTTPS base URL enforced, no redirects, no automatic retries, no
  cookies, no custom TrustManager/HostnameVerifier, timeouts, 512 KB body cap. Firestore/Auth use
  Google's TLS stack. Evidence: `SecurityV22ManifestAndStorageTest.networkSecurityConfigIsHttpsOnlyWithSystemAnchors`,
  `SecurityV22NetworkTest`, `WorkerV19Test.oversizedBodiesAreNotRead`, `apk_audit`, `source_audit`.
- Real interception attempts (proxy with user CA) were not performed (Device).

**NETWORK-2 — Identity pinning.** — **Deferred (justified)**
- Not implemented. The Worker host is a `workers.dev` endpoint whose certificates are managed and
  rotated by Cloudflare without notice to the app; Firebase endpoints rotate Google certificates.
  A pin without an operational rotation plan would turn certificate rotation into a full outage of
  AI and sync. System trust (no user CAs) already blocks the common interception path. Revisit with
  a custom domain and a backup-pin rotation procedure.

## MASVS-PLATFORM

**PLATFORM-1 — IPC used securely.** — **Pass**
- Only the launcher activity of the app is exported; dependency components are protected by
  `BIND_JOB_SERVICE`, `DUMP` or Google's signature permission. No implicit VIEW filter; routes from
  other apps are ignored (HMAC tag, V22-F06). PendingIntents immutable and explicit. Providers not
  exported, FileProvider grant-only. Narrow `<queries>`. Evidence: `apk_audit` (exported allow-list,
  providers, queries, filters), `SecurityV22ManifestAndStorageTest`, `SecurityV22DeepLinkAuthTest`,
  `source_audit` (`kotlin.pending-intents-immutable`).

**PLATFORM-2 — WebViews used securely.** — **Pass** (no WebView exists; `source_audit` `kotlin.webview`).

**PLATFORM-3 — User interface used securely.** — **Partial**
- No secrets are ever displayed (no token, no uid). The account screen shows the email by design.
- Decision: no `FLAG_SECURE`. The app is a productivity tool whose screens are shown in support
  requests and the pitch; blocking screenshots app-wide would harm those workflows, and no screen
  shows credentials or deletion secrets (account deletion is a confirmation plus Google
  reauthentication, not a typed secret). Recents previews may show the email and Mission titles.
- Task hijacking: OS-patched on Android 11+, residual on 8–10 (V22-F13). Partial because overlay and
  tapjacking behavior was not validated on devices (Device).

## MASVS-CODE

**CODE-1 — Up-to-date platform version.** — **Partial**
- targetSdk 37, compileSdk 37.2; minSdk 26 keeps Android 8–10 devices (without some OS security
  fixes, e.g. StrandHogg 2) supported — a product decision (V22-F13).

**CODE-2 — Enforced app updates.** — **Deferred (justified)**
- No forced-update mechanism; there is no store release yet. Play in-app updates can be added at
  release time.

**CODE-3 — No components with known vulnerabilities.** — **Pass**
- v22 CI: OSV audit of the release and debug runtime classpaths (CRITICAL/HIGH fail unless reviewed
  and allow-listed with an expiry), `npm audit --audit-level=high` for the Rules tooling (fixed
  V22-F10). Evidence: `scripts/security/dependency_audit.py`, CI `security` job report. (The OSV
  query runs in CI; this development container cannot reach api.osv.dev.)

**CODE-4 — Untrusted input validated and sanitized.** — **Pass**
- Backup import (size, depth, nodes, keys, arrays, strings, number literals, ids, times, durations,
  trust fields), AI responses (schema, bounds, markup/URL rejection), remote documents (exact field
  sets, owner, schema, bounds), images (size, signature, dimensions, decode bounds), deep links
  (strict parser, tags), URIs (content:// only). Evidence: `SecurityV22BackupAdversarialTest`,
  `WorkerV19Test`, `RemoteCodecTest`, `SecurityV22SyncTest`, `SecurityV22AttachmentTest`,
  `SecurityV22DeepLinkParserTest`, `Rules`.

## MASVS-RESILIENCE

**RESILIENCE-1 — Platform integrity validated.** — **Live Firebase / Device**
- App Check with Play Integrity is wired for release builds but enforcement is off until metrics are
  verified (firebase-app-check-v21.md). No root detection (it would not protect the user's own data
  from the user and is easily bypassed).

**RESILIENCE-2 — Anti-tampering.** — **Deferred (justified)**
- Not implemented beyond APK signing and App Check. All authorization is server-side (Rules);
  client History is unverified by design, so tampering with the client cannot raise privileges.

**RESILIENCE-3 — Anti-static analysis.** — **Partial**
- R8 shrinking and obfuscation in release (`apk_audit` `release.r8-obfuscated`, mapping file
  produced). This raises effort only; **the APK contains no secret, and none can be kept in a client
  app** — the Gemini key stays in the Worker.

**RESILIENCE-4 — Anti-dynamic analysis.** — **Deferred (justified)**
- No debugger or hook detection. Release is not debuggable (`apk_audit` `build.debuggable`); debug
  and release are separate apps (`apk_audit` separation checks). Detection would not protect any
  server-side asset.

## MASVS-PRIVACY

**PRIVACY-1 — Minimal access to sensitive data and resources.** — **Pass**
- Permissions are an exact allow-list (`apk_audit` `permissions.allow-list`): no camera or microphone
  permission (system camera and speech apps are used through intents), no storage permission (SAF
  and the photo picker), no location, contacts, accounts, overlay, accessibility, exact alarms or
  `QUERY_ALL_PACKAGES`; v22 removed `READ_GSERVICES` (V22-F11). Usage Access is a user-granted
  special access used only during protected Sessions.

**PRIVACY-2 — Prevents identification of the user.** — **Pass**
- No analytics, no advertising id, no crash reporting; the Worker receives only goal text and an
  optional re-encoded photo without metadata; sync carries no device identifiers, app inventory or
  usage data. Evidence: `SecurityV22NetworkTest.requestsCarryNoCredentials…`,
  `SecurityV22AttachmentTest.exifGps…`, `source_audit` `trust.no-app-inventory-or-usage-synced`.

**PRIVACY-3 — Transparent about data collection.** — **Partial**
- In-app privacy categories (account screen) and `privacy-account-sync-v21.md` (technical draft, not
  legal advice). A reviewed privacy policy and Play Data safety form do not exist yet.

**PRIVACY-4 — User control over data.** — **Pass**
- Guest mode, explicit migration choice, export/import, sign-out keeping or removing local data,
  full local deletion, account deletion (cloud documents first, verified). Evidence:
  `AccountLifecycleTest`, `AccountLifecycleTest.fullLocalEraseSignsOutAndNeverRedownloads`,
  `PersonalDataEraserTest`, `DataBackupTest`.
