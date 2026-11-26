# Focus Mode+ — Android (v20 Android Parity Completion)

A native Android app for Focus Mode+, written in Kotlin with Jetpack Compose. It is **not** a
WebView, a Trusted Web Activity or a wrapper around the PWA. The PWA in the repository root is
unchanged and stays the reference for product terms, Mission and Session behavior, timer semantics,
History, Recovery, colors, typography, the Cosmic Core identity, backups and the AI Worker contract.

- [Parity matrix v20](docs/parity-matrix-v20.md): every PWA screen and workflow, compared and classified
- [Visual parity v20](docs/visual-parity-v20.md): History, Insights, Activity, Briefing, Data & Backup
- [Remaining gaps after v20](docs/remaining-gaps-after-v20.md): device validation, deferred scope, open items
- [Missions v19](docs/missions-v19.md): Mission architecture, generation lifecycle, validation, review, adaptation
- [Protection capabilities](docs/protection-capabilities.md): what consumer Android can and cannot do
- [PWA parity](docs/pwa-parity.md): what is implemented, partial, deferred or intentionally different
- [Visual parity v18.1](docs/visual-parity-v18.1.md): screen-by-screen comparison with the live PWA
- [Performance v18.1](docs/performance-v18.1.md): emulator ANR diagnosis, fixed bottlenecks, Cosmic Core tiers
- [Device smoke test](docs/device-smoke-test.md): manual physical-device checklist (results pending)

## Selected versions (resolved from official repositories on 2026-11-19)

| Component | Version | Note |
|---|---|---|
| Android Gradle Plugin | 9.5.1 | built-in Kotlin (no `kotlin-android` plugin), max API 37 |
| Gradle (wrapper) | 9.9.2 | SHA-256 pinned in `gradle-wrapper.properties`; AGP 9.5 needs ≥ 9.6.0 |
| Kotlin / Compose compiler | 2.4.21 | |
| KSP | 2.3.14 | Room and Hilt via KSP (no kapt) |
| JDK | 21 to run, bytecode target 17 | AGP 9.5 needs JDK ≥ 17 |
| compileSdk | 37, minor 2 (`android-37.2`) | Compose 1.13 requires compileSdk ≥ 37.1 |
| targetSdk | 37 (Android 17) | |
| minSdk | 26 (Android 8.0) | java.time and notification channels are native |
| Build tools | 36.0.0 | AGP 9.5 default |
| Compose BOM | 2026.11.01 | UI/foundation 1.13.0, Material 3 1.5.0 |
| Navigation Compose | 2.10.2 | type-safe routes (kotlinx.serialization) |
| Lifecycle | 2.11.1 | |
| Activity Compose | 1.13.0 | |
| Room | 2.8.5 | schemas exported to `core/data/schemas` |
| DataStore Preferences | 1.3.0 | |
| WorkManager | 2.12.0 | |
| Hilt / AndroidX Hilt | 2.60.1 / 1.4.0 | |
| Coroutines / Serialization | 1.11.0 / 1.13.0 | |
| Retrofit / OkHttp | 3.0.0 / 5.5.1 | the only networking stack |
| JUnit / Robolectric / Turbine | 4.13.2 / 4.18.1 / 1.2.1 | Robolectric runs the Android 17 (SDK 37) runtime |

All versions live in `gradle/libs.versions.toml`. Shared build logic is in `build-logic/`
(convention plugins `focus.android.application|library|compose|hilt|feature`, `focus.jvm.library`).

## Build

Requirements: JDK 17+ (21 tested), Android SDK with `platforms;android-37.2` and
`build-tools;36.0.0`. Android Studio: a version that supports AGP 9.5.

```bash
cd android
export ANDROID_HOME=/path/to/sdk      # or sdk.dir in local.properties (never committed)
./gradlew :app:assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest :core:domain:test   # all unit, Room, Robolectric and Compose tests
./gradlew :app:lintDebug              # lint for the app and every module it depends on
./gradlew :app:assembleRelease        # R8-minified, unsigned (no signing config in the repo)
```

CI: `.github/workflows/android.yml` (Node 24 action majors; tests, lint, debug and unsigned
release builds, both APKs and the test/lint reports with screenshots as artifacts, Gradle
caching). No signing secrets, no Play upload.

## Architecture

