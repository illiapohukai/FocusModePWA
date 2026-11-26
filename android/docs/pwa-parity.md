# PWA ↔ Android parity (v19)

The PWA (repository root, v17.17) is the reference for product behavior. This file tracks every
material difference. The screen-by-screen visual comparison of v18.1 is in
[visual-parity-v18.1.md](visual-parity-v18.1.md); the v19 Mission slice is described in
[missions-v19.md](missions-v19.md).

## Implemented

| Area | Android | PWA reference |
|---|---|---|
| Quick Focus | task (default "Konzentriert arbeiten", title = task), 5–60 min in steps of 5, static `MM:00`, one duration owner, one launch per tap | `quick-focus.js`, `startConfiguredQuickFocus` |
| Session types | `quick` never touches Missions; `mission` requires a valid Mission and step link (validated in the model and the database) | `session-model.js` |
| Finalize once | one History record per Session id (`INSERT IGNORE` + transaction); terminal statuses completed/cancelled/interrupted | `saveFinalSessionOnceV112` |
| < 1 minute | an end below one counted minute is cancelled (`ended_too_early`); Recovery cannot save below 1 min | `endSessionByUser`, `renderSessionRecovery` |
| Recovery choices | Fortsetzen / Fokuszeit speichern / Verwerfen (discard writes no History) | `recovery-controller.js` |
| Mission review | Mission Sessions wait for the step review; a step completes only on "completed"; never twice | `openRecoveredMissionReview`, `completeStep` |
| Mission rules | at most two active Missions; only the current step can start; safe ids `[A-Za-z0-9_.:-]{2,160}` | `mission-repository.js` |
| Mission identity tones | FNV-1a id hash + collision avoidance, verified against the PWA function | `resolveMissionThemesV177` |
| History | Heute / Woche (Monday) / Alle, newest first, PWA badge labels | `session-history.js` |
| Local streak | counted days (≥ 1 min, countAsFocus), current from today or yesterday, best run | `focus-insights.js` |
| Visual identity | cream, graphite, aubergine surfaces, purple accent, restrained green, Inter, floating nav with raised Plus, Cosmic Core | `styles/*`, `cosmic-core.js` |
| Motion tokens | 90/160/350/390/720 ms, standard/exit/soft-spring easings, reduced durations | `styles/motion.css` |
| Backup import | `focusmode-backup` v1 + legacy shape, v17.17 limits, unverified trust | `data-import.js`, security baseline §3 |
| Worker contract | `/create-mission` multipart fields, timeouts, error mapping, request id | `ai-service.js` |
| Mission Creation (v19) | goal text (6000 chars), one photo, type, confidence, Zeitplan (deadline, start modes with previews and recommendation, weekdays, session length), privacy note, "Mission planen" enabled only with type, confidence and a valid Zeitplan | `app-screen-goal`, `mission-renderer.js` Zeitplan |
| Inline planning calendar (v19) | Monday-first German month, new deadline/start/range, active Missions' deadlines and starts in identity tones, existing dates open details first ("Als Frist wählen") | `mission-calendar.js` planner |
| Photo handling (v19) | JPEG/PNG/WEBP by signature, 20 MB original, header dimension guard (12 000 px, 40 MP), re-encode ≤ 1800 px / 1.5 MB without metadata, generic upload name | `handleMissionFile`, `compressMissionImage` |
| Generation lifecycle (v19) | one request, cancel keeps input, late answers dropped, no automatic retry, safe German errors, support request id | `analyzeGoal`, `cancelMissionAnalysis` |
| Plan validation (v19) | `normalizeAIPlan` bounds, safe unique step ids, no step after the deadline | `normalizeAIPlan` |
| Generation operator (v19) | the approved sprite sheets (byte-identical), PWA frame metadata, work loop, one-shot success after validation, reduced-motion crossfade | `mission-operator.js`, `assets/operator` |
| Mission Briefing (v19) | tone hero, summary, route, "Noch unklar", ordered steps with "Warum so?", "So wurde der Plan erstellt", accept / change | `renderAIConfirmation` |
| Draft identity color (v19) | the Briefing previews the tone the stored Mission keeps | `previewMissionThemeV177` |
| Mission Plan (v19) | hero with progress, segmented route, open steps, remaining time, next focus; dominant current step; "Warum so?" | `app-screen-plan`, `renderAIPlan` |
| Mission review (v19) | Abgeschlossen / Teilweise / Abgebrochen, 1–5 rating, optional note; applied once per Session | `app-screen-result`, `commitMissionSessionResult` |
| Optional adaptation (v19) | explicit, result committed first, this Mission only, open steps replaced only, retry / continue on failure | `optional-adaptation.js` |
| Home Mission states (v19) | first-time hero until the first Mission ever, returning card, one-Mission hero (progress left, start right), two-Mission carousel | `resolveHomeEmptyStateV1716`, `renderMissionHeroV116`, `renderHomeCarouselV116` |
| Home Mission calendar (v19) | deadlines, starts, completions, focus days; compact empty note; events open their Mission | `mission-calendar.js` |
| Archive / restore / delete (v19) | archive keeps the current step; restore respects the limit; delete keeps History | `archiveMissionV116`, `restoreMissionV116` |

