# v19 Android Missions

The native Mission vertical slice: Home → Mission Creation → AI generation → Mission Briefing →
Mission Plan → next step → Mission Focus Session → Result → progress update → optional plan
adaptation → Home. The current PWA (repository root) is the product specification; native
differences are listed in [pwa-parity.md](pwa-parity.md).

## Architecture and single owners

| Concern | Owner | Module |
|---|---|---|
| Missions, steps, generation draft, progress entries (persistence) | `RoomMissionRepository` (`MissionRepository`) | `core:data` |
| Mission rules (step transitions, review, acceptance, adaptation replacement, archive/restore) | `MissionRules` (pure) | `core:domain` |
| Generation request lifecycle (start, cancel, late-result guard, accept, discard, draft edits) | `MissionGenerationOwner` | `core:domain` |
| Optional adaptation request | `MissionAdaptationOwner` | `core:domain` |
| Zeitplan (start modes, validation, payload) | `MissionSchedulePlanner` (pure) | `core:domain` |
| Calendar events (Home calendar and Creation planner) | `MissionCalendar` (pure) | `core:domain` |
| Home Mission state (first-time / returning / one / two) | `HomeMissionClassifier` (pure) | `core:domain` |
| Identity colors | `MissionThemes` (PWA `resolveMissionThemesV177`, incl. draft preview) | `core:domain` |
| Draft photo (read, check, re-encode, store) | `AndroidMissionMaterialStore` (`MissionMaterialStore`) | `core:network` |
| Worker transport | `WorkerMissionGenerationRepository`, `WorkerMissionAdaptationRepository` | `core:network` |
| Focus Session (unchanged owner) | `FocusSessionManager` | `core:session` |
| Mission result application (History + progress + step in one transaction) | `RoomSessionRepository.applyMissionReview` | `core:data` |
| Reminders (unchanged owner) | `AndroidReminderScheduler` via `ReminderSyncCoordinator` | `core:reminders` |

ViewModels never touch DAOs and keep no Mission copy: they observe the repository Flows. Progress
is one value (`Mission.progressPercent`, PWA rounding) read by Home, Plan, Result and Missions.

## Mission states

| State | Where it lives | Meaning |
|---|---|---|
| Draft | `mission_drafts.phase = EDITING` | form being filled (also after a cancel) |
| Generating | `GENERATING` | exactly one Worker request is running |
| GenerationFailed | `FAILED` (+ safe error code, optional support id) | inputs kept, retry is explicit |
| Briefing | `BRIEFING` (+ validated plan rows) | plan shown; no Mission exists yet |
| Active | `missions.status = active` | created by "Mission übernehmen" |
| Completed | `completed` | the final step was completed |
| Archived | `archived` (+ `archived_step_id`) | off Home; can be restored if the limit allows |

Step states stored: `pending`, `active` (current), `completed`. Derived live (`MissionRules.stepStates`):
`IN_PROGRESS` (a running Session), `NEEDS_REVIEW` (a finished Session awaiting its result). Skipping
is not a PWA action and is not offered.

## Room schema 2 → 3

Explicit `MIGRATION_2_3` (no destructive fallback), exported schema `core/data/schemas/.../3.json`:

- `missions`: `start_mode`, `preferred_weekdays` (bit mask, default 0), `preferred_session_minutes`,
  `estimate_minutes`, `summary` (default `''`), `schedule_warning`, `archived_step_id`.
- `session_results`: `review_note`.
- `mission_drafts` (single slot) and `mission_draft_steps` (FK, cascade) — the generation draft.
- `mission_progress` (PK `session_id`, FK `mission_id` cascade, index) — applied Session results.
- Backfill: already reviewed Mission Sessions of existing Missions are copied into `mission_progress`.

Every v18.1 row (History, active Session, reminders, selected apps, schedules, DND record,
distractions, Missions and steps) keeps its values; `MigrationTest` proves it on a real v2 database
created from the exported v2 schema.

Step ids are global primary keys. Stored Missions therefore always get local step ids; AI ids are
transport-only. The PWA-backup import now remaps colliding step ids (and the imported Sessions that
reference them) instead of overwriting another Mission's step.

## Mission Creation

PWA `app-screen-goal`: goal text (bounded; above 6000 characters the plan button explains the
limit), one optional photo, Mission type, confidence, Zeitplan (deadline in an inline calendar,
start mode with previews and the recommended mode, preferred weekdays, session length), privacy
note, "Mission planen". Everything is persisted in the Room draft through
`MissionGenerationOwner.edit` (goal text debounced, mirrored in `SavedStateHandle`), so input
survives backgrounding, process death, a declined picker, a failed or cancelled request.

## Inline calendar

