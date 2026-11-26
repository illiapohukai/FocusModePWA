# Parity matrix v20 (PWA ↔ Android)

Evidence-based comparison of the current PWA (repository root, v17.17) and the Android app
(`android/`, starting point `ce219ba` v19.1, result v20). Every row was checked against source
code on both sides, not against screen titles. "Before" is the v19.1 state; "Final" is the v20
state. Visual details are in [visual-parity-v20.md](visual-parity-v20.md); open items in
[remaining-gaps-after-v20.md](remaining-gaps-after-v20.md).

**Status values**: Parity · Native equivalent · Partially implemented · Missing · Intentionally
different · Deferred · Not applicable · Requires device validation.
**Difference classes**: A missing feature · B incomplete port · C visual drift · D native
adaptation · E PWA limitation · F unverified behavior · G deferred product feature.
**Test column**: JVM = unit/Room/Robolectric/Compose test on the JVM (no emulator, no device).

## PWA screens and overlays (all classified)

| PWA screen / overlay | PWA owner | Android owner | Final status |
|---|---|---|---|
| `app-screen-home` | `index.html`, `session-history.js`, `mission-management.js`, `mission-calendar.js` | `feature/home` `HomeScreen` | Parity (League preview deferred) |
| `app-screen-quicksetup` | `quick-focus.js` | `feature/quickfocus` | Parity / Native equivalent (honest protection copy) |
| `app-screen-distractions` | `navigation.js` (`openDistractionPicker`) | `feature/settings` `AppSelectionRoute` | Native equivalent (real launchable apps, no blocking claim) |
| `app-screen-goal` | `ai-service.js`, `mission-renderer.js` | `feature/missions` `MissionCreateScreen` | Parity (v20: camera, dictation) |
| `app-screen-filepicker` | demo sheet (`selectDemoFile`) | — | Not applicable (PWA demo; Android uses the system photo picker and camera) |
| `app-screen-analyzing` | `mission-operator.js` | `MissionGenerateScreen` | Parity |
| `app-screen-confirm` (Briefing) | `ai-service.js` `renderAIConfirmation` | `MissionBriefingScreen` | Parity (v20: material facts, tools, apps) |
| `app-screen-plan` (Missions root) | `mission-renderer.js` `renderAIPlan` | `MissionPlanScreen`, `MissionsScreen` | Parity (v20: tools, rename); route geometry intentionally different |
| `app-screen-result` | `session-finalization.js`, `optional-adaptation.js` | `feature/result` | Parity / Intentionally different (Quick Focus result screen) |
| `app-screen-feedbackprocessing`, `app-screen-update` | `optional-adaptation.js` | `ResultScreen` adaptation states | Native equivalent (states inline, no fake progress stages) |
| `app-screen-session-recovery` | `recovery-controller.js` | `feature/focus` Recovery UI | Parity / Intentionally different (reboot-only freeze) |
| `app-screen-timer` | `session-runtime.js`, `cosmic-core.js` | `feature/focus`, `core/designsystem/cosmic` | Parity / Native equivalent |
| `app-screen-history` | `session-history.js`, `focus-insights.js` | `feature/history` `HistoryScreen` | Parity (v20: Fokus-Rhythmus, nodes, Mission identity) |
| `app-screen-stats` ("Dein Fokusverlauf") | `focus-insights.js` `computeInsights` | `feature/history` `InsightsScreen` (new) | Parity (League link deferred) |
| `app-screen-score` (League) | `league.js` (demo data, labelled) | — | Deferred (future server-authoritative) |
| `app-screen-settings` | `index.html`, `data-management-controller.js` | `feature/settings` `SettingsRoute` | Native equivalent (v20: Daten & Backup, privacy section) |
| `app-screen-data` ("Daten & Backup") | `data-management-controller.js`, `src/data/*` | `DataBackupScreen` (new), `DataImportRoute` | Parity / Native equivalent (v20) |
| `app-screen-premium` | static upsell | — | Deferred (no payments in v20) |
| `app-screen-chat` (AI Coach) | placeholder | — | Deferred (no backend) |
| Focus-choice overlay / Plus sheet | `MotionController.openPlusSheet` | `FocusApp` start sheet | Native equivalent |
| Mission action sheet (open, primary, rename, archive, restore, delete) | `mission-management.js` | Plan screen actions, carousel selection | Parity (v20: rename) |
| PWA install help | `pwa-install-help` | — | Not applicable (native install) |

