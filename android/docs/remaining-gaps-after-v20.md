# Remaining gaps after v20

Everything here is known and deliberately open. Nothing in this list is a hidden failure: the
JVM suite, Lint and both builds pass. Categories: **device validation**, **platform limitation**,
**deferred product scope**, **missing backend**, **future account feature**, **future
commercial feature**, **genuine unfinished parity item**.

## Device validation (code exists, verified only on the JVM)

| Item | Why a device is needed |
|---|---|
| System camera capture (`TakePicture` into the FileProvider target) | real camera apps, URI grants on Android 8–17, EXIF orientation of real photos, process death while the camera is open |
| System speech input (`RecognizerIntent`) | availability differs by device and vendor; offline recognition; TalkBack interplay |
| Export through "Speichern unter" (SAF `CreateDocument`) and import (`OpenDocument`) | real document providers (Downloads, Drive), cancelled dialogs, provider errors |
| Full data deletion with a running foreground Session | service stop, notification removal and DND restore on a real device |
| Reminder cancellation after deletion | WorkManager and AlarmManager behaviour under OEM battery managers |
| Fokus-Rhythmus and Insights with TalkBack | bar-by-bar reading order, selection announcements, large-font reflow on real fonts |
| Everything already listed in [device-smoke-test.md](device-smoke-test.md) | no physical device or working emulator was available (see v18.1 performance notes) |

## Platform limitation

| Item | Note |
|---|---|
| No application blocking on consumer Android | unchanged: monitoring hints only; strict blocking needs a managed device |
| Speech recognition runs in the device's speech service | Focus Mode+ receives only the recognized text; where that service processes audio is outside the app (the app says so) |
| System permissions survive data deletion | notifications, Usage Access and DND access are Android settings; the deletion dialog says so |
| Work-profile apps, OEM battery managers, Android 15+ implicit DND mode | unchanged from v18.1 |

## Deferred product scope

| Item | PWA state | Decision |
|---|---|---|
| Weekly Focus League, score, achievements, League preview on Home and in Insights | demo data, labelled as preview | needs a server-authoritative backend; not shown on Android |
| AI Coach screen | placeholder | not ported |
| Language switch DE/EN | DE + EN | German stays authoritative; no half-translated selector |
| "Letztes Backup wiederherstellen" (automatic in-browser backup copies) | localStorage copies | Android keeps no internal backup copies; OS backup and file export replace it |
| Importing settings from a backup | PWA restores language | Android keeps the device's own settings (merge import); exported settings are informational |
| Free-form orbital Mission route | Home orbit | vertical plotted route kept for readability at 160 % font and for TalkBack order |
| Separate default Quick Focus duration in Settings | not present in the PWA | Android remembers the last chosen duration (one owner) |

## Missing backend

Verified Sessions and streaks, server-side Session ids, AI quota and entitlement checks
(`ServerAuthoritative` types exist, no producer). v21 reserves the remote field names and the
Firestore Rules refuse client writes to them. Backup integrity proves only that a file is
unchanged; it never proves honest History, streak or progress.

## Future account feature

**v21 status**: optional Google account and account-backed synchronization are implemented and
tested on the JVM and against the Firebase Emulator Suite ([account-and-sync-v21.md](account-and-sync-v21.md)).
Still open:

| Item | Category | Detail |
|---|---|---|
| Firebase project, `google-services.json`, SHA fingerprints, Google provider | owner setup | none exists in the repository; see [firebase-setup-v21.md](firebase-setup-v21.md) |
| Live Google sign-in | device validation | never run: no Firebase project, no Google Play emulator image, no device in this environment |
| Firestore deployment | owner setup | Rules are tested on the emulator only; not deployed |
| App Check | owner setup | providers wired (Play Integrity / debug); registration and metrics pending; enforcement off |
| Recursive deletion function | missing backend | implemented and emulator-tested in `firebase/functions`; not deployed, so production recursive deletion is not complete |
| Android emulator / physical device | device validation | account UI, Credential Manager chooser, WorkManager sync and R8 release sign-in unverified on a device |
| Profile photo | deferred | the Account screen shows initials; the Google profile image is not downloaded (no image library, no extra request) |
| Cross-device active Session handoff | deferred | an active Session belongs to its device; only finalized Sessions synchronize |
| Public profiles, friends | deferred | not part of v21 |
| Verified History and League | missing backend | needs a server-authoritative Session ledger ([future-verified-league.md](future-verified-league.md)) |
| Legal review, Data Safety form | owner | [privacy-account-sync-v21.md](privacy-account-sync-v21.md) marks open items **[TO BE CONFIRMED]** |

## Future commercial feature

Premium screen, subscriptions, payments, Play publication (including the `specialUse`
foreground-service declaration review).

## Genuine unfinished parity items

| Item | Detail |
|---|---|
| Not in the backup file | selected distracting apps (package names stay on the device), Geplanter Fokus schedules, reminder state, distraction counts, the open AI draft, a running Session. The export screen states this. |
| Reserved server fields | the PWA rejects a backup that contains them; Android ignores them (never imports such values). Behaviour differs, the outcome (nothing server-authoritative is imported) is the same. |
| Adaptation timeout | 60 s on Android vs 120 s in the PWA. Audited in v20: the Worker code is not in this repository; its documented budgets are 35 s (text) and 47 s (photo) for `/create-mission` (docs/API_CONTRACTS.md §2.6), and no budget is documented for `/adapt-mission`. The request is user-initiated, cancellable and never retried automatically; 60 s stays below the client's 70 s call timeout. Kept; raise only together with a documented Worker budget. |
| Chart motion detail | bars grow together (PWA staggers by 30 ms); selection change is immediate (PWA fades in 160 ms). |
| Activity rail | drawn per row (hairline) instead of one continuous rail element. |
| Home Mission hero step nodes | the PWA hero shows a numbered step-node row ("01 — 02 — 03"); Android shows the step counter, the next step and "n/m Schritte" instead. |
| "Als Hauptmission festlegen" | Android selects the primary Mission through the carousel page instead of an explicit action. |
| Side-by-side PWA rendering | v20 compared against PWA stylesheets and renderers; the PWA was not re-rendered next to the new Android screens. |
| Instrumented / emulator tests, formatting check in CI | still none; the JVM suite and Lint are the CI gates. |
