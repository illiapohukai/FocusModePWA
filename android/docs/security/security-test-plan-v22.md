# Security test plan and report — v22

All adversarial testing was limited to the local repository, local JVM/Robolectric runs, local
Android build artifacts, the Firebase Emulator Suite with the demo project `demo-focus-mode`, a
local MockWebServer, test fixtures generated at runtime and this repository's CI. No production
Google, Firebase, Cloudflare or Gemini system, no real account and no third-party app was
targeted; no load, fuzz or denial-of-service traffic was sent to any external service.

## How to run

```bash
# Android (JDK 21, Android SDK platform 37.2 + build-tools 36.0.0)
cd android
./gradlew testDebugUnitTest :core:domain:test :core:model:test     # complete JVM suite (513 tests)
./gradlew :core:domain:test --tests '*SecurityV22*' \
          :core:data:testDebugUnitTest --tests '*SecurityV22*' \
          :core:network:testDebugUnitTest --tests '*SecurityV22*' \
          :core:notifications:testDebugUnitTest --tests '*SecurityV22*' \
          :app:testDebugUnitTest --tests '*SecurityV22*'          # security regression grouping (62 tests)
./gradlew :app:lintDebug :app:assembleDebug :app:assembleRelease
python3 scripts/security/apk_audit.py --self-test
python3 scripts/security/apk_audit.py --apk app/build/outputs/apk/release/app-release-unsigned.apk --variant release --out build/reports/security/apk-release.json
python3 scripts/security/apk_audit.py --apk app/build/outputs/apk/debug/app-debug.apk --variant debug --out build/reports/security/apk-debug.json
(cd .. && python3 android/scripts/security/source_audit.py --out android/build/reports/security/source-audit.json)
python3 scripts/security/dependency_audit.py --self-test   # the OSV query itself runs in CI

# Firestore Rules (Node ≥ 24.12, Java 21)
cd ../firebase && npm ci && npm run test:emulator && npm audit

# PWA security suite (repository root)
node tests/security/run-all.js
```

CI (`.github/workflows/android.yml`): the `build` job runs the complete JVM suite (which contains
the `SecurityV22*` grouping), Lint and both builds and checks that R8 wrote its mapping; the
`security` job (`needs: build`) audits exactly the artifacts `build` produced — it fails unless all
seven `SecurityV22*` classes reported results without failures — and runs both APK audits, the
source audit, OSV and npm advisories and the adversarial Rules suite, then uploads
`security-reports` with `security-summary.json` (counts and statuses only — no runtime user data).
It does not recompile: a cold Gradle home re-downloads a 165 MB Robolectric artifact that Maven
Central throttles for CI runners (HTTP 429, observed in the first v22 run).

## Test inventory

| Suite | Location | Tests | Covers (brief §8) |
|---|---|---|---|
| `SecurityV22SyncTest` | `core/data` (Room + Rules-enforcing fake) | 10 | B, A: migration stall (V22-F01), Quick/Mission link (F02), A→sign-out→B, other-account outbox, replayed migration, wrong-account attach, forged server documents, rewritten History, 20× account switching |
| `SecurityV22BackupAdversarialTest` | `core/domain` | 15 | F, G: every malicious backup class of brief §11, F03/F04/F05, 400 seeded mutations |
| `SecurityV22DeepLinkParserTest` | `core/domain` | 7 | E, F: unknown/malformed/oversized ids, encoded separators, traversal, repeated/conflicting parameters, other schemes, 2 000 seeded links |
| `SecurityV22DeepLinkAuthTest` | `core/notifications` (Robolectric) | 6 | E: route tags (V22-F06), foreign callers, replay of tags, other installs, PendingIntent immutability |
| `SecurityV22AttachmentTest` | `core/network` (Robolectric, native graphics) | 12 | F: brief §12 image cases, hostile provider (V22-F08), EXIF/GPS removal, cleanup, cancellation |
| `SecurityV22NetworkTest` | `core/network` (MockWebServer) | 4 | D: HTTPS-only base URL, no redirects, no automatic retries, no credentials/cookies/query strings |
| `SecurityV22ManifestAndStorageTest` | `app` (Robolectric) | 8 | C, E, G: identity, exported components, debug tooling (F09), permissions, backup rules, network config, FileProvider paths, Room schema |
| `firestore.rules.adversarial.test.cjs` | `firebase/test` (Emulator Suite) | 30 | B: brief §9 (plus generated cases) |
| `firestore.rules.test.cjs`, `account-deletion.test.cjs` | `firebase/test` | 40 | v21 Rules and recursive deletion |
| `apk_audit.py` | `android/scripts/security` | 42 checks × 2 APKs + 3 separation | E, G: merged manifests, secrets, R8, identity |
| `source_audit.py` | `android/scripts/security` | 38 checks | C, D, E, G: logging, TLS, WebView, PendingIntents, identity, hygiene, trust boundaries, CI policy |
| `dependency_audit.py` | `android/scripts/security` | CI | CODE-3: OSV advisories for shipped classpaths |
| v21/v20 suites (unchanged) | all modules | 451 | A–G baseline: account lifecycle, sync engine, codec, Worker boundary, notifications, erase, migrations |