`MonthCalendar` (design system) renders; `MissionCalendar` derives. German, Monday first, month
navigation (never before the current month in the planner), local dates only (no UTC shift).
Shown: today, the new deadline (filled), the new preparation start (ring) and range, active
Missions' deadlines and starts and completion days in their identity tone. A day that already holds
Mission dates opens its details first and is chosen only with "Als Frist wählen"; an empty
selectable day is chosen directly. Each cell has a full spoken label.

## Attachments

System photo picker (`PickVisualMedia`, no storage permission, one image). Before decoding: size
≤ 20 MB, real format by signature (JPEG/PNG/WEBP) matching the declared type, declared
dimensions ≤ 12 000 px per side and ≤ 40 MP. Decoding is down-sampled, the EXIF orientation is
applied to the pixels, and the image is re-encoded (JPEG 84/76 %, ≤ 1800 px; small PNGs stay PNG),
which drops EXIF, GPS and camera metadata. The upload must be ≤ 1.5 MB. Only the processed bytes are
kept, in no-backup app storage, and deleted when the Mission is accepted or the draft discarded.
Nothing is uploaded before "Mission planen"; no file name leaves the device (`material.jpg`).

## Worker boundary

Unchanged production route and contract (`BuildConfig.WORKER_BASE_URL`, HTTPS only):
`POST /create-mission` multipart (`goalText`, `missionType`, `confidence`, optional `schedule`
JSON, optional `image`) and `POST /adapt-mission` JSON (PWA `requestAdaptation()` shape, this
Mission only). No Gemini key exists on Android. Never sent: streak, History beyond this Mission's
progress entries, other Missions, selected apps, device data, installed apps, settings.

Error mapping: offline/IO → offline; 400/422/other 4xx → invalid request (not retried); 401/403 →
refused; 402 → AI billing unavailable (not retried); 408/504/524/client timeout → timeout; 413 →
file too large; 415 → unsupported file; 429 → rate limited; 500/502/503 → unavailable; unreadable
or invalid body → invalid response. Only a validated request id (`^[A-Za-z0-9_-]{4,64}$`) is shown
as "Anfrage-ID"; server messages and exceptions are never shown or logged. No redirects, no
automatic retries, no HTTP cache, no logging interceptor, bounded response body (512 KB).

## Generation lifecycle and cancellation

`MissionGenerationOwner.start()` validates locally (input, length, type, confidence, Zeitplan,
active limit, connectivity) before any paid request, moves the draft to GENERATING, and runs one
request in the application scope. Each start and cancel advances a token; the outcome is written
only if the token is unchanged and the draft is still the same draft in GENERATING
(`saveDraftIf`). Therefore:

- "Abbrechen" cancels the HTTP call, keeps every input, returns to the form, never retries.
- A late Worker answer after a cancel (or after a newer request) is dropped: no Briefing, no Mission.
- A double tap cannot start a second request; a second Briefing tap cannot create a second Mission
  (the Mission id is fixed in the draft and inserted with ABORT).
- After process death a GENERATING draft becomes FAILED (`Die Analyse wurde unterbrochen …`); it is
  never re-sent automatically.

The generation screen shows the approved pixel operator (byte-identical PWA sprite sheets in
`feature/missions/src/main/res/drawable-nodpi`) driven by the PWA frame metadata: the work loop runs
while the request is pending; the success segment plays once only after the plan was fully
validated and stored, joining at the matching frame (no restart), then holds 450 ms; failure and
cancel stop immediately. Reduced motion shows one still frame and a 200 ms crossfade. The operator
is excluded from accessibility; one polite live status carries the meaning. No percentages, no
invented backend stages.

## Response validation

`MissionPlanMapper` (PWA `normalizeAIPlan`, stricter where truncation would be unsafe): plan object
and non-empty title required; 1–6 steps (more is rejected, not truncated); every step needs title,
task and a finite 5–60 minute duration; dates must be valid ISO dates not after the chosen deadline;
text is control-stripped and bounded; unsafe or duplicate step ids are regenerated; duplicate steps,
markup, script, inline handlers or executable URL schemes reject the plan; unknown fields are
dropped; unknown enums fall back to `custom`. The Mission is written only after full validation,
in one transaction; a failure keeps the draft and shows safe German copy with retry.

## Mission Briefing

Hero in the identity tone the Mission will keep (`MissionThemes.preview`), deadline chip, title,
subject, explanation, summary (steps, estimated total, level), preparation route (start → days →
deadline, rhythm), "Noch unklar" warning, ordered steps (first step marked, "Warum so?"), "So wurde
der Plan erstellt". Actions: "Mission übernehmen", "Angaben ändern" (back to the form; any input
change drops the stale plan; a new plan needs an explicit tap), "Verwerfen" (confirmed).

## Active-Mission limit

Two active Missions (PWA). Checked before a paid request, in the Briefing, and inside the Room
transactions (`create`, `acceptDraft`, `restore`). Completed and archived Missions do not count.
At the limit, Creation, Briefing and Missions explain the choice (complete or archive) and link to
Missions; nothing is deleted or hidden.

