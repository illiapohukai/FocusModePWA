# v21 Google Account & Secure Sync Foundation

Optional Google account with secure, account-backed synchronization of Missions, finalized Focus
Sessions and a few device-independent settings. The complete app keeps working without an
account. Related: [setup](firebase-setup-v21.md) · [conflict policy](sync-conflict-policy-v21.md) ·
[account deletion](account-deletion-v21.md) · [App Check](firebase-app-check-v21.md) ·
[future verified League](future-verified-league.md) · [privacy draft](privacy-account-sync-v21.md).

## Product decisions (final for v21)

- **Guest mode stays complete**: Quick Focus, Missions (incl. AI generation and adaptation),
  Sessions, History, Activity, Insights, reminders, settings, app selection, permissions,
  backup/import/export and full local deletion. There is no login wall; guests who tap "Ohne
  Konto fortfahren" are not asked again. Guest mode is local use, **not** Firebase anonymous auth.
- **Google is the only provider**: Sign in with Google through Android Credential Manager,
  authenticated with Firebase Authentication. No email/password, links, phone, anonymous, Apple,
  Microsoft, GitHub or separate passkey flow.
- **Existing local data is never attached silently**: after sign-in a preview is shown and the
  user decides.
- Achievements, League UI, points, ranks, seasons and verified streaks are **not** part of v21.

## Architecture

```
core:model    AccountState, AccountUser, SyncState, MigrationPreview, AccountDataDecision, ...
core:domain   account/AccountRepository (the one account owner contract)
              account/AccountLifecycle  (sign-out, account switch, account deletion)
              sync/SyncRepository + SyncEngine (the one sync owner and the one migration owner)
              sync/RemoteSyncStore      (Firestore boundary), sync/SyncLocalStore (Room boundary)
              sync/RemoteSchema + RemoteCodec (the Firestore model; mirrored by firestore.rules)
              sync/SyncConflictPolicy   (pure, deterministic rules)
core:data     Room v5: account_binding, sync_outbox, sync_entity_state, sync_tombstones, sync_conflicts
              RoomSyncLocalStore, SyncOutboxWriter (used inside the existing repository transactions)
core:account  the only module with Firebase/Credential Manager: FirebaseAccountRepository,
              FirestoreRemoteSyncStore, FirebaseRuntime, App Check boundary,
              AndroidSyncScheduler + SyncWorker (WorkManager), unconfigured implementations
feature:settings  Settings section "Konto & Synchronisierung" + Account screen (AccountViewModel)
firebase/     firestore.rules, firestore.indexes.json, firebase.json, Rules tests, deletion function
```

Composables never call Firebase; ViewModels never hold Firebase listeners (there are none: the
engine reads on demand). The Activity is passed per sign-in call as an opaque `AccountChooserHost`.

## Configured and unconfigured builds

| | Configured (`android/app/google-services.json` present) | Unconfigured (repository, CI) |
|---|---|---|
| Gradle | Google Services plugin applied; `FIREBASE_CONFIGURED=true`; web client id from the file | plugin not applied; `FIREBASE_CONFIGURED=false` |
| Account owner | `FirebaseAccountRepository` | `UnconfiguredAccountRepository` (`FirebaseUnconfigured`) |
| Remote store | `FirestoreRemoteSyncStore` | `RemoteSyncStore.Unavailable` |
| Firebase init | explicit, lazy (`FirebaseRuntime`) | never (`FirebaseInitProvider` removed) |
| Network | Firebase Auth / Firestore over HTTPS | none for accounts |
| UI | full account section | "Cloud-Synchronisierung ist in diesem Build noch nicht eingerichtet." |

One app, one DI boundary (`AccountProvidesModule`), no second app implementation.

## Account states

`FirebaseUnconfigured`, `Guest`, `SignInAvailable`, `SigningIn`, `SignedIn`, `Refreshing`,
`ReauthenticationRequired`, `NetworkUnavailable`, `CredentialUnavailable`, `SignInCanceled`,
`AccountDisabled`, `AccountDeletedRemotely`, `PermissionDenied`, `Error(kind)` with kinds
`INVALID_CREDENTIAL`, `PROVIDER_UNAVAILABLE`, `ACCOUNT_MISMATCH`, `TOO_MANY_REQUESTS`, `UNKNOWN`.
`AccountErrorMapper` maps Credential Manager, Google ID and Firebase exceptions; messages, tokens
and stack traces are never shown or logged. Only `SignedIn`/`Refreshing` may synchronize.