The 451 pre-existing tests all still pass; none was removed, skipped or weakened.

## Firestore Rules attack tests

Result: **70 / 70 pass** (40 v21 + 30 v22) on the emulator with the v22 Rules. Against the v21 Rules
the adversarial suite showed **25 / 30**: the V22-F02 and V22-F07 suites failed (Quick Session with
a Mission link accepted; steps written alone at another version or mutation id accepted; a stale
device could overwrite steps), plus two generated-case harness errors that were test bugs and were
fixed before the comparison was repeated.

| Attack (brief §9) | Outcome | Test |
|---|---|---|
| Unauthenticated read / write / list / delete | denied | adversarial `unauthenticated clients…`, v21 `unauthenticated…` |
| User A reads / lists / writes / deletes user B (all doc types, A's or B's uid in payload) | denied | `user A cannot read, list, write or delete…` |
| Collection-group queries across accounts | denied | `collection-group queries…` |
| Owner uid change; document under another uid | denied | `owner uid and record ids…`, `a document created under another uid…` |
| Immutable id / createdAt / startedAt change; payload id ≠ document id | denied | `owner uid and record ids…`, `a document id that differs…` |
| Unknown field; nested unknown object in scalar fields | denied | v21 `unknown or missing fields`, `nested objects in scalar fields…` |
| Nested items inside lists | **accepted (documented limitation, V22-F14)** | `DOCUMENTED LIMITATION: list items…` |
| Oversized array (stepIds 101, progress 501, requiredTools 21) / oversized strings | denied; maxima accepted | `oversized strings and arrays…` |
| Negative / excessive / fractional durations, ratings | denied | `negative, excessive, fractional…` |
| Invalid timestamps (string, < 2000, > 2100, fraction, bool, reversed), invalid dates | denied | `invalid timestamps…` |
| Future / downgraded / non-integer schema | denied | `future, downgraded and non-integer schema…` |
| Malformed tombstones (10 variants + missing field) | denied | `malformed tombstones…` |
| Resurrecting a deleted Mission at versions 1, 2, 7 | denied | `a deleted Mission cannot be resurrected…` |
| Modifying a finalized Session (set and update), duplicate Session id | denied | `a finalized Session cannot be rewritten…`, v21 append-only test |
| Owner deletes and re-creates own History | **accepted (documented limitation, V22-F14)** | `DOCUMENTED LIMITATION: the owner may delete…` |
| League points, verified minutes, subscription, entitlement, AI quota, admin role, moderation, server trust | denied on all 7 document types × 5 value shapes; server-only paths denied | `every reserved field is denied…`, `League points… paths are denied` |
| Decreasing / skipping / overflowing the sync version; replaying a mutation | denied | `replaying a Mission mutation…`, v21 optimistic concurrency |
| Step written alone at another version/mutation; stale step overwrite (V22-F07) | denied | `v22: steps follow the Mission version` |
| Quick Session with Mission link; Mission Session without (V22-F02) | denied | `v22: Session type and Mission link agree` |
| Local data attached to the wrong account (migration docs) | denied | `migration documents are bound…`, v21 `cross-account migration` |
| Generated: every wrong-typed and every missing field of Session, Settings, Tombstone, Mission, Step (bounded, deterministic, < 200 cases each) | denied; valid payload accepted | `bounded generated cases` |

## Account and synchronization attack tests

