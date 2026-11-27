# Threat model — Focus Mode+ Android v22

Scope: the native Android app in `android/` (v22, application id `app.focusmodeplus`), its
Firebase boundary (`firebase/firestore.rules`, the account-deletion function) and its calls to the
existing Cloudflare Worker. The PWA is out of scope except as a source of backup files.
Method: STRIDE per trust boundary, with qualitative likelihood × impact. Every threat names the
control that exists in code, the test that exercises it, and what remains. This is a structured
self-assessment, **not a formal penetration test**; live Google/Firebase behavior is listed in
[live-validation-required-v22.md](live-validation-required-v22.md).

## 1. System and data flow

```
 User ──► Focus Mode+ (app.focusmodeplus, one process)
            │  Room (focus.db)  DataStore  no_backup/{install.id, deeplink_route.key, mission_material/}
            │  cache/material_capture/ (camera target, FileProvider)
            ├─► Android OS: AlarmManager, WorkManager, notifications, UsageStats, DND, Auto Backup
            ├─► Credential Manager ─► Google account chooser ─► Google ID token (in memory, one call)
            ├─► Firebase Auth (signInWithCredential) ─► Firebase uid, session in shared_prefs
            ├─► Cloud Firestore /users/{uid}/… (memory cache only)  ◄── Security Rules
            ├─► Cloudflare Worker (HTTPS, /create-mission, /adapt-mission) ─► Gemini API (Worker only)
            ├─► System photo picker / document providers (content:// read grants)
            ├─► System camera (write grant to one FileProvider file) / speech recognizer
            └─► Backup files (Storage Access Framework: export via CreateDocument, import via OpenDocument)
```

## 2. Assets

| ID | Asset | Where | Sensitivity |
|---|---|---|---|
| A1 | Google account identity (email, display name, photo URL) | Firebase Auth state; shown on the account screen | Personal data |
| A2 | Firebase uid | Firebase Auth; Room `account_binding.uid`, `sync_outbox.account_uid` | Pseudonymous identifier |
| A3 | Missions and steps | Room; Firestore `/users/{uid}/missions/**` | Personal (study goals, deadlines) |
| A4 | Session History | Room `session_results`; Firestore `/users/{uid}/sessions` | Personal behavior data |
| A5 | Synchronization outbox and entity state | Room `sync_outbox`, `sync_entity_state` | Integrity of sync |
| A6 | Migration state | Room `account_binding`; Firestore `syncMeta` | Integrity of sync |
| A7 | User notes (review notes, end notes) | Room; Firestore Session `reviewNote` | Personal free text |
| A8 | Temporary images (draft photo, camera capture) | `no_backup/mission_material/`, `cache/material_capture/` | May contain personal content/EXIF |
| A9 | Backup files | User-chosen documents (SAF) | Contain A3, A4, A7 |
| A10 | Firestore documents | Cloud Firestore | A3–A7 in the cloud |
| A11 | Security Rules | `firebase/firestore.rules` (to be deployed by the owner) | Authorization logic |
| A12 | Future League fields | Not implemented; reserved names | Must stay server-authoritative |
| A13 | Cloudflare Worker requests | HTTPS multipart / JSON | Goal text, optional photo |
| A14 | Gemini API payload / key | Worker only (no key on Android) | Paid API, secret key |
| A15 | Selected distracting apps | Room `selected_apps` (package names) | Reveals installed apps |
| A16 | UsageStats readings | Memory during a protected Session; counts in Room | Sensitive behavior data |
| A17 | Local settings | DataStore; three synchronized settings | Low |
| A18 | Account-deletion state | Room `account_binding.status = deletion_pending` | Integrity of deletion |
| A19 | Deep-link route key (v22) | `no_backup/deeplink_route.key` | Integrity of in-app navigation |

## 3. Trust boundaries