## Data that synchronizes

| Data | Remote | Notes |
|---|---|---|
| Missions | `/users/{uid}/missions/{missionId}` | title, subject, type, deadline, preparation start, Zeitplan (start mode, weekdays, session length), estimate, summary, warning, status, archive/completion, current/archived step, origin, progress entries (`progress[]`), `stepIds`, timestamps, `syncVersion` |
| Mission Steps | `/users/{uid}/missions/{missionId}/steps/{stepId}` | position, title, task, duration, status, reason, scheduled date, completion, required tools, recommended apps (text only) |
| Finalized Sessions | `/users/{uid}/sessions/{sessionId}` | type, status, outcome, end reason, title, task, planned/actual minutes, elapsed ms, counts-as-focus, start/end, local day, zone, Mission/step link, protection mode, end note, rating, review note, origin |
| Settings | `/users/{uid}/settings/profile` | Quick Focus default duration, motion preference, lock-screen detail level |
| Migration record | `/users/{uid}/syncMeta/migration_{migrationId}` | id, status, counts, times |
| Tombstones | `/users/{uid}/tombstones/mission_{missionId}` | entity type/id, deletion time, version |
| Profile | `/users/{uid}` | owner, schema version, created/updated |

History is derived from the synchronized Sessions (no second History source). Every document has
`ownerUid`, `schemaVersion`, client times in epoch ms, `syncVersion`, `deviceMutationId` and the
server commit time `serverUpdatedAt`. Document ids are entity ids (safe-id policy), never user text.
Mission confidence and structured material facts are not stored on Missions in v20 (they live only
in the generation draft), so there is nothing to synchronize for them.

## Local only (enforced)

Installed-app inventory, selected distracting apps and package names, UsageStats and detected
opening attempts, permission and capability states (Usage Access, DND, notifications, exact alarms,
battery optimization), notification channels, WorkManager jobs, reminders and Geplanter Fokus, the
active Session and its monotonic timing, the Mission draft before acceptance, photos and other
attachments, speech audio, AI request state, diagnostics. The codec has no field for any of them;
the Rules refuse unknown fields.

## Derived values

Streak, totals, Session counts by outcome, completed Missions/steps, week totals, Activity,
History summaries, Fokus-Rhythmus, 28-day consistency and Insights are recalculated from the
synchronized source records by the existing owners (`ProgressCalculator`, `FocusInsights`,
`HistoryQueries`). No total is uploaded; Activity rows are rebuilt from downloaded Sessions and
completed steps; Mission progress entries are a union by Session id.

## Room schema v5 and the outbox

`MIGRATION_4_5` only creates tables; no existing row changes and nothing becomes linked. Every
synchronized product change writes its outbox row in the **same transaction** (`SyncOutboxWriter`
inside `RoomMissionRepository`, `RoomSessionRepository`, `BackupImportRepository`), and only when
the dataset is bound (`ACTIVE` or `MIGRATING`). The outbox stores an entity reference, not a copy
of the content: the current row is read at upload time. Fields: mutation id, account uid, entity
type/id, operation, schema version, local version, created time, retry count, last failure class,
next attempt, state (`pending`/`parked`). Settings are compared by fingerprint instead of queued.

## Synchronization

`SyncEngine` runs everything under one mutex: download → merge → upload → settings. Uploads are
batched (≤ 450 writes; a Mission and all its steps always share a batch), idempotent (document id
= entity id; Sessions are append-only), and versioned (`syncVersion` must be exactly the next
version; a stale device is refused, downloads, merges and retries once). A lost acknowledgement is
recognized by the device mutation id on the server copy. Failures get exponential backoff (15 s
doubling, at most 6 h); after 8 attempts a mutation is parked and the UI offers "Erneut
versuchen". Every remote call is followed by an account check and every local write re-checks the
binding inside its transaction, so a sign-out or account switch mid-sync writes nothing.

Background: one unique WorkManager job `focus-account-sync` (network constraint, exponential
backoff, `KEEP` coalescing, at most 6 runs per job) carrying the uid; it does nothing for another
or a signed-out account. Triggers: outbox growth (3 s coalescing), settings changes, sign-in,
app start. No timer-tick or recomposition writes; no permanent Firestore listener.

## Local-data migration