## Product, data and behavior matrix

Columns: **Area** · **PWA owner** · **Android owner** · **PWA behavior** · **Android before (v19.1)** ·
**Parity** · **Visual** · **Data** · **Tests** · **Class** · **v20 action** · **Final**.

### A. App shell

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Launch / startup state | `bootstrap.js` | `MainActivity`, `AppViewModel` | renders Home, recovers Session | same, routes once to an active Session | Parity | Parity | — | JVM app flow | — | none | Parity |
| Root navigation | `navigation.js` (`ROOT_SECTIONS`) | `FocusApp` `Root` | home → missions → history → settings | same order | Parity | Parity | — | JVM | — | none | Parity |
| Child screens | `LOGICAL_PARENT_MAP` (stats ← history, data ← settings) | NavHost child routes | stats and data are children | no stats, no data screen | Missing | — | — | — | A | `InsightsDestination`, `DataBackupDestination` | Parity |
| Back handling | `navigateBack`, edge swipe | system Back / predictive back | one back per gesture | same | Native equivalent | — | — | JVM | D | none | Native equivalent |
| Plus action | plus sheet | start sheet | action, not a root | same | Parity | Parity | — | JVM | — | none | Parity |
| Deep links | n/a (web) | `DeepLinks`, notifications | — | strict parser | Native equivalent | — | — | JVM | E | none | Native equivalent |
| Insets / edge-to-edge | CSS safe areas | `WindowInsets` | — | done | Native equivalent | — | — | JVM screenshots | D | Insights/Data screens use safe insets | Native equivalent |

### B. Home

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Header (date, brand, Bereit/Offline) | `updateHomeDateHeaderV173` | `HomeScreen.Header` | date, brand, pill, profile avatar | done; avatar replaced by "Profil" tab | Native equivalent | Parity | — | JVM | D | none | Native equivalent |
| First-time / returning / one / two Missions | `resolveHomeEmptyStateV1716`, carousel | `HomeMissionClassifier` | four states | done | Parity | Parity | Room | JVM | — | first-time returns after full deletion (onboarding mark reset) | Parity |
| Completed / archived Mission | classifier | same | returning card | done | Parity | Parity | Room | JVM | — | none | Parity |
| Active Quick / Mission Session banner | recovery router | `SessionBanner` | routes to Session | done | Parity | Parity | Room | JVM | — | none | Parity |
| Quick Focus entry | `openQuickSetupFromHome` | `QuickFocusRow` | row with "25 MIN" | done (last chosen duration) | Native equivalent | Parity | DataStore | JVM | D | none | Native equivalent |
| Today summary | `refreshHomeStats` | `TodayCard` (`ProgressCalculator`) | minutes, Sessions, streak; cells open History | values done; cells not interactive | Partially implemented | Parity | Room | JVM | B | card opens History; week minutes use the shared formatter | Parity |
| Weekly goal "von 4 Sitzungen" | `refreshHomeStats` | — | fixed goal of 4 | not shown | Intentionally different | — | — | — | D | none (no invented goal) | Intentionally different |
| Mission calendar | `mission-calendar.js` | `MissionCalendarCard`, `MissionCalendar` | deadlines, starts, focus days | done | Parity | Parity | Room | JVM | — | none | Parity |
| Reminders card | — | `RemindersCard` | — | done | Native equivalent | — | Room | JVM | E | none | Native equivalent |
| Activity timeline | `renderHomeActivityTimelineV173` | `ActivityTimeline` | 6 rows grouped by day, node shapes, Mission title + tone, start time | 3 flat rows, text badge only, no Mission context | Partially implemented | Visual drift | Room | JVM | B, C | grouped day timeline, outcome nodes, Mission tone, PWA empty copy | Parity |
| League preview | `league.js` | — | demo ranking | — | Deferred | — | — | — | G | none | Deferred |