Unidirectional data flow: immutable UI state from `ViewModel`s (`StateFlow`), user actions as
methods, one-shot navigation events. Composables never touch Room or platform APIs directly.
Hilt is the only DI framework. Time comes from `FocusClock`, so it is testable.

```
android/
├── app/                 Application, MainActivity, NavHost, adaptive navigation, deep links, system receiver
├── build-logic/         convention plugins
├── core/
│   ├── model/           (JVM) domain models + data-trust classification
│   ├── domain/          (JVM) business rules, repository/gateway contracts, PWA backup parser
│   ├── testing/         (JVM) fakes and fixtures (tests only; never shipped)
│   ├── data/            Room (v4, migrations 1→2→3→4), DataStore, repositories, backup import/snapshot, data wipe, RestoreGuard
│   ├── network/         Cloudflare Worker client (Retrofit/OkHttp), DTO validation, error mapping, draft photo store
│   ├── designsystem/    tokens, Inter typography, components, icons, Cosmic Core renderer
│   ├── notifications/   channels, capability reader, the single notifier, PendingIntents
│   ├── protection/      the single capability owner, Usage Access, DND, managed-device boundary, app catalog
│   ├── session/         the single Focus Session owner, foreground service, end alarm
│   └── reminders/       the single reminder scheduler (WorkManager + AlarmManager)
└── feature/             home, quickfocus, focus, result, history, missions, reminders, settings
```

Each feature owns its UI state, ViewModel, actions, navigation contract (`…Destination` +
`NavGraphBuilder` extension) and UI. Features depend only on `core:domain`, `core:model`,
`core:designsystem` and, where needed, the capability owner in `core:protection`.

**Single owners**
- Session: `FocusSessionManager` (`core:session`), behind `FocusSessionController`.
- Missions (persistence, draft, progress): `RoomMissionRepository`. History: `RoomSessionRepository`.
- Mission rules: `MissionRules`. Generation request: `MissionGenerationOwner`. Adaptation: `MissionAdaptationOwner`.
- Calendar events: `MissionCalendar`. Zeitplan: `MissionSchedulePlanner`. Draft photo: `AndroidMissionMaterialStore`.
- Notifications: `FocusNotifier`. Reminders: `AndroidReminderScheduler`.
- Protection capability: `ProtectionCapabilityOwner`.
- Statistics: `ProgressCalculator` (Today, week, streak) and `FocusInsights` (Fokus-Rhythmus, Dein
  Fokusverlauf); History order and labels: `HistoryQueries` / `HistoryFormat` (v20).
- Backup: `PwaBackupImporter` validates every file (incl. `BackupIntegrity`); `AndroidBackupExporter`
  / `BackupExportOwner` write; `BackupImportRepository` applies. Data deletion: `PersonalDataEraser` (v20).

## First vertical slice (fully implemented)

Home → Quick Focus setup → active Focus Session → Result → History, persisted in Room.

- **Home**: localized date, title, Bereit/Offline readiness, active-Session banner (running, paused
  after a restart, or Mission review pending), primary Mission card (repository-backed; there are
  no hard-coded sample Missions), Quick Focus entry, Today summary (minutes, Sessions, local streak,
  week), upcoming reminders (with an honest notice when they cannot be delivered), recent Activity,
  floating bottom navigation with the raised Plus action (start sheet).
- **Quick Focus**: optional task, circular duration dial (5–60 min in steps of 5, the PWA contract)
  with the static `MM:00` readout in the timer typography, one authoritative duration, honest
  Fokus-Schutz summary, contextual notification prompt, Start (one launch per tap).
- **Active Focus**: native Cosmic Core, the timer as real text, task card, protection badge, end
  flow (Standard / Protected barrier / emergency exit), landscape layout, Recovery UI.