| ID | Boundary | Crossing |
|---|---|---|
| B1 | App process ↔ Android OS | Intents, alarms, jobs, notifications, backup agent |
| B2 | App ↔ other installed apps | Exported launcher activity, PendingIntents, content URIs |
| B3 | App ↔ Credential Manager / Google Play services | Account chooser, ID token |
| B4 | App ↔ Firebase Authentication | ID token → Firebase session |
| B5 | App ↔ Cloud Firestore (+ Rules) | Documents below `/users/{uid}` |
| B6 | Tests ↔ Firebase Emulator Suite | Demo project only (`demo-focus-mode`) |
| B7 | App ↔ Cloudflare Worker | HTTPS requests/responses (AI plans) |
| B8 | Worker ↔ Gemini API | Not in this repository; key stays in the Worker |
| B9 | App ↔ FileProvider consumers (camera app) | One write grant per capture |
| B10 | App ↔ external document providers / photo picker | content:// reads |
| B11 | App ↔ system camera | `IMAGE_CAPTURE` with EXTRA_OUTPUT |
| B12 | App ↔ speech recognition | `RECOGNIZE_SPEECH` result text |
| B13 | App ↔ notification surfaces (shade, lock screen, wearables) | Notification text, PendingIntents |
| B14 | App ↔ backup files (import/export) | JSON documents written or chosen by the user |
| B15 | App ↔ OS backup (Auto Backup, device transfer, adb backup ≤ Android 11) | Room + DataStore |

## 4. Threat actors

| ID | Actor | Capability |
|---|---|---|
| TA1 | Malicious local application | Sends intents to exported components, registers schemes/providers, reads its own sandbox |
| TA2 | Malicious backup file | Arbitrary JSON content the user is persuaded to import |
| TA3 | User modifying local Room data | Root or debuggable build; edits rows |
| TA4 | User replaying sync requests | Valid own Firebase token; custom client against Firestore |
| TA5 | User attempting cross-account access | Valid own token, targets another uid |
| TA6 | User injecting reserved League fields | Own token, crafted documents |
| TA7 | Compromised or rooted test device | Reads all app files, hooks the process |
| TA8 | Accidental data leakage through logs | Developer mistake |
| TA9 | Accidental Firebase misconfiguration | Permissive Rules deployed, wrong project, App Check off |
| TA10 | Unauthorized deep-link caller | Any app or adb sending `focusmode://` |
| TA11 | Attacker with a decompiled APK | Reads all strings, resources and code |
| TA12 | Attacker with an exported component | Calls exported activities/services/receivers |
| TA13 | Attacker controlling an attachment file | Crafted image or hostile document provider |

## 5. Threat register

Risk after v22: **L** low, **M** medium, **H** high. "Test" names the regression test or audit.

### Spoofing

**T01 — Forged in-app navigation from another app** (TA10, TA12; A3, A4, A19; B2)
- Surface: exported `MainActivity` (launcher). Precondition: any installed app.
- Impact: UI redress (e.g. the end-of-Session confirmation opened repeatedly); no action is
  performed by a route, and targets only show local data to the device user.
- Existing control (v21): strict route parser, navigation-only targets, confirmation for ending.
- v22 action: VIEW intent filter removed; routes carry a per-install HMAC tag; untagged routes open
  the app without navigating (V22-F06). Test: `SecurityV22DeepLinkAuthTest`,
  `SecurityV22DeepLinkParserTest`, `SecurityV22ManifestAndStorageTest`, `apk_audit.py`.
- Residual: L — an attacker can still bring the app to the foreground (any launcher can).
- Future: none.

**T02 — Sign-in as another Google account / token replay** (TA4, TA5; A1, A2; B3, B4)
- Surface: Credential Manager result → `signInWithCredential`. Precondition: stolen ID token.
- Impact: account takeover of the cloud copy.
- Control: tokens are short-lived Google ID tokens verified by Firebase; never stored or logged;
  reauthentication must return the same uid before deletion (`AccountLifecycle.deleteAccount`).