### C. Quick Focus and Session

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Task, dial 5–60/5, `MM:00` | `quick-focus.js` | `feature/quickfocus` | one duration owner | done | Parity | Parity | DataStore | JVM | — | none | Parity |
| Default duration | in-memory 25 per page load | `defaultQuickFocusMinutes` (last chosen) | resets to 25 | remembers last | Intentionally different | — | DataStore | JVM | E | none; no separate Settings value (would create a second owner) | Intentionally different |
| Protection summary | "N Apps blockiert" | honest summary | blocking claim | honest | Intentionally different | Parity | Room | JVM | E | none | Intentionally different |
| Start, Session creation | `startSession` | `FocusSessionManager` | one launch | done | Parity | — | Room | JVM | — | none | Parity |
| Active Focus, Cosmic Core | `session-runtime.js`, `cosmic-core.js` | `feature/focus`, cosmic renderer | live timer, core | done | Parity | Parity | Room | JVM | — | none | Parity |
| End flow, < 1 min cancel | `endSessionByUser` | `FinalizationPolicy` | cancel below 1 min | done (+ partial outcome) | Intentionally different | Parity | Room | JVM | D | none | Intentionally different |
| Recovery | `recovery-controller.js` | Session owner | reload freezes | runs in background; reboot freezes | Intentionally different | Parity | Room | JVM | E | full deletion abandons a Session without History | Intentionally different |
| Result | home return / result screen | `feature/result` | Mission review; Quick returns Home | Result screen for both | Intentionally different | Parity | Room | JVM | D | none | Intentionally different |