| Scenario (brief §10) | Test |
|---|---|
| A → logout → B; A's data never shown to or uploaded for B | `SecurityV22SyncTest.accountAThenSignOutThenAccountB…`, `AccountLifecycleTest.signOutWithRemovalThenAnotherAccountShowsOnlyThatAccount`, `…anotherAccountIsAskedAndNothingIsReassigned` |
| Stale StateFlow / account change mid-sync | `SyncEngineTest.anAccountChangeDuringASyncWritesNothing` |
| Stale Firestore listener | none can exist: one-shot server reads only (`source_audit` `kotlin.firestore-listener`) |
| Pending WorkManager job after sign-out / for another uid | `SyncWorkTest.aJobForAnotherOrSignedOutAccountNeverSyncs`, `AccountLifecycleTest.signOutKeepsLocalAndCloudDataAndStopsSync` |
| Pending outbox from another uid | `SecurityV22SyncTest.anOutboxEntryOfAnotherAccount…` |
| Duplicate migration confirmation / replayed migration id | `SyncEngineTest.duplicateConfirmationTapsMigrateOnce`, `SecurityV22SyncTest.aReplayedMigrationConfirmationIsIdempotent` |
| Process death during migration; network failure after partial upload | `SyncEngineTest.migrationSurvivesNetworkLossAndProcessDeath`, `…lostAcknowledgementsNeverDuplicateOrBlock` |
| Remote deletion during local update; offline tombstone; resurrection | `SyncEngineTest.aRemoteDeletionWinsAndIsNeverResurrected` |
| Duplicate finalized Session; rewritten server copy | `SecurityV22SyncTest.aRewrittenServerCopyNeverChangesOrDuplicatesAFinalizedSession` |
| Manipulated derived totals / streak / total minutes | derived values are recomputed, never synchronized: `SyncEngineTest.secondDeviceDownloads…RecalculatesDerivedValues`, Room schema has no such columns |
| Future League field injection; malicious unknown fields; invalid remote version | `SecurityV22SyncTest.forgedServerDocumentsAreNeverApplied…`, `RemoteCodecTest.serverDocumentsAreReadStrictly` |
| Reordered upload / out-of-order download / very old cursor | `SyncEngineTest.concurrentPlanChanges…`, cursor overlap and full re-fetch in `secondDevice…` and `emptyDeviceRestores…` |
| Remotely invalid local record (V22-F01) | `SecurityV22SyncTest.aRemotelyInvalidRecord…` (2) |
| Disabled / remotely deleted / revoked account; reauthentication | `AccountBoundaryTest.refreshDistinguishesDisabledDeletedRevokedAndOffline`, `AccountLifecycleTest.aRemotelyDeletedAccount…`, `…revokedCredentialsPauseSync…`, `…accountDeletionNeedsARecentSignIn…` |

Live Google sign-in, real tokens and real Firestore were **not** tested (see live-validation).

## Backup adversarial tests (brief §11)

All inputs are generated at runtime; no large fixture is committed. Result: **15 / 15 pass**.
Empty, whitespace, invalid JSON and non-UTF-8 → rejected; nesting 21 / 1 000 / 200 000 levels →
rejected by the pre-parse depth scan (no stack overflow); 10 001-item array, 201-key object,
60 000-char string, 200 000-digit number (V22-F05) → rejected; 300 Missions → 250 read, ≤ 2 active;
6 000 Sessions → capped; duplicate and conflicting ids → first record wins; path-like,
`file://`-like, NUL and percent-encoded ids → skipped; checksum mismatch, malformed digest, wrong
algorithm → rejected; missing checksum → accepted as unchecked; unknown format, export version 0/2,
string version, future schema → rejected; HTML/`<script>`/`javascript:` text → stored as inert
text (no WebView exists); control characters removed; corrupted, negative, pre-2000, 2099, `1e400`
and ±1 000 000 000-year times → skipped, never a crash (V22-F03, V22-F04); impossible and negative
durations → skipped; impossible elapsed time → bounded; forged streak, totals, League points,
verified minutes, entitlement, admin role, selected-app inventory and UsageStats-like payloads →
never in the plan; Android `STRICT_MANAGED` and pending reviews → neutralized; 400 seeded byte
mutations and 8 type-confusion shapes → no exception. Import application stays one Room
transaction (`BackupImportRepositoryTest`), non-content URIs are refused (V22-F12).

## Attachment and image tests (brief §12)

**12 / 12 pass** (Robolectric native graphics, images generated at runtime): valid JPEG/PNG/WebP
re-encoded; PNG declared as JPEG, JPEG declared as PNG, text, PDF, ELF, GIF, BMP, HEIC refused;
truncated and corrupted JPEGs never crash; four malformed EXIF blocks ignored; EXIF orientation
applied and GPS/marker bytes absent from the output; header-declared 65 000² JPEG, 50 000 px PNG,
16 384² WebP refused before decoding; 20 MB + 1 original refused; `file://`, `http://`,
`javascript:`, empty and unreachable URIs refused; hostile provider exceptions handled (V22-F08);
traversal/control-character names never reach storage (stored `draft.jpg`, uploaded
`material.jpg`); no temporary file survives; cancellation while reading writes nothing.

