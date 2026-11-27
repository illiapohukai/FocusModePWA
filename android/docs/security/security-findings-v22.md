# Security findings — v22

Every finding below was reproduced locally (JVM/Robolectric tests, the Firebase Emulator Suite
with the demo project, local APK inspection, or a JDK snippet) before it was fixed, and each fix
has a regression test that failed before the fix and passes after it. Nothing was tested against
production Google, Firebase, Cloudflare or Gemini systems. This is a structured self-assessment,
not a formal penetration test.

Severity scale: Critical, High, Medium, Low, Informational. Severities are not inflated: there was
**no Critical and no High finding in the project's own code or configuration**. One transitive
dependency carried an advisory rated High upstream (V22-F17, fixed); its contextual severity here is
Low. Most findings need a user action (choosing a file, installing a hostile app) or concern the
user's own data.

| ID | Title | Category | Severity | Status |
|---|---|---|---|---|
| V22-F01 | A remotely invalid local record blocks account migration forever | Sync integrity / availability | Medium | Fixed |
| V22-F02 | Quick Session with a Mission link accepted by Rules and decoder | Authorization / data integrity | Low | Fixed |
| V22-F03 | Backup import accepts pre-2000 and far-future timestamps | Input validation | Low | Fixed |
| V22-F04 | Backup preview crashes on an ISO date with an extreme year | Input validation / DoS | Low | Fixed |
| V22-F05 | Megabyte number literal: quadratic integrity check on the main thread | DoS | Low | Fixed |
| V22-F06 | Any installed app could drive in-app navigation via the exported launcher | Platform / intent spoofing | Low | Fixed |
| V22-F07 | Step documents bypass the Mission's optimistic-concurrency check | Authorization / integrity | Low | Fixed |
| V22-F08 | A hostile document provider crashes the photo pipeline | Input validation / DoS | Low | Fixed |
| V22-F09 | Debug builds export Compose `PreviewActivity` and a test host | Platform / debug exposure | Low | Fixed |
| V22-F10 | `@grpc/grpc-js` 1.9.16 advisories in Rules test tooling | Dependency | Low | Fixed |
| V22-F17 | Shipped `guava` 33.6.0-android affected by GHSA-xxph-c9ww-hj94 (upstream High) | Dependency | Low in context (upstream High) | Fixed |
| V22-F11 | Unneeded `READ_GSERVICES` permission from the reCAPTCHA SDK | Least privilege | Informational | Fixed |
| V22-F12 | Backup import would open any URI scheme passed to it | Defense in depth | Informational | Fixed |
| V22-F13 | Task hijacking on Android 8–10 (minSdk 26) | Platform | Informational | Accepted (residual) |
| V22-F14 | Rules cannot validate list items; owners can re-create own History | Authorization (design limit) | Informational | Accepted (documented, tested) |
| V22-F15 | Room database in OS backups (and `adb backup` on Android ≤ 11) | Storage | Informational | Accepted (residual) |
| V22-F16 | Application identity change separates old and new installs | Identity (not a vulnerability) | Informational | Documented |

Open findings: none that are fixable without a product decision; accepted residual risks are in
[residual-risks-v22.md](residual-risks-v22.md).

---

## V22-F01 — A remotely invalid local record blocks account migration forever

- **Category**: synchronization integrity / availability. **Severity**: Medium.
- **Evidence**: `RoomSyncLocalStore.scopeLocked()` put every record with a safe id into the
  migration scope; `SyncEngine.buildUnit` refuses records the remote schema cannot hold
  (`RemoteCodec.validMission/validSession`: times before 2000-01-01, oversized text, …) and parks
  them as `invalid_data`; `SyncEngine.verifyMigration` returned `false` while
  `local.pendingCount(uid) > 0`, which counts parked entries.
- **Impact**: one such record — from a device whose clock was before 2000, or from an imported or
  hand-edited backup (see V22-F03) — kept the binding in `MIGRATING` forever: the account never
  became active, cloud data never downloaded, every sync ended as a retryable failure and the
  preview claimed everything was synchronizable.
- **Reproduction**: `SecurityV22SyncTest.aRemotelyInvalidRecordNeverBlocksTheMigration` before the
  fix: `migration must complete, was Failed(failure=SERVER, retryable=true) (binding MIGRATING)`.