### D. Missions

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Goal, type, confidence, Zeitplan, calendar | `mission-renderer.js` | `MissionCreateScreen`, `MissionSchedulePlanner` | full form | done | Parity | Parity | Room draft | JVM | — | none | Parity |
| Photo from gallery | `openRealFilePicker(false)` | `PickVisualMedia` | file input | done | Parity | Parity | no-backup file | JVM | — | none | Parity |
| Camera capture | `openRealFilePicker(true)` (`capture="environment"`) | — | system camera | missing | Missing | — | — | — | A | system camera via FileProvider target owned by `AndroidMissionMaterialStore`; no CAMERA permission; original deleted after re-encoding | Parity (Requires device validation) |
| Dictation | `mockVoiceInput()` (re-types existing text, no recognition) | — | demo only | missing | Missing | — | — | — | A, E | system speech input (`RecognizerIntent`), explicit tap, text lands in the editable field, no RECORD_AUDIO | Native equivalent (Requires device validation) |
| Generation, cancel, errors | `analyzeGoal`, `cancelMissionAnalysis` | `MissionGenerationOwner` | one request | done | Parity | Parity | Room draft | JVM | — | none | Parity |
| Briefing "Aus dem Material erkannt" | `renderAIConfirmation` (`sourceAnalysis`) | — | facts, pages, tasks, confidence, missing info, estimates | dropped by the DTO | Missing | — | — | — | B | DTO + bounded mapping (PWA `AI_TEXT_LIMITS`), Room draft columns, Briefing groups incl. "Im Foto wurde nichts eindeutig erkannt" | Parity |
| Step tools / recommended apps | `requiredTools`, `recommendedApps` (Briefing "Warum so?", Plan "Du brauchst") | — | shown, stored on steps | dropped | Missing | — | — | — | B | stored on steps (Room v4), Briefing chips, Plan "Du brauchst"; apps text only, never selected or blocked | Parity |
| "Noch unklar" | schedule warning + `missingInformation` | schedule warning only | both | partial | Partially implemented | — | Room draft | JVM | B | both listed | Parity |
| Briefing accept / change / discard | `confirmAIPlan` | `MissionBriefingScreen` | one Mission | done | Parity | Parity | Room | JVM | — | none | Parity |
| Plan, next step, Mission Focus | `renderAIPlan`, `prepareLearningSession` | `MissionPlanScreen`, Session owner | dominant current step | done | Parity | Parity | Room | JVM | — | tools on the current step | Parity |
| Route geometry | segmented route + free-form orbital on Home | vertical plotted route | — | vertical | Intentionally different | Native adaptation | — | JVM | D | kept (accessible ordered list, stable at 160 % font) | Intentionally different |
| Review, progress, completion | `commitMissionSessionResult` | `RoomSessionRepository.applyMissionReview` | applied once | done | Parity | Parity | Room | JVM | — | none | Parity |
| Optional adaptation | `optional-adaptation.js` | `MissionAdaptationOwner` | 120 s timeout, retry/continue | 60 s | Intentionally different | Parity | Room | JVM | D | tools/apps of adapted steps kept; timeout audited and kept at 60 s | Intentionally different |
| Rename | `renameMissionV116` (80 chars) | — | sheet "Mission umbenennen" | missing | Missing | — | — | — | A | `MissionRules.rename`, `MissionRepository.rename`, Plan dialog; id, colour, steps, History, reminder keys unchanged | Parity |
| "Als Hauptmission festlegen" | `makePrimaryV116` | carousel page selection → `lastSelectedMissionId` | explicit action | implicit via carousel | Native equivalent | — | DataStore | JVM | D | none | Native equivalent |
| Archive / restore / delete, limit | `mission-management.js` | `RoomMissionRepository` | two active | done | Parity | Parity | Room | JVM | — | none | Parity |
| Mission reminders | — | `ReminderPlanner` | — | done | Native equivalent | — | Room/WorkManager | JVM | E | none | Native equivalent |

### E. History, Activity, Insights

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Filters Heute / Woche (Mon) / Alle | `setHistoryFilter` | `HistoryQueries` | three filters | done | Parity | Parity (short labels) | Room | JVM | — | filter and chart range kept in `SavedStateHandle` | Parity |
| Stable ordering, same-timestamp tie | `sessionRepository.getAll` | `HistoryQueries.ORDER` | newest first | DAO order only | Partially implemented | — | Room | JVM | B | one comparator (end desc, Session id desc) used by History, Activity, Insights | Parity |
| Fokus-Bilanz | `renderSessionHistory` | `HistoryScreen.Summary` | period totals and outcome counts | done (computed in the ViewModel) | Parity | Parity | Room | JVM | — | moved to `HistoryQueries.periodSummary` (one owner) | Parity |
| Fokus-Rhythmus (7 Tage / 4 Wochen) | `FocusInsights.renderChart` | — | stacked bars, average, best, legend, trend, tooltip | missing | Missing | — | — | — | A | `FocusInsights.series` + Compose Canvas `FocusRhythmCard`, accessible per-bar descriptions, inline detail instead of a floating tooltip | Parity |
| Row: Mission identity, step | `createHistoryCard` (`modeLabel · Schritt 02`) | — | Mission meta line | type chip only | Partially implemented | Visual drift | Room | JVM | B | "Mission-Titel · Schritt 02" in the Mission tone | Parity |
| Outcome presentation | badges with check / x icons | pill with check icon | coloured pills | coloured pills | Visual drift | Visual drift | — | JVM | C | node shapes (filled / half / open) plus German label; no crosses or emoji | Native equivalent (shape-coded) |
| Empty states | `createHistoryEmpty` | `EmptyState` | four copies | partial | Partially implemented | — | — | JVM | B | PWA copies and actions | Parity |
| Insights screen | `computeInsights`, `refreshStatsScreen` | — | total, average, completion rate, today, consistency grid, best values, Session types | missing | Missing | — | — | — | A | `FocusInsights.summary` + `InsightsScreen`; all values local and labelled unverified | Parity |
| One stats owner | `focus-insights.js` | `ProgressCalculator` + `FocusInsights` | one module | Home and History each built their own pieces | Partially implemented | — | — | JVM | B | Home, History and Insights read `ProgressCalculator`/`FocusInsights`/`HistoryQueries`/`HistoryFormat` only | Parity |
| Local streak | `computeInsights.streak` | `ProgressCalculator.streak` | today or yesterday | done | Parity | Parity | derived | JVM | — | none | Parity |
| Timezone-correct grouping | local `Date` | stored `localDate` | browser zone | stored day | Native equivalent | — | Room | JVM | D | tested explicitly | Native equivalent |
| Duplicate prevention | `saveFinalSessionOnceV112` | `INSERT IGNORE` | one record per id | done | Parity | — | Room | JVM | — | none | Parity |
| Duration format | "1h 5m" | "1 Std. 5 Min." | — | Android wording | Intentionally different | — | — | JVM | D | none | Intentionally different |