## Deep-link tests (brief §14)

**13 / 13 pass** (parser 7, authentication 6). Unknown routes, missing/malformed/oversized ids,
encoded separators, traversal sequences, repeated and conflicting parameters, untrusted account or
Mission ids in queries, other schemes and intent syntax → Home or a navigation-only target. Deleted,
archived and other-account Missions/Sessions are opaque lookups that show "not found" in the target
screen (v20/v21 screen tests); no target changes the account, deletes data, finalizes a Session or
skips a confirmation. v22: routes need a valid per-install tag; a foreign VIEW intent, a replayed
tag on another route, a tag from another install, a wrong extra type and non-VIEW actions never
navigate.

## Exported component audit (merged manifests, brief §13)

Inventory from `apk_audit.py` (`merged-manifest-release.xml`, `merged-manifest-debug.xml` in the
CI `security-reports` artifact). Identical in debug and release except where noted.

| Component | Exported | Reason | Required permission | Caller | Attack surface | Test |
|---|---|---|---|---|---|---|
| `app.focusmodeplus.MainActivity` | yes | launcher (MAIN/LAUNCHER only) | — | launcher, own PendingIntents | routes need a valid tag (V22-F06) | `apk_audit`, `SecurityV22DeepLinkAuthTest` |
| `androidx.work…SystemJobService` | yes | JobScheduler | `BIND_JOB_SERVICE` | system | none for apps | `apk_audit` |
| `androidx.work…DiagnosticsReceiver` | yes | WorkManager diagnostics | `DUMP` | shell/system | none for apps | `apk_audit` |
| `androidx.profileinstaller.ProfileInstallReceiver` | yes | baseline profiles | `DUMP` | shell/system | none for apps | `apk_audit` |
| `com.google.android.gms.auth.api.signin.RevocationBoundService` | yes | Play services revocation callback | Google signature permission | Google Play services | none for apps | `apk_audit` |
| `SystemEventsReceiver` | no | boot/time/package events (protected broadcasts) | — | system | — | `apk_audit` |
| `FocusSessionService`, `SessionAlarmReceiver`, `ReminderAlarmReceiver` | no | Session timer, alarms | — | own explicit intents | — | `apk_audit` |
| `androidx.core.content.FileProvider` (`<id>.materialcapture`) | no, grant-only | camera write target | per-Uri grant | system camera (one capture) | one cache file | `apk_audit`, Manifest test |
| `androidx.startup.InitializationProvider` | no | startup (WorkManager initializer removed) | — | — | — | `apk_audit` |
| WorkManager internals, Room invalidation service, Firebase `ComponentDiscoveryService`, Credential Manager hidden activities, `SignInHubActivity`, `GoogleApiActivity`, Play Core dialog wrapper | no | dependency internals | — | own process | — | `apk_audit` |
| `androidx.compose.ui.tooling.PreviewActivity`, `androidx.activity.ComponentActivity` | **debug only, no** (v22) | Compose tooling/test host | — | in-process tests | was exported (V22-F09) | `apk_audit`, Manifest test |
| Removed: Firebase `GenericIdpActivity`, `RecaptchaActivity`, `FirebaseInitProvider` | — | unused flows / explicit init | — | — | — | `apk_audit` forbidden list |