- **Fix**: `RemoteCodec.canEncode(…)` exposes the encoder's own rules; the migration scope and the
  preview's `notSyncable` count use them; `verifyMigration` waits only for entries that can still
  upload (`SyncLocalStore.blockingCount`, which ignores entries parked as `invalid_data`).
- **Regression tests**: `SecurityV22SyncTest.aRemotelyInvalidRecordNeverBlocksTheMigration`,
  `…aRemotelyInvalidRecordFinalizedAfterLinkingIsReportedNotRetriedForever`; every v21 sync and
  account-lifecycle test still passes.
- **Status**: fixed. **Residual risk**: the invalid record stays local (shown as not synchronizable);
  the user keeps it and can export it.

## V22-F02 — Quick Session with a Mission link accepted by Rules and decoder

- **Category**: authorization / data integrity. **Severity**: Low.
- **Evidence**: Rules required a link only for `type == 'mission'`; `RemoteCodec.decodeSession`
  accepted a `quick` Session with `missionId`/`missionStepId`.
- **Impact**: a crafted (own-account) document could make a Quick Focus record point at a Mission,
  breaking the invariant "Quick Focus never touches Missions" on every synchronized device.
- **Reproduction**: adversarial Rules suite against the v21 Rules (`a Quick Session carrying a
  Mission link is denied` failed: write succeeded); `SecurityV22SyncTest.aRemoteQuickSession…`
  failed (`expected null, but was RemoteSession(type=QUICK…)`).
- **Fix**: Rules `((s.type == 'mission') == (s.missionId != null))`; decoder and encoder refuse a
  Quick Session with a link; `FakeRemoteSyncStore` mirrors the rule.
- **Regression tests**: `firestore.rules.adversarial.test.cjs` (V22-F02 suite),
  `SecurityV22SyncTest.aRemoteQuickSessionCarryingAMissionLinkIsNeverApplied`.
- **Status**: fixed. **Residual risk**: none known.

## V22-F03 — Backup import accepts pre-2000 and far-future timestamps

- **Category**: input validation. **Severity**: Low.
- **Evidence**: `PwaBackupImporter.instant()` accepted 1970-01-01 … 2100-01-01; the remote schema
  accepts 2000 … 2100; nothing bounded the future.