### F. Calendar and reminders

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Deadlines, preparation starts, Home and Creation calendar | `mission-calendar.js` | `MissionCalendar` | local dates | done | Parity | Parity | Room | JVM | — | none | Parity |
| Reminder delivery, reboot, time zone, update | — | `AndroidReminderScheduler` | — | done | Native equivalent | — | Room/WorkManager | JVM | E | full deletion cancels every scheduled occurrence before rows go | Native equivalent (Requires device validation) |
| Empty calendar | compact note | compact note | — | done | Parity | Parity | — | JVM | — | none | Parity |

### G. Settings

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| "Dein Fokus-System" summary | `refreshDataManagementUI` | `SystemHero` | counts | done | Parity | Parity | Room | JVM | — | none | Parity |
| Missionen verwalten | settings row | Missions tab | navigation | tab | Native equivalent | — | — | — | D | none | Native equivalent |
| Fokus-Schutz & Apps, intensity | static "Mittel" chip | Standard / Geschützt default, monitoring, DND | — | done | Intentionally different | Parity | DataStore | JVM | E | none | Intentionally different |
| Notifications, lock-screen privacy, reminders | — | settings rows | — | done | Native equivalent | — | DataStore | JVM | E | none | Native equivalent |
| Reduced motion | system only | system or "Reduziert" | — | done | Native equivalent | — | DataStore | JVM | D | chart reveal respects it | Native equivalent |
| Permission center | — | `PermissionCenterRoute` | — | done | Native equivalent | — | — | JVM | E | none | Native equivalent |
| Daten & Backup entry, "Letztes Backup" | settings group | — | row + last backup line | import row only behind a dev flag | Partially implemented | — | DataStore | JVM | B | always-visible "Daten & Backup" row and last-backup line | Parity |
| Privacy information | data note | one paragraph | — | partial | Partially implemented | — | — | JVM | B | "Was auf diesem Gerät bleibt" / "Was an die AI geht" section | Parity |
| Language DE/EN | `setLanguage` | German only | two languages | German | Deferred | — | — | — | G | none (no half-translated selector) | Deferred |
| App version | "Über die App" | version line | — | done | Parity | Parity | — | — | — | adds "Offline-fähig · Lokale Speicherung" | Parity |
| AI Coach, Premium | placeholders | — | — | — | Deferred | — | — | — | G | none | Deferred |

### H. Data and backup