Permissions (both builds): `INTERNET`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`,
`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `PACKAGE_USAGE_STATS`
(special access), `ACCESS_NOTIFICATION_POLICY`, `WAKE_LOCK` (WorkManager) and androidx.core's
signature permission `<id>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Not present: Accessibility
Service, overlay, `QUERY_ALL_PACKAGES`, storage, camera, microphone, location, biometric/passkey,
exact alarms, AD_ID, `READ_GSERVICES` (removed in v22). Queries: image capture, speech
recognition, launcher apps, two settings screens. Metadata: `com.google.android.gms.version` only
(and Firebase component registrars inside `ComponentDiscoveryService`).

## FileProvider audit (brief §15)

One provider, authority `<applicationId>.materialcapture`, `exported=false`,
`grantUriPermissions=true`, paths: `<cache-path name="material_capture" path="material_capture/"/>`
only — no root, files, database, shared-preferences, no_backup or external path. The camera receives
a write grant for one `capture.jpg` per capture (`ActivityResultContracts.TakePicture`); the grant
ends with the camera activity's result; the original is deleted right after re-encoding and on draft
clear; the processed draft lives in `noBackupFilesDir`, never behind the provider; output names are
neutral. Tests: `SecurityV22ManifestAndStorageTest.fileProviderExposesOnlyTheCameraCacheDirectory`,
`AndroidMissionMaterialStoreTest.cameraCaptureTarget…`, `apk_audit` provider checks,
`source_audit` `fileprovider.narrow-paths`.

## Local storage and backup audit (brief §16)

| Item | Location | Backed up | Notes |
|---|---|---|---|
| Room `focus.db` (Missions, History, outbox, binding uid, selected apps) | `databases/` | yes (cloud + device transfer; adb ≤ Android 11) | product decision; no tokens (schema test) |
| DataStore preferences | `files/datastore/` | yes | settings only |
| Firebase Auth session (tokens) | `shared_prefs/` (Firebase SDK) | **no** | restored devices sign in again |
| Account meta flags | `shared_prefs/focus_account_meta.xml` | no | two booleans |
| Draft photo | `no_backup/mission_material/` | no | deleted with the draft |
| Camera capture | `cache/material_capture/` | no | deleted after processing |
| Route-tag key (v22), install marker, WorkManager DB | `no_backup/` | no | device-bound |
| Firestore cache | memory only | — | no disk copy |

No custom encryption is added: the realistic actor against local data is the device owner (root),
against whom an app-held key gives no protection; Android file-based encryption protects data at
rest on locked devices. Residual risk on rooted devices: full read/write of the sandbox.

## Logging and telemetry audit (brief §17)

`source_audit.py` scans all production Kotlin (`src/main`, `src/release`) with comments stripped:
no `android.util.Log`, `Log.x(`, `println`, `printStackTrace`, Timber or `System.out/err`; no request
or AI text logging (no OkHttp logging interceptor); error mappers return privacy-safe classes; no
analytics and no crash reporting are present. The only logging in any build is Firebase App Check's
**debug** provider printing its debug secret (debug builds only, by design). Result: pass.

## Network audit (brief §18)

Network security config: `cleartextTrafficPermitted="false"`, system anchors only, no domain
configs or debug overrides. Manifest: no `usesCleartextTraffic`. OkHttp: base URL must be `https://`
and end with `/`; `followRedirects(false)`, `followSslRedirects(false)`,
`retryOnConnectionFailure(false)`, no cache, no cookie jar, timeouts 15 s connect / 60 s read and
write / 70 s call (plus 38 s text / 52 s image coroutine timeouts), 512 KB response cap, one
generation in flight, no automatic retry. Multipart image ≤ 1.5 MB after re-encoding. Firestore and
Auth use the Firebase SDK's TLS; Firestore reads go to the server with a 30 s timeout. The Worker
URL is a public `BuildConfig` value without query parameters or keys. No certificate pinning (see
MASVS-NETWORK-2). Tests: `SecurityV22NetworkTest`, `WorkerMissionGenerationRepositoryTest`,
`WorkerV19Test`, `apk_audit` (no cleartext endpoint strings, Worker host only via HTTPS).

## Dependency audit (brief §19)

- **Shipped Android dependencies**: `dependency_audit.py` parses `:app:dependencies` for
  `releaseRuntimeClasspath` and `debugRuntimeClasspath` (228 coordinates incl. BOM-managed ones)
  and queries OSV in one batch plus one detail call per advisory. CRITICAL/HIGH fail unless listed
  in `advisory-allowlist.json` with a reason and an expiry (empty today). This container cannot
  reach api.osv.dev, so the live query result is the CI `security` job's `dependency-audit.json`.
- **Rules test tooling (npm)**: 5 high → 0 after V22-F10; `npm audit --audit-level=high` gates CI.
- **Gradle dependency verification metadata**: not introduced (a verification file for 228
  artifacts would have to be regenerated on every upgrade; deferred). Major upgrades were not made.

## APK inspection (brief §20)

| Check | Debug | Release |
|---|---|---|
| Application id / label | `app.focusmodeplus.debug` / Focus Mode+ Debug | `app.focusmodeplus` / Focus Mode+ |
| Version | 22.0.0-debug (2200) | 22.0.0 (2200) |
| min/target SDK | 26 / 37 | 26 / 37 |
| Debuggable | yes (expected) | **no** |
| R8 | not obfuscated (expected) | obfuscated, `mapping.txt` produced |
| Exported components | 5 allow-listed | 5 allow-listed |
| Debug tooling activities | present, not exported | absent |
| Secrets (API keys, private keys, tokens, service accounts, signing files) | none | none |
| App Check debug token | library default resource is empty | resource and debug provider absent |
| google-services resources | none (unconfigured build) | none |
| Emulator endpoints / demo project id | none | none |
| Cleartext endpoint URLs | none (library documentation URLs only) | none |

## Denial-of-service limits (brief §22)

| Input | Limit |
|---|---|
| Backup file | 5 MB, depth 20, 250 000 nodes, 200 keys/object, 10 000 items/array, 50 000 chars/string, 40 chars/number, 250 Missions, 100 steps, 5 000 Sessions |
| AI response | 512 KB, call timeout 70 s, one request in flight, no automatic retry |
| Firestore document | 1 MiB (Firestore) and Rules bounds; batches ≤ 450 writes |
| Image | 20 MB original, 12 000 px side, 40 MP, decode downsampled to 1 800 px, 1.5 MB output |
| Migration / sync taps | one mutex per owner; idempotent migration id; unique WorkManager job (KEEP) |
| Sync retries | outbox: exponential backoff up to 6 h, parked after 8 attempts; worker: 6 runs; invalid records parked, never blocking (V22-F01) |
| Deep links | 400 chars; navigation only; tagged |
| Account switching | 20× loop test without leak or stuck state |

## Data-deletion audit (brief §23)

| Action | Local data | Cloud data | Sync / jobs | Notes |
|---|---|---|---|---|
| Sign out (keep data) | kept, bound to the account | untouched | job cancelled; nothing uploads until the same account signs in | `AccountLifecycleTest.signOutKeepsLocalAndCloudDataAndStopsSync` |
| Sign out + remove local data | erased (Room transaction, preferences, draft photo, reminders, notifications, Session) | untouched | binding/outbox removed | `…signOutWithRemoval…` |
| Full local deletion | erased | untouched | signs out first; binding removed, so nothing re-downloads | `AccountLifecycleTest.fullLocalEraseSignsOutAndNeverRedownloads`, `PersonalDataEraserTest` |
| Account deletion | kept as guest data or erased (user choice); export offered first | client deletes every app document (verified count 0), then the Auth account; incomplete stays pending and retryable | job cancelled | `AccountLifecycleTest.accountDeletion…`; the UI never claims recursive server deletion before the function is deployed (account-deletion-v21.md) |

## Stability (repeated runs)

The complete `SecurityV22*` grouping (all five modules) was re-executed 20 times in a row with
`--rerun`. No sleeps are used in the v22 tests; asynchronous waits are awaited states
(`withTimeout { flow.first { … } }`) or deterministic coroutine joins.

Result: **20 / 20 runs green, 62 tests per run, 1 240 executions, 0 failures** (local JDK 21,
Robolectric SDK 37). No v22 test was flaky, and none of the pre-existing tests failed in any of the
full-suite runs made during v22 (baseline, after the package migration, after the fixes, final).

## Evidence of reproduction (before the fixes)

| Finding | Pre-fix result |
|---|---|
| V22-F01 | `migration must complete, was Failed(failure=SERVER, retryable=true) (binding MIGRATING)` |
| V22-F02 | Rules: write succeeded; decoder: `expected null, but was RemoteSession(type=QUICK…)` |
| V22-F03 | v21 importer: `AssertionError: pre_2000 must not import` |
| V22-F04 | v21 importer: `java.lang.ArithmeticException: long overflow` (also `Instant.parse("+1000000000-01-01T00:00:00Z").toEpochMilli()` in jshell) |
| V22-F05 | v21 importer reached the integrity check; JDK timing: 100 k digits 0.18 s, 400 k 2.6 s, 1 M 16.5 s (`new BigDecimal`) |
| V22-F07 | Rules: both step tests failed (writes succeeded) |
| V22-F08 | `java.lang.IllegalStateException: hostile` escaped `prepare()` |
| V22-F09 | `apk_audit.py` on the pre-fix debug APK: PreviewActivity and ComponentActivity exported |
| V22-F10 | `npm audit`: 5 high (grpc-js GHSA-m9gg-hp2v-232j, GHSA-f596-whhp-79r4) |
| V22-F11 | `apk_audit.py`: `READ_GSERVICES` requested (manifest merger: recaptcha 18.6.1) |