## Partially implemented

| Area | State |
|---|---|
| Goal input | no camera capture and no dictation (the PWA offers both); photos come from the system picker. |
| Briefing details | the PWA's "Aus dem Material erkannt" facts, open questions and per-step tools/apps are not kept on Android. |
| Mission editing | renaming a Mission is not offered; changing a plan is done through the optional adaptation. |
| Reminders for Missions | from the stored deadline and preparation start of active Missions with open steps (both are set by v19 Mission Creation). |
| Data export | not implemented (import only). |

## Deferred

Leagues and score (server-authoritative in the future), Focus insights screen and Fokus-Rhythmus
chart, home pull-refresh (coffee operator), managed-device enforcement, an explicit
`AutomaticZenRule` DND integration, instrumented and device tests, a formatting check in CI
(`.editorconfig` only), camera capture and dictation on the goal input, Mission rename.

## Intentionally different

| Topic | PWA | Android | Why |
|---|---|---|---|
| Closing the app during a Session | Document termination freezes the timer; the user resumes later | The Session keeps running in real time (foreground service + ongoing notification); only a **reboot** freezes it at the last checkpoint | A native app can keep a visible Session alive; a web page cannot |
| Early end outcome | quick Session ended early ≥ 1 min → outcome `completed` | outcome `partial` (counts as focus) | The task asks to distinguish completed, partial and canceled; the PWA badge "Teilweise abgeschlossen" already exists |
| Quick Focus result | returns to Home | shows a Result screen (duration, outcome, local streak) | vertical-slice requirement |
| "Apps blockiert" | the PWA shows "N Apps blockiert" (a web app cannot block anything) | never claims blocking: "Ablenkungen überwachen", "App-Auswahl vorbereitet", "Strikte Sperre auf diesem Gerät nicht verfügbar" | honesty; Android consumer apps cannot block |
| Protected Focus end barrier | — | 10-second pause + reason, emergency exit always available | Android protection level |
| Emergency exit | — | counts focused time when ≥ 1 min (`emergency_exit`) | an emergency must not be punished |
| Session timer clock | `Date.now()` | monotonic `elapsedRealtime` + boot counter | survives sleep and wall-clock changes |
| Language | DE + EN | German only | v18 scope |
| Distracting apps | a fixed illustrative list | the user's real launchable apps, stored locally | native capability |
| Session actions | "Blockierung testen" next to "Sitzung beenden" | "Sitzung beenden" only | nothing is blocked on consumer Android, so there is nothing to test |
| Preparation start | not stored with the Mission; the calendar's "Start" is the creation day | stored (`preparation_start_date`) with the Zeitplan; the calendar shows it as "Vorbereitung", falling back to the creation day | needed for the preparation reminder and the honest calendar |
| Step ids | the AI id is kept when safe | always a local id when the Mission is stored | Android step ids are global keys; untrusted ids never become keys |
| More than six AI steps | truncated to six | the plan is rejected (retry possible) | truncation would silently drop the plan's final steps |
| Markup in AI text | escaped at render time | the plan or adaptation is rejected as a whole | it is stored and exported, not only rendered |
| Briefing primary action | "Plan übernehmen" | "Mission übernehmen" | v19 task wording; same behavior |
| Leaving the generation screen | any route change cancels | Back or "Abbrechen" cancels; backgrounding keeps the one request running and its result lands in the draft | a native screen survives backgrounding; process death marks the draft interrupted |
| Adaptation timeout | 120 s | 60 s | stays below the Android client's 70 s call timeout |
| Review default outcome | "Teilweise" preselected | "Abgeschlossen" when the planned time elapsed, otherwise "Teilweise" | matches what the Session already measured |
| Backup step ids | n/a | colliding step ids are remapped with their History links | prevents overwriting another Mission's step |
| Home calendar and reminders | calendar only | calendar plus the upcoming-reminders card | Android delivers real reminders |