- Test: `AccountLifecycleTest.accountDeletionNeedsARecentSignInAndChangesNothingWhenCanceled`,
  `AccountBoundaryTest`. Residual: L (not validated live). Future: consider a nonce once a backend
  verifies tokens itself; validate live (see live-validation).

**T03 — Spoofed system broadcasts** (TA1; A5, alarms; B1)
- Control: receivers are not exported; `BOOT_COMPLETED`, `TIME_SET`, … are delivered by the system
  only; alarms use explicit, immutable PendingIntents. Test: `apk_audit.py`, `source_audit.py`.
  Residual: L.

### Tampering

**T04 — Cross-account write or read in Firestore** (TA5; A10; B5)
- Control: Rules allow only `request.auth.uid == uid` below `/users/{uid}`; owner uid in every
  document; deny by default; no collection-group rule.
- Test: `firestore.rules.test.cjs`, `firestore.rules.adversarial.test.cjs` (reads, lists, writes,
  deletes, collection-group queries, foreign uid in payloads). Residual: L in the emulator; the
  deployed Rules must be verified live. Future: deploy exactly this file; App Check enforcement.

**T05 — Reserved League/verification/entitlement fields written by a client** (TA6; A12; B5)
- Control: every reserved name is refused on every document type; unknown fields refused (exact
  key sets); server-only paths denied; client decoder refuses documents with unknown keys.
- v22: every reserved field × every document type × five value shapes; client/Rules parity check
  (`source_audit.py` `trust.reserved-fields-match`). Residual: L — **list items are not validated
  by Rules** (documented test); no server code reads client lists. Future: League values only from
  server-side verification, never from client documents.

**T06 — Stale or rogue client bypasses optimistic concurrency through step documents** (TA4; A3; B5)
- v22 finding V22-F07: steps could be written alone at any version. Now a step must carry the
  version and mutation id of its Mission after the write. Test: adversarial Rules suite. Residual: L
  (an owner can still replay the current version on purpose; own data only).

**T07 — Rewriting or forging finalized History** (TA3, TA4; A4, A7)
- Control: Sessions are append-only in Rules (only note/metadata change), duplicate ids become
  updates and are refused; client applies a remote Session only once and never changes a local one.
- v22: forged server copies are not applied (`SecurityV22SyncTest.aRewrittenServerCopy…`);
  documented limitation: the owner may delete and re-create own History (needed for deletion).
- Residual: M for *integrity of self-reported data* by design — History is unverified and must never
  be used for League or rewards. Future: server-verified sessions in a separate, server-only path.

**T08 — Malicious backup file** (TA2; A3, A4, A9; B14)
- Control: 5 MB cap, depth/node/key/array/string limits, strict types, safe ids, integrity check,
  all-or-nothing Room transaction, unverified origin, no streak/League/app inventory import.
- v22 findings: pre-2000/future timestamps (V22-F03), huge ISO year crash (V22-F04), megabyte number
  literal → minutes of CPU on the main thread (V22-F05), non-content URIs (V22-F12).
- Test: `SecurityV22BackupAdversarialTest` (empty/invalid/nested/oversized/duplicate/conflicting ids,
  checksum mismatch, future schema, HTML/script text, corrupted/impossible times and durations,
  forged trust values, 400 seeded mutations). Residual: L.

**T09 — Local data edited on a rooted device** (TA3, TA7; A3–A6, A15)
- Control: none against the device owner (by design); derived values (streak, totals) are
  recomputed from records; edited records sync only if they pass the remote schema; invalid records
  no longer block synchronization (V22-F01). Residual: M on rooted devices — documented.

**T10 — Accidental permissive Rules deployment** (TA9; A10, A11)
- Control: deny-by-default Rules in the repository; deployment is a manual owner step; the client
  decoder rejects foreign owners, unknown and reserved fields, invalid versions, impossible values.