- **Impact**: a 2099 Session sits on top of History and counts towards today's streak; pre-2000
  records could never synchronize (and, before V22-F01's fix, blocked the migration).
- **Reproduction**: `SecurityV22BackupAdversarialTest.corruptedImpossibleAndForgedTimesNeverCrashAndAreNotImported`
  run against the v21 importer: `AssertionError: pre_2000 must not import`.
- **Fix**: imported times must lie between 2000-01-01 and the import time + 24 h (clock skew);
  invalid Session times skip the Session, invalid Mission times fall back to the import time.
- **Status**: fixed. **Residual risk**: plausible but false times inside the window remain possible
  (the data is the user's own and marked unverified).

## V22-F04 — Backup preview crashes on an ISO date with an extreme year

- **Category**: input validation / denial of service. **Severity**: Low.
- **Evidence**: `Instant.parse("+1000000000-01-01T00:00:00Z")` succeeds, `toEpochMilli()` then
  throws `ArithmeticException: long overflow` (JDK snippet in the test plan). The exception escaped
  `PwaBackupImporter.parse`, and `DataImportViewModel.onFilePicked` called the preview inside
  `viewModelScope.launch` without a handler, so a crafted backup crashed the app.
- **Reproduction**: `SecurityV22BackupAdversarialTest.anExtremeIsoYearNeverCrashesThePreview` run
  against the v21 importer: `java.lang.ArithmeticException: long overflow`.
- **Fix**: times are compared as `Instant`s (no millisecond conversion); the import view model also
  turns any unexpected parser exception into a rejected file (cancellation still propagates).
- **Status**: fixed. **Residual risk**: none known.

## V22-F05 — Megabyte number literal: quadratic integrity check on the main thread

- **Category**: denial of service. **Severity**: Low.
- **Evidence**: string values were bounded (50 000 chars) but number literals were not;
  `BackupIntegrity.number()` parses each literal as an exact `BigDecimal`, which is quadratic
  (measured: 1 M digits ≈ 16 s on a desktop CPU; a 4.9 MB literal would take minutes). The preview
  ran on the caller's dispatcher, i.e. the main thread → ANR.
- **Reproduction**: JDK timing (test plan) and
  `SecurityV22BackupAdversarialTest.aMegabyteNumberLiteralIsRejectedBeforeTheIntegrityCheck` run
  against the v21 importer: the file passed the structure guard and reached the exact-decimal
  integrity check (`expected "Die Datei enthält eine ungültige Zahl." but was "Datei beschädigt: …"`).
- **Fix**: `ImportLimits.maxNumberLiteral = 40` in the structure guard (JSON.stringify never writes
  more than 24 characters); `BackupImportRepository.preview` parses on `Dispatchers.Default`.
- **Status**: fixed. **Residual risk**: a 5 MB file of ordinary content still takes about a second
  to parse, now off the main thread.

## V22-F06 — Any installed app could drive in-app navigation via the exported launcher

- **Category**: Android platform / intent spoofing. **Severity**: Low.
- **Evidence**: `MainActivity` (exported as the launcher) declared a `VIEW` filter for
  `focusmode://` and navigated for any VIEW intent with that scheme, implicit or explicit.
- **Impact**: navigation only (routes never act; ending a Session needs a confirmation), but a
  foreign app could repeatedly open screens such as the end confirmation (UI redress), and with
  several Focus Mode+ builds installed the scheme was ambiguous.
- **Reproduction**: code review of the v21 `MainActivity.handleIntent` (any VIEW intent with the
  scheme navigated; no origin check) and of the v21 manifest (the `focusmode` VIEW filter made an
  implicit `focusmode://session/end` intent resolve to `MainActivity`). The regression tests assert
  the opposite: `SecurityV22ManifestAndStorageTest.onlyTheLauncherActivityOfTheAppIsExported` (no
  activity resolves the implicit intent) and `SecurityV22DeepLinkAuthTest.aForeignCallerWithoutAValidTagNeverNavigates`.
- **Fix**: VIEW filter removed (the app's own PendingIntents are explicit and do not need it);
  `DeepLinkAuthenticator` tags every route with HMAC-SHA256 under a random per-install key in
  `noBackupFilesDir`; `MainActivity` navigates only for a valid tag; unreadable extras are a plain
  launch. Tags are bound to the exact route and the install.
- **Regression tests**: `SecurityV22DeepLinkAuthTest` (6), `SecurityV22DeepLinkParserTest` (7),
  `SecurityV22ManifestAndStorageTest.onlyTheLauncherActivityOfTheAppIsExported`, `apk_audit.py`
  `launcher.only-main-launcher-filter`.
- **Status**: fixed. **Residual risk**: notifications posted by an older build (or before a key file
  loss) open the app without navigating; `adb shell am start -d focusmode://…` no longer navigates
  (documented for developers).

## V22-F07 — Step documents bypass the Mission's optimistic-concurrency check

- **Category**: authorization / integrity. **Severity**: Low.
- **Evidence**: Mission updates had to be exactly `syncVersion + 1`, but steps only needed a version
  between 1 and 1e9 and an existing parent; a step could be rewritten on its own at any version.
- **Impact**: a stale or buggy client could overwrite step content behind the concurrency guard that
  protects completed steps and conflict merging (own account only).
- **Reproduction**: adversarial Rules suite against the v21 Rules: `a step written alone with another
  version or mutation id is denied` and `a stale device that lost the Mission race cannot overwrite
  steps afterwards` both failed (writes succeeded).
- **Fix**: `stepMatchesMission()` — a step's `syncVersion` and `deviceMutationId` must equal those of
  its Mission after the write (exactly what the client batch writes); `FakeRemoteSyncStore` mirrors it.
- **Status**: fixed. **Residual risk**: an owner can deliberately replay the current version and
  mutation id (own data only; see V22-F14).

## V22-F08 — A hostile document provider crashes the photo pipeline

- **Category**: input validation / denial of service. **Severity**: Low.
- **Evidence**: `AndroidMissionMaterialStore.prepare` caught only `SecurityException` and
  `IOException`. A document provider (another app, reachable through `ACTION_OPEN_DOCUMENT` on
  devices without the system photo picker) can throw `IllegalStateException`,
  `IllegalArgumentException`, `UnsupportedOperationException` or `NullPointerException` across
  Binder; `MissionCreateViewModel.onPhotoPicked` called `prepare` in `viewModelScope.launch`.
- **Reproduction**: `SecurityV22AttachmentTest.aHostileDocumentProviderCannotCrashTheApp` with a real
  `ContentProvider` registered in Robolectric: `java.lang.IllegalStateException: hostile` escaped.
- **Fix**: runtime exceptions while opening/reading are an unreadable photo; cancellation is rethrown.
- **Status**: fixed. **Residual risk**: none known.

## V22-F09 — Debug builds export Compose `PreviewActivity` and a test host

- **Category**: Android platform / debug exposure. **Severity**: Low (debug builds only).
- **Evidence**: merged debug manifest (`apk_audit.py` on the debug APK built before the fix; the
  v21 manifests and Compose convention plugin were identical in this respect):
  `androidx.compose.ui.tooling.PreviewActivity exported=true` (renders any `@Composable` named in an
  intent extra via reflection) and `androidx.activity.ComponentActivity exported=true`, both from the
  `debugImplementation` Compose tooling/test-manifest artifacts. Release APK: absent.
- **Impact**: on a test device (CI debug APKs are downloadable artifacts) any app could render
  arbitrary app composables, including screens with side effects.
- **Fix**: `app/src/debug/AndroidManifest.xml` marks both `exported=false` (`tools:replace`); they
  stay usable in-process (Robolectric/Compose tests).
- **Regression tests**: `SecurityV22ManifestAndStorageTest.debugToolingActivitiesAreNotExportedToOtherApps`,
  `apk_audit.py` (debug: exported allow-list; release: absent).
- **Status**: fixed. **Residual risk**: Android Studio's "run preview on device" needs the activity
  to be exported temporarily.

## V22-F10 — `@grpc/grpc-js` 1.9.16 advisories in Rules test tooling

- **Category**: dependency. **Severity**: Low (development tooling, not shipped).
- **Evidence**: `npm audit` in `firebase/`: 5 high via `@grpc/grpc-js` < 1.13.6
  (GHSA-m9gg-hp2v-232j, CVSS 7.4; GHSA-f596-whhp-79r4, CVSS 3.7), pinned `~1.9.0` by
  `@firebase/firestore` 4.20.0 (firebase 13.4.0, the latest release). Reachability: both advisories
  concern grpc-js **server** behavior; the tests use it as a client against the local emulator. The
  package is not part of the APK or the PWA.
- **Fix**: `overrides: { "@grpc/grpc-js": "1.14.7" }` (the version firebase-admin already uses);
  lockfile regenerated; `npm audit`: 0 vulnerabilities; all 70 Rules tests pass with it.
- **Status**: fixed. **Residual risk**: the override must be revisited when the Firebase JS SDK
  updates its own range (CI `npm audit --audit-level=high` will flag regressions).

## V22-F17 — Shipped `guava` affected by GHSA-xxph-c9ww-hj94

- **Category**: dependency (known vulnerability). **Severity**: upstream **High** (GitHub CVSS 4.0
  8.2, availability only; the reporter's CVSS 3.1 is 5.9); **contextual severity: Low**.
- **Evidence**: found by the new CI dependency audit (OSV) in the first complete `security` run:
  `[FATAL] HIGH GHSA-xxph-c9ww-hj94 com.google.guava:guava:33.6.0-android fixed=33.7.2` (228
  coordinates checked, 1 advisory). Path: `firebase-firestore 27.2.0 → grpc-android 1.84.0 →
  grpc-api → guava 33.6.0-android`. Advisory (CVE-2026-102554): `CompactHashMap`, `CompactHashSet`
  and `MapMakerInternalMap` `readObject()` allocate arrays sized by an unvalidated count from a Java
  serialization stream; a tiny stream forces a multi-gigabyte allocation (OutOfMemoryError).
  Affected ≤ 33.7.1 (both `-jre` and `-android`), patched in 33.7.2.
- **Reachability**: the app never uses `ObjectInputStream`. Java deserialization of untrusted bytes
  can still happen through intent extras: on Android ≤ 12 reading any extra unparcels every
  `Serializable` extra of an intent sent to the exported launcher, and both classes are present in
  the APKs. Impact would be an OOM in Focus Mode+ (local denial of service by a malicious app); no
  data is exposed or changed. `DeepLinkAuthenticator.routeFrom` already treats unreadable extras as
  a plain launch.
- **Fix**: a Gradle dependency constraint in `:app` enforces `com.google.guava:guava:33.7.2-android`
  (first patched release, same major) on every app classpath; it is not a new dependency. Verified:
  both runtime classpaths resolve `33.6.0-android -> 33.7.2-android`; all app tests, Lint, debug and
  R8 release builds and both APK audits pass. The CI OSV audit re-checks it on every run.
- **Status**: fixed. **Residual risk**: the constraint must be removed (or raised) when grpc/Firebase
  ship a fixed guava; CI flags any regression.

## V22-F11 — Unneeded `READ_GSERVICES` permission from the reCAPTCHA SDK

- **Category**: least privilege. **Severity**: Informational.
- **Evidence**: manifest merger report: `com.google.android.providers.gsf.permission.READ_GSERVICES`
  added by `com.google.android.recaptcha:recaptcha:18.6.1` (a firebase-auth dependency). reCAPTCHA
  serves phone and email/password sign-in, which the app does not use (its `RecaptchaActivity` was
  already removed in v21).
- **Fix**: `tools:node="remove"` in the app manifest; the library stays (removing it could break
  firebase-auth class loading); Google sign-in does not use reCAPTCHA.
- **Status**: fixed. **Residual risk**: live sign-in must still be validated on a device (listed).

## V22-F12 — Backup import would open any URI scheme passed to it

- **Category**: defense in depth. **Severity**: Informational.
- **Evidence**: `DataImportViewModel.onFilePicked` opened whatever URI it received. Only the system
  document picker supplies it today (always `content://`), so this was not exploitable.
- **Fix**: non-`content://` URIs are rejected before any read, like the photo pipeline already did.
- **Status**: fixed.

## V22-F13 — Task hijacking on Android 8–10 (minSdk 26)

- **Category**: Android platform. **Severity**: Informational.
- **Evidence**: Android's StrandHogg guidance: Android 11 (API 30) contains the OS patches; version 2
  "can only be prevented by this SDK version patch"; app configuration only partially mitigates v1.
- **Decision**: no `taskAffinity` change (its device behavior could not be validated here, and sign-in
  runs in system Credential Manager UI, not an app-drawn form). Raise minSdk to 30 when the audience allows.
- **Status**: accepted residual risk.

## V22-F14 — Rules cannot validate list items; owners can re-create own History

- **Category**: authorization (design limit). **Severity**: Informational.
- **Evidence**: Rules cannot iterate lists, so items of `progress`, `stepIds`, `requiredTools` are
  only bounded in count; the owner may delete a Session (needed for account deletion) and create it
  again with other values. Both behaviors are pinned by "DOCUMENTED LIMITATION" tests.
- **Mitigation**: the client decoder requires exact field sets for list items; client History is
  "local, unverified"; any future League or reward value must come from server-side verification
  and a server-only path, never from these documents.
- **Status**: accepted, documented, tested.

## V22-F15 — Room database in OS backups (and `adb backup` on Android ≤ 11)

- **Category**: storage. **Severity**: Informational.
- **Evidence**: backup rules include the database and DataStore (v20 product decision: the user's
  own backup restores Missions and History). This includes the Firebase uid binding and the
  selected-app list; it excludes Firebase Auth's session, tokens, the draft photo and the route key.
- **Decision**: kept (user benefit), documented; restored devices must sign in again.
- **Status**: accepted residual risk.

## V22-F16 — Application identity change

- Not a vulnerability. See [application-identity-v22.md](../application-identity-v22.md): old and
  new builds are separate apps; data moves only through backup export/import.

## Checked and found sound (no finding)

- No logging, printing or stack traces in production code; no analytics or crash reporting.
- Network: HTTPS only, system CAs, no custom trust manager or hostname verifier, no redirects, no
  automatic retries, no cookies, 512 KB response cap, call timeouts, one AI request in flight.
- FileProvider: one cache sub-directory, not exported, per-capture grant, original deleted.
- PendingIntents: all immutable and explicit.
- No WebView, no clipboard use, no world-readable files, no dynamic code loading.
- Backup exclusions: Firebase Auth session (shared preferences) and `no_backup` are not backed up.
- Firestore: memory cache only (no on-disk copy of cloud data), one-shot server reads, no listeners.
- Account switching, sign-out, account deletion and full local deletion stay distinct and tested
  (v21 suites plus `SecurityV22SyncTest`); sign-out never deletes cloud data; account deletion
  reports "incomplete" until the server confirms; local erase signs out and never re-downloads.
- Release APK: not debuggable, R8 obfuscated, no test or tooling components, no secrets, no
  google-services resources, no App Check debug provider, no emulator endpoints.
