# PWA ↔ Android parity (v20)

The PWA (repository root, v17.17) is the reference for product behavior. This file tracks every
material difference. The full v20 audit with every screen, workflow and status is
[parity-matrix-v20.md](parity-matrix-v20.md); visuals are in [visual-parity-v20.md](visual-parity-v20.md)
(and [visual-parity-v18.1.md](visual-parity-v18.1.md) for unchanged screens); open items are in
[remaining-gaps-after-v20.md](remaining-gaps-after-v20.md); the Mission slice is described in
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
| Mission rename (v20) | title only (cleaned, 1–80 characters); id, identity colour, steps, History and reminder keys unchanged | `renameMissionV116` |
| Material facts (v20) | Worker `sourceAnalysis` kept in the draft (Room v4): "Aus dem Material erkannt" / "Aus deinem Ziel erkannt", "Geschätzt", "AI-Erkennung", missing information under "Noch unklar", unreadable-photo note | `renderAIConfirmation` |
| Step tools and apps (v20) | `requiredTools` / `recommendedApps` stored per step (Room v4), Briefing chips, Plan "Du brauchst"; apps are text only | `normalizeAIPlan`, `renderAIPlan` |
| Camera (v20) | system camera into a FileProvider cache target, same re-encoding, original deleted; no CAMERA permission | `openRealFilePicker(true)` |
| Fokus-Rhythmus (v20) | 7 Tage / 4 Wochen, Mission/Quick split, average, best period, trend, accessible bars | `FocusInsights.buildSeries`, `renderChart` |
| Dein Fokusverlauf (v20) | total, average, completion rate, today, Konstanz grid, Bestwerte, Sitzungsarten | `computeInsights`, `refreshStatsScreen` |
| History rows and Activity (v20) | Mission title and step, outcome node shapes, day-grouped Activity with Mission tone, PWA empty states | `createHistoryCard`, `renderHomeActivityTimelineV173` |
| Backup export (v20) | `focusmode-backup` v1, full or "Nur Missionen & Einstellungen", SHA-256 over the canonical payload (byte-compatible both ways), safe file name, self-check before writing | `data-export.js` |
| Backup integrity on import (v20) | mismatch and malformed digests rejected; verified / absent shown in the preview | `verifyBundleIntegrity` |
| Delete all local data (v20) | Session dropped without History, one Room transaction, preferences reset (technical metadata kept), draft photo, reminders cancelled, notifications removed | `deleteAllLocalData` |

## Partially implemented

| Area | State |
|---|---|
| Backup content | selected apps, Geplanter Fokus, reminder state and a running Session are not part of the file (stated on the export screen). |
| Settings import | an imported backup never changes this device's settings (merge import). |
| Reminders for Missions | from the stored deadline and preparation start of active Missions with open steps (both are set by v19 Mission Creation). |

## Deferred

Leagues, score and the League links on Home and in Insights (server-authoritative in the future),
AI Coach, Premium, language switch (German stays authoritative), home pull-refresh (coffee
operator), the free-form orbital Mission route, managed-device enforcement, an explicit
`AutomaticZenRule` DND integration, instrumented and device tests, a formatting check in CI
(`.editorconfig` only).

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
| Adaptation timeout | 120 s | 60 s | audited in v20: below the client's 70 s call timeout; no Worker budget for `/adapt-mission` is documented (create-mission: 35 s / 47 s) |
| Dictation | `mockVoiceInput()` re-types the existing text (demo) | real system speech input on an explicit tap; text lands in the editable field; no RECORD_AUDIO | native equivalent of a placeholder control |
| Outcome indicators | coloured pills with check / x icons | node shapes (filled / half / open) with a German label | outcome never by colour alone, no crosses |
| Import policy | replaces local data | merges, never overwrites an existing id | an import can never destroy local data |
| Reserved server fields in a backup | file rejected | values ignored (never imported) | same outcome without failing legacy files |
| Android backups | — | keep up to 5 000 Sessions (web backups: newest 500, as in the PWA) | Android History is not capped at 500 |
| Duration format | "1h 5m" | "1 Std. 5 Min." | German wording, kept from v18 |
| Mission rename UI | bottom sheet | dialog from the Plan top bar | Material pattern |
| Delete-all confirmation | inline card | alert dialog | Material pattern, explicit destructive button |
| Review default outcome | "Teilweise" preselected | "Abgeschlossen" when the planned time elapsed, otherwise "Teilweise" | matches what the Session already measured |
| Backup step ids | n/a | colliding step ids are remapped with their History links | prevents overwriting another Mission's step |
| Home calendar and reminders | calendar only | calendar plus the upcoming-reminders card | Android delivers real reminders |