- v22: `SecurityV22SyncTest.forgedServerDocumentsAreNeverAppliedEvenIfTheRulesFailed`. Residual: M —
  a permissive deployment would expose cloud data across accounts; only live checks can catch it.
  Future: deploy from CI with Rules tests as a gate; App Check enforcement.

### Repudiation

**T11 — Disputed account deletion or migration** (A6, A18)
- Control: deletion is pending until the server confirms every document gone; migration completes
  only after the server holds every migrated id; outcomes are explicit states. No audit log exists
  (no analytics by design). Residual: L. Future: server-side deletion audit in the function.

### Information disclosure

**T12 — Tokens or personal data in logs** (TA8; A1, A2, A3, A7, A13)
- Control: no `Log`/`println`/stack traces in production code (enforced by `source_audit.py`),
  privacy-safe error mapping, no logging interceptor. Residual: L — the App Check **debug** provider
  prints its secret in debug builds only (by design, documented). Future: keep the check in CI.

**T13 — Notification and lock-screen exposure** (A3, A4; B13)
- Control: detail levels FULL_TASK/GENERIC/HIDDEN, public versions without task text, `setLocalOnly`.
  Test: `FocusNotifierTest.lockScreenNeverShowsTaskTextAndHiddenModeIsSecret`. Residual: L.

**T14 — Backup exposure (cloud, device transfer, adb backup ≤ Android 11)** (A3–A7, A15; B15)
- Control: explicit include list (database, DataStore); shared preferences (Firebase Auth session),
  `no_backup` (photo, route key, install marker, WorkManager) and caches excluded; restored devices
  must sign in again; `RestoreGuard` neutralizes device-bound state.
- Test: `SecurityV22ManifestAndStorageTest.backupPoliciesIncludeOnlyTheDatabaseAndPreferences`.
- Residual: L–M — the Room database (including the selected-app list and the Firebase uid) is part
  of the user's own Google backup and, on Android ≤ 11, of `adb backup` with physical access.
  Future: consider excluding `selected_apps` from cloud backup when the backup policy is revisited.

**T15 — FileProvider over-exposure** (TA1; A8; B9)
- Control: one `cache-path material_capture/`, provider not exported, per-capture grant, original
  deleted after processing. Test: `SecurityV22ManifestAndStorageTest.fileProviderExposesOnlyTheCameraCacheDirectory`,
  `apk_audit.py`, `source_audit.py`. Residual: L.

**T16 — EXIF/GPS or file names leaving the device** (A8, A13; B7)
- Control: re-encoding drops metadata; neutral names `material.jpg/png`; only processed bytes stored.
  Test: `SecurityV22AttachmentTest.exifGpsAndCameraMetadataNeverSurviveProcessing`,
  `…hostileFileNamesNeverReachStorageOrTheUpload`. Residual: L.

**T17 — App inventory or UsageStats synchronized** (A15, A16; B5)
- Control: the synchronized schema has no such fields; protection data stays local.
  Test: `source_audit.py` `trust.no-app-inventory-or-usage-synced`, `RemoteCodecTest`. Residual: L.

**T18 — Secrets in the APK** (TA11; A14)
- Control: no API key on Android (Gemini key only in the Worker); no google-services.json in the
  repository; release has no debug provider. Test: `apk_audit.py` secret rules, resource checks,
  `source_audit.py` repository scan. Residual: L. Note: a client app cannot keep a server secret;
  everything in the APK is public.

**T19 — Cleartext or intercepted network traffic** (A13; B7)
- Control: network security config: cleartext off, system CAs only; HTTPS-only base URL; no
  redirects; no cookies; bounded bodies. Test: `SecurityV22NetworkTest`, `apk_audit.py`.
  Residual: L (no pinning, by decision; see MASVS-NETWORK). Live interception not tested.

**T20 — Account screen in screenshots/recents** (A1)
- Decision: no `FLAG_SECURE` (it would break support screenshots and the pitch); the screen shows
  the email but never a token or the uid. Residual: L. Documented in the MASVS assessment.