## Mission Plan

Tone hero (type, deadline, title, progress percent and status, a segment per step as in the PWA,
open steps, remaining time, next focus), then the plotted route: one node per step on a continuous
line (done = tone, current = ringed, future = dashed). The current step is the dominant card with
"Schritt n starten" (or "Sitzung öffnen" / "Ergebnis bewerten" when a Session is running or awaits
its review). Steps are an ordered collection for TalkBack with spoken state. Archive (confirmed),
restore (limit permitting), delete (confirmed, explains what is removed).

## Mission Focus and Recovery

"Schritt starten" calls the unchanged Session owner (`startMissionStep`) with the user's default
protection mode; selected apps, DND and notification settings apply as for Quick Focus. The
Session carries Mission id, step id, task, duration, "Schritt n von m" and the subject; the Focus
screen shows the Mission relationship. Recovery, expiry while away, reboot freeze, notification
taps and rotation are the v18.1 Session paths; the active row keeps the Mission link (FK RESTRICT),
so a recovered Mission Session never turns into Quick Focus.

## Result and progress application

A Mission Session with at least one counted minute waits for the review (PWA): "Abgeschlossen",
"Teilweise" or "Abgebrochen", a 1–5 confidence scale and an optional note. One transaction updates
the History record (outcome, rating, note), inserts the progress entry (primary key = Session id)
and moves the Mission:

- Abgeschlossen: the step completes, progress advances once, the next step becomes current; after
  the final step the Mission is completed.
- Teilweise: focused time counts; the same step stays current; adaptation is offered.
- Abgebrochen: focused time already measured stays in History (PWA); the step stays current;
  progress does not move.

A second review of the same Session (double tap, restart) changes nothing.

## Optional adaptation

Implemented against the PWA contract (docs/API_CONTRACTS.md §3): offered only on the review when
open steps would remain, never automatic. The result is committed first; then one request with this
Mission's fields only. A valid answer (1–20 complete steps of 5–60 min, no markup) replaces only the
open steps in one transaction, only if the Mission did not change meanwhile; completed steps,
History and earlier results never change. Failure or offline keeps the plan and offers "Erneut
versuchen" / "Ohne Anpassung fortfahren". No live adaptation call was made from this environment.

## Home

- First-time: the onboarding hero (AI PLAN card, "Mission erstellen") until the first Mission
  exists. A one-way DataStore mark (`home_mission_onboarding_done`) plus Mission/History evidence
  keep it from returning after completion, archive or deletion.
- Returning without an active Mission: a compact card ("Bereit für ein neues Ziel?", "Neue Mission").
- One active Mission: tone hero (HAUPTMISSION, step counter, next step with minutes), progress on
  the left (percent, n/m steps), "Schritt starten" on the right.
- Two active Missions: a `HorizontalPager` with a peek of the second card, page dots, "Alle ansehen".
- Missionskalender: the same events as the planner plus focus days; a compact centred note when
  there is nothing to show; tapping an event opens its Mission.

## Reminders

Unchanged single scheduler. Preparation-start and deadline reminders come from the stored
`preparation_start_date` and `deadline_date` of active Missions with open steps; the coordinator
re-syncs on every Mission change (create, step completion, completion, archive, restore, delete,
adaptation), on time/time-zone change, boot and app update, using stable occurrence keys (no
duplicates). Preferred weekdays never create reminders. With "Nur Fokus-Status" or "Ausblenden",
Mission reminders show no Mission text even in the shade; the lock screen always gets the generic
version.

## Known limitations

- No camera capture or dictation on the goal input (PWA has both); photos come from the picker.
- Briefing does not show the PWA "Aus dem Material erkannt" facts (the Android plan keeps only the
  fields it stores); tools and recommended apps per step are not kept.
- Mission title rename is not offered (PWA has it).
- The plotted route is vertical; there is no free-form orbital animation.
- Adaptation timeout is 60 s (PWA 120 s) to stay below the client's call timeout.
- Worker behaviour was verified only against MockWebServer; no live Worker call was made.

## Verification (JVM only)

- `./gradlew testDebugUnitTest :core:domain:test`: 299 tests, 0 failures (v18.1 baseline: 181).
  Robolectric is not a device; the app flows use the real Hilt graph, Room and DataStore.
- `./gradlew lint` passes; `assembleDebug` and `assembleRelease` (R8, unsigned) build.
- No live Worker call was made: generation and adaptation run against MockWebServer and fakes.
- No emulator or physical-device run.

## Device tests still required

Photo picker on Android 8–12 (fallback picker) and 13+; EXIF rotation from real cameras; large font
and TalkBack walkthrough of Creation, calendar, Briefing, Plan and review; process death during
generation on a real device; reminders after reboot and time-zone change; carousel swipe versus
vertical scroll; OEM battery managers. See [device-smoke-test.md](device-smoke-test.md).