- **Result**: outcome, real duration, task, time range, local streak (labelled "lokal berechnet,
  nicht verifiziert"), Mission step review for Mission Sessions.
- **History**: Heute/Woche/Alle filters (PWA), grouped by local day, local statistics.

Also implemented: Scheduled Focus (list and editor), Settings, Permission Center, distracting-app
selection, Daten & Backup (export, import, full deletion).

## v20 Android Parity Completion

See [docs/parity-matrix-v20.md](docs/parity-matrix-v20.md). In short:

- **History**: Fokus-Rhythmus (7 Tage / 4 Wochen, Mission/Quick split, average, best period,
  trend; every bar is an accessible button), Mission title and step on rows, outcome node shapes
  (filled / half / open + German label), PWA empty states, one stable order (end, then Session id).
- **Dein Fokusverlauf** (History → Statistik): total, Ø pro Sitzung, Abschlussquote, Heute,
  Konstanz with the 28-day grid, Bestwerte, Sitzungsarten — all from local History, labelled unverified.
- **Home Activity**: one card grouped by day, six rows, nodes in the Mission tone, Mission title
  as context; the Today card opens History.
- **Missions**: rename (title only; id, colour, steps, History and reminders unchanged); the
  Worker's `sourceAnalysis` ("Aus dem Material erkannt", "Noch unklar") and per-step
  `requiredTools` / `recommendedApps` are kept (Room v4) and shown in Briefing and Plan; system
  camera and system speech input on the goal field (no camera or microphone permission).
- **Daten & Backup**: export in the PWA envelope (full or "Nur Missionen & Einstellungen"),
  SHA-256 integrity that the PWA verifies and vice versa, import for web and Android backups,
  and "Alle persönlichen und nutzergenerierten Daten löschen".
- **Settings**: Daten & Backup row with "Letztes Backup", a privacy section (what stays local,
  what goes to the AI).

## Mission vertical slice (v19)

Home → Mission Creation → AI generation (pixel operator) → Mission Briefing → Mission Plan → next
step → Mission Focus Session → Result review → progress update → optional plan adaptation → Home.
Missions are real Room data (no production fixtures). See [docs/missions-v19.md](docs/missions-v19.md).

- **Creation**: goal, one photo (system picker), type, confidence, Zeitplan with an inline German
  calendar that shows existing Mission dates; everything lives in a Room draft and survives process
  death, a declined picker, failure and cancel.
- **Generation**: one Worker request per tap, cancel with late-answer protection, safe German errors,
  success animation only after full validation, nothing stored before "Mission übernehmen".
- **Briefing → Plan**: one transactional Mission write (two active Missions at most); the Plan makes
  the current step dominant and starts it through the one Session owner.
- **Result**: Abgeschlossen / Teilweise / Abgebrochen, rating and note, applied once per Session;
  the final step completes the Mission; optional adaptation replaces only open steps.
- **Home**: first-time, returning, one Mission, two-Mission carousel; Mission calendar.

## Session timing and Recovery

- One durable `active_session` row (single slot; a second active Session is rejected by the database).
- Timing: banked elapsed time plus the current segment's start on `SystemClock.elapsedRealtime`
  (monotonic, includes deep sleep, ignores wall-clock changes), together with the boot counter
  (`Settings.Global.BOOT_COUNT`). Remaining time is always derived, never ticked.
- No per-second writes: one write at start, one coarse checkpoint per minute while running, one
  transactional finalization.
- Backgrounding, process death and app restart: the Session keeps running in real time and is
  re-derived on start, from the end alarm or from the foreground-service tick. If the planned time
  passed while away, it is finalized once as `timer_finished_while_away`, with the exact completion instant.
- Reboot: the monotonic base is gone, so the Session freezes at the last checkpoint and waits
  ("Fortsetzen" / "Fokuszeit speichern" / "Verwerfen"), like the PWA after a reload. A notification
  tells the user.
- Finalization (`FinalizationPolicy`): below 1 minute → cancelled (`ended_too_early`); user cancel
  never counts; an early end with ≥ 1 minute → PARTIAL (counts); the planned time elapsed → COMPLETED.
  Mission Sessions wait for the step review. History insert is `IGNORE` on the Session id, inside one
  transaction that also clears the active row and appends one Activity entry, so a Session is
  finalized exactly once even when the alarm, a tick and a tap race.
- Restore from an OS backup on a new device: `RestoreGuard` freezes a running Session and drops the
  DND record and reminder occurrences (they belong to the old device).

## Active-session notification

`FocusSessionService` is a visible foreground service of type `specialUse`. It is started only with
a running Session and stops itself when the Session ends. One ongoing notification ("Fokus läuft",
"Noch 18 Minuten") is updated at whole-minute boundaries of the remaining time and on screen-on,
never every second. Tapping it returns to the Session; "Beenden …" opens the safe end flow (it never
ends the Session directly). If Android refuses a foreground start from the background, the same
notification is posted directly. The end alarm (`ELAPSED_REALTIME_WAKEUP`, inexact
allow-while-idle; exact only where the platform already allows it) finalizes the Session even when
the process was killed.

Lock-screen privacy (Settings → "Inhalt auf dem Sperrbildschirm"): *Aufgabe* (task in the shade; the
lock screen gets a generic public version when it hides sensitive content), *Nur Status* (never the
task), *Ausblenden* (secret on the lock screen).

## Notification strategy and permission

Channels: `focus_active` (low, silent), `focus_finished`, `reminders`, `mission_deadlines`,
`protection_status` (silent hints). `POST_NOTIFICATIONS` is never requested at launch, only in
context (Quick Focus card, Permission Center). States: not requested, denied, permanently denied
(denied after a request with no rationale), OS-disabled, channel disabled, app switch off. The UI
says when reminders cannot be delivered. Copy is concise German without motivational filler; hints
never name the opened app.

## Reminder strategy

`ReminderPlanner` (pure) produces the desired occurrences; the scheduler diffs them with stored rows
and only changes the differences, using one stable key per occurrence (`type:source:date`) as the
unique WorkManager name and PendingIntent identity. Repeated syncs (start, data change, boot,
time or time-zone change, app update) never duplicate. A delivered occurrence is marked atomically
before posting, so it is never shown twice, and the next occurrence is planned.

| Type | Copy | Delivery |
|---|---|---|
| Vorbereitungsstart | "Deine Vorbereitung beginnt heute" | WorkManager (deferrable) |
| Mission-Deadline (2 days before) | "Deadline in 2 Tagen" / "Für „…“ sind noch 3 Schritte offen." | WorkManager |
| Geplanter Fokus (start − lead time) | "Bereit für deinen Fokus?" / "25 Minuten · …" | AlarmManager, inexact allow-while-idle |
| Tagesplanung (opt-in) | "Zeit für deinen Plan" | WorkManager |

`SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` are **not** declared. On Android 12+ reminders are
therefore delivered approximately on time (usually within minutes; Doze and OEM battery policies can
delay them), and the app says so. Exact delivery is used only where the platform allows it without a
permission (Android 8–11). Battery-optimization status is shown read-only; the exemption is never requested.

## Permission model (Settings → Berechtigungen)

Each row states why, which data, whether it leaves the device (it never does), the current state,
the action and the limits: notifications, Usage Access, Do Not Disturb, exact reminders, battery
optimization, managed device, accessibility ("Nicht verwendet"). Quick Focus works with none of them.

Declared permissions: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`,
`PACKAGE_USAGE_STATS` (granted by the user in settings), `ACCESS_NOTIFICATION_POLICY`,
`RECEIVE_BOOT_COMPLETED`, `INTERNET`, `ACCESS_NETWORK_STATE` (plus `WAKE_LOCK` from WorkManager).
Not requested: exact alarms, `SYSTEM_ALERT_WINDOW`, `QUERY_ALL_PACKAGES`, accessibility service,
device admin, notification listener, SMS, contacts, call log, location, microphone, camera.
The goal field hands off to the system camera (`ACTION_IMAGE_CAPTURE` into one FileProvider cache
file, deleted after re-encoding) and to system speech input (`RECOGNIZE_SPEECH`); both are
visible only through two narrow `<queries>` intents and need no permission of their own.

## App selection, Usage Access, DND, managed devices

See [docs/protection-capabilities.md](docs/protection-capabilities.md). In short: launchable apps
come from a narrow `<queries>` launcher intent; only package name, label snapshot and an optional
icon reference are stored, locally. Usage Access is read only during an active *protected* Session,
every 20 s while the screen is on (off the main thread; with the screen off it waits for screen-on
instead of waking), for the selected packages only. `DistractionMonitorOwner` keeps exactly one job
per protected Session and stops it on every end path. Hints are rate-limited to one per
3 minutes; per-Session counts are kept for 30 days. DND changes only the interruption filter, records
the prior state first and restores it only if Focus Mode+ set it. Managed-device enforcement is a
detection boundary with a no-op implementation. **No app is ever blocked.**

## AI Worker integration

`core:network` talks only to the existing Cloudflare Worker (`BuildConfig.WORKER_BASE_URL`, public
URL, HTTPS enforced). There is no Gemini key on Android. `POST /create-mission` (multipart:
`goalText`, `missionType`, `confidence`, optional `schedule` JSON and `image` with a generic file
name) is implemented with PWA-equivalent validation (1–6 steps of 5–60 min, text bounds, safe step
ids, no step after the deadline) plus v19 rejections (more than six steps, duplicate steps, markup,
script or executable URLs), timeouts (38 s text / 52 s image), cancellation, one request at a time,
a validated support request id, a 512 KB body cap and error mapping (400/401/402/403/408/413/415/
422/429/5xx and malformed output). `POST /adapt-mission` (JSON, PWA payload, this Mission only)
backs the optional adaptation (60 s). No redirects, no automatic retries, no logging interceptor,
no cache. No live Worker call is made by any test.

## Backup import, export and data deletion

Settings → "Daten & Backup" (all builds). **Import** reads a Focus Mode+ backup from the web app or
from Android through the Storage Access Framework (`OpenDocument`); it is never read from browser
storage. (The development flag now only controls a shortcut on the Missions screen.) It accepts `format: "focusmode-backup"` with exportVersion 1, and the legacy
root-array shape. The v17.17 limits apply: 5 MB, depth 20 (checked before parsing), 250 000 nodes,
200 keys, 10 000 array items, 250 Missions, 100 steps, 5 000 Sessions (newest 500 kept), string
bounds, safe ids, sane timestamps. There is a preview (new vs. existing, archived by the
two-active limit, everything not imported), then one Room transaction: all or nothing, existing ids
never overwritten. Imported Sessions stay `LOCAL_UNVERIFIED`; streak, League and score values are
never imported. The web app's app selection has no Android package names and is not mapped.
A SHA-256 digest (PWA canonical form `focusmode-canonical-json-v1`) is verified when present: a
changed or truncated file is rejected; older files without a digest are shown as such. Android
backups (`platform: "android"`) also restore the Zeitplan, step tools, review notes, the stored
local day, the protection mode and Mission progress entries, and keep up to 5 000 Sessions.

**Export** builds the same `focusmode-backup` v1 envelope (full, or "Nur Missionen &
Einstellungen" without History), timestamps in epoch milliseconds, `trust:
local_backup_unverified`, a digest over `{format, exportVersion, schemaVersions, scope, data}`.
Before anything is written the file is read back by the import validator; a file this app could
not restore is never handed out. The user picks the place (`CreateDocument`, no storage
permission); the name is `focus-mode-backup[-missionen]-YYYY-MM-DD.json` (never user content).
Not included: a running Session, selected apps, Geplanter Fokus, reminder state. The PWA's own
importer accepts these files (verified in v20 with the PWA security harness).

**Delete all** ("Alle persönlichen und nutzergenerierten Daten löschen", confirmed in a dialog):
the Session owner drops a running or frozen Session without History (service, alarm and DND
restored), then one Room transaction removes Missions, steps, the draft, progress, History,
Activity, selected apps, schedules, distraction counts and the DND record; user preferences
return to defaults (the install id, schema version and permission-request counter stay); the
draft photo is deleted; the reminder scheduler cancels every scheduled occurrence before the
remaining rows go; all app notifications are removed. Android system permissions are not changed.

## Data trust and privacy

- User-editable: Missions, tasks, schedules, selected apps, settings.
- Local derived: streak, totals, Activity, progress (always recomputed; never "verified").
- Future server-authoritative (no producer exists): verified Sessions and streak, League, rank,
  subscription, entitlements, AI quota (`ServerAuthoritative` types).

Everything stays on the device except: the goal text, Mission type, confidence, Zeitplan and an
optional re-encoded photo, sent to the Worker only when the user taps "Mission planen"; and, only on
"Plan mit AI anpassen", this Mission's title, subject, deadline text, open steps, the reviewed
step's result, rating and note and this Mission's progress entries. Never sent: other Missions,
History, streak, selected apps, settings, device data. No analytics, crash reporting, ads or trackers.

## Backup policy

`data_extraction_rules.xml` / `backup_rules.xml` include the Room database (Missions, History,
schedules, selected apps) and the preferences DataStore in OS backup and device transfer. Caches and
`no_backup` (install marker, WorkManager's database) are excluded. Device-bound transient state is
neutralized after a restore by `RestoreGuard` (see above). No tokens or API secrets exist.

## Security

HTTPS only via Network Security Config (no cleartext; system CAs only). Every component is
non-exported except the launcher activity. Receivers listen only to protected system broadcasts.
PendingIntents are immutable and explicit. Deep links (`focusmode://…`, not BROWSABLE) are strictly
parsed: ids must match the safe-id policy, and anything unknown goes to Home. They carry no task
text. Narrow package queries. DTO and backup validation. No sensitive logs (there is no logging of
tasks, Missions or AI text). No signing material in the repository.

## Accessibility and adaptive UI

Semantic headings, content descriptions, 48 dp minimum targets, sp typography (tested at 160 %
font scale on a 320 dp width), the duration dial as an adjustable slider (TalkBack `setProgress`,
custom actions, arrow keys). The timer exposes minute-level descriptions ("Noch 18 Minuten"), never
per-second announcements. Decorative Cosmic Core content is excluded from semantics. Reduced motion
(system "Remove animations" or the app setting) shortens transitions and shows a static Core. Layout
is edge-to-edge with safe insets. Widths ≥ 600 dp use a floating navigation rail, and content keeps a
readable maximum width. Focus has a landscape layout.

## Cosmic Core

A native Canvas renderer (`core/designsystem/cosmic`) translated from the PWA's v17.9 architecture:
the PWA orbit table and palette, front/rear layering, the PWA's per-pixel core sphere (`bakeCore`,
baked once per size off the main thread), a readability zone that keeps front orbits off the timer,
and particles as a closed-form function of one clock (no simulation). It draws in two layers (a
static deep field and the animated system in its own graphics layer), so a frame never re-records
the rest of the screen. One frame loop runs only while the screen is at least STARTED and motion is
allowed; reduced motion draws one static frame. Quality tiers HIGH → BALANCED → LOW (fewer particles,
then the primary ring only at ~30 fps) start from the device class and step down on sustained slow
frames, never up. Session timing never depends on it. See [performance](docs/performance-v18.1.md).