| Area | PWA owner | Android owner | PWA behavior | Android before | Parity | Visual | Data | Tests | Class | v20 action | Final |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Export (full) | `data-export.js` | — | `focusmode-backup` v1, SHA-256 digest, safe file name | missing | Missing | — | — | — | A | `AndroidBackupExporter` (same envelope, canonical JSON + SHA-256, epoch-ms timestamps, `android` extension objects), `BackupExportOwner` self-check, SAF `CreateDocument` | Parity |
| Export "Nur Missionen & Einstellungen" | scope `missions` | — | no History | missing | Missing | — | — | — | A | `BackupScope.MISSIONS` | Parity |
| Import preview, transactional apply, rollback | `data-import.js` | `BackupImportRepository` | preview, replace | preview, merge (never overwrite), one transaction | Intentionally different | Parity | Room | JVM | D | import always available in Daten & Backup; preview shows origin and checksum | Intentionally different (merge) |
| Integrity verification | `verifyBundleIntegrity` | — | mismatch / invalid rejected | not checked (noted only) | Missing | — | — | — | A | `BackupIntegrity` (byte-compatible canonical JSON; verified against a PWA-produced digest fixture) | Parity |
| Reserved server fields | rejected | ignored, never imported | reject file | ignore | Intentionally different | — | — | JVM | D | kept (values are never imported either way) | Intentionally different |
| Restore an Android backup | — | — | — | not possible | Missing | — | — | — | A | Zeitplan, tools, review notes, local day, protection mode, progress entries round-trip; up to 5 000 Sessions | Parity |
| Last automatic backup restore | `restoreLastBackup` (localStorage copy) | — | internal backups | — | Not applicable | — | — | — | E | none (Android OS backup and file export) | Not applicable |
| Safe local reset (keep backups) | `safeResetLocalData` | — | missions & sessions | — | Native equivalent | — | — | — | D | covered by full deletion (Android keeps no internal backup copies) | Native equivalent |
| Delete all local data | `deleteAllLocalData` | "Ablenkungsdaten löschen" only | everything | missing | Missing | — | — | — | A | `PersonalDataEraser` (Session owner, one Room transaction, preferences, photo, reminders, notifications) | Parity |
| PWA backup import (v17.16 / legacy) | — | `PwaBackupImporter` | — | done (dev flag) | Parity | — | Room | JVM | — | flag now only gates the Missions shortcut | Parity |
| Room migration | n/a | `Migrations` 3 → 4 | — | v3 | — | — | Room | JVM migration test | — | columns only, no data change | Parity |

### I. Visual system, J. Accessibility

See [visual-parity-v20.md](visual-parity-v20.md). Summary: shared tokens were added for outcome
inks, hero text, chart and consistency colours (no new near-duplicate screen colours); repeated
literals in features were replaced by tokens. Accessibility: chart bars are individual buttons
with full spoken descriptions and a "Stärkster Tag/Woche" state; the consistency grid is one
summary node; outcome nodes are decorative next to their text label; new controls are ≥ 48 dp;
History and Activity layouts were rendered at 320 dp with 160 % font, landscape and tablet.

## Android-native extensions (no PWA equivalent, kept)

Foreground Session service and ongoing notification, end alarm, reboot freeze, notification
permission handling, lock-screen privacy, WorkManager/AlarmManager reminders with reboot and
time-zone resync, Usage Access distraction hints (never blocking), DND integration, real app
picker, system document picker for import and export, system camera and speech input hand-off,
OS backup with `RestoreGuard`, Room migrations.

## Single owners confirmed after v20

Session `FocusSessionManager` · Missions `RoomMissionRepository` (+ `MissionRules`) · History
`RoomSessionRepository` · statistics `ProgressCalculator` + `FocusInsights` · History formatting
`HistoryFormat` / `HistoryQueries` · calendar events `MissionCalendar` · reminders
`AndroidReminderScheduler` · backup validation `PwaBackupImporter` (+ `BackupIntegrity`) · backup
export `AndroidBackupExporter` / `BackupExportOwner` · data deletion `PersonalDataEraser` · draft
photo and camera target `AndroidMissionMaterialStore` · preferences `DataStorePreferencesRepository`.