### Denial of service

**T21 — Permanent migration stall / retry loop** (TA2, TA3; A5, A6)
- v22 finding V22-F01 (Medium): one remotely invalid local record kept the migration open forever.
  Fixed; Test: `SecurityV22SyncTest.aRemotelyInvalidRecordNeverBlocksTheMigration`. Worker retries
  bounded (6 attempts) and outbox parking (8 attempts). Residual: L.

**T22 — Crafted images and hostile providers** (TA13; A8; B10, B11)
- Control: size cap before decoding, header dimension caps, downsampled decode, OOM handling.
- v22 finding V22-F08: a provider's runtime exception crashed the app. Fixed. Test:
  `SecurityV22AttachmentTest`. Residual: L.

**T23 — Oversized AI responses / repeated AI requests** (A13, A14; B7)
- Control: 512 KB body cap, timeouts, one request in flight, no automatic retry, no redirect.
  Test: `WorkerV19Test.oversizedBodiesAreNotRead`, `WorkerMissionGenerationRepositoryTest`,
  `SecurityV22NetworkTest`. Residual: L.

**T24 — Repeated taps, links and account switches** (A5, A6)
- Control: one mutex per owner; unique WorkManager job (KEEP); idempotent migration id.
  Test: `SyncEngineTest.duplicateConfirmationTapsMigrateOnce`, `SecurityV22SyncTest.repeatedAccountSwitchingNeverLeaksOrWedges`,
  `…aReplayedMigrationConfirmationIsIdempotent`. Residual: L.

### Elevation of privilege

**T25 — Exported components and debug tooling** (TA12; B2)
- Control: only the launcher activity is exported among app components; dependency components
  are protected by system/signature permissions (`BIND_JOB_SERVICE`, `DUMP`, Google's revocation
  permission). v22 finding V22-F09: debug builds exported Compose `PreviewActivity`
  (renders any composable named by the caller) and an empty test host — now not exported.
  Test: `apk_audit.py` (both APKs), `SecurityV22ManifestAndStorageTest`. Residual: L.

**T26 — Task hijacking (StrandHogg)** (TA1; A1)
- Android 11+ is patched by the OS; minSdk 26 leaves Android 8–10 devices without the Sept 2020
  patch exposed to StrandHogg 2, which no app setting prevents. Sign-in happens in system UI
  (Credential Manager), not in an app-drawn form. Residual: L (V22-F13). Future: raise minSdk to 30
  when the audience allows.

**T27 — Account binding confusion across accounts** (TA5; A2, A3, A5)
- Control: binding re-checked inside every transaction; every remote call followed by an account
  check; jobs carry their uid; explicit decision on account switch; nothing merged across accounts.
  Test: `SyncEngineTest.anAccountChangeDuringASyncWritesNothing`,
  `SecurityV22SyncTest.accountAThenSignOutThenAccountB…`, `…anOutboxEntryOfAnotherAccount…`,
  `…anotherDeviceCannotAttachItsLocalDataToTheWrongAccount`, `AccountLifecycleTest`. Residual: L.

**T28 — Supply-chain advisories in dependencies** (A14 indirectly)
- Control: v22 OSV audit of the shipped runtime classpaths; npm audit of the test tooling; a
  CRITICAL/HIGH advisory fails CI unless reviewed and allow-listed. v22 finding V22-F10: test-only
  grpc-js advisories, pinned to the fixed version. Residual: L.

## 6. Out of scope / assumptions

- The Cloudflare Worker and Gemini usage are not changed or tested here (no load or fuzz traffic
  against them, by policy). The Worker must keep enforcing its own limits and key secrecy.
- Google Play services, Credential Manager and Firebase SDK internals are trusted platform code.
- A device owner with root can read and change everything in the app sandbox; no custom
  encryption is added (no key-management benefit against that actor).