## Testing

`./gradlew testDebugUnitTest :core:domain:test` runs the JVM suite (v20: 364 tests; v19.1: 299) (domain rules, Room DAO,
relationships, transactions, rollback and the 1→2 and 2→3 migrations, DataStore defaults and
migration, the Session owner incl. process recreation and reboot, the reminder scheduler,
notifications under Robolectric, protection gateways, the Worker client against MockWebServer,
Compose screens, and end-to-end app flows on Robolectric with the real Hilt graph). The app flow
tests write screenshots to `app/build/screenshots/`. v18.1 adds tests for the monitor owner, Cosmic
Core tiers and reduced motion, bottom-bar label fit at 320 dp / 130 % / 160 %, Home states, History
period summary, the app picker and the Focus screen. v19 adds the Mission rules, Zeitplan and
calendar, the generation and adaptation owners (cancel and late-answer races), the Room 2→3
migration and draft repository, the Worker v19 mapping and photo pipeline, Mission Session
recovery, the Creation, generation, Briefing, Plan, review and Home Mission screens, and an app flow
that runs a Mission step as a real Session (screenshots also in `feature/*/build/screenshots/`).
v20 adds the insights owner, History order and labels, backup integrity against a digest written
by the PWA exporter, the export round trip, the Room 3→4 migration, the data wipe and the eraser
with the real Session owner, rename, the Worker `sourceAnalysis`/tools mapping, the camera target,
dictation and capture recovery, the History/Insights/Data screens, and an app flow that checks
Activity, Fokus-Rhythmus, Insights and full deletion on the real graph (screenshots 22–34).
There are no instrumented or emulator tests yet; the physical-device checklist is
[device-smoke-test.md](docs/device-smoke-test.md).

## Known limitations and OEM notes

- Not tested on a physical device yet; Robolectric is not a device. The only emulator available to
  v18.1 (software CPU emulation, no KVM) kept restarting `system_server` and logged System UI ANRs
  without the app installed, so no in-app emulator measurement exists (see performance doc).
- Usage events can arrive late on some OEM builds. Aggressive battery managers (several OEMs) can
  delay or drop inexact alarms and WorkManager jobs, and can kill foreground services.
- Work-profile apps are not listed in the app picker, and the personal profile's Usage Access does
  not see them.
- Android 15+ turns `setInterruptionFilter` into an implicit app-owned DND mode; an explicit
  `AutomaticZenRule` integration is future work.
- The `specialUse` foreground-service type needs a Play Console declaration; Play eligibility has
  not been reviewed.
- `applicationId` `io.github.illiapohukai.focusmode` (debug: `.debug`) may be changed before any store release.