After sign-in with existing guest data the Account screen shows: Missions (and steps), finalized
Sessions, History date range, total local Focus minutes (labelled local), included settings,
excluded local-only data, Sessions still awaiting review, and the account's cloud counts. Text:
"Lokaler Fortschritt wird mit diesem Konto verbunden." and "Dadurch entstehen keine verifizierten
Liga-Punkte." Actions: "Lokale Daten mit diesem Konto verbinden", "Später", "Cloud-Daten
verwenden" (only when the cloud has data; a dialog warns that unsynchronized local data is
replaced), "Lokales Backup exportieren".

Confirming runs one transaction (binding `MIGRATING` with a stable migration id + one mutation per
Mission and finalized Session), then uploads. The migration completes only after
`existingIds` proves that the server holds every migrated id; missing ids are re-queued. It is
resumable (process death keeps the binding and outbox), idempotent (same migration id, document
id = entity id; duplicate taps are serialized), account-aware (another account is refused) and
never deletes local data. An empty device signing in restores the account automatically.

## Sign-out, account switching, deletion

- **Sign-out**: stops the job, signs out of Firebase and clears the Credential Manager state (no
  automatic sign-in afterwards). Local data stays bound to the account unless "Lokale Daten von
  diesem Gerät entfernen" is chosen; cloud data is never deleted. Pending changes are shown and
  upload at the next sign-in with the same account.
- **Another account signs in** while local data belongs to the previous one: nothing synchronizes
  and nothing is reassigned. Choices: "Getrennt halten" (signs the new account out again), export a
  backup, "Lokale Daten löschen und fortfahren", "Daten dieses Kontos verwenden". Pending changes
  of the previous account are shown as lost before confirming.
- **Account deletion**: see [account-deletion-v21.md](account-deletion-v21.md).
- **Full local deletion** (Daten & Backup) removes the binding and sync metadata in the same
  transaction and signs the device out, so nothing is re-downloaded; cloud data stays.
- **Remotely deleted / disabled account**: detected on refresh; the device signs out locally,
  local data stays, "Verknüpfung lösen" turns it into guest data.

## Backup versus account sync

Backups stay portable, local, manual, user-controlled and unverified; the envelope is unchanged
(no backup schema change in v21). Account sync is authenticated, account-owned and conflict-managed.
Imported Sessions synchronize as ordinary, unverified History only when the dataset is linked
(i.e. after the user confirmed linking); imports never fill reserved fields.

## Security baseline

Deny-by-default Rules with owner-only paths and strict validation; reserved-field protection;
no token, email or content logging (there is no logging at all in the sync path); no service-account
credentials and no Admin SDK in the APK; recent sign-in required for deletion; bounded retries;
idempotent mutations; payload bounds; narrow exported components (Firebase Auth's browser and
reCAPTCHA activities and the passkey biometric permissions are removed; all new components are
non-exported; Google Play services adds the normal `READ_GSERVICES` permission and a service
protected by a Google signature permission); HTTPS only (unchanged network security config); Firestore memory cache only; no
custom cryptography and no client-side keys.

## Tests

- `core:domain`: `RemoteCodecTest` (round trips, strict decoding, reserved fields, golden payload
  fixture shared with the Rules tests), `SyncConflictPolicyTest`.
- `core:data`: `SyncEngineTest` (guest isolation, preview, verified/idempotent/resumable migration,
  outbox transactions, active Session exclusion, lost acknowledgements, batching, bounded retries,
  second device download, derived values, tombstones, structural conflicts, settings LWW, account
  change mid-sync, imports, second-device linking), `AccountLifecycleTest` (sign-out, switching,
  replacement during a running Session, remote deletion, revocation, account deletion paths, local
  erase), `MigrationV5Test` (4→5 populated incl. imported History and an active Session, 1→5,
  empty, interrupted migration).
- `core:account`: error mapping, value conversion, unconfigured selection, unique account-aware worker.
- `feature:settings`: `AccountScreenTest` (all states, dialogs, TalkBack semantics, 320 dp/160 %,
  landscape, tablet, screenshots `acc01…acc18`), `AccountViewModelTest`.
- `app`: `AccountFlowTest` on the real Hilt graph of the unconfigured build (screenshots 40–41).
- `firebase/test`: 37 Rules tests + 3 deletion tests on the Firestore emulator.

## Not done / external

No live Google sign-in, no real Firebase project, no Firestore deployment, no App Check
enforcement, no deployed deletion function, no Android emulator or physical-device run. See the
setup guide and [remaining-gaps-after-v20.md](remaining-gaps-after-v20.md).
